package pe.edu.pucp.areasverdes.mod02.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod02.servicios.RutaCorteService;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Ruta de jardines del corte de césped (AV-129, RF-INV-004, HU-MOD02-009). */
@RestController
@RequestMapping("/api/v1/inventario/ruta-corte")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class RutaCorteController {

    /**
     * Un retiro de la ruta: desde qué día, por qué y quién; si se reincorporó, cuándo y por qué.
     */
    public record Retiro(
            long id,
            long areaVerdeId,
            String areaVerdeNombre,
            LocalDate desde,
            String motivo,
            String autor,
            OffsetDateTime fechaRegistro,
            LocalDate reincorporadoDesde,
            String motivoReincorporacion,
            String autorReincorporacion) {}

    /**
     * Un jardín (área verde activa con sector). {@code enRuta}: se corta en los ciclos que empiezan
     * en la fecha consultada. {@code retiro}: el retiro abierto, aunque empiece más adelante.
     */
    public record Jardin(
            long areaVerdeId,
            String nombre,
            String sector,
            boolean aDemanda,
            BigDecimal superficieM2,
            boolean enRuta,
            Retiro retiro) {}

    public record Ruta(
            LocalDate fecha,
            int totalJardines,
            BigDecimal superficieTotalM2,
            List<Jardin> jardines) {}

    public record NuevoRetiro(
            @NotNull(message = "Elige el jardín") @Positive Long areaVerdeId,
            @NotNull(message = "Indica desde qué día se aplica") LocalDate desde,
            @Size(max = 300, message = "El motivo admite hasta 300 caracteres") String motivo) {}

    public record Reincorporacion(
            @NotNull(message = "Indica desde qué día vuelve a la ruta") LocalDate desde,
            @Size(max = 300, message = "El motivo admite hasta 300 caracteres") String motivo) {}

    private final RutaCorteService rutas;

    public RutaCorteController(RutaCorteService rutas) {
        this.rutas = rutas;
    }

    @GetMapping({"", "/"})
    @Operation(
            summary = "Ruta de jardines del corte de césped en una fecha",
            description =
                    "Jardines de los sectores con su superficie; en la ruta, los de sectores"
                            + " ordinarios que no están retirados en esa fecha (por defecto, hoy)."
                            + " Es la ruta que MOD-03 copia al programar un ciclo.")
    public Ruta ruta(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fecha) {
        return rutas.ruta(fecha, usuario);
    }

    @GetMapping({"/retiros", "/retiros/"})
    @Operation(
            summary = "Historial de retiros de la ruta",
            description = "Todos los retiros, abiertos y reincorporados, el más reciente primero.")
    public List<Retiro> retiros(@AuthenticationPrincipal Usuario usuario) {
        return rutas.retiros(usuario);
    }

    @PostMapping({"/retiros", "/retiros/"})
    @Operation(
            summary = "Retira un jardín de la ruta con motivo",
            description =
                    "Se aplica a los ciclos que empiezan desde esa fecha (hoy o después); los ya"
                            + " programados conservan su ruta.")
    public ResponseEntity<Retiro> retirar(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevoRetiro entrada) {
        Retiro creado = rutas.retirar(entrada, usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/ruta-corte/retiros/"))
                .body(creado);
    }

    @PostMapping({"/retiros/{id}/reincorporar", "/retiros/{id}/reincorporar/"})
    @Operation(
            summary = "Reincorpora a la ruta un jardín retirado",
            description = "Cierra el retiro con la fecha y el motivo; el historial se conserva.")
    public Retiro reincorporar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody Reincorporacion entrada) {
        return rutas.reincorporar(id, entrada, usuario);
    }
}
