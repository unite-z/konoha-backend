package pe.edu.pucp.areasverdes.mod03;

import static java.util.stream.Collectors.toSet;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod03.ConfiguracionServicios.NuevaDuracion;
import pe.edu.pucp.areasverdes.mod03.ConfiguracionServicios.NuevaFrecuencia;
import pe.edu.pucp.areasverdes.mod03.Programador.Bloqueo;
import pe.edu.pucp.areasverdes.mod03.RestriccionesController.Restriccion;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Planificación")
@SecurityRequirement(name = "basicAuth")
public class ServiciosController {

    private final ServiciosRepository servicios;
    private final ConfiguracionServicios configuracion;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock reloj;

    public ServiciosController(
            ServiciosRepository servicios,
            ConfiguracionServicios configuracion,
            NamedParameterJdbcTemplate jdbc,
            Clock reloj) {
        this.servicios = servicios;
        this.configuracion = configuracion;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Operation(summary = "Servicios periódicos con su frecuencia por estación y su duración máxima")
    @GetMapping({"/servicios", "/servicios/"})
    public List<ServicioPeriodico> listar(@AuthenticationPrincipal Usuario usuario) {
        Acceso.consultar(usuario);
        return servicios.periodicos();
    }

    @Operation(summary = "Configura la frecuencia de un servicio en una estación (jefe de sección)")
    @PutMapping({
        "/servicios/{id}/frecuencias/{estacion}",
        "/servicios/{id}/frecuencias/{estacion}/"
    })
    public ServicioPeriodico configurarFrecuencia(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @PathVariable String estacion,
            @Valid @RequestBody NuevaFrecuencia nueva) {
        configuracion.frecuencia(id, estacion, nueva, usuario);
        return servicios.periodicos().stream().filter(s -> s.id() == id).findFirst().orElseThrow();
    }

    @Operation(summary = "Configura la duración máxima de un servicio (jefe de sección)")
    @PutMapping({"/servicios/{id}/duracion", "/servicios/{id}/duracion/"})
    public ServicioPeriodico configurarDuracion(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody NuevaDuracion nueva) {
        configuracion.duracion(id, nueva, usuario);
        return servicios.periodicos().stream().filter(s -> s.id() == id).findFirst().orElseThrow();
    }

    /** Fechas propuestas, restricciones que coinciden y primera ventana libre (o null). */
    public record Propuesta(
            String estacion,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            List<Restriccion> conflictos,
            LocalDate ventanaLibreInicio) {}

    /** Frecuencia de la estación, duración total con limpieza y fin del último ciclo. */
    private record DatosPropuesta(
            boolean ruidoso, Integer frecuencia, Integer duracion, LocalDate ultimoFin) {}

    static final int HORIZONTE_DIAS = 60;

    @Operation(summary = "Propone las fechas del siguiente ciclo y avisa de las restricciones")
    @GetMapping({"/servicios/{id}/propuesta", "/servicios/{id}/propuesta/"})
    public Propuesta propuesta(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        Acceso.consultar(usuario);
        LocalDate hoy = LocalDate.now(reloj);
        String estacion = Programador.estacion(hoy);
        List<DatosPropuesta> datos =
                jdbc.query(
                        """
                        SELECT t.es_ruidoso ruidoso,
                            coalesce(f.frecuencia_practica_dias, f.frecuencia_teorica_dias) frecuencia,
                            d.duracion_maxima_dias + coalesce(d.dias_limpieza, 0) duracion,
                            (SELECT max(coalesce(p.fecha_fin_real, p.fecha_fin_prevista))
                             FROM mod03_programacion_servicio p
                             WHERE p.tipo_servicio_id = t.id AND p.estado = 'Completada') ultimo_fin
                        FROM mod05_tiposervicio t
                        LEFT JOIN mod03_frecuencia_servicio f
                            ON f.tipo_servicio_id = t.id AND f.estacion = :estacion
                        LEFT JOIN mod03_duracion_servicio d ON d.tipo_servicio_id = t.id
                        WHERE t.id = :id AND t.activo AND t.es_periodico
                        """,
                        Map.of("id", id, "estacion", estacion),
                        new DataClassRowMapper<>(DatosPropuesta.class));
        DatosPropuesta servicio =
                datos.stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ErrorPlanificacion(
                                                404,
                                                "servicio-no-encontrado",
                                                "No existe ese servicio"));
        if (servicio.frecuencia() == null || servicio.duracion() == null) {
            throw new ErrorPlanificacion(
                    409,
                    "configuracion-incompleta",
                    "Configura la frecuencia de %s y la duración del servicio".formatted(estacion));
        }
        LocalDate inicio =
                Programador.inicioPropuesto(servicio.ultimoFin(), servicio.frecuencia(), hoy);
        LocalDate fin = inicio.plusDays(servicio.duracion() - 1L);
        List<Restriccion> calendario =
                jdbc.query(
                        RestriccionesController.CONSULTA.formatted(
                                "r.fecha_inicio <= :hasta AND r.fecha_fin >= :desde"),
                        Map.of("desde", inicio, "hasta", fin.plusDays(HORIZONTE_DIAS)),
                        new DataClassRowMapper<>(Restriccion.class));
        List<Bloqueo> bloqueos = calendario.stream().map(Bloqueo::de).toList();
        Set<Long> conflictos =
                Programador.conflictos(bloqueos, inicio, fin, servicio.ruidoso()).stream()
                        .map(Bloqueo::id)
                        .collect(toSet());
        Optional<LocalDate> ventana =
                conflictos.isEmpty()
                        ? Optional.empty()
                        : Programador.ventanaLibre(
                                bloqueos,
                                inicio.plusDays(1),
                                servicio.duracion(),
                                servicio.ruidoso(),
                                HORIZONTE_DIAS);
        return new Propuesta(
                estacion,
                inicio,
                fin,
                calendario.stream().filter(r -> conflictos.contains(r.id())).toList(),
                ventana.orElse(null));
    }
}
