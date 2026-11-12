package pe.edu.pucp.areasverdes.mod05;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** RF-INV-011: ejemplares con atención fitosanitaria especial y catálogo de plagas. */
@RestController
@RequestMapping("/api/tercerizados")
@Tag(name = "MOD-05")
@SecurityRequirement(name = "basicAuth")
public class AtencionEspecialController {
    final AtencionEspecialService service;
    final PlanFitosanitarioService planFitosanitario;

    public AtencionEspecialController(
            AtencionEspecialService service, PlanFitosanitarioService planFitosanitario) {
        this.service = service;
        this.planFitosanitario = planFitosanitario;
    }

    @GetMapping({"/atenciones-especiales/", "/atenciones-especiales"})
    public ConsultasService.Pagina listar(
            @AuthenticationPrincipal Usuario user,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return service.listar(user, params, request.getRequestURL().toString());
    }

    @PostMapping({"/atenciones-especiales/", "/atenciones-especiales"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> marcar(
            @AuthenticationPrincipal Usuario user,
            @Valid @RequestBody EntradasMaestros.AtencionEspecial body) {
        return service.marcar(body, user);
    }

    @GetMapping({"/atenciones-especiales/{id}/", "/atenciones-especiales/{id}"})
    public Map<String, Object> detalle(
            @AuthenticationPrincipal Usuario user, @PathVariable long id) {
        return service.detalle(user, id);
    }

    @PostMapping({
        "/atenciones-especiales/{id}/aplicaciones/",
        "/atenciones-especiales/{id}/aplicaciones"
    })
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> programarAplicacion(
            @AuthenticationPrincipal Usuario user,
            @PathVariable long id,
            @Valid @RequestBody EntradasFitosanitario.AplicacionAdicional body) {
        return planFitosanitario.programarAplicacionAdicional(id, body, user);
    }

    @PostMapping({"/atenciones-especiales/{id}/cancelar/", "/atenciones-especiales/{id}/cancelar"})
    public Map<String, Object> cancelar(
            @AuthenticationPrincipal Usuario user,
            @PathVariable long id,
            @Valid @RequestBody EntradasMaestros.CancelarAtencionEspecial body) {
        return service.cancelar(id, body, user);
    }

    @GetMapping({"/plagas/", "/plagas"})
    public ConsultasService.Pagina plagas(
            @AuthenticationPrincipal Usuario user,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return service.plagas(user, params, request.getRequestURL().toString());
    }
}
