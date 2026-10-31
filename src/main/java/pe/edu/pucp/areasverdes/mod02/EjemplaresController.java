package pe.edu.pucp.areasverdes.mod02;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
import pe.edu.pucp.areasverdes.mod02.EjemplaresService.Ejemplar;
import pe.edu.pucp.areasverdes.mod02.EjemplaresService.Especie;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/inventario")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class EjemplaresController {

    private final EjemplaresService ejemplares;

    public EjemplaresController(EjemplaresService ejemplares) {
        this.ejemplares = ejemplares;
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

    public record NuevoEjemplar(
            @Size(max = 80) String codigoPlacaAntigua,
            Long especieId,
            @Size(max = 80) String tipoVegetacion,
            @Size(max = 200) String ubicacion,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitud,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitud,
            @Positive Integer cantidad) {}

    public record CambioEjemplar(
            @Size(max = 80) String codigoPlacaAntigua,
            Long especieId,
            @Size(max = 80) String tipoVegetacion,
            @Size(max = 200) String ubicacion,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitud,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitud,
            @Positive Integer cantidad) {}

    @GetMapping("/ejemplares")
    @Operation(summary = "Consulta ejemplares por código, referencia o especie")
    public List<Ejemplar> listarEjemplares(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String referencia,
            @RequestParam(defaultValue = "1") int page) {
        AccesoCoberturas.consultar(usuario);
        return ejemplares.ejemplares(q, referencia, page);
    }

    @PostMapping("/ejemplares")
    @Operation(summary = "Registra un ejemplar con código nuevo y único")
    public ResponseEntity<Ejemplar> crearEjemplar(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevoEjemplar entrada) {
        Ejemplar creado =
                ejemplares.crearEjemplar(
                        new EjemplaresService.NuevoEjemplar(
                                entrada.codigoPlacaAntigua(),
                                entrada.especieId(),
                                entrada.tipoVegetacion(),
                                entrada.ubicacion(),
                                entrada.latitud(),
                                entrada.longitud(),
                                entrada.cantidad()),
                        usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/ejemplares/" + creado.id()))
                .body(creado);
    }

    @PatchMapping("/ejemplares/{id}")
    @Operation(summary = "Actualiza los datos de un ejemplar")
    public Ejemplar cambiarEjemplar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioEjemplar cambio) {
        return ejemplares.cambiarEjemplar(
                id,
                new EjemplaresService.CambioEjemplar(
                        cambio.codigoPlacaAntigua(),
                        cambio.especieId(),
                        cambio.tipoVegetacion(),
                        cambio.ubicacion(),
                        cambio.latitud(),
                        cambio.longitud(),
                        cambio.cantidad()),
                usuario);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/ejemplares/{id}/placa-antigua")
    @Operation(summary = "Quita el código de placa antigua del ejemplar")
    public Ejemplar quitarPlacaAntigua(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return ejemplares.quitarPlacaAntigua(id, usuario);
    }

    @GetMapping("/especies")
    @Operation(summary = "Consulta el catálogo de especies vegetales")
    public List<Especie> listarEspecies(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean incluirInactivas) {
        AccesoCoberturas.consultar(usuario);
        return ejemplares.especies(q, incluirInactivas);
    }

    @PostMapping("/especies")
    @Operation(summary = "Añade una especie al catálogo")
    public ResponseEntity<Especie> crearEspecie(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevaEspecie entrada) {
        Especie creada =
                ejemplares.crearEspecie(
                        entrada.nombreComun(),
                        entrada.nombreCientifico(),
                        entrada.formaBiologica(),
                        usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/especies/" + creada.id()))
                .body(creada);
    }

    @PatchMapping("/especies/{id}")
    @Operation(summary = "Modifica o desactiva una especie conservando sus referencias")
    public Especie cambiarEspecie(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioEspecie cambio) {
        return ejemplares.cambiarEspecie(
                id,
                cambio.nombreComun(),
                cambio.nombreCientifico(),
                cambio.formaBiologica(),
                cambio.activo(),
                usuario);
    }
}
