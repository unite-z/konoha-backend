package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import static pe.edu.pucp.areasverdes.mod06.gestion.ia.PriorizacionInconsistenciasController.motivo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod06.gestion.api.Paginas;

/**
 * RF-IA-006 ({@code ia/evidencias-fitosanitarias/}). Consultar (incluidas las fotos):
 * REP.INDICADOR.CONSULTAR; solicitar: IA.PROPUESTA.PROPONER; validar, descartar y evaluación
 * manual: IA.PROPUESTA.VALIDAR (SUPUESTO: solo el jefe de sección valida).
 */
@RestController
@RequestMapping("/api/v1/ia/evidencias-fitosanitarias")
@Tag(name = "RF-IA-006 Evidencia fitosanitaria")
@SecurityRequirement(name = "sesionCore")
public class EvidenciasFitosanitariasController {

    private final EvidenciasFitosanitariasService evidencias;

    public EvidenciasFitosanitariasController(EvidenciasFitosanitariasService evidencias) {
        this.evidencias = evidencias;
    }

    @Operation(summary = "Análisis de evidencia fitosanitaria, del más reciente al más antiguo")
    @GetMapping({"", "/"})
    public SugerenciasIa.Pagina listar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String page,
            HttpServletRequest peticion) {
        SugerenciasIa.puedeConsultar(usuario);
        return evidencias.listar(estado, page, Paginas.desde(peticion));
    }

    @Operation(
            summary =
                    "Analiza de 1 a 5 fotografías (multipart: datos + fotografias); 201 también con"
                            + " calidad insuficiente")
    @PostMapping(
            value = {"", "/"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> solicitar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestPart(name = "datos", required = false) String datos,
            @RequestPart(name = "fotografias", required = false) List<MultipartFile> fotografias) {
        SugerenciasIa.puedeProponer(usuario);
        return evidencias.solicitar(datos, fotografias, usuario.id());
    }

    @Operation(summary = "Evaluación técnica manual, sin IA (201 en Validada)")
    @PostMapping({"/manual", "/manual/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> manual(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return evidencias.manual(cuerpo, usuario);
    }

    @Operation(summary = "Detalle de un análisis")
    @GetMapping({"/{id:[0-9]+}", "/{id:[0-9]+}/"})
    public Map<String, Object> detalle(
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable long id) {
        SugerenciasIa.puedeConsultar(usuario);
        return evidencias.obtener(id);
    }

    @Operation(summary = "Bytes de una fotografía del análisis (con sesión; 404 si no existe)")
    @GetMapping({
        "/{id:[0-9]+}/fotografias/{archivo:[0-9]+}",
        "/{id:[0-9]+}/fotografias/{archivo:[0-9]+}/"
    })
    public ResponseEntity<Resource> fotografia(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @PathVariable long archivo) {
        SugerenciasIa.puedeConsultar(usuario);
        EvidenciasFitosanitariasService.Fotografia f = evidencias.fotografia(id, archivo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.tipoMime()))
                .body(f.contenido());
    }

    @Operation(summary = "Validación técnica: Confirmada, OtraCategoria o NoConcluyente")
    @PostMapping({"/{id:[0-9]+}/validar", "/{id:[0-9]+}/validar/"})
    public Map<String, Object> validar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return evidencias.validar(id, cuerpo, usuario);
    }

    @Operation(summary = "Descarta el análisis")
    @PostMapping({"/{id:[0-9]+}/rechazar", "/{id:[0-9]+}/rechazar/"})
    public Map<String, Object> rechazar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> cuerpo) {
        SugerenciasIa.puedeValidar(usuario);
        return evidencias.rechazar(id, motivo(cuerpo), usuario.id());
    }
}
