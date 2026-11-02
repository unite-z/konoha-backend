package pe.edu.pucp.areasverdes.mod02.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod02.servicios.AccesoCoberturas;
import pe.edu.pucp.areasverdes.mod02.servicios.MapaAreasService;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/inventario/mapa")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class MapaAreasController {

    private final MapaAreasService mapa;

    public MapaAreasController(MapaAreasService mapa) {
        this.mapa = mapa;
    }

    public record UsoFuente(String usoFuente, Long tipoUsoId, String tipoUsoNombre) {}

    public record PoligonoAsignado(UUID idFuente, Long areaVerdeId, String areaVerdeNombre) {}

    public record CambioUsoFuente(@NotBlank @Size(max = 180) String usoFuente, Long tipoUsoId) {}

    public record CambioPoligono(Long areaVerdeId) {}

    @GetMapping("/usos-fuente")
    @Operation(summary = "Lista la clasificación de los usos originales del mapa")
    public List<UsoFuente> usosFuente(@AuthenticationPrincipal Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return mapa.usosFuente();
    }

    @PutMapping("/usos-fuente")
    @Operation(summary = "Asigna o quita el tipo de uso de una categoría del mapa")
    public UsoFuente cambiarUsoFuente(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody CambioUsoFuente cambio) {
        return mapa.cambiarUsoFuente(cambio, usuario);
    }

    @GetMapping("/poligonos")
    @Operation(summary = "Lista los vínculos existentes entre polígonos y áreas del inventario")
    public List<PoligonoAsignado> poligonos(@AuthenticationPrincipal Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return mapa.poligonos();
    }

    @PutMapping("/poligonos/{idFuente}")
    @Operation(summary = "Asigna o quita el área del inventario vinculada a un polígono")
    public ResponseEntity<PoligonoAsignado> asignarPoligono(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable UUID idFuente,
            @RequestBody CambioPoligono cambio) {
        PoligonoAsignado asignado = mapa.asignarPoligono(idFuente, cambio, usuario);
        return ResponseEntity.ok(asignado);
    }
}
