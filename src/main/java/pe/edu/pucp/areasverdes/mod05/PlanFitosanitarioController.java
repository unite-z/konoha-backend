package pe.edu.pucp.areasverdes.mod05;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** RF-FIT-001: plan fitosanitario estacional. Solo el jefe de sección lo programa. */
@RestController
@RequestMapping("/api/tercerizados")
@Tag(name = "MOD-05")
@SecurityRequirement(name = "basicAuth")
public class PlanFitosanitarioController {
    final PlanFitosanitarioService service;

    public PlanFitosanitarioController(PlanFitosanitarioService service) {
        this.service = service;
    }

    @PostMapping({"/plan-fitosanitario/", "/plan-fitosanitario"})
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> crear(
            @AuthenticationPrincipal Usuario user,
            @Valid @RequestBody EntradasFitosanitario.Plan body) {
        return service.crear(body, user);
    }
}
