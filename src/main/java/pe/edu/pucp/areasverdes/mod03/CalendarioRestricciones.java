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
import pe.edu.pucp.areasverdes.mod05.Usuario;

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
            @Size(max = 200, message = "Máximo 200 caracteres") String descripcion,
            @Valid Recurrencia recurrencia) {}

    static final int MAXIMO_REPETICIONES = 200;

    private static final String AUDITAR =
            """
            INSERT INTO mod05_registroauditoria(entidad, registro_id, accion, fecha_hora,
                valores_anteriores, valores_nuevos, motivo, usuario_id)
            SELECT 'mod03_restriccion_calendario', r.id, :accion, now(), '{}', to_jsonb(r), '',
                :usuario FROM mod03_restriccion_calendario r WHERE r.id IN (:ids)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CalendarioRestricciones(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Crea la restricción, o una por cada repetición con la misma serie, y devuelve sus ids. */
    @Transactional
    public List<Long> crear(NuevaRestriccion nueva, Usuario usuario) {
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
        var datos =
                new MapSqlParameterSource()
                        .addValue("tipo", nueva.tipoRestriccionId())
                        .addValue("horaInicio", nueva.horaInicio())
                        .addValue("horaFin", nueva.horaFin())
                        .addValue("area", nueva.areaVerdeId())
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
                                descripcion, serie, usuario_creacion_id, usuario_modificacion_id)
                            VALUES (:tipo, :inicio, :fin, :horaInicio, :horaFin, :area,
                                :descripcion, :serie, :usuario, :usuario) RETURNING id
                            """,
                            datos,
                            Long.class));
        }
        jdbc.update(AUDITAR, Map.of("ids", ids, "accion", "crear", "usuario", usuario.id()));
        return ids;
    }

    /** Da de baja la restricción o, con {@code serie}, todas las de su serie. Devuelve cuántas. */
    @Transactional
    public int baja(long id, boolean serie, Usuario usuario) {
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
        jdbc.update(AUDITAR, Map.of("ids", ids, "accion", "baja", "usuario", usuario.id()));
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
