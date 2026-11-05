package pe.edu.pucp.areasverdes.mod03;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.areasverdes.mod03.Incidencias.Catalogos;
import pe.edu.pucp.areasverdes.mod03.Incidencias.Detalle;
import pe.edu.pucp.areasverdes.mod03.Incidencias.NuevaIncidencia;
import pe.edu.pucp.areasverdes.mod03.Incidencias.Resumen;
import pe.edu.pucp.areasverdes.mod03.Incidencias.Seguimiento;

/** Incidencias de MOD-03 (AV-274). Ver {@link Incidencias}. */
@RestController
@RequestMapping("/api/v1/planificacion/incidencias")
@Tag(name = "MOD-03 Incidencias")
@SecurityRequirement(name = "basicAuth")
public class IncidenciasController {

    private final Incidencias incidencias;
    private final ClasificacionIncidencias clasificacion;
    private final CierreIncidencias cierre;
    private final PlazosIncidencias plazos;
    private final DerivacionTercerizada derivacion;
    private final ObjectMapper json;
    private final Validator validador;

    public IncidenciasController(
            Incidencias incidencias,
            ClasificacionIncidencias clasificacion,
            CierreIncidencias cierre,
            PlazosIncidencias plazos,
            DerivacionTercerizada derivacion,
            ObjectMapper json,
            Validator validador) {
        this.incidencias = incidencias;
        this.clasificacion = clasificacion;
        this.cierre = cierre;
        this.plazos = plazos;
        this.derivacion = derivacion;
        this.json = json;
        this.validador = validador;
    }

    @Operation(summary = "Tipos, espacios con sus ejemplares y usuarios para registrar")
    @GetMapping({"/catalogos-registro", "/catalogos-registro/"})
    public Catalogos catalogos(UsuarioPlanificacion usuario) {
        return incidencias.catalogos(usuario);
    }

