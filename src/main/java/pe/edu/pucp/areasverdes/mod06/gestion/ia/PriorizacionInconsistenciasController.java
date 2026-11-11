package pe.edu.pucp.areasverdes.mod06.gestion.ia;

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
 * RN-IA-002 ({@code ia/priorizaciones/}) y RF-IA-002 ({@code ia/inconsistencias/}). Consultar exige
 * REP.INDICADOR.CONSULTAR; solicitar, IA.PROPUESTA.PROPONER; validar y rechazar,
 * IA.PROPUESTA.VALIDAR. Las respuestas siguen {@code SugerenciaIAGenerica} del frontend.
 */
@RestController
@RequestMapping("/api/v1/ia")
@SecurityRequirement(name = "sesionCore")
public class PriorizacionInconsistenciasController {

    static final String TAG_PRIORIZACION = "RN-IA-002 Priorización de atenciones";
    static final String TAG_INCONSISTENCIAS = "RF-IA-002 Inconsistencias de datos";

    private final SugerenciasIa sugerencias;
    private final PriorizacionesService priorizaciones;
    private final InconsistenciasService inconsistencias;

    public PriorizacionInconsistenciasController(
            SugerenciasIa sugerencias,
            PriorizacionesService priorizaciones,
            InconsistenciasService inconsistencias) {
        this.sugerencias = sugerencias;
        this.priorizaciones = priorizaciones;
        this.inconsistencias = inconsistencias;
    }

    // ------------------------------------------------------------------ RN-IA-002

    @Tag(name = TAG_PRIORIZACION)
    @Operation(summary = "Sugerencias de priorización, de la más reciente a la más antigua")
    @GetMapping({"/priorizaciones", "/priorizaciones/"})
    public SugerenciasIa.Pagina listarPriorizaciones(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String page,
            HttpServletRequest peticion) {
        SugerenciasIa.puedeConsultar(usuario);
        return sugerencias.listar(
                PriorizacionesService.TIPO, estado, page, Paginas.desde(peticion));
    }

    @Tag(name = TAG_PRIORIZACION)
    @Operation(
            summary =
                    "Pide a la IA una propuesta de prioridades (201 en Propuesta; 503 si la IA"
                            + " falla)")
    @PostMapping({"/priorizaciones", "/priorizaciones/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> solicitarPriorizacion(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeProponer(usuario);
        return priorizaciones.solicitar(cuerpo, usuario.id());
    }

    @Tag(name = TAG_PRIORIZACION)
    @Operation(summary = "Detalle de una sugerencia de priorización")
    @GetMapping({"/priorizaciones/{id:[0-9]+}", "/priorizaciones/{id:[0-9]+}/"})
    public Map<String, Object> priorizacion(
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable long id) {
        SugerenciasIa.puedeConsultar(usuario);
        return sugerencias.obtener(id, PriorizacionesService.TIPO);
    }

    @Tag(name = TAG_PRIORIZACION)
    @Operation(summary = "Valida la priorización elemento por elemento (409 si ya fue revisada)")
    @PostMapping({"/priorizaciones/{id:[0-9]+}/validar", "/priorizaciones/{id:[0-9]+}/validar/"})
    public Map<String, Object> validarPriorizacion(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return priorizaciones.validar(id, cuerpo, usuario.id());
    }

    @Tag(name = TAG_PRIORIZACION)
    @Operation(summary = "Rechaza la priorización completa")
    @PostMapping({"/priorizaciones/{id:[0-9]+}/rechazar", "/priorizaciones/{id:[0-9]+}/rechazar/"})
    public Map<String, Object> rechazarPriorizacion(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return priorizaciones.rechazar(id, motivo(cuerpo), usuario.id());
    }

    // ------------------------------------------------------------------ RF-IA-002

    @Tag(name = TAG_INCONSISTENCIAS)
    @Operation(
            summary =
                    "Analiza un conjunto de datos: una sugerencia por hallazgo (0 hallazgos no es"
                            + " error)")
    @PostMapping({"/inconsistencias/analisis", "/inconsistencias/analisis/"})
    public Map<String, Object> analizar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeProponer(usuario);
        return inconsistencias.analizar(cuerpo, usuario.id());
    }

    @Tag(name = TAG_INCONSISTENCIAS)
    @Operation(summary = "Hallazgos de inconsistencias, filtrables por estado, tipo y conjunto")
    @GetMapping({"/inconsistencias", "/inconsistencias/"})
    public SugerenciasIa.Pagina listarInconsistencias(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String estado,
            @RequestParam(name = "tipo_hallazgo", required = false) String tipoHallazgo,
            @RequestParam(required = false) String conjunto,
            @RequestParam(required = false) String page,
            HttpServletRequest peticion) {
        SugerenciasIa.puedeConsultar(usuario);
        return inconsistencias.listar(
                estado, tipoHallazgo, conjunto, page, Paginas.desde(peticion));
    }

    @Tag(name = TAG_INCONSISTENCIAS)
    @Operation(summary = "Detalle de un hallazgo")
    @GetMapping({"/inconsistencias/{id:[0-9]+}", "/inconsistencias/{id:[0-9]+}/"})
    public Map<String, Object> inconsistencia(
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable long id) {
        SugerenciasIa.puedeConsultar(usuario);
        return sugerencias.obtener(id, InconsistenciasService.TIPO);
    }

    @Tag(name = TAG_INCONSISTENCIAS)
    @Operation(summary = "Acepta el hallazgo: solo registra la decisión, no corrige el registro")
    @PostMapping({"/inconsistencias/{id:[0-9]+}/validar", "/inconsistencias/{id:[0-9]+}/validar/"})
    public Map<String, Object> aceptarInconsistencia(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return inconsistencias.aceptar(id, cuerpo == null ? Map.of() : cuerpo, usuario.id());
    }

    @Tag(name = TAG_INCONSISTENCIAS)
    @Operation(summary = "Descarta el hallazgo")
    @PostMapping({
        "/inconsistencias/{id:[0-9]+}/rechazar",
        "/inconsistencias/{id:[0-9]+}/rechazar/"
    })
    public Map<String, Object> descartarInconsistencia(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return inconsistencias.descartar(id, motivo(cuerpo), usuario.id());
    }

    static String motivo(Map<String, Object> cuerpo) {
        return cuerpo != null && cuerpo.get("motivo") instanceof String m && !m.isBlank()
                ? m
                : null;
    }
}
