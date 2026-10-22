package pe.edu.pucp.areasverdes.mod03;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod03.Programador.Bloqueo;
import pe.edu.pucp.areasverdes.mod03.RestriccionesController.Restriccion;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Alta de programaciones (HU-MOD03-004, RN-002 y RN-003). Ante una advertencia exige justificación,
 * que queda como constancia; ante una prohibición, además, confirmación expresa. Sin ellas no
 * guarda.
 */
@Service
public class ProgramacionServicios {

    public record NuevaProgramacion(
            @NotNull(message = "Indica el servicio") Long tipoServicioId,
            Long proveedorId,
            @NotNull(message = "Indica el inicio") LocalDate fechaInicioPrevista,
            @NotNull(message = "Indica el fin") LocalDate fechaFinPrevista,
            @Size(max = 300, message = "Máximo 300 caracteres") String justificacion,
            Boolean confirmarProhibicion) {}

    private record Duracion(boolean ruidoso, Integer maxima, Integer limpieza) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ProgramacionServicios(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long crear(NuevaProgramacion nueva, Usuario usuario) {
        Acceso.gestionar(usuario);
        LocalDate inicio = nueva.fechaInicioPrevista();
        LocalDate fin = nueva.fechaFinPrevista();
        if (fin.isBefore(inicio)) {
            throw new ErrorPlanificacion(
                    400, "periodo-invalido", "El fin debe ser posterior al inicio");
        }
        List<Duracion> servicios =
                jdbc.query(
                        "SELECT t.es_ruidoso ruidoso, d.duracion_maxima_dias maxima,"
                            + " coalesce(d.dias_limpieza, 0) limpieza FROM mod05_tiposervicio t"
                            + " LEFT JOIN mod03_duracion_servicio d ON d.tipo_servicio_id = t.id"
                            + " WHERE t.id = :id AND t.activo AND t.es_periodico",
                        Map.of("id", nueva.tipoServicioId()),
                        new DataClassRowMapper<>(Duracion.class));
        if (servicios.isEmpty()) {
            throw new ErrorPlanificacion(404, "servicio-no-encontrado", "No existe ese servicio");
        }
        Duracion servicio = servicios.getFirst();
        if (servicio.maxima() == null) {
            throw new ErrorPlanificacion(
                    409, "configuracion-incompleta", "Configura antes la duración del servicio");
        }
        Programador.exigirDuracion(
                Programador.dias(inicio, fin), servicio.maxima(), servicio.limpieza());
        List<Restriccion> conflictos =
                jdbc
                        .query(
                                RestriccionesController.CONSULTA.formatted(
                                        "r.fecha_inicio <= :hasta AND r.fecha_fin >= :desde"),
                                Map.of("desde", inicio, "hasta", fin),
                                new DataClassRowMapper<>(Restriccion.class))
                        .stream()
                        .filter(r -> afecta(r, inicio, fin, servicio.ruidoso()))
                        .toList();
        exigirConstancia(conflictos, nueva);
        var datos =
                new MapSqlParameterSource()
                        .addValue("servicio", nueva.tipoServicioId())
                        .addValue("proveedor", nueva.proveedorId())
                        .addValue("estacion", Programador.estacion(inicio))
                        .addValue("inicio", inicio)
                        .addValue("fin", fin)
                        .addValue("usuario", usuario.id())
                        .addValue("justificacion", nueva.justificacion());
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod03_programacion_servicio(tipo_servicio_id, proveedor_id, estacion,
                            fecha_inicio_prevista, fecha_fin_prevista, creado_por_id, usuario_creacion_id,
                            usuario_modificacion_id)
                        VALUES (:servicio, :proveedor, :estacion, :inicio, :fin,
                            :usuario, :usuario, :usuario) RETURNING id
                        """,
                        datos,
                        Long.class);
        for (Restriccion r : conflictos) {
            jdbc.update(
                    "INSERT INTO mod03_confirmacion_restriccion(programacion_id, restriccion_id,"
                            + " usuario_id, justificacion) VALUES (:id, :restriccion, :usuario,"
                            + " :justificacion)",
                    datos.addValue("id", id).addValue("restriccion", r.id()));
        }
        return id;
    }

    /** Cambio de estado con su fecha real (hoy si no se indica): inicio, fin o cancelación. */
    public record Cambio(LocalDate fecha) {}

    private record Transicion(String desde, String hasta, String columna) {}

    private static final Map<String, Transicion> TRANSICIONES =
            Map.of(
                    "inicio", new Transicion("Programada", "EnCurso", "fecha_inicio_real"),
                    "fin", new Transicion("EnCurso", "Completada", "fecha_fin_real"),
                    "cancelacion", new Transicion("Programada", "Cancelada", null));

    /** Aplica la transición (inicio, fin o cancelación) si el estado actual lo permite. */
    @Transactional
    public void cambiar(long id, String accion, LocalDate fecha, Usuario usuario) {
        Acceso.gestionar(usuario);
        Transicion t = TRANSICIONES.get(accion);
        String fechaReal = t.columna() == null ? "" : t.columna() + " = :fecha, ";
        int filas =
                jdbc.update(
                        ("UPDATE mod03_programacion_servicio SET estado = :hasta, %s"
                                        + " fecha_modificacion = now(), usuario_modificacion_id ="
                                        + " :usuario WHERE id = :id AND estado = :desde")
                                .formatted(fechaReal),
                        new MapSqlParameterSource()
                                .addValue("id", id)
                                .addValue("desde", t.desde())
                                .addValue("hasta", t.hasta())
                                .addValue("fecha", fecha)
                                .addValue("usuario", usuario.id()));
        if (filas == 0) {
            throw new ErrorPlanificacion(
                    409,
                    "transicion-invalida",
                    "Solo se puede registrar %s de una programación en estado %s"
                            .formatted(accion, t.desde()));
        }
    }

    private static boolean afecta(Restriccion r, LocalDate inicio, LocalDate fin, boolean ruidoso) {
        return !Programador.conflictos(List.of(Bloqueo.de(r)), inicio, fin, ruidoso).isEmpty();
    }

    /** Escenarios «Aviso por actividad extracurricular» y «Prohibición por examen de admisión». */
    private static void exigirConstancia(List<Restriccion> conflictos, NuevaProgramacion nueva) {
        if (conflictos.isEmpty()) {
            return;
        }
        String motivos =
                String.join(
                        ", ",
                        conflictos.stream().map(r -> r.tipo() + " el " + r.fechaInicio()).toList());
        if (nueva.justificacion() == null || nueva.justificacion().isBlank()) {
            throw new ErrorPlanificacion(
                    409,
                    "restricciones-sin-justificar",
                    "Coincide con " + motivos + ". Justifica la fecha o elige otra");
        }
        if (!Boolean.TRUE.equals(nueva.confirmarProhibicion())
                && conflictos.stream().anyMatch(r -> "Prohibicion".equals(r.efecto()))) {
            throw new ErrorPlanificacion(
                    409,
                    "confirmacion-requerida",
                    "Coincide con " + motivos + ", que lo prohíbe. Confírmalo expresamente");
        }
    }
}
