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
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * RF-REP-006 · HU-MOD06-014 · CU-MOD06-014 · CP-TR-MOD06-014: atenciones por espacio y sector
 * ({@code AtencionesEspacioSectorView}). Solo lectura (GET).
 */
@RestController
@Tag(name = Documentacion.TAG_REP_006)
public class AtencionesEspacioSectorController {

    static final String RUTA = "/api/v1/reportes/atenciones-espacio-sector/";
    static final Vista VISTA = new Vista("Atenciones Espacio Sector", List.of("GET", "HEAD", "OPTIONS"),
        Permisos.ROLES_CONSULTA_REPORTES, null);

    private final DespachadorDrf drf;
    private final AtencionesEspacioSectorService servicio;

    public AtencionesEspacioSectorController(DespachadorDrf drf, AtencionesEspacioSectorService servicio) {
        this.drf = drf;
        this.servicio = servicio;
    }

    @GetMapping(RUTA)
    @Operation(summary = "Atenciones por espacio y sector",
        description = "Número de atenciones realizadas por sector (agrupador principal), espacio y cuartel (solo si "
            + "está registrado). Cada evento cuenta una sola vez. «Sin sector» va siempre al final y los eventos sin "
            + "espacio se informan aparte. Solo lectura.",
        parameters = {
            @Parameter(name = "desde", in = ParameterIn.QUERY, required = true, description = PARAM_DESDE,
                schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "hasta", in = ParameterIn.QUERY, required = true, description = PARAM_HASTA,
                schema = @Schema(type = "string", format = "date")),
            @Parameter(name = "sector_id", in = ParameterIn.QUERY,
                description = "Sector (SectorMantenimiento.id) o 'sin_sector' para el grupo sin sector.",
                schema = @Schema(type = "string", example = "sin_sector")),
            @Parameter(name = "modalidad", in = ParameterIn.QUERY, description = "Modalidad de ejecución (enumerado Ejecutor).",
                schema = @Schema(type = "string", allowableValues = {"PersonalPropio", "Tercerizado"})),
            @Parameter(name = "area_verde_id", in = ParameterIn.QUERY, description = "Espacio (AreaVerde.id).",
                schema = @Schema(type = "integer", minimum = "1")),
            @Parameter(name = "orden", in = ParameterIn.QUERY,
                description = "asc: menos atendidos primero (para priorizar); desc (por defecto): más atendidos primero.",
                schema = @Schema(type = "string", allowableValues = {"asc", "desc"}))},
        responses = {
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Salidas.ResultadoAtencionesEspacioSector.class))),
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
    @RequestMapping(path = "/api/v1/reportes/atenciones-espacio-sector", method = {RequestMethod.GET,
        RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> sinBarraFinal(HttpServletRequest peticion) {
        return DespachadorDrf.sinBarraFinal(peticion);
    }

    private ResponseEntity<byte[]> despachar(HttpServletRequest peticion) {
        return drf.despachar(peticion, VISTA, Map.of("GET", () -> {
            Map<String, Object> entrada = SerializadoresEntrada.ATENCIONES_ESPACIO_SECTOR_QUERY.validarRaiz(DespachadorDrf.parametrosConsulta(peticion));
            Map<String, Object> resultado = servicio.calcular(SerializadoresEntrada.periodo(entrada), SerializadoresEntrada.filtros(entrada));
            return Respuesta.ok(Serializadores.RESULTADO_ATENCIONES_ESPACIO_SECTOR.representar(resultado));
        }));
    }
}
