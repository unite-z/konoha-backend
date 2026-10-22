package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Planificación")
@SecurityRequirement(name = "basicAuth")
public class ServiciosController {

    private final ServiciosRepository servicios;

    public ServiciosController(ServiciosRepository servicios) {
        this.servicios = servicios;
    }

    @Operation(summary = "Servicios periódicos con su frecuencia por estación y su duración máxima")
    @GetMapping({"/servicios", "/servicios/"})
    public List<ServicioPeriodico> listar(@AuthenticationPrincipal Usuario usuario) {
        Acceso.consultar(usuario);
        return servicios.periodicos();
    }
}
