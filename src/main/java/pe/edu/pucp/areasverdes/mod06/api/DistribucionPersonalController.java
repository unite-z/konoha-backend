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
import pe.edu.pucp.areasverdes.mod06.servicios.DistribucionPersonalService;

/**
 * RF-REP-004 · HU-MOD06-002 · CU-MOD06-002: distribución del personal ({@code DistribucionPersonalView}).
 * Solo lectura (GET).
 */
@RestController
@Tag(name = Documentacion.TAG_REP_004)
public class DistribucionPersonalController {

    static final String RUTA = "/api/v1/reportes/distribucion-personal/";
    static final Vista VISTA = new Vista("Distribucion Personal", List.of("GET", "HEAD", "OPTIONS"),
        Permisos.ROLES_CONSULTA_REPORTES, null);

    private final DespachadorDrf drf;
    private final DistribucionPersonalService servicio;

    public DistribucionPersonalController(DespachadorDrf drf, DistribucionPersonalService servicio) {
        this.drf = drf;
        this.servicio = servicio;
    }

    @GetMapping(RUTA)
    @Operation(summary = "Distribución del personal por cuadrilla, actividad, espacio y jornada",
        description = "Filas jornada × cuadrilla × tipo de actividad × espacio, totales por cuadrilla y jornada, y "
            + "cuadrillas «Sin parte aún». Marca el apoyo eventual (CU-MOD04-011 1a). No devuelve datos personales "
            + "(RNF-008). Solo lectura; la exportación (RF-REP-009) queda fuera de este corte.",
        parameters = {
            @Parameter(name = "desde", in = ParameterIn.QUERY, required = true, description = PARAM_DESDE,
                schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "hasta", in = ParameterIn.QUERY, required = true, description = PARAM_HASTA,
                schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "cuadrilla_id", in = ParameterIn.QUERY, description = "Cuadrilla (Cuadrilla.id).",
                schema = @Schema(type = "integer", minimum = "1")),
            @Parameter(name = "clase_actividad_id", in = ParameterIn.QUERY, description = "Clase de actividad (ClaseActividad.id).",
                schema = @Schema(type = "integer", minimum = "1")),
            @Parameter(name = "tipo_actividad_id", in = ParameterIn.QUERY, description = "Tipo de actividad (TipoActividad.id).",
                schema = @Schema(type = "integer", minimum = "1")),
            @Parameter(name = "area_verde_id", in = ParameterIn.QUERY, description = "Espacio (AreaVerde.id).",
                schema = @Schema(type = "integer", minimum = "1"))},
        responses = {
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Salidas.ResultadoDistribucionPersonal.class))),
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
    @RequestMapping(path = "/api/v1/reportes/distribucion-personal", method = {RequestMethod.GET,
        RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> sinBarraFinal(HttpServletRequest peticion) {
        return DespachadorDrf.sinBarraFinal(peticion);
    }

    private ResponseEntity<byte[]> despachar(HttpServletRequest peticion) {
        return drf.despachar(peticion, VISTA, Map.of("GET", () -> {
            Map<String, Object> entrada = SerializadoresEntrada.DISTRIBUCION_PERSONAL_QUERY.validarRaiz(DespachadorDrf.parametrosConsulta(peticion));
            Map<String, Object> resultado = servicio.calcular(SerializadoresEntrada.periodo(entrada), SerializadoresEntrada.filtros(entrada));
            return Respuesta.ok(Serializadores.RESULTADO_DISTRIBUCION_PERSONAL.representar(resultado));
        }));
    }
}
