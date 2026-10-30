package pe.edu.pucp.areasverdes.mod01.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.areasverdes.mod01.servicios.AdjuntosService;
import pe.edu.pucp.areasverdes.mod01.servicios.AdjuntosService.Adjunto;
import pe.edu.pucp.areasverdes.mod01.servicios.HistorialService;
import pe.edu.pucp.areasverdes.mod01.servicios.HistorialService.Entrada;
import pe.edu.pucp.areasverdes.mod01.servicios.RepositorioService;
import pe.edu.pucp.areasverdes.mod01.servicios.RepositorioService.Elemento;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/**
 * Historial de cambios (RF-T-008), adjuntos (RF-T-007) y repositorio común (RF-T-010). Cada
 * consulta comprueba el permiso de la entidad a la que pertenece el registro.
 */
@RestController
@RequestMapping("/api/v1/core")
@Tag(name = "MOD-01 Trazabilidad, evidencias y repositorio")
@SecurityRequirement(name = "sesionCore")
public class EvidenciasController {

    /** Página del repositorio: el formato de {@link Pagina} más si el tipo ya está disponible. */
    public record PaginaRepositorio(
            boolean disponible, long count, String next, String previous, List<Elemento> results) {}

    private final HistorialService historial;
    private final AdjuntosService adjuntos;
    private final RepositorioService repositorio;

    public EvidenciasController(
            HistorialService historial, AdjuntosService adjuntos, RepositorioService repositorio) {
        this.historial = historial;
        this.adjuntos = adjuntos;
        this.repositorio = repositorio;
    }

    /** Entidades: actividad, atencion, incidencia, ejemplar y usuario. */
    @Operation(summary = "Historial de altas y modificaciones de un registro")
    @GetMapping({"/historial", "/historial/"})
    public List<Entrada> historial(
            @AuthenticationPrincipal UsuarioSesion yo,
            @RequestParam String entidad,
            @RequestParam(name = "registro_id") long registroId) {
        return historial.consultar(entidad, registroId, yo);
    }

    /**
     * Adjunta una fotografía o documento. Reintentar con el mismo {@code Idempotency-Key} devuelve
     * el adjunto ya creado (200) en lugar de duplicarlo.
     */
    @Operation(summary = "Adjuntar un archivo a una actividad, atención, incidencia o ejemplar")
    @ApiResponse(responseCode = "201", description = "Adjunto creado")
    @ApiResponse(responseCode = "200", description = "Reintento: el adjunto ya existía")
    @ApiResponse(responseCode = "409", description = "Archivo ya adjunto o clave reutilizada")
    @PostMapping(
            value = {"/adjuntos", "/adjuntos/"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Adjunto> adjuntar(
            @AuthenticationPrincipal UsuarioSesion yo,
            @Parameter(description = "UUID generado por el cliente para esta subida")
                    @RequestHeader("Idempotency-Key")
                    UUID clave,
            @RequestParam String entidad,
            @RequestParam(name = "registro_id") long registroId,
            @RequestParam(required = false) String descripcion,
            @RequestPart("archivo") MultipartFile archivo) {
        var subida = adjuntos.subir(entidad, registroId, clave, descripcion, archivo, yo);
        if (!subida.creado()) {
            return ResponseEntity.ok(subida.adjunto());
        }
        return ResponseEntity.created(
                        URI.create(
                                "/api/v1/core/adjuntos/" + subida.adjunto().id() + "/contenido/"))
                .body(subida.adjunto());
    }

    @Operation(summary = "Adjuntos de un registro")
    @GetMapping({"/adjuntos", "/adjuntos/"})
    public List<Adjunto> adjuntos(
            @AuthenticationPrincipal UsuarioSesion yo,
            @RequestParam String entidad,
            @RequestParam(name = "registro_id") long registroId) {
        return adjuntos.listar(entidad, registroId, yo);
    }

    @Operation(summary = "Descargar un adjunto")
    @GetMapping({"/adjuntos/{id}/contenido", "/adjuntos/{id}/contenido/"})
    public ResponseEntity<Resource> contenido(
            @AuthenticationPrincipal UsuarioSesion yo, @PathVariable long id) {
        var contenido = adjuntos.contenido(id, yo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contenido.tipoMime()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(contenido.nombre(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(contenido.recurso());
    }

    /** Tipos: jardin, especie, sector y servicio. Búsqueda por nombre con {@code q}. */
    @Operation(summary = "Consultar el repositorio común de información")
    @GetMapping({"/repositorio", "/repositorio/"})
    public PaginaRepositorio repositorio(
            @AuthenticationPrincipal UsuarioSesion yo,
            @RequestParam String tipo,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page) {
        var resultado = repositorio.buscar(tipo, q, page, yo);
        var pagina = Pagina.conTotal(resultado.elementos(), resultado.total(), page);
        return new PaginaRepositorio(
                resultado.disponible(),
                pagina.count(),
                pagina.next(),
                pagina.previous(),
                pagina.results());
    }
}
