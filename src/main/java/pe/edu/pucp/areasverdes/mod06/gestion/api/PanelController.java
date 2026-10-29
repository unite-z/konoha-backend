package pe.edu.pucp.areasverdes.mod06.gestion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod05.Atencion;
import pe.edu.pucp.areasverdes.mod05.Usuario;
import pe.edu.pucp.areasverdes.mod06.gestion.AccesoGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ConsultaPanel;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.DetalleIndicador;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.FiltrosPanel;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.PanelIndicadores;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.PanelService;

/**
 * RF-REP-001 · HU-MOD06-001 · CU-MOD06-001: panel de indicadores de gestión y detalle de cada
 * indicador con los mismos filtros. Solo lectura.
 *
 * <p>El periodo es obligatorio, como en todos los reportes del MOD-06; el periodo predeterminado
 * (paso 1 del CU) lo pone el frontend. Los filtros por cuadrilla y sector no se declaran: sus datos
 * aún no están en el sistema (se explica en {@code notas}).
 */
@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = DocumentacionGestion.TAG_REP_001)
@SecurityRequirement(name = "basicAuth")
public class PanelController {

    private static final String DESCRIPCION_ESTADO = "Estado de la atención tercerizada";
    private static final String DESCRIPCION_MODALIDAD = "Modalidad de ejecución";

    private final PanelService servicio;

    public PanelController(PanelService servicio) {
        this.servicio = servicio;
    }

    @Operation(
            summary =
                    "Panel de indicadores de gestión: los cinco indicadores mínimos del periodo con"
                            + " los filtros elegidos")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Panel; cada indicador sin datos lleva su motivo"),
        @ApiResponse(responseCode = "400", description = DocumentacionGestion.R400),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(responseCode = "403", description = DocumentacionGestion.R403)
    })
    @GetMapping({"/panel", "/panel/"})
    public PanelIndicadores panel(
            @AuthenticationPrincipal Usuario usuario,
            @Parameter(description = "Inicio del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @Parameter(description = "Fin del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta,
            @Parameter(description = "Espacio (área verde)")
                    @RequestParam(name = "area_verde_id", required = false)
                    Long areaVerdeId,
            @Parameter(description = "Tipo de servicio de las atenciones tercerizadas")
                    @RequestParam(name = "tipo_servicio_id", required = false)
                    Long tipoServicioId,
            @Parameter(
                            description = DESCRIPCION_ESTADO,
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                Atencion.REQUERIMIENTO,
                                                Atencion.OC,
                                                Atencion.EJECUCION,
                                                Atencion.EJECUTADO,
                                                Atencion.REPORTE,
                                                "Observado",
                                                "Conforme / cerrado"
                                            }))
                    @RequestParam(required = false)
                    String estado,
            @Parameter(
                            description = DESCRIPCION_MODALIDAD,
                            schema = @Schema(allowableValues = {"PersonalPropio", "Tercerizado"}))
                    @RequestParam(required = false)
                    String modalidad) {
        AccesoGestion.consultar(usuario);
        return servicio.panel(
                consulta(desde, hasta, areaVerdeId, tipoServicioId, estado, modalidad));
    }

    @Operation(
            summary =
                    "Detalle paginado de los registros de un indicador, con los mismos filtros del"
                            + " panel")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Página de 25 registros: {count, next, previous, results}"),
        @ApiResponse(responseCode = "400", description = DocumentacionGestion.R400),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(responseCode = "403", description = DocumentacionGestion.R403),
        @ApiResponse(
                responseCode = "404",
                description =
                        DocumentacionGestion.R404_INDICADOR
                                + " "
                                + DocumentacionGestion.R404_PAGINA)
    })
    @GetMapping({"/panel/{indicador}", "/panel/{indicador}/"})
    public DetalleIndicador detalle(
            @AuthenticationPrincipal Usuario usuario,
            @Parameter(
                            description = "Clave del indicador",
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "actividades-por-estado",
                                                "distribucion-operarios",
                                                "atenciones-por-espacio",
                                                "consumo-materiales",
                                                "cumplimiento-tercerizados"
                                            }))
                    @PathVariable
                    String indicador,
            @Parameter(description = "Inicio del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @Parameter(description = "Fin del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta,
            @Parameter(description = "Espacio (área verde)")
                    @RequestParam(name = "area_verde_id", required = false)
                    Long areaVerdeId,
            @Parameter(description = "Tipo de servicio de las atenciones tercerizadas")
                    @RequestParam(name = "tipo_servicio_id", required = false)
                    Long tipoServicioId,
            @Parameter(
                            description = DESCRIPCION_ESTADO,
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                Atencion.REQUERIMIENTO,
                                                Atencion.OC,
                                                Atencion.EJECUCION,
                                                Atencion.EJECUTADO,
                                                Atencion.REPORTE,
                                                "Observado",
                                                "Conforme / cerrado"
                                            }))
                    @RequestParam(required = false)
                    String estado,
            @Parameter(
                            description = DESCRIPCION_MODALIDAD,
                            schema = @Schema(allowableValues = {"PersonalPropio", "Tercerizado"}))
                    @RequestParam(required = false)
                    String modalidad,
            @Parameter(description = "Página (desde 1) o last") @RequestParam(required = false)
                    String page,
            @Parameter(hidden = true) HttpServletRequest peticion) {
        AccesoGestion.consultar(usuario);
        return servicio.detalle(
                indicador,
                consulta(desde, hasta, areaVerdeId, tipoServicioId, estado, modalidad),
                page,
                Paginas.desde(peticion));
    }

    private static ConsultaPanel consulta(
            LocalDate desde,
            LocalDate hasta,
            Long areaVerdeId,
            Long tipoServicioId,
            String estado,
            String modalidad) {
        RangoFechas periodo = ParametrosGestion.periodo(desde, hasta);
        ParametrosGestion.unoDe("estado", estado, PanelService.ESTADOS);
        ParametrosGestion.unoDe("modalidad", modalidad, PanelService.MODALIDADES);
        return new ConsultaPanel(
                periodo,
                new FiltrosPanel(desde, hasta, areaVerdeId, tipoServicioId, estado, modalidad));
    }
}
