package pe.edu.pucp.areasverdes.mod02.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod02.servicios.AccesoCoberturas;
import pe.edu.pucp.areasverdes.mod02.servicios.MapaAreasService;
import pe.edu.pucp.areasverdes.mod02.servicios.GeometriaCampus;
import pe.edu.pucp.areasverdes.mod02.servicios.EjemplaresService;
import pe.edu.pucp.areasverdes.mod02.servicios.EjemplaresService.Ejemplar;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/inventario/mapa")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class MapaAreasController {

    private final MapaAreasService mapa;

    private final GeometriaCampus geometria;
    private final EjemplaresService ejemplares;

    public MapaAreasController(MapaAreasService mapa, GeometriaCampus geometria, EjemplaresService ejemplares) {
        this.mapa = mapa;
        this.geometria = geometria;
        this.ejemplares = ejemplares;
    }

    public record UsoFuente(String usoFuente, Long tipoUsoId, String tipoUsoNombre) {}

    public record PoligonoAsignado(
            UUID idFuente,
            Long areaVerdeId,
            String areaVerdeNombre,
            Long areaVerdeTipoUsoId,
            String areaVerdeTipoUsoNombre,
            String superficieRegistradaM2,
            Boolean areaVerdeActiva) {}

    public record CambioUsoFuente(@NotBlank @Size(max = 180) String usoFuente, Long tipoUsoId) {}

    public record ConfirmacionPoligono(Long areaAnteriorId, List<Long> ejemplaresIds) {}

    public record CambioPoligono(Long areaVerdeId, ConfirmacionPoligono confirmacion) {}

    public record EjemplarAfectado(long id, String codigo) {}

    public record ImpactoPoligono(Long areaAnteriorId, List<EjemplarAfectado> reasignables,
            List<EjemplarAfectado> conflictos) {}

    @GetMapping(value = "/geometria", produces = "application/geo+json")
    @Operation(summary = "Devuelve la capa de referencia del campus")
    public String geometria(@AuthenticationPrincipal Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return geometria.geojson();
    }

    @GetMapping("/ubicacion")
    @Operation(summary = "Consulta las áreas vinculadas que contienen un punto")
    public GeometriaCampus.Ubicacion ubicacion(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud,
            @RequestParam(name = "area_verde_id", required = false) Long areaVerdeId) {
        AccesoCoberturas.consultar(usuario);
        return geometria.consultar(latitud, longitud, areaVerdeId);
    }

    @GetMapping("/poligonos/{idFuente}/ejemplares")
    @Operation(summary = "Lista los ejemplares ubicados físicamente dentro del polígono")
    public List<Ejemplar> ejemplares(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID idFuente) {
        return ejemplares.enPoligono(idFuente, usuario);
    }

    @GetMapping("/poligonos/{idFuente}/impacto")
    @Operation(summary = "Consulta qué ejemplares afecta un cambio de vínculo cartográfico")
    public ImpactoPoligono impacto(
            @AuthenticationPrincipal Usuario usuario, @PathVariable UUID idFuente,
            @RequestParam(name = "area_verde_id", required = false) Long areaVerdeId) {
        return mapa.impacto(idFuente, areaVerdeId, usuario);
    }

    @GetMapping("/usos-fuente")
    @Operation(
            summary =
                    "Lista equivalencias cartográficas de los usos originales; no sustituye el tipo"
                            + " del área vinculada")
    public List<UsoFuente> usosFuente(@AuthenticationPrincipal Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return mapa.usosFuente();
    }

    @PutMapping("/usos-fuente")
    @Operation(summary = "Asigna o quita la equivalencia cartográfica de una categoría del mapa")
    public UsoFuente cambiarUsoFuente(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody CambioUsoFuente cambio) {
        return mapa.cambiarUsoFuente(cambio, usuario);
    }

    @GetMapping("/poligonos")
    @Operation(summary = "Lista los vínculos existentes y la clasificación del área vinculada")
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
