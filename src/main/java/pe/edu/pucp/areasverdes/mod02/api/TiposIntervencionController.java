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
import pe.edu.pucp.areasverdes.mod02.servicios.TiposIntervencionService;
import pe.edu.pucp.areasverdes.mod02.servicios.TiposIntervencionService.TipoIntervencion;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Catálogo de tipos de intervención tercerizada (AV-313, RF-POD-006). */
@RestController
@RequestMapping("/api/v1/inventario/tipos-intervencion")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class TiposIntervencionController {

    private final TiposIntervencionService tipos;

    public TiposIntervencionController(TiposIntervencionService tipos) {
        this.tipos = tipos;
    }

    public record NuevoTipo(
            @NotBlank(message = "Indica el nombre del tipo")
                    @Size(max = 80, message = "El nombre no puede superar 80 caracteres")
                    String nombre,
            @Size(max = 300, message = "El alcance no puede superar 300 caracteres")
                    String descripcion,
            Boolean esPoda) {}

    public record CambioTipo(
            @Size(max = 80, message = "El nombre no puede superar 80 caracteres") String nombre,
            @Size(max = 300, message = "El alcance no puede superar 300 caracteres")
                    String descripcion,
            Boolean activo) {}

    @GetMapping({"", "/"})
    @Operation(summary = "Consulta los tipos de intervención tercerizada con su uso")
    public List<TipoIntervencion> listar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "incluir_inactivos", defaultValue = "false")
                    boolean incluirInactivos) {
        return tipos.listar(incluirInactivos, usuario);
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Añade un tipo de intervención (jefe de sección)")
    public ResponseEntity<TipoIntervencion> crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevoTipo entrada) {
        TipoIntervencion creado =
                tipos.crear(
                        entrada.nombre(),
                        entrada.descripcion(),
                        Boolean.TRUE.equals(entrada.esPoda()),
                        usuario);
        return ResponseEntity.created(
                        URI.create("/api/v1/inventario/tipos-intervencion/" + creado.id()))
                .body(creado);
    }

    @PatchMapping({"/{id}", "/{id}/"})
    @Operation(
            summary =
                    "Modifica o desactiva un tipo conservando sus intervenciones (jefe de sección)")
    public TipoIntervencion cambiar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioTipo cambio) {
        return tipos.cambiar(id, cambio.nombre(), cambio.descripcion(), cambio.activo(), usuario);
    }
}
