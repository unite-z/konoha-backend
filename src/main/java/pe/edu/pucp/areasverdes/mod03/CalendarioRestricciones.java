package pe.edu.pucp.areasverdes.mod03;

import static java.util.stream.Collectors.toSet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;

/** Alta y baja de restricciones del calendario (HU-MOD03-003), sueltas o en serie semanal. */
@Service
public class CalendarioRestricciones {

    /** Repetición semanal: días ISO (1 lunes, 7 domingo) desde la fecha de inicio hasta otra. */
    public record Recurrencia(
            @NotEmpty(message = "Indica al menos un día")
                    List<@NotNull @Min(1) @Max(7) Integer> diasSemana,
            @NotNull(message = "Indica hasta cuándo se repite") LocalDate hasta) {}

    public record NuevaRestriccion(
            @NotNull(message = "Indica el tipo") Long tipoRestriccionId,
            @NotNull(message = "Indica la fecha de inicio") LocalDate fechaInicio,
            @NotNull(message = "Indica la fecha de fin") LocalDate fechaFin,
            LocalTime horaInicio,
            LocalTime horaFin,
            Long areaVerdeId,
            Boolean aplicaServiciosRuidosos,
            @Size(max = 200, message = "Máximo 200 caracteres") String descripcion,
            @Valid Recurrencia recurrencia,
            Boolean forzar) {}

    static final int MAXIMO_REPETICIONES = 200;

    /** Entidad en la auditoría común de MOD-01 (AV-287: ya no en la de MOD-05). */
    static final String ENTIDAD = "mod03_restriccion_calendario";

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;

    public CalendarioRestricciones(
            NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    /** Deja en la auditoría común el alta o la baja lógica de cada restricción, con su fila. */
    private void auditar(List<Long> ids, boolean alta, UsuarioPlanificacion usuario) {
        for (Long id : ids) {
            Map<String, Object> fila =
                    jdbc.queryForMap(
                            "SELECT to_jsonb(r)::text fila FROM mod03_restriccion_calendario r"
                                    + " WHERE r.id = :id",
                            Map.of("id", id));
            Map<String, Object> valores = Map.of("restriccion", fila.get("fila"));
            if (alta) {
                auditoria.registrarAlta(ENTIDAD, id, valores, usuario.id());
            } else {
                auditoria.registrarCambio(
                        ENTIDAD, id, "BajaLogica", Map.of(), valores, null, usuario.id());
            }
        }
    }

    /** Crea la restricción, o una por cada repetición con la misma serie, y devuelve sus ids. */
    @Transactional
    public List<Long> crear(NuevaRestriccion nueva, UsuarioPlanificacion usuario) {
        Acceso.gestionar(usuario);
        if (nueva.fechaFin().isBefore(nueva.fechaInicio())
                || (nueva.horaInicio() != null
                        && nueva.horaFin() != null
                        && !nueva.horaFin().isAfter(nueva.horaInicio()))) {
            throw new ErrorPlanificacion(
                    400, "periodo-invalido", "El fin debe ser posterior al inicio");
        }
        if (jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_tipo_restriccion WHERE id = :id AND activo",
                        Map.of("id", nueva.tipoRestriccionId()),
                        Integer.class)
                == 0) {
            throw new ErrorPlanificacion(400, "tipo-invalido", "No existe ese tipo de restricción");
        }
        List<LocalDate> inicios =
                nueva.recurrencia() == null ? List.of(nueva.fechaInicio()) : repeticiones(nueva);
        long dias = ChronoUnit.DAYS.between(nueva.fechaInicio(), nueva.fechaFin());
        if (!Boolean.TRUE.equals(nueva.forzar())) {
            exigirSinDuplicados(nueva, inicios);
        }
        var datos =
                new MapSqlParameterSource()
                        .addValue("tipo", nueva.tipoRestriccionId())
                        .addValue("horaInicio", nueva.horaInicio())
                        .addValue("horaFin", nueva.horaFin())
                        .addValue("area", nueva.areaVerdeId())
                        .addValue("ruidosos", nueva.aplicaServiciosRuidosos())
                        .addValue("descripcion", nueva.descripcion())
                        .addValue("serie", nueva.recurrencia() == null ? null : UUID.randomUUID())
                        .addValue("usuario", usuario.id());
        List<Long> ids = new ArrayList<>();
        for (LocalDate inicio : inicios) {
            datos.addValue("inicio", inicio).addValue("fin", inicio.plusDays(dias));
            ids.add(
                    jdbc.queryForObject(
                            """
                            INSERT INTO mod03_restriccion_calendario(tipo_restriccion_id,
                                fecha_inicio, fecha_fin, hora_inicio, hora_fin, area_verde_id,
                                aplica_servicios_ruidosos, descripcion, serie,
                                usuario_creacion_id, usuario_modificacion_id)
                            VALUES (:tipo, :inicio, :fin, :horaInicio, :horaFin, :area,
                                :ruidosos, :descripcion, :serie, :usuario, :usuario) RETURNING id
                            """,
                            datos,
                            Long.class));
        }
        auditar(ids, true, usuario);
        return ids;
    }

