package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod03.AlertasPlanificacion.Alerta;
import pe.edu.pucp.areasverdes.mod03.AlertasPlanificacion.AlertaAtendida;

/**
 * Alertas al jefe de sección (solo dentro de la aplicación, ADR 0003): frecuencia a punto de vencer
 * (HU-MOD03-006, RF-PLA-008), duración próxima o excedida (HU-MOD03-007, RF-PLA-009) y el motivo
 * con que se atienden (AV-255). Ver {@link AlertasPlanificacion}.
 */
@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Planificación")
@SecurityRequirement(name = "basicAuth")
public class AlertasController {

    public record Motivo(@Size(max = 300, message = "Máximo 300 caracteres") String motivo) {}

    private final AlertasPlanificacion alertas;

    public AlertasController(AlertasPlanificacion alertas) {
        this.alertas = alertas;
    }

    @Operation(summary = "Alertas abiertas de frecuencia y de duración, con su id")
    @GetMapping({"/alertas", "/alertas/"})
    public List<Alerta> alertas(UsuarioPlanificacion usuario) {
        Acceso.consultar(usuario);
        return alertas.abiertas();
    }

    @Operation(summary = "Registra el motivo o la acción de una alerta y la deja atendida (jefe)")
    @PostMapping({"/alertas/{id}/motivo", "/alertas/{id}/motivo/"})
    public AlertaAtendida motivo(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @Valid @RequestBody(required = false) Motivo cuerpo) {
        return alertas.registrarMotivo(id, cuerpo == null ? null : cuerpo.motivo(), usuario);
    }
}
