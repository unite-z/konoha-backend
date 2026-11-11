package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import static pe.edu.pucp.areasverdes.mod06.gestion.ia.PriorizacionInconsistenciasController.motivo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod06.gestion.api.Paginas;

/**
 * RF-IA-005 ({@code ia/clasificaciones-incidencia/}) y RF-IA-004 ({@code ia/estimaciones/}).
 * Consultar: REP.INDICADOR.CONSULTAR; solicitar: IA.PROPUESTA.PROPONER; validar y rechazar:
 * IA.PROPUESTA.VALIDAR. Clasificar (validar o manual) exige además PLA.INCIDENCIA.CLASIFICAR,
 * porque escribe en MOD-03.
 */
@RestController
@RequestMapping("/api/v1/ia")
@SecurityRequirement(name = "sesionCore")
public class ClasificacionEstimacionController {

    static final String TAG_CLASIFICACION = "RF-IA-005 Clasificación de incidencias";
    static final String TAG_ESTIMACION = "RF-IA-004 Estimación de recursos";

    private final SugerenciasIa sugerencias;
    private final ClasificacionesIncidenciaService clasificaciones;
    private final EstimacionesService estimaciones;

    public ClasificacionEstimacionController(
            SugerenciasIa sugerencias,
            ClasificacionesIncidenciaService clasificaciones,
            EstimacionesService estimaciones) {
        this.sugerencias = sugerencias;
        this.clasificaciones = clasificaciones;
        this.estimaciones = estimaciones;
    }

    // ------------------------------------------------------------------ RF-IA-005

    @Tag(name = TAG_CLASIFICACION)
    @Operation(summary = "Incidencias de MOD-03 en estado Registrada, para clasificar")
    @GetMapping({
        "/clasificaciones-incidencia/pendientes",
        "/clasificaciones-incidencia/pendientes/"
    })
    public SugerenciasIa.Pagina pendientes(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String q,
            @RequestParam(name = "incidencia_id", required = false) Long incidenciaId,
            @RequestParam(required = false) String page,
            HttpServletRequest peticion) {
        SugerenciasIa.puedeConsultar(usuario);
        return clasificaciones.pendientes(q, incidenciaId, page, Paginas.desde(peticion));
    }

    @Tag(name = TAG_CLASIFICACION)
    @Operation(summary = "Sugerencias de clasificación")
    @GetMapping({"/clasificaciones-incidencia", "/clasificaciones-incidencia/"})
    public SugerenciasIa.Pagina listarClasificaciones(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String page,
            HttpServletRequest peticion) {
        SugerenciasIa.puedeConsultar(usuario);
        return sugerencias.listar(
                ClasificacionesIncidenciaService.TIPO, estado, page, Paginas.desde(peticion));
    }

