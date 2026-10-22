package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod03.ConfiguracionServicios.NuevaFrecuencia;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Planificación")
@SecurityRequirement(name = "basicAuth")
public class ServiciosController {

    private final ServiciosRepository servicios;
    private final ConfiguracionServicios configuracion;

    public ServiciosController(
            ServiciosRepository servicios, ConfiguracionServicios configuracion) {
        this.servicios = servicios;
        this.configuracion = configuracion;
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
}
