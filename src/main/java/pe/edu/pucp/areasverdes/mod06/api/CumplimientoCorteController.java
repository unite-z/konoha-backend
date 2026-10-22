package pe.edu.pucp.areasverdes.mod06.api;

import static pe.edu.pucp.areasverdes.mod06.api.Documentacion.PARAM_DESDE;
import static pe.edu.pucp.areasverdes.mod06.api.Documentacion.PARAM_HASTA;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod06.api.drf.DespachadorDrf;
import pe.edu.pucp.areasverdes.mod06.api.drf.DespachadorDrf.Respuesta;
import pe.edu.pucp.areasverdes.mod06.api.drf.DespachadorDrf.Vista;
import pe.edu.pucp.areasverdes.mod06.api.dto.Salidas;
import pe.edu.pucp.areasverdes.mod06.permisos.Permisos;
import pe.edu.pucp.areasverdes.mod06.servicios.CumplimientoCorteService;

/**
 * RF-REP-002 · HU-MOD06-009 · CU-MOD06-009 · CP-TR-MOD06-009: cumplimiento del corte de césped
 * ({@code CumplimientoCorteView}).
 *
 * <p>La vista solo: (1) valida la entrada, (2) llama al servicio y (3) serializa la salida. No calcula ni lee datos
 * (ADR-001, figura 1). Solo lectura (GET).
 */
@RestController
@Tag(name = Documentacion.TAG_REP_002)
public class CumplimientoCorteController {

    static final String RUTA = "/api/v1/reportes/cumplimiento-corte/";
    static final Vista VISTA = new Vista("Cumplimiento Corte", List.of("GET", "HEAD", "OPTIONS"),
        Permisos.ROLES_CONSULTA_REPORTES, null);

    private final DespachadorDrf drf;
    private final CumplimientoCorteService servicio;

    public CumplimientoCorteController(DespachadorDrf drf, CumplimientoCorteService servicio) {
        this.drf = drf;
        this.servicio = servicio;
    }

    @GetMapping(RUTA)
    @Operation(summary = "Cumplimiento del corte de césped",
        description = "Días entre ciclos frente a la frecuencia y días de ejecución frente a la duración máxima "
            + "(HU-MOD06-009). Si faltan fechas o límites, se informa en `faltantes` y no se calcula el cumplimiento. "
            + "Solo lectura.",
        parameters = {
            @Parameter(name = "desde", in = ParameterIn.QUERY, required = true, description = PARAM_DESDE,
                schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "hasta", in = ParameterIn.QUERY, required = true, description = PARAM_HASTA,
                schema = @Schema(type = "string", format = "date"))},
        responses = {
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Salidas.ResultadoCumplimientoCorte.class))),
            @ApiResponse(responseCode = "400", description = Documentacion.R400, content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = Documentacion.R403, content = @Content(schema = @Schema(implementation = Salidas.Detalle.class))),
            @ApiResponse(responseCode = "503", description = Documentacion.R503_DATOS, content = @Content(schema = @Schema(implementation = Salidas.Error.class)))})
    public ResponseEntity<byte[]> consultar(@Parameter(hidden = true) HttpServletRequest peticion) {
        return despachar(peticion);
    }

    @Hidden
    @RequestMapping(path = RUTA, method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE,
        RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> otrosMetodos(HttpServletRequest peticion) {
        return despachar(peticion);
    }

    @Hidden
    @RequestMapping(path = "/api/v1/reportes/cumplimiento-corte", method = {RequestMethod.GET,
        RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> sinBarraFinal(HttpServletRequest peticion) {
        return DespachadorDrf.sinBarraFinal(peticion);
    }

    private ResponseEntity<byte[]> despachar(HttpServletRequest peticion) {
        return drf.despachar(peticion, VISTA, Map.of("GET", () -> {
            Map<String, Object> entrada = SerializadoresEntrada.CUMPLIMIENTO_CORTE_QUERY.validarRaiz(DespachadorDrf.parametrosConsulta(peticion));
            Map<String, Object> resultado = servicio.calcular(SerializadoresEntrada.periodo(entrada), SerializadoresEntrada.filtros(entrada));
            return Respuesta.ok(Serializadores.RESULTADO_CUMPLIMIENTO_CORTE.representar(resultado));
        }));
    }
}
