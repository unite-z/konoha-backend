package pe.edu.pucp.areasverdes.mod06.gestion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod05.Usuario;
import pe.edu.pucp.areasverdes.mod06.gestion.AccesoGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.FiltrosMapa;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.MapaAtenciones;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.MapaAtencionesService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.PanelService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.TiposAtencion;

/**
 * RF-REP-007 · HU-MOD06-003 · CU-MOD06-003: mapa de atenciones de mantenimiento, poda y control
 * fitosanitario del periodo, en GeoJSON (WGS84, {@code [longitud, latitud]}) y en lista. El
 * componente de mapa es del frontend. Solo lectura. El historial de un espacio (paso 7) es esta
 * misma consulta con {@code area_verde_id}.
 */
@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = DocumentacionGestion.TAG_REP_007)
@SecurityRequirement(name = "basicAuth")
public class MapaAtencionesController {

    private final MapaAtencionesService servicio;

    public MapaAtencionesController(MapaAtencionesService servicio) {
        this.servicio = servicio;
    }

    @Operation(
            summary =
                    "Mapa de atenciones de mantenimiento, poda y control fitosanitario del periodo"
                            + " (GeoJSON y lista)")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description =
                        "Mapa (FeatureCollection) y lista; las atenciones sin coordenadas van en"
                                + " sin_coordenadas"),
        @ApiResponse(responseCode = "400", description = DocumentacionGestion.R400),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(responseCode = "403", description = DocumentacionGestion.R403)
    })
    @GetMapping({"/mapa-atenciones", "/mapa-atenciones/"})
    public MapaAtenciones mapa(
            @AuthenticationPrincipal Usuario usuario,
            @Parameter(description = "Inicio del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @Parameter(description = "Fin del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta,
            @Parameter(
                            description = "Tipo de atención",
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                TiposAtencion.MANTENIMIENTO,
                                                TiposAtencion.PODA,
                                                TiposAtencion.FITOSANITARIO
                                            }))
                    @RequestParam(name = "tipo_atencion", required = false)
                    String tipoAtencion,
            @Parameter(
                            description =
                                    "Tipo de uso del espacio. Hoy responde sin_datos con su"
                                            + " motivo: el tipo de uso aún no está en el sistema")
                    @RequestParam(name = "tipo_uso_id", required = false)
                    Long tipoUsoId,
            @Parameter(description = "Espacio (área verde): también es el historial del espacio")
                    @RequestParam(name = "area_verde_id", required = false)
                    Long areaVerdeId,
            @Parameter(description = "Tipo de servicio (actividad)")
                    @RequestParam(name = "tipo_servicio_id", required = false)
                    Long tipoServicioId,
            @Parameter(
                            description = "Estado de la atención (solo realizadas)",
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "Ejecutado pendiente de reporte",
                                                "Reporte adjunto",
                                                "Observado",
                                                "Conforme / cerrado"
                                            }))
                    @RequestParam(required = false)
                    String estado,
            @Parameter(
                            description = "Modalidad de ejecución",
                            schema = @Schema(allowableValues = {"PersonalPropio", "Tercerizado"}))
                    @RequestParam(required = false)
                    String modalidad,
            @Parameter(description = "Nivel de riesgo: true o false")
                    @RequestParam(name = "es_riesgo", required = false)
                    Boolean esRiesgo) {
        AccesoGestion.consultar(usuario);
        RangoFechas periodo = ParametrosGestion.periodo(desde, hasta);
        ParametrosGestion.unoDe("tipo_atencion", tipoAtencion, TiposAtencion.TODOS);
        ParametrosGestion.unoDe("estado", estado, MapaAtencionesService.ESTADOS_REALIZADOS);
        ParametrosGestion.unoDe("modalidad", modalidad, PanelService.MODALIDADES);
        return servicio.mapa(
                periodo,
                new FiltrosMapa(
                        desde,
                        hasta,
                        tipoAtencion,
                        tipoUsoId,
                        areaVerdeId,
                        tipoServicioId,
                        estado,
                        modalidad,
                        esRiesgo));
    }
}
