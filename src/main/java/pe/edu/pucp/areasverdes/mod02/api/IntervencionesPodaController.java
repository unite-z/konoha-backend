package pe.edu.pucp.areasverdes.mod02.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import pe.edu.pucp.areasverdes.mod01.api.Pagina;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService.CambioIntervencion;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService.CatalogosRegistro;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService.Intervencion;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService.NuevaIntervencion;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService.OpcionCatalogo;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Registro de poda por ejemplar (AV-309, RF-POD-002). La foto va en {@code /core/adjuntos/}. */
@RestController
@RequestMapping("/api/v1/inventario/intervenciones-poda")
@Tag(name = "MOD-02 Registro de poda")
@SecurityRequirement(name = "basicAuth")
public class IntervencionesPodaController {

    private final IntervencionesPodaService intervenciones;

    public IntervencionesPodaController(IntervencionesPodaService intervenciones) {
        this.intervenciones = intervenciones;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Lista las podas por campaña o ejemplar; es el historial del ejemplar")
    public Pagina<Intervencion> listar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "campana_id", required = false) Long campanaId,
            @RequestParam(name = "ejemplar_id", required = false) Long ejemplarId,
            @RequestParam(defaultValue = "1") int page) {
        return intervenciones.listar(campanaId, ejemplarId, page, usuario);
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Registra la poda de un ejemplar; sin foto queda incompleta")
    public ResponseEntity<Intervencion> crear(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody NuevaIntervencion entrada) {
        Intervencion creada = intervenciones.crear(entrada, usuario);
        return ResponseEntity.created(
                        URI.create("/api/v1/inventario/intervenciones-poda/" + creada.id() + "/"))
                .body(creada);
    }

    @GetMapping({"/catalogos", "/catalogos/"})
    @Operation(summary = "Tipos de poda y unidades para el formulario de registro")
    public CatalogosRegistro catalogos(@AuthenticationPrincipal Usuario usuario) {
        return intervenciones.catalogos(usuario);
    }

    @GetMapping({"/incidencias", "/incidencias/"})
    @Operation(summary = "Incidencias de un ejemplar, para indicar la de origen")
    public List<OpcionCatalogo> incidencias(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "ejemplar_id") long ejemplarId) {
        return intervenciones.incidencias(ejemplarId, usuario);
    }

    @GetMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Consulta una intervención con su estado de verificación")
    public Intervencion detalle(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return intervenciones.detalle(id, usuario);
    }

    @PatchMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Corrige los datos de una intervención mientras la campaña sigue abierta")
    public Intervencion cambiar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioIntervencion cambio) {
        return intervenciones.cambiar(id, cambio, usuario);
    }

    @DeleteMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Anula una intervención registrada por error")
    public ResponseEntity<Void> anular(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        intervenciones.anular(id, usuario);
        return ResponseEntity.noContent().build();
    }
}
