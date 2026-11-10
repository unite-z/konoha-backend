package pe.edu.pucp.areasverdes.mod06.gestion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod06.gestion.AccesoGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ProgramadoEjecutado;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ProgramadoEjecutadoService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado;

/**
 * RF-REP-008 (reporte por servicio tercerizado, CU-MOD06-002) y RF-REP-010 (programado frente a
 * ejecutado). Solo lectura, con el permiso REP.INDICADOR.CONSULTAR. Los parámetros se validan antes
 * que el permiso, como en el resto de reportes de gestión.
 */
@RestController
@RequestMapping("/api/v1/reportes")
@SecurityRequirement(name = "sesionCore")
public class ServiciosYProgramacionController {

    private final ReporteServicioService servicios;
    private final ProgramadoEjecutadoService programado;

    public ServiciosYProgramacionController(
            ReporteServicioService servicios, ProgramadoEjecutadoService programado) {
        this.servicios = servicios;
        this.programado = programado;
    }

    @Tag(name = DocumentacionGestion.TAG_REP_008)
    @Operation(
            summary =
                    "Avance, rendimiento, especies o plagas atendidas y conformidad de un servicio"
                            + " tercerizado en un periodo")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Reporte; cada sección sin datos lleva su motivo"),
        @ApiResponse(responseCode = "400", description = DocumentacionGestion.R400),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(responseCode = "403", description = DocumentacionGestion.R403)
    })
    @GetMapping({"/servicios-tercerizados", "/servicios-tercerizados/"})
    public ReporteServicioTercerizado servicioTercerizado(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @Parameter(description = "Inicio del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @Parameter(description = "Fin del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta,
            @Parameter(description = "Tipo de servicio tercerizado")
                    @RequestParam(name = "tipo_servicio_id")
                    long tipoServicioId,
            @Parameter(description = "Proveedor")
                    @RequestParam(name = "proveedor_id", required = false)
                    Long proveedorId,
            @Parameter(description = "Contrato")
                    @RequestParam(name = "contrato_id", required = false)
                    Long contratoId) {
        var periodo = ParametrosGestion.periodo(desde, hasta);
        AccesoGestion.consultar(usuario);
        return servicios.generar(periodo, tipoServicioId, proveedorId, contratoId);
    }

    @Tag(name = DocumentacionGestion.TAG_REP_010)
    @Operation(summary = "Trabajos programados de un periodo frente a lo ejecutado")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Comparación; sin_datos=true con su motivo si no hay nada"),
        @ApiResponse(responseCode = "400", description = DocumentacionGestion.R400),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(responseCode = "403", description = DocumentacionGestion.R403)
    })
    @GetMapping({"/programado-ejecutado", "/programado-ejecutado/"})
    public ProgramadoEjecutado programadoEjecutado(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @Parameter(description = "Inicio del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @Parameter(description = "Fin del periodo (AAAA-MM-DD), incluido")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta,
            @Parameter(description = "Tipo de servicio")
                    @RequestParam(name = "tipo_servicio_id", required = false)
                    Long tipoServicioId,
            @Parameter(description = "Espacio (área verde de MOD-05)")
                    @RequestParam(name = "area_verde_id", required = false)
                    Long areaVerdeId,
            @Parameter(
                            description = "Modalidad de ejecución",
                            schema = @Schema(allowableValues = {"PersonalPropio", "Tercerizado"}))
                    @RequestParam(required = false)
                    String modalidad,
            @Parameter(
                            description = "Situación de la programación",
                            schema =
                                    @Schema(
                                            allowableValues = {
                                                "Ejecutado",
                                                "EjecutadoConDesviacion",
                                                "EnCurso",
                                                "Pendiente",
                                                "Cancelado"
                                            }))
                    @RequestParam(required = false)
                    String situacion) {
        var periodo = ParametrosGestion.periodo(desde, hasta);
        ParametrosGestion.unoDe(
                "modalidad",
                modalidad,
                List.of(
                        ProgramadoEjecutadoService.PERSONAL_PROPIO,
                        ProgramadoEjecutadoService.TERCERIZADO));
        ParametrosGestion.unoDe("situacion", situacion, ProgramadoEjecutadoService.SITUACIONES);
        AccesoGestion.consultar(usuario);
        return programado.comparar(periodo, tipoServicioId, areaVerdeId, modalidad, situacion);
    }
}
