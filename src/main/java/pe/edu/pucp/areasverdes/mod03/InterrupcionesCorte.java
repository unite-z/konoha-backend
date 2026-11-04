package pe.edu.pucp.areasverdes.mod03;

import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interrupción y traslado del corte por molestia (AV-258, HU-009, CU-009 y RN-004): el jefe detiene
 * el corte en una zona y lo traslada a otra zona pendiente de la ruta sin restricción ese día. Se
 * descuenta lo que queda de la jornada (de la hora de la interrupción a las 17:00) en la zona
 * detenida. Puede registrar la molestia también como restricción (CU-009 6a).
 */
@Service
public class InterrupcionesCorte {

    /** Fin de la jornada de corte: lo que queda hasta esta hora se descuenta. */
    static final LocalTime FIN_JORNADA = LocalTime.of(17, 0);

    public record ZonaAlternativa(String zona, int pendientes, String avisoHoy) {}

    public record NuevaInterrupcion(
            @NotBlank(message = "Indica la zona afectada") String zonaAfectada,
            @NotBlank(message = "Indica la zona alternativa") String zonaAlternativa,
            @NotNull(message = "Indica la hora") OffsetDateTime hora,
            @NotBlank(message = "Indica el motivo")
                    @Size(max = 300, message = "Máximo 300 caracteres")
                    String motivo,
            boolean avisoProveedor,
            boolean registrarRestriccion) {}

    public record Interrupcion(
            long id,
            String zonaAfectada,
            String zonaAlternativa,
            OffsetDateTime hora,
            String motivo,
            int minutosDescontados,
            boolean avisoProveedor,
            boolean registradaComoRestriccion) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock reloj;

