package pe.edu.pucp.areasverdes.mod02.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod02.servicios.FichaTecnicaService;
import pe.edu.pucp.areasverdes.mod02.servicios.FichaTecnicaService.CambioFicha;
import pe.edu.pucp.areasverdes.mod02.servicios.FichaTecnicaService.Evaluacion;
import pe.edu.pucp.areasverdes.mod02.servicios.FichaTecnicaService.Ficha;
import pe.edu.pucp.areasverdes.mod02.servicios.FichaTecnicaService.NuevaEvaluacion;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Ficha técnica y evaluación del ejemplar (AV-133, RF-INV-005). */
@RestController
@RequestMapping("/api/v1/inventario/ejemplares/{id}")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class FichaTecnicaController {

    private final FichaTecnicaService fichas;

    public FichaTecnicaController(FichaTecnicaService fichas) {
        this.fichas = fichas;
    }

    @GetMapping({"/ficha-tecnica", "/ficha-tecnica/"})
    @Operation(
            summary = "Ficha técnica del ejemplar con sus evaluaciones",
            description =
                    "Datos dasométricos, origen, estado de salud y evaluaciones fitosanitarias y"
                            + " estructurales, la más reciente primero. Sin ninguna medida, queda"
                            + " pendiente de medir.")
    public Ficha ficha(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return fichas.ficha(id, usuario);
    }

    @PatchMapping({"/ficha-tecnica", "/ficha-tecnica/"})
    @Operation(
            summary = "Actualiza los datos dasométricos, el origen y el estado de salud",
            description =
                    "Un campo nulo no cambia; para vaciar una medida, nómbrala en vaciar. La altura"
                            + " del fuste no puede superar la total.")
    public Ficha cambiar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioFicha cambio) {
        return fichas.cambiar(id, cambio, usuario);
    }

    @PostMapping({"/evaluaciones", "/evaluaciones/"})
    @Operation(
            summary = "Registra una evaluación fitosanitaria y estructural",
            description =
                    "Con hallazgos por criterio y su manejo recomendado, o marcada sin hallazgos."
                            + " Vacía se rechaza. No se edita: una corrección es otra evaluación.")
    public ResponseEntity<Evaluacion> evaluar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody NuevaEvaluacion entrada) {
        Evaluacion creada = fichas.evaluar(id, entrada, usuario);
        return ResponseEntity.created(
                        URI.create("/api/v1/inventario/ejemplares/" + id + "/ficha-tecnica/"))
                .body(creada);
    }
}