    /**
     * CU-003 6a: dos restricciones son duplicadas si tienen el mismo tipo, la misma fecha y el
     * mismo lugar (o las dos sin lugar). En una serie se compara cada fecha generada. Con {@code
     * forzar} se guarda igualmente.
     */
    private void exigirSinDuplicados(NuevaRestriccion nueva, List<LocalDate> inicios) {
        List<LocalDate> repetidas =
                jdbc.queryForList(
                        """
                        SELECT DISTINCT fecha_inicio FROM mod03_restriccion_calendario
                        WHERE activo AND tipo_restriccion_id = :tipo AND fecha_inicio IN (:inicios)
                          AND area_verde_id IS NOT DISTINCT FROM CAST(:area AS bigint)
                        ORDER BY fecha_inicio
                        """,
                        new MapSqlParameterSource()
                                .addValue("tipo", nueva.tipoRestriccionId())
                                .addValue("inicios", inicios)
                                .addValue("area", nueva.areaVerdeId()),
                        LocalDate.class);
        if (!repetidas.isEmpty()) {
            throw new ErrorPlanificacion(
                    409,
                    "posible-duplicado",
                    "Ya existe una restricción igual el "
                            + String.join(
                                    ", ", repetidas.stream().map(LocalDate::toString).toList())
                            + ". Guárdala igualmente con forzar");
        }
    }

    /** Da de baja la restricción o, con {@code serie}, todas las de su serie. Devuelve cuántas. */
    @Transactional
    public int baja(long id, boolean serie, UsuarioPlanificacion usuario) {
        Acceso.gestionar(usuario);
        List<Long> ids =
                jdbc.queryForList(
                        """
                        UPDATE mod03_restriccion_calendario SET activo = false,
                            fecha_modificacion = now(), usuario_modificacion_id = :usuario
                        WHERE activo AND (id = :id OR (:serie AND serie =
                            (SELECT serie FROM mod03_restriccion_calendario WHERE id = :id)))
                        RETURNING id
                        """,
                        Map.of("id", id, "serie", serie, "usuario", usuario.id()),
                        Long.class);
        if (ids.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "restriccion-no-encontrada", "No existe esa restricción");
        }
        auditar(ids, false, usuario);
        return ids.size();
    }

    /** Fechas de inicio de cada repetición: días de la semana pedidos dentro del periodo. */
    static List<LocalDate> repeticiones(NuevaRestriccion nueva) {
        var dias = nueva.recurrencia().diasSemana().stream().map(DayOfWeek::of).collect(toSet());
        List<LocalDate> inicios =
                nueva.fechaInicio()
                        .datesUntil(nueva.recurrencia().hasta().plusDays(1))
                        .filter(d -> dias.contains(d.getDayOfWeek()))
                        .limit(MAXIMO_REPETICIONES + 1L)
                        .toList();
        if (inicios.isEmpty() || inicios.size() > MAXIMO_REPETICIONES) {
            throw new ErrorPlanificacion(
                    400,
                    "recurrencia-invalida",
                    "La repetición debe generar entre 1 y %d restricciones"
                            .formatted(MAXIMO_REPETICIONES));
        }
        return inicios;
    }
}
