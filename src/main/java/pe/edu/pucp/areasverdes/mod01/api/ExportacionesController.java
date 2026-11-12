package pe.edu.pucp.areasverdes.mod01.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod01.servicios.ExportacionesService;
import pe.edu.pucp.areasverdes.mod01.servicios.ExportacionesService.Archivo;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/** Exportación de listados a Excel (RF-T-011). */
@RestController
@RequestMapping("/api/v1/core/exportaciones")
@Tag(name = "MOD-01 Exportación a Excel")
@SecurityRequirement(name = "sesionCore")
public class ExportacionesController {

    private static final MediaType XLSX =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportacionesService exportaciones;

    public ExportacionesController(ExportacionesService exportaciones) {
        this.exportaciones = exportaciones;
    }

    @GetMapping({"/{tipo}", "/{tipo}/"})
    @Operation(
            summary =
                    "Descarga en Excel un listado: actividades, atenciones, incidencias o"
                            + " ejemplares")
    public ResponseEntity<byte[]> exportar(
            @PathVariable String tipo, @AuthenticationPrincipal UsuarioSesion usuario) {
        Archivo archivo = exportaciones.exportar(tipo, usuario);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(archivo.nombre(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(archivo.contenido());
    }
}
