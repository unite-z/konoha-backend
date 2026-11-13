package pe.edu.pucp.areasverdes.mod02.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod02.servicios.EjemplaresService;
import pe.edu.pucp.areasverdes.mod02.servicios.EjemplaresService.Ejemplar;
import pe.edu.pucp.areasverdes.mod02.servicios.EjemplaresService.NuevoEjemplar;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/inventario/ejemplares")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class EjemplaresController {

    private final EjemplaresService ejemplares;

    public EjemplaresController(EjemplaresService ejemplares) {
        this.ejemplares = ejemplares;
    }

    public record NuevoEjemplar(
            @Size(max = 40) String alias,
            @Size(max = 80) String codigoPlacaAntigua,
            Long especieId,
            @Size(max = 80) String tipoVegetacion,
            @Size(max = 200) String ubicacion,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitud,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitud,
            @Positive Integer cantidad,
            @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal alturaTotalM,
            @Positive Long areaVerdeId) {}

    public record CambioEjemplar(
            @Size(max = 40) String alias,
            @Size(max = 80) String codigoPlacaAntigua,
            Long especieId,
            @Size(max = 80) String tipoVegetacion,
            @Size(max = 200) String ubicacion,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitud,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitud,
            @Positive Integer cantidad,
            @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal alturaTotalM,
            @Positive Long areaVerdeId) {}

    @GetMapping
    @Operation(summary = "Consulta ejemplares por texto o por código exacto")
    public List<Ejemplar> listar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String referencia,
            @RequestParam(name = "area_verde_id", required = false) @Positive Long areaVerdeId,
            @RequestParam(required = false) @Size(max = 40) String codigo,
            @RequestParam(defaultValue = "1") int page) {
        return ejemplares.listar(q, referencia, areaVerdeId, codigo, page, usuario);
    }

    @PostMapping
    @Operation(summary = "Registra un ejemplar con código nuevo y único")
    public ResponseEntity<Ejemplar> crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevoEjemplar entrada) {
        Ejemplar creado =
                ejemplares.crear(
                        new EjemplaresService.NuevoEjemplar(
                                entrada.alias(),
                                entrada.codigoPlacaAntigua(),
                                entrada.especieId(),
                                entrada.tipoVegetacion(),
                                entrada.ubicacion(),
                                entrada.latitud(),
                                entrada.longitud(),
                                entrada.cantidad(),
                                entrada.alturaTotalM(),
                                entrada.areaVerdeId()),
                        usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/ejemplares/" + creado.id()))
                .body(creado);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Actualiza los datos de un ejemplar")
    public Ejemplar cambiar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioEjemplar cambio) {
        return ejemplares.cambiar(
                id,
                new EjemplaresService.CambioEjemplar(
                        cambio.alias(),
                        cambio.codigoPlacaAntigua(),
                        cambio.especieId(),
                        cambio.tipoVegetacion(),
                        cambio.ubicacion(),
                        cambio.latitud(),
                        cambio.longitud(),
                        cambio.cantidad(),
                        cambio.alturaTotalM(),
                        cambio.areaVerdeId()),
                usuario);
    }

    @PatchMapping("/{id}/situacion")
    @Operation(summary = "Cambia la situación del ejemplar y conserva su historial de ubicaciones")
    public Ejemplar cambiarSituacion(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody EjemplaresService.CambioSituacion cambio) {
        return ejemplares.cambiarSituacion(id, cambio, usuario);
    }

    @GetMapping("/{id}/historial-situacion")
    @Operation(summary = "Consulta los cambios de situación del ejemplar")
    public List<EjemplaresService.CambioRegistrado> historialSituacion(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return ejemplares.historialSituacion(id, usuario);
    }

    @GetMapping("/{id}/historial-actividades")
    @Operation(summary = "Consulta actividades de campo vinculadas al ejemplar")
    public List<EjemplaresService.Intervencion> historialActividades(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return ejemplares.historialActividades(id, usuario);
    }

    @GetMapping("/{id}/historial-intervenciones")
    @Operation(summary = "Consulta podas, aplicaciones fitosanitarias o incidencias de un ejemplar")
    public List<EjemplaresService.EventoHistorial> historialIntervenciones(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @RequestParam EjemplaresService.FuenteHistorial fuente) {
        return ejemplares.historialIntervenciones(id, fuente, usuario);
    }

    @DeleteMapping("/{id}/placa-antigua")
    @Operation(summary = "Quita el código de placa antigua del ejemplar")
    public Ejemplar quitarPlacaAntigua(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return ejemplares.quitarPlacaAntigua(id, usuario);
    }
}