    @Operation(summary = "Registra una incidencia sin foto (desde la oficina, en nombre de otro)")
    @PostMapping(
            value = {"", "/"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Detalle registrar(
            UsuarioPlanificacion usuario,
            @RequestHeader(name = "Idempotency-Key", required = false) UUID clave,
            @Valid @RequestBody NuevaIncidencia nueva) {
        return incidencias.ficha(incidencias.registrar(nueva, null, clave, usuario));
    }

    @Operation(summary = "Registra una incidencia con foto: `datos` en JSON y `foto`")
    @PostMapping(
            value = {"", "/"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Detalle registrarConFoto(
            UsuarioPlanificacion usuario,
            @RequestHeader(name = "Idempotency-Key", required = false) UUID clave,
            @RequestParam("datos") String datos,
            @RequestPart(name = "foto", required = false) MultipartFile foto) {
        NuevaIncidencia nueva;
        try {
            nueva = json.readValue(datos, NuevaIncidencia.class);
        } catch (JsonProcessingException e) {
            throw new ErrorPlanificacion(400, "datos-invalidos", "Los datos no son un JSON válido");
        }
        Set<ConstraintViolation<NuevaIncidencia>> errores = validador.validate(nueva);
        if (!errores.isEmpty()) {
            throw new ErrorPlanificacion(
                    400, "datos-invalidos", errores.iterator().next().getMessage());
        }
        return incidencias.ficha(incidencias.registrar(nueva, foto, clave, usuario));
    }

    @Operation(summary = "Incidencias abiertas iguales: mismo tipo y mismo ejemplar o espacio")
    @GetMapping({"/posibles-duplicados", "/posibles-duplicados/"})
    public List<Incidencias.PosibleDuplicado> posiblesDuplicados(
            UsuarioPlanificacion usuario,
            @RequestParam String tipo,
            @RequestParam(required = false) String espacio,
            @RequestParam(required = false) String ejemplar) {
        return incidencias.posiblesDuplicados(tipo, espacio, ejemplar, usuario);
    }

    @Operation(summary = "Suma un reporte (descripción y foto) a una incidencia abierta")
    @PostMapping(
            value = {"/{id:\\d+}/reportes", "/{id:\\d+}/reportes/"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sumarReporte(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @RequestParam(required = false) String descripcion,
            @RequestPart(name = "foto", required = false) MultipartFile foto) {
        incidencias.sumarReporte(id, descripcion, foto, usuario);
    }

    @Operation(summary = "Ingenieros y practicantes que pueden inspeccionar (jefe)")
    @GetMapping({"/inspectores", "/inspectores/"})
    public List<ClasificacionIncidencias.Inspector> inspectores(UsuarioPlanificacion usuario) {
        return clasificacion.inspectores(usuario);
    }

    @Operation(summary = "Clasifica por severidad e impacto y elige la vía (jefe)")
    @PostMapping({"/{id:\\d+}/clasificar", "/{id:\\d+}/clasificar/"})
    public Detalle clasificar(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @Valid @RequestBody ClasificacionIncidencias.Clasificacion cuerpo) {
        clasificacion.clasificar(id, cuerpo, usuario);
        return incidencias.ficha(id);
    }

    @Operation(summary = "Descarta una incidencia con motivo (jefe)")
    @PostMapping({"/{id:\\d+}/descartar", "/{id:\\d+}/descartar/"})
    public Detalle descartar(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @Valid @RequestBody(required = false) ClasificacionIncidencias.Descarte cuerpo) {
        clasificacion.descartar(id, cuerpo, usuario);
        return incidencias.ficha(id);
    }

    @Operation(summary = "Pide una inspección en campo antes de clasificar (jefe)")
    @PostMapping({"/{id:\\d+}/inspeccion", "/{id:\\d+}/inspeccion/"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inspeccion(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @Valid @RequestBody ClasificacionIncidencias.SolicitudInspeccion cuerpo) {
        clasificacion.solicitarInspeccion(id, cuerpo, usuario);
    }

    @Operation(summary = "Proveedores activos con sus contratos para derivar (jefe)")
    @GetMapping({"/{id:\\d+}/proveedores", "/{id:\\d+}/proveedores/"})
    public List<DerivacionTercerizada.Proveedor> proveedores(
            UsuarioPlanificacion usuario, @PathVariable long id) {
        return derivacion.proveedores(usuario);
    }

    @Operation(summary = "Deriva una incidencia clasificada a un proveedor de MOD-05 (jefe)")
    @PostMapping({"/{id:\\d+}/requerimiento", "/{id:\\d+}/requerimiento/"})
    public Detalle requerimiento(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @Valid @RequestBody DerivacionTercerizada.NuevoRequerimiento cuerpo) {
        derivacion.derivar(id, cuerpo, usuario);
        return incidencias.ficha(id);
    }

    @Operation(summary = "Cierra una incidencia atendida con su evidencia (jefe)")
    @PostMapping({"/{id:\\d+}/cerrar", "/{id:\\d+}/cerrar/"})
    public Detalle cerrar(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @Valid @RequestBody(required = false) CierreIncidencias.Revision cuerpo) {
        cierre.cerrar(id, cuerpo, usuario);
        return incidencias.ficha(id);
    }

    @Operation(summary = "Devuelve una incidencia atendida a En atención con observaciones (jefe)")
    @PostMapping({"/{id:\\d+}/devolver", "/{id:\\d+}/devolver/"})
    public Detalle devolver(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @Valid @RequestBody(required = false) CierreIncidencias.Revision cuerpo) {
        cierre.devolver(id, cuerpo, usuario);
        return incidencias.ficha(id);
    }

    @Operation(summary = "Anota la causa del retraso de una alerta de plazo (jefe)")
    @PostMapping({"/{id:\\d+}/causa-retraso", "/{id:\\d+}/causa-retraso/"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void causaRetraso(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @Valid @RequestBody(required = false) PlazosIncidencias.CausaRetraso cuerpo) {
        plazos.anotarCausa(id, cuerpo, usuario);
    }

    @Operation(summary = "Capataces a los que se puede reasignar la incidencia (jefe)")
    @GetMapping({"/{id:\\d+}/capataces", "/{id:\\d+}/capataces/"})
    public List<PlazosIncidencias.Capataz> capataces(
            UsuarioPlanificacion usuario, @PathVariable long id) {
        return plazos.capataces(id, usuario);
    }

    @Operation(summary = "Reasigna el capataz responsable de la incidencia (jefe)")
    @PostMapping({"/{id:\\d+}/reasignar", "/{id:\\d+}/reasignar/"})
    public Resumen reasignar(
            UsuarioPlanificacion usuario,
            @PathVariable long id,
            @Valid @RequestBody PlazosIncidencias.Reasignacion cuerpo) {
        plazos.reasignar(id, cuerpo, usuario);
        return incidencias.ficha(id).resumen();
    }

    @Operation(summary = "Bandeja de incidencias de la oficina (jefe)")
    @GetMapping({"", "/"})
    public List<Resumen> bandeja(
            UsuarioPlanificacion usuario, @RequestParam(required = false) String grupo) {
        return incidencias.bandeja(grupo, usuario);
    }

    @Operation(summary = "Las incidencias que reportó el usuario")
    @GetMapping({"/mias", "/mias/"})
    public List<Resumen> mias(UsuarioPlanificacion usuario) {
        return incidencias.mias(usuario);
    }

    @Operation(summary = "Seguimiento de una incidencia propia")
    @GetMapping({"/mias/{id}", "/mias/{id}/"})
    public Seguimiento seguimiento(UsuarioPlanificacion usuario, @PathVariable long id) {
        return incidencias.seguimiento(id, usuario);
    }

    @Operation(summary = "Ficha de una incidencia (jefe)")
    @GetMapping({"/{id:\\d+}", "/{id:\\d+}/"})
    public Detalle detalle(UsuarioPlanificacion usuario, @PathVariable long id) {
        return incidencias.detalle(id, usuario);
    }
}
