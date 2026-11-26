package pe.edu.pucp.areasverdes.mod02.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod02.api.MapaAreasController.EjemplarAfectado;
import pe.edu.pucp.areasverdes.mod02.servicios.RedibujoContornoService;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Contorno de un área verde existente: consulta, impacto previo, redibujado e historial. */
@RestController
@RequestMapping("/api/v1/inventario/areas-verdes/{id}/contorno")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class ContornoAreaController {

    private final RedibujoContornoService contornos;

    public ContornoAreaController(RedibujoContornoService contornos) {
        this.contornos = contornos;
    }

    /** {@code contorno} es un GeoJSON {@code Polygon} en EPSG:4326 (longitud, latitud). */
    public record NuevoContorno(@NotNull(message = "Indica el contorno") JsonNode contorno) {}

    public record ConfirmacionContorno(Long areaAnteriorId, List<Long> ejemplaresIds) {}

    public record CambioContorno(
            @NotNull(message = "Indica el contorno") JsonNode contorno,
            ConfirmacionContorno confirmacion) {}

    public record ContornoArea(
            long areaVerdeId,
            UUID idFuente,
            int poligonos,
            JsonNode contorno,
            BigDecimal superficieCalculadaM2,
            BigDecimal superficieRegistradaM2) {}

    /**
     * Mismo contrato que {@code ImpactoPoligono} ({@code area_anterior_id}, {@code reasignables},
     * {@code conflictos}) más lo que ya queda dentro y las superficies. Los reasignables son los
     * ejemplares del área que quedan fuera del contorno nuevo: al confirmar pasan a no tener área.
     */
    public record ImpactoContorno(
            Long areaAnteriorId,
            List<EjemplarAfectado> dentro,
            List<EjemplarAfectado> reasignables,
            List<EjemplarAfectado> conflictos,
            BigDecimal superficieAnteriorM2,
            BigDecimal superficieNuevaM2) {}

    public record CambioContornoHistorial(
            long id,
            OffsetDateTime fecha,
            Long usuarioId,
            String usuarioNombre,
            BigDecimal superficieAnteriorM2,
            BigDecimal superficieNuevaM2,
            int ejemplaresDesvinculados) {}

    @GetMapping
    @Operation(summary = "Consulta el contorno vigente de un área verde y su superficie")
    public ContornoArea consultar(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return contornos.consultar(id, usuario);
    }

    @PostMapping("/impacto")
    @Operation(
            summary =
                    "Calcula, sin guardar, qué ejemplares quedan dentro y fuera de un contorno"
                            + " nuevo")
    public ImpactoContorno impacto(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody NuevoContorno nuevo) {
        return contornos.impacto(id, nuevo.contorno(), usuario);
    }

    @PutMapping
    @Operation(
            summary =
                    "Sustituye el contorno del área, recalcula su superficie y deja constancia en"
                            + " el historial",
            description =
                    "422 contorno-invalido, contorno-no-cerrado, contorno-fuera-del-campus o"
                            + " contorno-solapado. 409 confirmar-impacto-contorno o"
                            + " conflictos-cartograficos, con el impacto en el cuerpo, y"
                            + " area-con-varios-contornos. Reenviar el mismo contorno no cambia"
                            + " nada.")
    public ContornoArea redibujar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioContorno cambio) {
        return contornos.redibujar(id, cambio, usuario);
    }

    @GetMapping("/historial")
    @Operation(summary = "Lista los cambios de contorno del área, del más reciente al más antiguo")
    public List<CambioContornoHistorial> historial(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return contornos.historial(id, usuario);
    }
}
