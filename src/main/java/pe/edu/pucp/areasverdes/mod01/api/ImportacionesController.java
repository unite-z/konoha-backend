package pe.edu.pucp.areasverdes.mod01.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.areasverdes.mod01.importacion.ImportacionesService;
import pe.edu.pucp.areasverdes.mod01.importacion.ImportacionesService.Fila;
import pe.edu.pucp.areasverdes.mod01.importacion.ImportacionesService.Importacion;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/** Importación repetible desde Excel (RF-T-009): validar, revisar el informe y confirmar. */
@RestController
@RequestMapping("/api/v1/core/importaciones")
@Tag(name = "MOD-01 Importación desde Excel")
@SecurityRequirement(name = "sesionCore")
public class ImportacionesController {

    private final ImportacionesService importaciones;

    public ImportacionesController(ImportacionesService importaciones) {
        this.importaciones = importaciones;
    }

    @PostMapping({"", "/"})
    @Operation(summary = "Valida un archivo .xlsx sin incorporar datos y devuelve su resumen")
    public ResponseEntity<Importacion> validar(
            @RequestParam String tipo,
            @RequestPart MultipartFile archivo,
            @AuthenticationPrincipal UsuarioSesion usuario) {
        try {
            Importacion importacion =
                    importaciones.validar(
                            tipo, archivo.getOriginalFilename(), archivo.getBytes(), usuario);
            return ResponseEntity.created(
                            URI.create("/api/v1/core/importaciones/" + importacion.id() + "/"))
                    .body(importacion);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Resumen de una importación: lo validado o lo incorporado")
    public Importacion obtener(
            @PathVariable long id, @AuthenticationPrincipal UsuarioSesion usuario) {
        return importaciones.obtener(id, usuario);
    }

    @GetMapping({"/{id}/filas", "/{id}/filas/"})
    @Operation(summary = "Resultado de cada fila, paginado")
    public Pagina<Fila> filas(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @AuthenticationPrincipal UsuarioSesion usuario) {
        return Pagina.de(importaciones.filas(id, usuario), page);
    }

    @PostMapping({"/{id}/confirmar", "/{id}/confirmar/"})
    @Operation(summary = "Incorpora las filas válidas. Las de error o por revisar no entran")
    public Importacion confirmar(
            @PathVariable long id, @AuthenticationPrincipal UsuarioSesion usuario) {
        return importaciones.confirmar(id, usuario);
    }
}
