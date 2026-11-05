package pe.edu.pucp.areasverdes.mod02.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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
import pe.edu.pucp.areasverdes.mod02.servicios.CampanasPodaService;
import pe.edu.pucp.areasverdes.mod02.servicios.CampanasPodaService.CambioCampana;
import pe.edu.pucp.areasverdes.mod02.servicios.CampanasPodaService.Campana;
import pe.edu.pucp.areasverdes.mod02.servicios.CampanasPodaService.EjemplarCampana;
import pe.edu.pucp.areasverdes.mod02.servicios.CampanasPodaService.NuevaCampana;
import pe.edu.pucp.areasverdes.mod02.servicios.CampanasPodaService.VentanaCierre;
import pe.edu.pucp.areasverdes.mod02.servicios.ReglasCampanaPoda.EstadoCampana;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Campañas de poda mayor (AV-308, RF-POD-001). */
@RestController
@RequestMapping("/api/v1/inventario/campanas-poda")
@Tag(name = "MOD-02 Campañas de poda")
@SecurityRequirement(name = "basicAuth")
public class CampanasPodaController {

    private final CampanasPodaService campanas;

    public CampanasPodaController(CampanasPodaService campanas) {
        this.campanas = campanas;
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Lista las campañas de poda por año y estado")
    public Pagina<Campana> listar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) EstadoCampana estado,
            @RequestParam(defaultValue = "1") int page) {
        return campanas.listar(anio, estado, page, usuario);
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Crea una campaña en borrador o planificada (jefe de sección)")
    public ResponseEntity<Campana> crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevaCampana entrada) {
        Campana creada = campanas.crear(entrada, usuario);
        return ResponseEntity.created(
                        URI.create("/api/v1/inventario/campanas-poda/" + creada.id() + "/"))
                .body(creada);
    }

    @GetMapping({"/ventana-cierre", "/ventana-cierre/"})
    @Operation(summary = "Consulta el cierre del campus de un año en el calendario de MOD-03")
    public VentanaCierre ventanaCierre(
            @AuthenticationPrincipal Usuario usuario, @RequestParam int anio) {
        return campanas.ventanaCierre(anio, usuario);
    }

    @GetMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Consulta una campaña con su capacidad estimada")
    public Campana detalle(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return campanas.detalle(id, usuario);
    }

    @PatchMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Modifica una campaña; ejemplar_ids reemplaza la lista (jefe de sección)")
    public Campana cambiar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioCampana cambio) {
        return campanas.cambiar(id, cambio, usuario);
    }

    @DeleteMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Retira una campaña que aún no se ejecuta (jefe de sección)")
    public ResponseEntity<Void> darDeBaja(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        campanas.darDeBaja(id, usuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/{id}/ejemplares", "/{id}/ejemplares/"})
    @Operation(summary = "Lista los ejemplares de la campaña con su referencia al catastro")
    public Pagina<EjemplarCampana> ejemplares(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page) {
        return campanas.ejemplares(id, q, page, usuario);
    }

    @PostMapping({"/{id}/planificacion", "/{id}/planificacion/"})
    @Operation(summary = "Planifica un borrador tras validar RN-011 y RN-018 (jefe de sección)")
    public Campana planificar(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return campanas.planificar(id, usuario);
    }

    @PostMapping({"/{id}/inicio", "/{id}/inicio/"})
    @Operation(summary = "Autoriza la ejecución de una campaña planificada (jefe de sección)")
    public Campana iniciar(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return campanas.iniciar(id, usuario);
    }

    @PostMapping({"/{id}/fin", "/{id}/fin/"})
    @Operation(summary = "Finaliza una campaña en ejecución (jefe de sección)")
    public Campana finalizar(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        return campanas.finalizar(id, usuario);
    }
}
