package pe.edu.pucp.areasverdes.mod03;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Alta de restricciones del calendario (HU-MOD03-003). */
@Service
public class CalendarioRestricciones {

    public record NuevaRestriccion(
            @NotNull(message = "Indica el tipo") Long tipoRestriccionId,
            @NotNull(message = "Indica la fecha de inicio") LocalDate fechaInicio,
            @NotNull(message = "Indica la fecha de fin") LocalDate fechaFin,
            LocalTime horaInicio,
            LocalTime horaFin,
            Long areaVerdeId,
            @Size(max = 200, message = "Máximo 200 caracteres") String descripcion) {}

    private final NamedParameterJdbcTemplate jdbc;

    public CalendarioRestricciones(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Crea la restricción y devuelve su identificador. */
    @Transactional
    public long crear(NuevaRestriccion nueva, Usuario usuario) {
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
        return jdbc.queryForObject(
                """
                INSERT INTO mod03_restriccion_calendario(tipo_restriccion_id, fecha_inicio, fecha_fin,
                    hora_inicio, hora_fin, area_verde_id, descripcion, usuario_creacion_id,
                    usuario_modificacion_id)
                VALUES (:tipo, :inicio, :fin, :horaInicio, :horaFin, :area, :descripcion, :usuario,
                    :usuario) RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("tipo", nueva.tipoRestriccionId())
                        .addValue("inicio", nueva.fechaInicio())
                        .addValue("fin", nueva.fechaFin())
                        .addValue("horaInicio", nueva.horaInicio())
                        .addValue("horaFin", nueva.horaFin())
                        .addValue("area", nueva.areaVerdeId())
                        .addValue("descripcion", nueva.descripcion())
                        .addValue("usuario", usuario.id()),
                Long.class);
    }
}
