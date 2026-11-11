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
 * RF-IA-003 ({@code ia/cronogramas/}). Consultar: REP.INDICADOR.CONSULTAR; solicitar:
 * IA.PROPUESTA.PROPONER; validar (crea programaciones en MOD-03) y rechazar: IA.PROPUESTA.VALIDAR;
 * validar exige además PLA.PLANIFICACION.GESTIONAR.
 */
@RestController
@RequestMapping("/api/v1/ia/cronogramas")
@Tag(name = "RF-IA-003 Cronograma propuesto")
@SecurityRequirement(name = "sesionCore")
public class CronogramasController {

    private final SugerenciasIa sugerencias;
    private final CronogramasService cronogramas;

    public CronogramasController(SugerenciasIa sugerencias, CronogramasService cronogramas) {
        this.sugerencias = sugerencias;
        this.cronogramas = cronogramas;
    }

    @Operation(summary = "Propuestas de cronograma, de la más reciente a la más antigua")
    @GetMapping({"", "/"})
    public SugerenciasIa.Pagina listar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String page,
            HttpServletRequest peticion) {
        SugerenciasIa.puedeConsultar(usuario);
        return sugerencias.listar(CronogramasService.TIPO, estado, page, Paginas.desde(peticion));
    }

    @Operation(
            summary =
                    "Pide a la IA una propuesta de cronograma del periodo (201; 503 si la IA"
                            + " falla)")
    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> solicitar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeProponer(usuario);
        return cronogramas.solicitar(cuerpo, usuario.id());
    }

    @Operation(summary = "Detalle de una propuesta de cronograma")
    @GetMapping({"/{id:[0-9]+}", "/{id:[0-9]+}/"})
    public Map<String, Object> detalle(
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable long id) {
        SugerenciasIa.puedeConsultar(usuario);
        return sugerencias.obtener(id, CronogramasService.TIPO);
    }

    @Operation(
            summary =
                    "Confirma el cronograma: crea en MOD-03 las programaciones aceptadas o"
                            + " corregidas")
    @PostMapping({"/{id:[0-9]+}/validar", "/{id:[0-9]+}/validar/"})
    public Map<String, Object> validar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return cronogramas.validar(id, cuerpo, usuario);
    }

    @Operation(summary = "Descarta la propuesta completa")
    @PostMapping({"/{id:[0-9]+}/rechazar", "/{id:[0-9]+}/rechazar/"})
    public Map<String, Object> rechazar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return cronogramas.rechazar(id, motivo(cuerpo), usuario.id());
    }
}
