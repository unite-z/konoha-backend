package pe.edu.pucp.areasverdes.mod03;

import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod03.ProgramacionServicios.Cambio;
import pe.edu.pucp.areasverdes.mod03.ProgramacionServicios.NuevaProgramacion;

/** Programaciones de servicios periódicos (HU-MOD03-004 y 005). */
@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Planificación")
@SecurityRequirement(name = "basicAuth")
public class ProgramacionController {

    /**
     * Un ciclo programado. {@code ventana_mes}: mes del tercerizado ({@code null} con personal
     * propio). {@code historial}: sus transiciones con autor y hora, de la más antigua a la más
     * reciente (AV-253).
     */
    public record Programacion(
            long id,
            long tipoServicioId,
            String servicio,
            Long proveedorId,
            String proveedor,
            String estacion,
            LocalDate fechaInicioPrevista,
            LocalDate fechaFinPrevista,
            String estado,
            LocalDate fechaInicioReal,
            LocalDate fechaFinReal,
            String ventanaMes,
            String motivoCancelacion,
            List<Transicion> historial) {}

    /** Una transición del ciclo: a qué estado pasó, cuándo, quién y, al cancelar, por qué. */
    public record Transicion(
            String estado, OffsetDateTime fechaHora, String autor, String motivo) {}

    private record Fila(
            long id,
            long tipoServicioId,
            String servicio,
            Long proveedorId,
            String proveedor,
            String estacion,
            LocalDate fechaInicioPrevista,
            LocalDate fechaFinPrevista,
            String estado,
            LocalDate fechaInicioReal,
            LocalDate fechaFinReal,
            String ventanaMes,
            String motivoCancelacion) {}

    static final String CONSULTA =
            """
            SELECT p.*, t.nombre servicio, v.razon_social proveedor FROM mod03_programacion_servicio p
            JOIN mod05_tiposervicio t ON t.id = p.tipo_servicio_id
            LEFT JOIN mod05_proveedor v ON v.id = p.proveedor_id
            WHERE %s ORDER BY p.fecha_inicio_prevista, p.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ProgramacionServicios programacion;
    private final Clock reloj;

    public ProgramacionController(
            NamedParameterJdbcTemplate jdbc, ProgramacionServicios programacion, Clock reloj) {
        this.jdbc = jdbc;
        this.programacion = programacion;
        this.reloj = reloj;
    }

    @Operation(summary = "Programa un ciclo de un servicio periódico (jefe de sección)")
    @PostMapping({"/programaciones", "/programaciones/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Programacion crear(
            UsuarioPlanificacion usuario, @Valid @RequestBody NuevaProgramacion nueva) {
        return buscar(programacion.crear(nueva, usuario));
    }

    @Operation(summary = "Calendario de actividades: programaciones del periodo, con filtros")
    @GetMapping({"/programaciones", "/programaciones/"})
    public List<Programacion> calendario(
            UsuarioPlanificacion usuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "tipo_servicio_id", required = false) Long tipoServicioId,
            @RequestParam(name = "proveedor_id", required = false) Long proveedorId,
            @RequestParam(required = false) String estado) {
        Acceso.consultar(usuario);
        if (hasta.isBefore(desde)
                || desde.plusDays(RestriccionesController.DIAS_MAXIMOS).isBefore(hasta)) {
            throw new ErrorPlanificacion(
                    400, "periodo-invalido", "El periodo debe empezar antes de terminar");
        }
        var datos =
                new MapSqlParameterSource()
                        .addValue("desde", desde)
                        .addValue("hasta", hasta)
                        .addValue("servicio", tipoServicioId)
                        .addValue("proveedor", proveedorId)
                        .addValue("estado", estado);
        return consultar(
                CONSULTA.formatted(
                        """
                        p.fecha_inicio_prevista <= :hasta AND p.fecha_fin_prevista >= :desde
                        AND (CAST(:servicio AS bigint) IS NULL OR p.tipo_servicio_id = :servicio)
                        AND (CAST(:proveedor AS bigint) IS NULL OR p.proveedor_id = :proveedor)
                        AND (CAST(:estado AS varchar) IS NULL OR p.estado = :estado)
                        """),
                datos);
    }

    @Operation(summary = "Registra el inicio, el fin o la cancelación de una programación (jefe)")
    @PostMapping({
        "/programaciones/{id}/{accion:inicio|fin|cancelacion}",
        "/programaciones/{id}/{accion:inicio|fin|cancelacion}/"
    })
    public Programacion cambiar(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @PathVariable String accion,
            @Valid @RequestBody(required = false) Cambio cambio) {
        LocalDate fecha =
                cambio == null || cambio.fecha() == null ? LocalDate.now(reloj) : cambio.fecha();
        programacion.cambiar(id, accion, fecha, cambio == null ? null : cambio.motivo(), usuario);
        return buscar(id);
    }

    Programacion buscar(long id) {
        return consultar(CONSULTA.formatted("p.id = :id"), new MapSqlParameterSource("id", id))
                .getFirst();
    }

    /** Las programaciones de la consulta, cada una con su historial (una consulta para todas). */
    private List<Programacion> consultar(String sql, MapSqlParameterSource datos) {
        List<Fila> filas = jdbc.query(sql, datos, new DataClassRowMapper<>(Fila.class));
        if (filas.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Transicion>> historial = new HashMap<>();
        jdbc.query(
                """
                SELECT h.programacion_id, h.estado, h.fecha_hora, u.nombre autor, h.motivo
                FROM mod03_historial_programacion h JOIN mod05_usuario u ON u.id = h.usuario_id
                WHERE h.programacion_id IN (:ids) ORDER BY h.fecha_hora, h.id
                """,
                Map.of("ids", filas.stream().map(Fila::id).toList()),
                (ResultSet rs) -> {
                    historial
                            .computeIfAbsent(rs.getLong("programacion_id"), k -> new ArrayList<>())
                            .add(
                                    new Transicion(
                                            rs.getString("estado"),
                                            rs.getObject("fecha_hora", OffsetDateTime.class)
                                                    .atZoneSameInstant(LIMA)
                                                    .toOffsetDateTime(),
                                            rs.getString("autor"),
                                            rs.getString("motivo")));
                });
        return filas.stream()
                .map(
                        f ->
                                new Programacion(
                                        f.id(),
                                        f.tipoServicioId(),
                                        f.servicio(),
                                        f.proveedorId(),
                                        f.proveedor(),
                                        f.estacion(),
                                        f.fechaInicioPrevista(),
                                        f.fechaFinPrevista(),
                                        f.estado(),
                                        f.fechaInicioReal(),
                                        f.fechaFinReal(),
                                        f.ventanaMes(),
                                        f.motivoCancelacion(),
                                        historial.getOrDefault(f.id(), List.of())))
                .toList();
    }
}