    public InterrupcionesCorte(NamedParameterJdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /**
     * Zonas de la ruta con jardines pendientes y sin prohibición hoy; {@code aviso_hoy} nombra la
     * restricción de aviso de hoy en la zona, si la hay.
     */
    public List<ZonaAlternativa> zonasAlternativas(
            long programacion, UsuarioPlanificacion usuario) {
        Acceso.consultar(usuario);
        return jdbc.query(
                """
                SELECT r.zona, count(*) FILTER (WHERE r.estado = 'Pendiente') pendientes,
                    min(t.nombre) FILTER (WHERE t.efecto = 'Advertencia') aviso_hoy,
                    bool_or(t.efecto = 'Prohibicion') prohibida
                FROM mod03_ruta_corte r
                JOIN mod02_area_verde a ON a.id = r.area_verde_id
                LEFT JOIN mod05_areaverde v ON v.nombre = a.nombre
                LEFT JOIN mod03_restriccion_calendario c ON c.activo
                    AND CAST(:hoy AS date) BETWEEN c.fecha_inicio AND c.fecha_fin
                    AND (c.area_verde_id IS NULL OR c.area_verde_id = v.id)
                LEFT JOIN mod03_tipo_restriccion t ON t.id = c.tipo_restriccion_id
                WHERE r.programacion_id = :programacion
                GROUP BY r.zona
                HAVING count(*) FILTER (WHERE r.estado = 'Pendiente') > 0
                    AND NOT coalesce(bool_or(t.efecto = 'Prohibicion'), false)
                ORDER BY r.zona
                """,
                Map.of("programacion", programacion, "hoy", LocalDate.now(reloj)),
                (rs, n) ->
                        new ZonaAlternativa(
                                rs.getString("zona"),
                                rs.getInt("pendientes"),
                                rs.getString("aviso_hoy")));
    }

    @Transactional
    public Interrupcion registrar(
            long programacion, NuevaInterrupcion n, UsuarioPlanificacion usuario) {
        Acceso.gestionar(usuario);
        List<String> estado =
                jdbc.queryForList(
                        "SELECT estado FROM mod03_programacion_servicio WHERE id = :id",
                        Map.of("id", programacion),
                        String.class);
        if (estado.isEmpty()) {
            throw new ErrorPlanificacion(404, "ciclo-no-encontrado", "No existe ese ciclo");
        }
        if (!List.of("Programada", "EnCurso").contains(estado.getFirst())) {
            throw new ErrorPlanificacion(409, "ciclo-cerrado", "Ese ciclo ya terminó");
        }
        List<String> zonas =
                jdbc.queryForList(
                        "SELECT DISTINCT zona FROM mod03_ruta_corte WHERE programacion_id = :id",
                        Map.of("id", programacion),
                        String.class);
        if (!zonas.contains(n.zonaAfectada())) {
            throw new ErrorPlanificacion(400, "zona-invalida", "Esa zona no está en la ruta");
        }
        if (n.zonaAfectada().equals(n.zonaAlternativa())
                || zonasAlternativas(programacion, usuario).stream()
                        .noneMatch(z -> z.zona().equals(n.zonaAlternativa()))) {
            throw new ErrorPlanificacion(
                    409,
                    "sin-zona-libre",
                    "La zona alternativa no tiene jardines pendientes libres hoy: reprograma");
        }
        OffsetDateTime hora = n.hora().atZoneSameInstant(LIMA).toOffsetDateTime();
        OffsetDateTime fin = hora.toLocalDate().atTime(FIN_JORNADA).atZone(LIMA).toOffsetDateTime();
        int minutos = (int) Math.max(0, Duration.between(hora, fin).toMinutes());
        boolean registrada =
                n.registrarRestriccion()
                        && registrarComoRestriccion(programacion, n, hora.toLocalDate(), usuario)
                                > 0;
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod03_interrupcion_corte (programacion_id, zona_afectada,
                            zona_alternativa, hora, motivo, minutos_descontados, aviso_proveedor,
                            registrada_como_restriccion, usuario_id)
                        VALUES (:programacion, :afectada, :alternativa, :hora, :motivo, :minutos,
                            :aviso, :restriccion, :usuario)
                        RETURNING id
                        """,
                        new MapSqlParameterSource()
                                .addValue("programacion", programacion)
                                .addValue("afectada", n.zonaAfectada())
                                .addValue("alternativa", n.zonaAlternativa())
                                .addValue("hora", hora)
                                .addValue("motivo", n.motivo().trim())
                                .addValue("minutos", minutos)
                                .addValue("aviso", n.avisoProveedor())
                                .addValue("restriccion", registrada)
                                .addValue("usuario", usuario.id()),
                        Long.class);
        return de(programacion).stream().filter(i -> i.id() == id).findFirst().orElseThrow();
    }

    /** Las interrupciones del ciclo, de la más antigua a la más reciente. */
    List<Interrupcion> de(long programacion) {
        return jdbc.query(
                """
                SELECT * FROM mod03_interrupcion_corte WHERE programacion_id = :id ORDER BY hora, id
                """,
                Map.of("id", programacion),
                (rs, n) ->
                        new Interrupcion(
                                rs.getLong("id"),
                                rs.getString("zona_afectada"),
                                rs.getString("zona_alternativa"),
                                rs.getObject("hora", OffsetDateTime.class)
                                        .atZoneSameInstant(LIMA)
                                        .toOffsetDateTime(),
                                rs.getString("motivo"),
                                rs.getInt("minutos_descontados"),
                                rs.getBoolean("aviso_proveedor"),
                                rs.getBoolean("registrada_como_restriccion")));
    }

    /** Días descontados del ciclo: los minutos de las interrupciones en medias jornadas de 8 h. */
    static double diasDescontados(List<Interrupcion> interrupciones) {
        int minutos = interrupciones.stream().mapToInt(Interrupcion::minutosDescontados).sum();
        return Math.round(minutos / 240.0) / 2.0;
    }

    /**
     * CU-009 6a: la molestia queda también como aviso en los jardines de la zona ese día. Las
     * restricciones aún apuntan al área de MOD-05 ({@code mod05_areaverde}), así que se cruzan por
     * el nombre; un jardín sin su área en MOD-05 no la recibe. Devuelve cuántas se registraron.
     */
    private int registrarComoRestriccion(
            long programacion, NuevaInterrupcion n, LocalDate dia, UsuarioPlanificacion usuario) {
        return jdbc.update(
                """
                INSERT INTO mod03_restriccion_calendario (tipo_restriccion_id, fecha_inicio, fecha_fin,
                    area_verde_id, descripcion, usuario_creacion_id, usuario_modificacion_id)
                SELECT t.id, :dia, :dia, v.id, :motivo, :usuario, :usuario
                FROM mod03_ruta_corte r
                JOIN mod02_area_verde a ON a.id = r.area_verde_id
                JOIN LATERAL (SELECT min(m.id) id FROM mod05_areaverde m WHERE m.nombre = a.nombre) v
                    ON v.id IS NOT NULL
                JOIN mod03_tipo_restriccion t ON t.nombre = 'Actividad extracurricular'
                WHERE r.programacion_id = :programacion AND r.zona = :zona
                """,
                new MapSqlParameterSource()
                        .addValue("dia", dia)
                        .addValue("motivo", recortar(n.motivo().trim(), 200))
                        .addValue("usuario", usuario.id())
                        .addValue("programacion", programacion)
                        .addValue("zona", n.zonaAfectada()));
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
