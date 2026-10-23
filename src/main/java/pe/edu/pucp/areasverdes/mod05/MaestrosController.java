package pe.edu.pucp.areasverdes.mod05;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * RF-SER-001 (proveedores) y RF-SER-002 (contratos). Lectura: los tres roles. Registro: jefe de
 * sección.
 */
@RestController
@RequestMapping("/api/tercerizados")
@Tag(name = "MOD-05")
@SecurityRequirement(name = "basicAuth")
public class MaestrosController {
    final MaestrosService service;

    public MaestrosController(MaestrosService service) {
        this.service = service;
    }

    @GetMapping({"/proveedores/", "/proveedores"})
    public ConsultasService.Pagina proveedores(
            @AuthenticationPrincipal Usuario user,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return service.proveedores(user, params, request.getRequestURL().toString());
    }

    @PostMapping({"/proveedores/", "/proveedores"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crearProveedor(
            @AuthenticationPrincipal Usuario user,
            @Valid @RequestBody EntradasMaestros.Proveedor body) {
        return service.crearProveedor(body, user);
    }

    @GetMapping({"/proveedores/{id}/", "/proveedores/{id}"})
    public Map<String, Object> proveedor(
            @AuthenticationPrincipal Usuario user, @PathVariable long id) {
        return service.proveedor(user, id);
    }

    @GetMapping({"/contratos/", "/contratos"})
    public ConsultasService.Pagina contratos(
            @AuthenticationPrincipal Usuario user,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return service.contratos(user, params, request.getRequestURL().toString());
    }

    @PostMapping({"/contratos/", "/contratos"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> crearContrato(
            @AuthenticationPrincipal Usuario user,
            @Valid @RequestBody EntradasMaestros.Contrato body) {
        return service.crearContrato(body, user);
    }

    @GetMapping({"/contratos/{id}/", "/contratos/{id}"})
    public Map<String, Object> contrato(
            @AuthenticationPrincipal Usuario user, @PathVariable long id) {
        return service.contrato(user, id);
    }
}
