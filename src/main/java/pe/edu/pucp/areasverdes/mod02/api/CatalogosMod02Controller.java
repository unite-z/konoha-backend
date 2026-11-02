package pe.edu.pucp.areasverdes.mod02.api;

import com.fasterxml.jackson.annotation.JsonAlias;
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
import pe.edu.pucp.areasverdes.mod02.servicios.AccesoCoberturas;
import pe.edu.pucp.areasverdes.mod02.servicios.CatalogosMod02Service;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/inventario")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class CatalogosMod02Controller {

    private final CatalogosMod02Service catalogos;

    public CatalogosMod02Controller(CatalogosMod02Service catalogos) {
        this.catalogos = catalogos;
    }

    public record Elemento(long id, String nombre, boolean activo) {}

    public record Cuartel(long id, String numero, String nombre, String estado) {}

    public record Sector(
            long id,
            String nombre,
            boolean activo,
            boolean atencionDemanda,
            String capatazNombre,
            String superficieM2) {}

    public record NuevoNombre(
            @NotBlank(message = "Indica el nombre") @Size(max = 120) String nombre) {}

    public record CambioElemento(String nombre, Boolean activo) {}

    public record NuevoCuartel(
            @NotBlank(message = "Indica el número") @Size(max = 40) String numero,
            @NotBlank(message = "Indica el nombre") @Size(max = 120) String nombre) {}

    public record CambioCuartel(String numero, String nombre, String estado) {}

    public record NuevoSector(
            @NotBlank(message = "Indica el nombre") @Size(max = 120) String nombre,
            @JsonAlias("atencion_a_demanda") Boolean atencionDemanda) {}

    public record CambioSector(
            Boolean activo, @JsonAlias("atencion_a_demanda") Boolean atencionDemanda) {}

    public record CambioCapataz(@JsonAlias("capataz_id") Long capatazId) {}

    public record Asignacion(
            long id, long capatazId, String capatazNombre, String fechaInicio, String fechaFin) {}

    public record Persona(long id, String nombre) {}

    @GetMapping("/tipos-uso")
    @Operation(summary = "Lista el catálogo de tipos de uso de las áreas verdes")
    public List<Elemento> tiposUso(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "incluir_inactivas", defaultValue = "false")
                    boolean incluirInactivos) {
        AccesoCoberturas.consultar(usuario);
        return catalogos.tiposUso(incluirInactivos);
    }

    @PostMapping("/tipos-uso")
    @Operation(summary = "Añade un tipo de uso al catálogo")
    public ResponseEntity<Elemento> crearTipoUso(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevoNombre entrada) {
        Elemento creado = catalogos.crearTipoUso(entrada, usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/tipos-uso/" + creado.id()))
                .body(creado);
    }

    @PatchMapping("/tipos-uso/{id}")
    public Elemento cambiarTipoUso(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioElemento cambio) {
        return catalogos.cambiarTipoUso(id, cambio, usuario);
    }

    @GetMapping("/cuarteles")
    public List<Cuartel> cuarteles(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "incluir_desaparecidos", defaultValue = "false")
                    boolean incluirDesaparecidos) {
        AccesoCoberturas.consultar(usuario);
        return catalogos.cuarteles(incluirDesaparecidos);
    }

    @PostMapping("/cuarteles")
    public ResponseEntity<Cuartel> crearCuartel(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevoCuartel entrada) {
        Cuartel creado = catalogos.crearCuartel(entrada, usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/cuarteles/" + creado.id()))
                .body(creado);
    }

    @PatchMapping("/cuarteles/{id}")
    public Cuartel cambiarCuartel(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @RequestBody CambioCuartel cambio) {
        return catalogos.cambiarCuartel(id, cambio, usuario);
    }

    @GetMapping("/sectores")
    public List<Sector> sectores(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(name = "incluir_inactivos", defaultValue = "false")
                    boolean incluirInactivos) {
        AccesoCoberturas.consultar(usuario);
        return catalogos.sectores(incluirInactivos);
    }

    @GetMapping("/capataces")
    public List<Persona> capataces(@AuthenticationPrincipal Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return catalogos.capataces();
    }

    @PostMapping("/sectores")
    public ResponseEntity<Sector> crearSector(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevoSector entrada) {
        Sector creado = catalogos.crearSector(entrada, usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/sectores/" + creado.id()))
                .body(creado);
    }

    @PatchMapping("/sectores/{id}")
    public Sector cambiarSector(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @RequestBody CambioSector cambio) {
        return catalogos.cambiarSector(id, cambio, usuario);
    }

    @PatchMapping("/sectores/{id}/capataz")
    public Sector asignarCapataz(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @RequestBody CambioCapataz cambio) {
        return catalogos.asignarCapataz(id, cambio.capatazId(), usuario);
    }

    @GetMapping("/sectores/{id}/historial-capataz")
    public List<Asignacion> historialCapataz(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        AccesoCoberturas.consultar(usuario);
        return catalogos.historialCapataz(id);
    }
}
