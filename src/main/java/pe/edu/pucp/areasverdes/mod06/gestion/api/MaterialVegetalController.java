package pe.edu.pucp.areasverdes.mod06.gestion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod06.gestion.AccesoGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.MaterialVegetalService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteMaterialVegetal;

/**
 * RF-REP-005 · HU-MOD05-029 · CU-MOD05-029: reporte mensual de consumo, propagación y traslado de
 * material vegetal e insumos por tipo y espacio. Solo lectura.
 *
 * <p>CONFIRMADO: lo implementa Aarón (decisión del responsable, 28/10/2026), dentro de {@code
 * mod06.gestion} y sin tocar MOD-05.
 *
 * <p>Spring resuelve los parámetros antes de entrar en el método: un {@code mes} ausente o mal
 * tipado responde 400 antes que el 403 de un usuario sin rol, como en MOD-03.
 */
@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = DocumentacionGestion.TAG_REP_005)
@SecurityRequirement(name = "sesionCore")
public class MaterialVegetalController {

    private final MaterialVegetalService servicio;

    public MaterialVegetalController(MaterialVegetalService servicio) {
        this.servicio = servicio;
    }

    @Operation(
            summary =
                    "Consumo, propagación y traslado de material vegetal e insumos de un mes, por"
                            + " tipo y espacio")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Reporte; sin_datos=true con su motivo si no hay datos"),
        @ApiResponse(responseCode = "400", description = DocumentacionGestion.R400),
        @ApiResponse(responseCode = "401", description = DocumentacionGestion.R401),
        @ApiResponse(responseCode = "403", description = DocumentacionGestion.R403)
    })
    @GetMapping({"/material-vegetal", "/material-vegetal/"})
    public ReporteMaterialVegetal materialVegetal(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @Parameter(description = "Mes del reporte, AAAA-MM", example = "2026-10") @RequestParam
                    String mes,
            @Parameter(description = "Espacio (área verde)")
                    @RequestParam(name = "area_verde_id", required = false)
                    Long areaVerdeId) {
        AccesoGestion.consultar(usuario);
        return servicio.generar(ParametrosGestion.mes(mes), areaVerdeId);
    }
}
