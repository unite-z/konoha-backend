package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod03.CalendarioRestricciones.NuevaRestriccion;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Calendario de restricciones (HU-MOD03-003): exámenes, feriados, eventos y demás días. */
@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Planificación")
@SecurityRequirement(name = "basicAuth")
public class RestriccionesController {

    public record TipoRestriccion(
            long id, String nombre, String efecto, boolean aplicaServiciosRuidosos) {}

    public record Restriccion(
            long id,
            long tipoRestriccionId,
            String tipo,
            String efecto,
            boolean aplicaServiciosRuidosos,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            LocalTime horaInicio,
            LocalTime horaFin,
            Long areaVerdeId,
            String descripcion,
            UUID serie) {}

    /** Periodo máximo de una consulta, para acotar la respuesta (un año y un mes). */
    static final int DIAS_MAXIMOS = 400;

    static final String CONSULTA =
            """
            SELECT r.*, t.nombre tipo, t.efecto, t.aplica_servicios_ruidosos
            FROM mod03_restriccion_calendario r
            JOIN mod03_tipo_restriccion t ON t.id = r.tipo_restriccion_id
            WHERE r.activo AND %s ORDER BY r.fecha_inicio, r.hora_inicio NULLS FIRST, r.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final CalendarioRestricciones calendario;

    public RestriccionesController(
            NamedParameterJdbcTemplate jdbc, CalendarioRestricciones calendario) {
        this.jdbc = jdbc;
        this.calendario = calendario;
    }

    @Operation(summary = "Tipos de restricción con su efecto en la programación")
    @GetMapping({"/tipos-restriccion", "/tipos-restriccion/"})
    public List<TipoRestriccion> tipos(@AuthenticationPrincipal Usuario usuario) {
        Acceso.consultar(usuario);
        return jdbc.query(
                "SELECT * FROM mod03_tipo_restriccion WHERE activo ORDER BY id",
                new DataClassRowMapper<>(TipoRestriccion.class));
    }

    @Operation(summary = "Restricciones vigentes que se solapan con el periodo [desde, hasta]")
    @GetMapping({"/restricciones", "/restricciones/"})
    public List<Restriccion> restricciones(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Acceso.consultar(usuario);
        if (hasta.isBefore(desde) || desde.plusDays(DIAS_MAXIMOS).isBefore(hasta)) {
            throw new ErrorPlanificacion(
                    400,
                    "periodo-invalido",
                    "El periodo debe empezar antes de terminar y durar como máximo %d días"
                            .formatted(DIAS_MAXIMOS));
        }
        return jdbc.query(
                CONSULTA.formatted("r.fecha_inicio <= :hasta AND r.fecha_fin >= :desde"),
                Map.of("desde", desde, "hasta", hasta),
                new DataClassRowMapper<>(Restriccion.class));
    }

    @Operation(summary = "Registra una restricción o una serie semanal (jefe de sección)")
    @PostMapping({"/restricciones", "/restricciones/"})
    @ResponseStatus(HttpStatus.CREATED)
    public List<Restriccion> crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevaRestriccion nueva) {
        return jdbc.query(
                CONSULTA.formatted("r.id IN (:ids)"),
                Map.of("ids", calendario.crear(nueva, usuario)),
                new DataClassRowMapper<>(Restriccion.class));
    }

    @Operation(summary = "Da de baja una restricción o, con serie=true, toda su serie (jefe)")
    @DeleteMapping({"/restricciones/{id}", "/restricciones/{id}/"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void baja(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean serie) {
        calendario.baja(id, serie, usuario);
    }
}
