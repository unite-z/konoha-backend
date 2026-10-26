package pe.edu.pucp.areasverdes.mod02;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/inventario/coberturas")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class CoberturasController {

    private final CoberturasService coberturas;

    public CoberturasController(CoberturasService coberturas) {
        this.coberturas = coberturas;
    }

    public record Cobertura(long id, String nombre, boolean activo) {}

    public record NuevaCobertura(
            @NotBlank(message = "Indica el nombre de la cobertura")
                    @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
                    String nombre) {}

    public record CambioCobertura(
            @Size(max = 120, message = "El nombre no puede superar 120 caracteres") String nombre,
            Boolean activo) {}

    @Operation(summary = "Lista las coberturas vegetales para consulta y altas de áreas")
    @ApiResponse(responseCode = "200", description = "Catálogo de coberturas")
    @GetMapping({"", "/"})
    public List<Cobertura> listar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "incluir_inactivas", defaultValue = "false")
                    boolean incluirInactivas) {
        AccesoCoberturas.consultar(usuario);
        return coberturas.listar(incluirInactivas);
    }

    @Operation(summary = "Crea una cobertura vegetal (jefe de sección)")
    @ApiResponse(responseCode = "201", description = "Cobertura creada")
    @ApiResponse(responseCode = "409", description = "Ya existe una cobertura con ese nombre")
    @PostMapping({"", "/"})
    public ResponseEntity<Cobertura> crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevaCobertura nueva) {
        Cobertura cobertura = coberturas.crear(nueva, usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/coberturas/" + cobertura.id()))
                .body(cobertura);
    }

    @Operation(summary = "Modifica o activa/desactiva una cobertura sin borrar su referencia")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "404", description = "Cobertura inexistente")
    @PatchMapping({"/{id}", "/{id}/"})
    public Cobertura cambiarEstado(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioCobertura cambio) {
        return coberturas.modificar(id, cambio, usuario);
    }
}
