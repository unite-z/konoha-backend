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
    private final ObjectMapper json;
    private final Validator validador;

    public IncidenciasController(Incidencias incidencias, ObjectMapper json, Validator validador) {
        this.incidencias = incidencias;
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

    @Operation(summary = "Bandeja de incidencias de la oficina (jefe)")
    @GetMapping({"", "/"})
    public List<Resumen> bandeja(UsuarioPlanificacion usuario) {
        return incidencias.bandeja(usuario);
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
