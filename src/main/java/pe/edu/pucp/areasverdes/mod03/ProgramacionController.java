package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod03.ProgramacionServicios.NuevaProgramacion;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Programaciones de servicios periódicos (HU-MOD03-004 y 005). */
@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Planificación")
@SecurityRequirement(name = "basicAuth")
public class ProgramacionController {

    public record Programacion(
            long id,
            long tipoServicioId,
            String servicio,
            Long proveedorId,
            String estacion,
            LocalDate fechaInicioPrevista,
            LocalDate fechaFinPrevista,
            String estado) {}

    static final String CONSULTA =
            """
            SELECT p.*, t.nombre servicio FROM mod03_programacion_servicio p
            JOIN mod05_tiposervicio t ON t.id = p.tipo_servicio_id
            WHERE %s ORDER BY p.fecha_inicio_prevista, p.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ProgramacionServicios programacion;

    public ProgramacionController(
            NamedParameterJdbcTemplate jdbc, ProgramacionServicios programacion) {
        this.jdbc = jdbc;
        this.programacion = programacion;
    }

    @Operation(summary = "Programa un ciclo de un servicio periódico (jefe de sección)")
    @PostMapping({"/programaciones", "/programaciones/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Programacion crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevaProgramacion nueva) {
        return jdbc.queryForObject(
                CONSULTA.formatted("p.id = :id"),
                Map.of("id", programacion.crear(nueva, usuario)),
                new DataClassRowMapper<>(Programacion.class));
    }
}
