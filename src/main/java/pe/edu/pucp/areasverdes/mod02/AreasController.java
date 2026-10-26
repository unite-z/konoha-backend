package pe.edu.pucp.areasverdes.mod02;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/inventario/areas-verdes")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class AreasController {

    private final AreasService areas;

    public AreasController(AreasService areas) {
        this.areas = areas;
    }

    public record AreaVerde(
            long id,
            String nombre,
            String lugar,
            String ubicacion,
            String superficieM2,
            Long tipoCoberturaId,
            String tipoCoberturaNombre,
            Long tipoUsoId,
            String tipoUsoNombre,
            Long cuartelId,
            String cuartelNumero,
            Long sectorId,
            String sectorNombre,
            boolean activo) {}

    public record PaginaAreas(int count, Integer next, Integer previous, List<AreaVerde> results) {}

    public record NuevaAreaVerde(
            @NotBlank(message = "Indica el nombre del área") @Size(max = 150) String nombre,
            @NotBlank(message = "Indica el lugar o referente") @Size(max = 150) String lugar,
            @NotBlank(message = "Indica la ubicación") @Size(max = 250) String ubicacion,
            @NotNull(message = "Indica la superficie")
                    @DecimalMin(value = "0.01", message = "La superficie debe ser mayor que cero")
                    BigDecimal superficieM2,
            Long tipoCoberturaId,
            Long sectorId,
            Long tipoUsoId,
            Long cuartelId) {}

    public record CambioAreaVerde(
            @Size(max = 150) String nombre,
            @Size(max = 150) String lugar,
            @Size(max = 250) String ubicacion,
            @DecimalMin(value = "0.01", message = "La superficie debe ser mayor que cero")
                    BigDecimal superficieM2,
            Boolean activo,
            Long tipoCoberturaId,
            Long tipoUsoId,
            Long cuartelId) {}

    public record CambioSectorArea(@JsonAlias("sector_id") Long sectorId) {}

    public record AsignacionSector(
            long id, long sectorId, String sectorNombre, String fechaInicio, String fechaFin) {}

    @Operation(summary = "Consulta áreas verdes por nombre")
    @ApiResponse(responseCode = "200", description = "Página del inventario")
    @GetMapping({"", "/"})
    public PaginaAreas listar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) String q,
            @RequestParam(name = "sector_id", required = false) Long sectorId,
            @RequestParam(name = "tipo_uso_id", required = false) Long tipoUsoId,
            @RequestParam(name = "cuartel_id", required = false) Long cuartelId,
            @RequestParam(defaultValue = "1") int page) {
        AccesoCoberturas.consultar(usuario);
        return areas.listar(q, sectorId, tipoUsoId, cuartelId, page);
    }

    @Operation(summary = "Registra un área verde")
    @ApiResponse(responseCode = "201", description = "Área verde registrada")
    @ApiResponse(responseCode = "400", description = "Datos no válidos")
    @ApiResponse(
            responseCode = "404",
            description = "La cobertura indicada no existe o está inactiva")
    @PostMapping(
            value = {"", "/"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AreaVerde> crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevaAreaVerde nueva) {
        AreaVerde area = areas.crear(nueva, usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/areas-verdes/" + area.id()))
                .body(area);
    }

    @PatchMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Actualiza o da de baja un área verde conservando sus referencias")
    public AreaVerde modificar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioAreaVerde cambio) {
        return areas.modificar(id, cambio, usuario);
    }

    @PatchMapping({"/{id}/sector", "/{id}/sector/"})
    @Operation(summary = "Asigna o reasigna un área a un sector manteniendo su historial")
    public AreaVerde asignarSector(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @RequestBody CambioSectorArea cambio) {
        return areas.asignarSector(id, cambio.sectorId(), usuario);
    }

    @GetMapping({"/{id}/historial-sector", "/{id}/historial-sector/"})
    @Operation(summary = "Consulta las asignaciones históricas de sector de un área verde")
    public List<AsignacionSector> historialSector(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        AccesoCoberturas.consultar(usuario);
        return areas.historialSector(id);
    }
}
