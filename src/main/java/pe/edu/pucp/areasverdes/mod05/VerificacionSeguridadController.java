package pe.edu.pucp.areasverdes.mod05;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tercerizados/atenciones")
public class VerificacionSeguridadController {
    private final AtencionService service;

    public VerificacionSeguridadController(AtencionService service) {
        this.service = service;
    }

    @PostMapping({"/{id}/seguridad", "/{id}/seguridad/"})
    @Operation(
            summary = "Registra o corrige la verificación de seguridad antes del inicio",
            description =
                    "Solo jefe de sección e ingeniero de campo. Requiere medidas y confirmación.")
    public Map<String, Object> registrar(
            @PathVariable long id,
            @Valid @RequestBody Entradas.VerificacionSeguridad entrada,
            @AuthenticationPrincipal Usuario user) {
        return service.verificarSeguridad(id, entrada, user);
    }
}
