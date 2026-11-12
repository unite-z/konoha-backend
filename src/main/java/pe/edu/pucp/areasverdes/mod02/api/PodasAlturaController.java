package pe.edu.pucp.areasverdes.mod02.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService.Intervencion;
import pe.edu.pucp.areasverdes.mod02.servicios.PodasAlturaService;
import pe.edu.pucp.areasverdes.mod02.servicios.PodasAlturaService.ContratoPoda;
import pe.edu.pucp.areasverdes.mod02.servicios.PodasAlturaService.EjemplarPoda;
import pe.edu.pucp.areasverdes.mod02.servicios.PodasAlturaService.NuevaPodaAltura;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Poda de altura a demanda vinculada al ejemplar (AV-312, RF-POD-005). */
@RestController
@RequestMapping("/api/v1/inventario/podas-altura")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class PodasAlturaController {

    private final PodasAlturaService podas;

    public PodasAlturaController(PodasAlturaService podas) {
        this.podas = podas;
    }

    @PostMapping({"", "/"})
    @Operation(
            summary = "Solicita una poda de altura a demanda (jefe de sección)",
            description =
                    "Crea la atención a demanda en MOD-05 con el contrato elegido y la intervención"
                            + " AlturaDemanda del ejemplar, en estado Solicitada. Por debajo de 5 m"
                            + " (RN-005) exige justificación. El motivo riesgo marca la atención"
                            + " como de riesgo (RN-009).")
    public ResponseEntity<Intervencion> solicitar(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevaPodaAltura entrada) {
        Intervencion creada = podas.solicitar(entrada, usuario);
        return ResponseEntity.created(
                        URI.create("/api/v1/inventario/intervenciones-poda/" + creada.id() + "/"))
                .body(creada);
    }

    @GetMapping({"/contratos", "/contratos/"})
    @Operation(summary = "Contratos vigentes de servicios tercerizados para elegir el proveedor")
    public List<ContratoPoda> contratos(@AuthenticationPrincipal Usuario usuario) {
        return podas.contratos(usuario);
    }

    @GetMapping({"/ejemplares/{id}", "/ejemplares/{id}/"})
    @Operation(summary = "Altura del ejemplar y si necesita justificación por RN-005")
    public EjemplarPoda ejemplar(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return podas.ejemplar(id, usuario);
    }
}