    @Tag(name = TAG_CLASIFICACION)
    @Operation(
            summary = "Pide a la IA la clasificación de una incidencia (201; 503 si la IA falla)")
    @PostMapping({"/clasificaciones-incidencia", "/clasificaciones-incidencia/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> solicitarClasificacion(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeProponer(usuario);
        return clasificaciones.solicitar(cuerpo, usuario.id());
    }

    @Tag(name = TAG_CLASIFICACION)
    @Operation(summary = "Clasificación manual, sin IA (201 ya en Validada)")
    @PostMapping({"/clasificaciones-incidencia/manual", "/clasificaciones-incidencia/manual/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> clasificarManual(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return clasificaciones.manual(cuerpo, usuario);
    }

    @Tag(name = TAG_CLASIFICACION)
    @Operation(summary = "Detalle de una sugerencia de clasificación")
    @GetMapping({
        "/clasificaciones-incidencia/{id:[0-9]+}",
        "/clasificaciones-incidencia/{id:[0-9]+}/"
    })
    public Map<String, Object> clasificacion(
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable long id) {
        SugerenciasIa.puedeConsultar(usuario);
        return sugerencias.obtener(id, ClasificacionesIncidenciaService.TIPO);
    }

    @Tag(name = TAG_CLASIFICACION)
    @Operation(
            summary =
                    "Valida la clasificación: la escribe en MOD-03 y la incidencia pasa a"
                            + " Clasificada")
    @PostMapping({
        "/clasificaciones-incidencia/{id:[0-9]+}/validar",
        "/clasificaciones-incidencia/{id:[0-9]+}/validar/"
    })
    public Map<String, Object> validarClasificacion(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return clasificaciones.validar(id, cuerpo, usuario);
    }

    @Tag(name = TAG_CLASIFICACION)
    @Operation(summary = "Descarta la sugerencia; la incidencia sigue Registrada")
    @PostMapping({
        "/clasificaciones-incidencia/{id:[0-9]+}/rechazar",
        "/clasificaciones-incidencia/{id:[0-9]+}/rechazar/"
    })
    public Map<String, Object> rechazarClasificacion(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return clasificaciones.rechazar(id, motivo(cuerpo), usuario.id());
    }

    // ------------------------------------------------------------------ RF-IA-004

    @Tag(name = TAG_ESTIMACION)
    @Operation(summary = "Actividades de MOD-04 sin recursos confirmados")
    @GetMapping({"/estimaciones/actividades-pendientes", "/estimaciones/actividades-pendientes/"})
    public SugerenciasIa.Pagina actividadesPendientes(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String page,
            HttpServletRequest peticion) {
        SugerenciasIa.puedeConsultar(usuario);
        return estimaciones.pendientes(q, page, Paginas.desde(peticion));
    }

    @Tag(name = TAG_ESTIMACION)
    @Operation(summary = "Estimaciones de recursos")
    @GetMapping({"/estimaciones", "/estimaciones/"})
    public SugerenciasIa.Pagina listarEstimaciones(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String page,
            HttpServletRequest peticion) {
        SugerenciasIa.puedeConsultar(usuario);
        return sugerencias.listar(EstimacionesService.TIPO, estado, page, Paginas.desde(peticion));
    }

    @Tag(name = TAG_ESTIMACION)
    @Operation(
            summary =
                    "Pide a la IA la estimación (201, también con datos insuficientes; 503 si"
                            + " falla)")
    @PostMapping({"/estimaciones", "/estimaciones/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> solicitarEstimacion(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeProponer(usuario);
        return estimaciones.solicitar(cuerpo, usuario.id());
    }

    @Tag(name = TAG_ESTIMACION)
    @Operation(summary = "Recursos registrados sin recomendación (201 ya en Validada)")
    @PostMapping({"/estimaciones/manual", "/estimaciones/manual/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> estimacionManual(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeProponer(usuario);
        return estimaciones.manual(cuerpo, usuario.id());
    }

    @Tag(name = TAG_ESTIMACION)
    @Operation(summary = "Detalle de una estimación")
    @GetMapping({"/estimaciones/{id:[0-9]+}", "/estimaciones/{id:[0-9]+}/"})
    public Map<String, Object> estimacion(
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable long id) {
        SugerenciasIa.puedeConsultar(usuario);
        return sugerencias.obtener(id, EstimacionesService.TIPO);
    }

    @Tag(name = TAG_ESTIMACION)
    @Operation(summary = "Confirma los recursos y calcula la diferencia con lo estimado")
    @PostMapping({"/estimaciones/{id:[0-9]+}/validar", "/estimaciones/{id:[0-9]+}/validar/"})
    public Map<String, Object> validarEstimacion(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return estimaciones.validar(id, cuerpo, usuario.id());
    }

    @Tag(name = TAG_ESTIMACION)
    @Operation(summary = "Descarta la estimación")
    @PostMapping({"/estimaciones/{id:[0-9]+}/rechazar", "/estimaciones/{id:[0-9]+}/rechazar/"})
    public Map<String, Object> rechazarEstimacion(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return estimaciones.rechazar(id, motivo(cuerpo), usuario.id());
    }
}
