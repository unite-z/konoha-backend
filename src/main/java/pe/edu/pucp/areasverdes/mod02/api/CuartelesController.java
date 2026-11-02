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
import pe.edu.pucp.areasverdes.mod02.servicios.CuartelesService;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/inventario/cuarteles")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class CuartelesController {

    private final CuartelesService cuarteles;

    public CuartelesController(CuartelesService cuarteles) {
        this.cuarteles = cuarteles;
    }

    public record Cuartel(long id, String numero, String nombre, String estado) {}

    public record NuevoCuartel(
            @NotBlank(message = "Indica el número") @Size(max = 40) String numero,
            @NotBlank(message = "Indica el nombre") @Size(max = 120) String nombre) {}

    public record CambioCuartel(String numero, String nombre, String estado) {}

    @GetMapping
    @Operation(summary = "Lista los cuarteles de referencia")
    public List<Cuartel> listar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "incluir_desaparecidos", defaultValue = "false")
                    boolean incluirDesaparecidos) {
        AccesoCoberturas.consultar(usuario);
        return cuarteles.listar(incluirDesaparecidos);
    }

    @PostMapping
    @Operation(summary = "Registra un cuartel de referencia")
    public ResponseEntity<Cuartel> crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevoCuartel nuevo) {
        Cuartel creado = cuarteles.crear(nuevo, usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/cuarteles/" + creado.id()))
                .body(creado);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Modifica o marca como desaparecido un cuartel")
    public Cuartel modificar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioCuartel cambio) {
        return cuarteles.modificar(id, cambio, usuario);
    }
}
