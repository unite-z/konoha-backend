package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod03.CorteCesped.CicloCorte;
import pe.edu.pucp.areasverdes.mod03.CorteCesped.RutaCorte;

/** Ciclo de corte de césped (AV-259 a AV-262). Ver {@link CorteCesped}. */
@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Corte de césped")
@SecurityRequirement(name = "basicAuth")
public class CorteController {

    private final CorteCesped corte;
    private final MarcadoCorte marcado;

    public CorteController(CorteCesped corte, MarcadoCorte marcado) {
        this.corte = corte;
        this.marcado = marcado;
    }

    @Operation(summary = "Ruta de hoy del capataz: el corte del día en sus sectores")
    @GetMapping({"/ruta-de-hoy", "/ruta-de-hoy/"})
    public MarcadoCorte.RespuestaRutaDeHoy rutaDeHoy(UsuarioPlanificacion usuario) {
        return marcado.rutaDeHoy(usuario);
    }

    @Operation(summary = "Jardines del maestro que no están en la ruta de hoy")
    @GetMapping({"/ruta-de-hoy/fuera-de-ruta", "/ruta-de-hoy/fuera-de-ruta/"})
    public MarcadoCorte.Busqueda fueraDeRuta(
            UsuarioPlanificacion usuario, @RequestParam(required = false) String q) {
        return marcado.buscarFueraDeRuta(q, usuario);
    }

    @Operation(summary = "Marca un jardín como cortado con la hora real (Idempotency-Key)")
    @PostMapping({
        "/ciclos/{programacion}/jardines/{jardin}/marca",
        "/ciclos/{programacion}/jardines/{jardin}/marca/"
    })
    public CorteCesped.JardinRuta marcar(
            UsuarioPlanificacion usuario,
            @PathVariable long programacion,
            @PathVariable long jardin,
            @RequestHeader(name = "Idempotency-Key", required = false) UUID clave,
            @RequestBody(required = false) MarcadoCorte.NuevaMarca cuerpo) {
        return marcado.marcar(programacion, jardin, cuerpo, clave, usuario);
    }

    @Operation(summary = "Anota una observación del jardín sin tocar la marca")
    @PostMapping({
        "/ciclos/{programacion}/jardines/{jardin}/observaciones",
        "/ciclos/{programacion}/jardines/{jardin}/observaciones/"
    })
    @ResponseStatus(HttpStatus.CREATED)
    public MarcadoCorte.ObservacionGuardada observar(
            UsuarioPlanificacion usuario,
            @PathVariable long programacion,
            @PathVariable long jardin,
            @Valid @RequestBody(required = false) MarcadoCorte.Observacion cuerpo) {
        return marcado.observar(programacion, jardin, cuerpo, usuario);
    }

    @Operation(summary = "Registra un jardín cortado fuera de la ruta, con nota (Idempotency-Key)")
    @PostMapping({"/ciclos/{programacion}/fuera-de-ruta", "/ciclos/{programacion}/fuera-de-ruta/"})
    @ResponseStatus(HttpStatus.CREATED)
    public MarcadoCorte.MarcaFueraDeRuta marcarFueraDeRuta(
            UsuarioPlanificacion usuario,
            @PathVariable long programacion,
            @RequestHeader(name = "Idempotency-Key", required = false) UUID clave,
            @Valid @RequestBody MarcadoCorte.NuevaMarcaFueraDeRuta cuerpo) {
        return marcado.marcarFueraDeRuta(programacion, cuerpo, clave, usuario);
    }

    @Operation(
            summary =
                    "Ruta propuesta por zonas para programar el servicio, y si va fuera de"
                            + " frecuencia")
    @GetMapping({"/servicios/{id}/ruta", "/servicios/{id}/ruta/"})
    public RutaCorte ruta(UsuarioPlanificacion usuario, @PathVariable long id) {
        return corte.ruta(id, usuario);
    }

    @Operation(summary = "Avance del ciclo de corte: porcentaje, cortados por día y por cortar")
    @GetMapping({"/ciclos/{id}/avance", "/ciclos/{id}/avance/"})
    public CorteCesped.Avance avance(UsuarioPlanificacion usuario, @PathVariable long id) {
        return corte.avance(id, usuario);
    }

    @Operation(summary = "Ciclo de corte con su ruta de jardines y sus marcas")
    @GetMapping({"/ciclos/{id}", "/ciclos/{id}/"})
    public CicloCorte ciclo(UsuarioPlanificacion usuario, @PathVariable long id) {
        return corte.ciclo(id, usuario);
    }
}
