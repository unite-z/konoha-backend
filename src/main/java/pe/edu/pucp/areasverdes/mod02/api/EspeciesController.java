package pe.edu.pucp.areasverdes.mod02.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod02.servicios.AccesoCoberturas;
import pe.edu.pucp.areasverdes.mod02.servicios.EspeciesService;
import pe.edu.pucp.areasverdes.mod02.servicios.EspeciesService.Especie;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/inventario/especies")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class EspeciesController {

    private final EspeciesService especies;

    public EspeciesController(EspeciesService especies) {
        this.especies = especies;
    }

    public record NuevaEspecie(
            @NotBlank @Size(max = 150) String nombreComun,
            @Size(max = 200) String nombreCientifico,
            @NotBlank @Size(max = 80) String formaBiologica) {}

    public record CambioEspecie(
            @Size(max = 150) String nombreComun,
            @Size(max = 200) String nombreCientifico,
            @Size(max = 80) String formaBiologica,
            Boolean activo) {}

    @GetMapping
    @Operation(summary = "Consulta el catálogo de especies vegetales")
    public List<Especie> listar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) String q,
            @RequestParam(name = "incluir_inactivas", defaultValue = "false")
                    boolean incluirInactivas) {
        AccesoCoberturas.consultar(usuario);
        return especies.listar(q, incluirInactivas);
    }

    @PostMapping
    @Operation(summary = "Añade una especie al catálogo")
    public ResponseEntity<Especie> crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevaEspecie entrada) {
        Especie creada =
                especies.crear(
                        entrada.nombreComun(),
                        entrada.nombreCientifico(),
                        entrada.formaBiologica(),
                        usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/especies/" + creada.id()))
                .body(creada);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Modifica o desactiva una especie conservando sus referencias")
    public Especie cambiar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioEspecie cambio) {
        return especies.cambiar(
                id,
                cambio.nombreComun(),
                cambio.nombreCientifico(),
                cambio.formaBiologica(),
                cambio.activo(),
                usuario);
    }
}
