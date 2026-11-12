package pe.edu.pucp.areasverdes.mod06.gestion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod06.gestion.AccesoGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.ExportacionReportesService;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.ExportacionReportesService.Descarga;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.ExportacionReportesService.Pagina;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.ExportacionReportesService.ReporteGenerado;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.TiposExportables;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.TiposExportables.TipoExportable;

/**
 * RF-REP-009: exportación de reportes a PDF o Excel. Consultar los tipos y el historial exige
 * REP.INDICADOR.CONSULTAR; generar y descargar, REP.INDICADOR.EXPORTAR (V9: jefe de sección).
 */
@RestController
@RequestMapping("/api/v1/reportes/exportaciones")
@Tag(name = DocumentacionGestion.TAG_REP_009)
@SecurityRequirement(name = "sesionCore")
public class ExportacionesReportesController {

    private final ExportacionReportesService servicio;

    public ExportacionesReportesController(ExportacionReportesService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Tipos de reporte exportables, con sus formatos, filtros y límites")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tipos exportables (sin paginar)"),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(responseCode = "403", description = DocumentacionGestion.R403)
    })
    @GetMapping({"/tipos", "/tipos/"})
    public List<TipoExportable> tipos(@AuthenticationPrincipal UsuarioSesion usuario) {
        AccesoGestion.consultar(usuario);
        return TiposExportables.TODOS;
    }

    @Operation(summary = "Historial de exportaciones, de la más reciente a la más antigua")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de 25 exportaciones"),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(responseCode = "403", description = DocumentacionGestion.R403),
        @ApiResponse(responseCode = "404", description = DocumentacionGestion.R404_PAGINA)
    })
    @GetMapping({"", "/"})
    public Pagina historial(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @Parameter(description = "Página (1, 2, …, last)") @RequestParam(required = false)
                    String page,
            @Parameter(description = "Tipo de reporte") @RequestParam(required = false) String tipo,
            HttpServletRequest peticion) {
        AccesoGestion.consultar(usuario);
        return servicio.historial(page, tipo, Paginas.desde(peticion));
    }

    @Operation(summary = "Genera el archivo de un reporte (no cambia ningún dato de origen)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "ReporteGenerado con su archivo"),
        @ApiResponse(
                responseCode = "400",
                description =
                        "{codigo: tipo-no-disponible | formato-no-permitido | parametro-invalido |"
                                + " periodo-invalido, detalle, errores}"),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(
                responseCode = "403",
                description =
                        "Usuario sin el permiso REP.INDICADOR.EXPORTAR: {codigo: sin-permiso}"),
        @ApiResponse(
                responseCode = "422",
                description = "{codigo: sin-datos | volumen-excedido, detalle}")
    })
    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public ReporteGenerado generar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody Map<String, Object> cuerpo) {
        AccesoGestion.exportar(usuario);
        @SuppressWarnings("unchecked")
        Map<String, Object> parametros =
                cuerpo.get("parametros") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m
                        : Map.of();
        return servicio.generar(
                cuerpo.get("tipo") instanceof String t ? t : null,
                cuerpo.get("formato") instanceof String f ? f : null,
                parametros,
                usuario.id());
    }

    @Operation(summary = "Descarga el archivo de una exportación")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "El PDF o el Excel"),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(
                responseCode = "403",
                description =
                        "Usuario sin el permiso REP.INDICADOR.EXPORTAR: {codigo: sin-permiso}"),
        @ApiResponse(
                responseCode = "404",
                description = "{codigo: exportacion-no-encontrada, detalle}")
    })
    @GetMapping({"/{id:[0-9]+}/archivo", "/{id:[0-9]+}/archivo/"})
    public ResponseEntity<Resource> archivo(
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable long id) {
        AccesoGestion.exportar(usuario);
        Descarga d = servicio.descargar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.tipoMime()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.nombre()).build().toString())
                .body(d.contenido());
    }
}
