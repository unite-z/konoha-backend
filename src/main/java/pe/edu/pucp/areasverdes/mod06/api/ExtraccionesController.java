package pe.edu.pucp.areasverdes.mod06.api;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod06.api.drf.DespachadorDrf;
import pe.edu.pucp.areasverdes.mod06.api.drf.DespachadorDrf.Respuesta;
import pe.edu.pucp.areasverdes.mod06.api.drf.DespachadorDrf.Vista;
import pe.edu.pucp.areasverdes.mod06.api.dto.Entradas;
import pe.edu.pucp.areasverdes.mod06.api.dto.Salidas;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.ia.ExtraccionReportesService;
import pe.edu.pucp.areasverdes.mod06.permisos.Permisos;

/**
 * RF-IA-001 · HU-MOD06-010 · CU-MOD06-010 · CP-TR-MOD06-010 · RN-IA-001: extracción de informes de proveedores con
 * IA ({@code ExtraccionesView}, {@code ExtraccionDetalleView}, {@code ExtraccionValidarView},
 * {@code ExtraccionRechazarView} y {@code ExtraccionMetricasView}).
 *
 * <p>Nada se incorpora al registro oficial sin validación humana (RN-IA-001).
 */
@RestController
@Tag(name = Documentacion.TAG_IA_001)
public class ExtraccionesController {

    static final String BASE = "/api/v1/ia/extracciones";
    static final String ID = "/{sugerenciaId:[0-9]+}";

    static final Vista VISTA_LISTA = new Vista("Extracciones", List.of("GET", "POST", "HEAD", "OPTIONS"),
        Permisos.ROLES_SOLICITAR_IA, Serializadores.SUGERENCIA_IA);
    static final Vista VISTA_DETALLE = new Vista("Extraccion Detalle", List.of("GET", "HEAD", "OPTIONS"),
        Permisos.ROLES_SOLICITAR_IA, null);
    static final Vista VISTA_VALIDAR = new Vista("Extraccion Validar", List.of("POST", "OPTIONS"), Permisos.ROLES_VALIDAR_IA, null);
    static final Vista VISTA_RECHAZAR = new Vista("Extraccion Rechazar", List.of("POST", "OPTIONS"), Permisos.ROLES_VALIDAR_IA, null);
    static final Vista VISTA_METRICAS = new Vista("Extraccion Metricas", List.of("GET", "HEAD", "OPTIONS"),
        Permisos.ROLES_VALIDAR_IA, null);

    private static final String EJEMPLO_SUGERENCIA = """
        {"id": 1, "tipo": "ExtraccionReporte", "entidad": "ReporteProveedor", "registro_id": 1, "archivo_id": 1,
         "contenido": {"version_contenido": 1,
           "entrada": {"reporte_proveedor_id": 1, "atencion_id": 4, "tipo_servicio_id": 2, "formato_id": 1, "formato_version": 1,
                       "archivo_id": 1, "campos_solicitados": [{"campo_id": 1, "nombre_campo": "Metrado", "tipo_dato": "Decimal", "obligatorio": true}]},
           "ia": {"proveedor": "Gemini (Google AI Studio)", "modelo": "<GEMINI_MODEL>", "solicitado_en": "2026-10-20T09:00:00-05:00", "duracion_ms": 4200},
           "propuesta": {"campo_1": {"nombre_campo": "Metrado", "valor": 1250.5}},
           "campos_obligatorios_faltantes": [], "decision": null},
         "estado": "Propuesta", "etiqueta_estado": "Pendiente de revisión", "validador_id": null, "fecha_validacion": null,
         "fecha_creacion": "2026-10-20T09:00:04-05:00", "usuario_creacion_id": 1,
         "fecha_modificacion": "2026-10-20T09:00:04-05:00", "usuario_modificacion_id": 1}""";

    private final DespachadorDrf drf;
    private final ExtraccionReportesService servicio;

    public ExtraccionesController(DespachadorDrf drf, ExtraccionReportesService servicio) {
        this.drf = drf;
        this.servicio = servicio;
    }

    private Object idUsuario(HttpServletRequest peticion) {
        return Permisos.idDeUsuario(drf.usuario(peticion));
    }

    // =========================================================================
    // Lista y solicitud
    // =========================================================================

    @GetMapping(BASE + "/")
    @Operation(summary = "Listar propuestas de extracción con IA",
        description = "Sugerencias de tipo ExtraccionReporte, de la más reciente a la más antigua, 25 por página.",
        parameters = {
            @Parameter(name = "estado", in = ParameterIn.QUERY, description = "Estado de la sugerencia (EstadoSugerencia).",
                schema = @Schema(type = "string", allowableValues = {"Propuesta", "Validada", "Rechazada"})),
            @Parameter(name = "reporte_proveedor_id", in = ParameterIn.QUERY, description = "Reporte del proveedor (ReporteProveedor.id).",
                schema = @Schema(type = "integer", minimum = "1")),
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Número de página (25 elementos por página).",
                schema = @Schema(type = "integer"))},
        responses = {
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Salidas.PaginaSugerencias.class))),
            @ApiResponse(responseCode = "400", description = Documentacion.R400, content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = Documentacion.R403, content = @Content(schema = @Schema(implementation = Salidas.Detalle.class))),
            @ApiResponse(responseCode = "503", description = Documentacion.R503_DATOS, content = @Content(schema = @Schema(implementation = Salidas.Error.class)))})
    public ResponseEntity<byte[]> listar(@Parameter(hidden = true) HttpServletRequest peticion) {
        return despacharLista(peticion);
    }

    @PostMapping(BASE + "/")
    @Operation(summary = "Solicitar el análisis con IA del informe de un proveedor",
        description = "Analiza el informe PDF adjunto al ReporteProveedor y crea una propuesta en estado Propuesta "
            + "(«Pendiente de revisión»). Nada se incorpora al registro oficial sin validación humana (RN-IA-001). Si la "
            + "IA no está disponible, responde 503 y no crea ninguna propuesta: continúa con el registro manual.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
            schema = @Schema(implementation = Entradas.SolicitudExtraccion.class),
            examples = @ExampleObject(name = "Solicitud", value = "{\"reporte_proveedor_id\": 1}"))),
        responses = {
            @ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = Salidas.SugerenciaIA.class),
                examples = @ExampleObject(name = "Propuesta creada", value = EJEMPLO_SUGERENCIA))),
            @ApiResponse(responseCode = "400", description = Documentacion.R400, content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = Documentacion.R403, content = @Content(schema = @Schema(implementation = Salidas.Detalle.class))),
            @ApiResponse(responseCode = "404", description = Documentacion.R404, content = @Content(schema = @Schema(implementation = Salidas.Error.class))),
            @ApiResponse(responseCode = "415", description = Documentacion.R415, content = @Content(schema = @Schema(implementation = Salidas.Error.class))),
            @ApiResponse(responseCode = "422", description = Documentacion.R422, content = @Content(schema = @Schema(implementation = Salidas.Error.class))),
            @ApiResponse(responseCode = "503", description = Documentacion.R503_IA, content = @Content(schema = @Schema(implementation = Salidas.Error.class)))})
    public ResponseEntity<byte[]> solicitar(@Parameter(hidden = true) HttpServletRequest peticion) {
        return despacharLista(peticion);
    }

    @Hidden
    @RequestMapping(path = BASE + "/", method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE,
        RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> listaOtrosMetodos(HttpServletRequest peticion) {
        return despacharLista(peticion);
    }

    private ResponseEntity<byte[]> despacharLista(HttpServletRequest peticion) {
        Map<String, Supplier<Respuesta>> manejadores = Map.of(
            "GET", () -> {
                Map<String, Object> entrada = SerializadoresEntrada.EXTRACCIONES_QUERY.validarRaiz(DespachadorDrf.parametrosConsulta(peticion));
                List<Map<String, Object>> sugerencias = servicio.listar(entrada.get("estado"), entrada.get("reporte_proveedor_id"));
                return Respuesta.ok(Paginacion25.paginar(sugerencias, peticion, Serializadores.SUGERENCIA_IA));
            },
            "POST", () -> {
                Map<String, Object> entrada = SerializadoresEntrada.SOLICITUD_EXTRACCION.validarRaiz(DespachadorDrf.datos(peticion));
                Entradas.SolicitudExtraccion solicitud = new Entradas.SolicitudExtraccion(entrada.get("reporte_proveedor_id"));
                Map<String, Object> sugerencia = servicio.solicitarExtraccion(solicitud.reporte_proveedor_id(), idUsuario(peticion));
                return new Respuesta(201, Serializadores.SUGERENCIA_IA.representar(sugerencia));
            });
        return drf.despachar(peticion, VISTA_LISTA, manejadores);
    }

    // =========================================================================
    // Métricas
    // =========================================================================

    @GetMapping(BASE + "/metricas/")
    @Operation(summary = "Métricas de la función de extracción con IA",
        responses = {
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Salidas.MetricasIA.class),
                examples = @ExampleObject(name = "Métricas", value = "{\"total_sugerencias\": 4, \"por_estado\": {\"Propuesta\": 1, "
                    + "\"Validada\": 2, \"Rechazada\": 1}, \"campos_aceptados\": 6, \"campos_corregidos\": 2, \"campos_descartados\": 7, "
                    + "\"tasa_aceptacion_sin_cambios\": 0.4, \"duracion_media_ms\": 3850.5}"))),
            @ApiResponse(responseCode = "403", description = Documentacion.R403, content = @Content(schema = @Schema(implementation = Salidas.Detalle.class))),
            @ApiResponse(responseCode = "503", description = Documentacion.R503_DATOS, content = @Content(schema = @Schema(implementation = Salidas.Error.class)))})
    public ResponseEntity<byte[]> metricas(@Parameter(hidden = true) HttpServletRequest peticion) {
        return despacharMetricas(peticion);
    }

    @Hidden
    @RequestMapping(path = BASE + "/metricas/", method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
        RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> metricasOtrosMetodos(HttpServletRequest peticion) {
        return despacharMetricas(peticion);
    }

    private ResponseEntity<byte[]> despacharMetricas(HttpServletRequest peticion) {
        return drf.despachar(peticion, VISTA_METRICAS,
            Map.of("GET", () -> Respuesta.ok(Serializadores.METRICAS_IA.representar(servicio.metricas()))));
    }

    // =========================================================================
    // Detalle
    // =========================================================================

    @GetMapping(BASE + ID + "/")
    @Operation(summary = "Detalle de una propuesta de extracción con IA",
        responses = {
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Salidas.SugerenciaIA.class),
                examples = @ExampleObject(name = "Propuesta", value = EJEMPLO_SUGERENCIA))),
            @ApiResponse(responseCode = "403", description = Documentacion.R403, content = @Content(schema = @Schema(implementation = Salidas.Detalle.class))),
            @ApiResponse(responseCode = "404", description = Documentacion.R404, content = @Content(schema = @Schema(implementation = Salidas.Error.class))),
            @ApiResponse(responseCode = "503", description = Documentacion.R503_DATOS, content = @Content(schema = @Schema(implementation = Salidas.Error.class)))})
    public ResponseEntity<byte[]> detalle(@PathVariable("sugerenciaId") @Parameter(name = "sugerencia_id", description = "SugerenciaIA.id",
                                              schema = @Schema(type = "integer")) String sugerenciaId,
                                          @Parameter(hidden = true) HttpServletRequest peticion) {
        return despacharDetalle(sugerenciaId, peticion);
    }

    @Hidden
    @RequestMapping(path = BASE + ID + "/", method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
        RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> detalleOtrosMetodos(@PathVariable("sugerenciaId") String sugerenciaId, HttpServletRequest peticion) {
        return despacharDetalle(sugerenciaId, peticion);
    }

    private ResponseEntity<byte[]> despacharDetalle(String sugerenciaId, HttpServletRequest peticion) {
        return drf.despachar(peticion, VISTA_DETALLE, Map.of("GET",
            () -> Respuesta.ok(Serializadores.SUGERENCIA_IA.representar(servicio.obtener(Py.parseInt(sugerenciaId))))));
    }

    // =========================================================================
    // Validar
    // =========================================================================

    @PostMapping(BASE + ID + "/validar/")
    @Operation(summary = "Validar campo por campo una propuesta de IA",
        description = "Una decisión por campo: aceptar, corregir (con valor_final) o descartar. Si todo se descarta, "
            + "la propuesta queda Rechazada; si no, Validada. Solo desde Propuesta (RI-32).",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
            schema = @Schema(implementation = Entradas.Validacion.class),
            examples = @ExampleObject(name = "Decisiones", value = "{\"decisiones\": {\"campo_1\": {\"accion\": \"aceptar\"}, "
                + "\"campo_2\": {\"accion\": \"corregir\", \"valor_final\": [{\"producto\": \"…\", \"dosis\": \"…\"}]}, "
                + "\"campo_3\": {\"accion\": \"descartar\"}}}"))),
        responses = {
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Salidas.SugerenciaIA.class))),
            @ApiResponse(responseCode = "400", description = Documentacion.R400, content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = Documentacion.R403, content = @Content(schema = @Schema(implementation = Salidas.Detalle.class))),
            @ApiResponse(responseCode = "404", description = Documentacion.R404, content = @Content(schema = @Schema(implementation = Salidas.Error.class))),
            @ApiResponse(responseCode = "409", description = Documentacion.R409, content = @Content(schema = @Schema(implementation = Salidas.Error.class))),
            @ApiResponse(responseCode = "503", description = Documentacion.R503_DATOS, content = @Content(schema = @Schema(implementation = Salidas.Error.class)))})
    public ResponseEntity<byte[]> validar(@PathVariable("sugerenciaId") @Parameter(name = "sugerencia_id", description = "SugerenciaIA.id",
                                              schema = @Schema(type = "integer")) String sugerenciaId,
                                          @Parameter(hidden = true) HttpServletRequest peticion) {
        return despacharValidar(sugerenciaId, peticion);
    }

    @Hidden
    @RequestMapping(path = BASE + ID + "/validar/", method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.PUT,
        RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> validarOtrosMetodos(@PathVariable("sugerenciaId") String sugerenciaId, HttpServletRequest peticion) {
        return despacharValidar(sugerenciaId, peticion);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<byte[]> despacharValidar(String sugerenciaId, HttpServletRequest peticion) {
        return drf.despachar(peticion, VISTA_VALIDAR, Map.of("POST", () -> {
            Map<String, Object> entrada = SerializadoresEntrada.VALIDACION.validarRaiz(DespachadorDrf.datos(peticion));
            Map<String, Object> decisiones = (Map<String, Object>) entrada.get("decisiones");
            Map<String, Object> sugerencia = servicio.validar(Py.parseInt(sugerenciaId), idUsuario(peticion), decisiones);
            return Respuesta.ok(Serializadores.SUGERENCIA_IA.representar(sugerencia));
        }));
    }

    // =========================================================================
    // Rechazar
    // =========================================================================

    @PostMapping(BASE + ID + "/rechazar/")
    @Operation(summary = "Rechazar una propuesta de IA completa",
        description = "Solo desde Propuesta. Todos los campos quedan descartados y se guarda el motivo.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            schema = @Schema(implementation = Entradas.Rechazo.class),
            examples = @ExampleObject(name = "Motivo", value = "{\"motivo\": \"El informe no corresponde a la atención\"}"))),
        responses = {
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Salidas.SugerenciaIA.class))),
            @ApiResponse(responseCode = "400", description = Documentacion.R400, content = @Content(schema = @Schema(type = "object"))),
            @ApiResponse(responseCode = "403", description = Documentacion.R403, content = @Content(schema = @Schema(implementation = Salidas.Detalle.class))),
            @ApiResponse(responseCode = "404", description = Documentacion.R404, content = @Content(schema = @Schema(implementation = Salidas.Error.class))),
            @ApiResponse(responseCode = "409", description = Documentacion.R409, content = @Content(schema = @Schema(implementation = Salidas.Error.class))),
            @ApiResponse(responseCode = "503", description = Documentacion.R503_DATOS, content = @Content(schema = @Schema(implementation = Salidas.Error.class)))})
    public ResponseEntity<byte[]> rechazar(@PathVariable("sugerenciaId") @Parameter(name = "sugerencia_id", description = "SugerenciaIA.id",
                                               schema = @Schema(type = "integer")) String sugerenciaId,
                                           @Parameter(hidden = true) HttpServletRequest peticion) {
        return despacharRechazar(sugerenciaId, peticion);
    }

    @Hidden
    @RequestMapping(path = BASE + ID + "/rechazar/", method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.PUT,
        RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> rechazarOtrosMetodos(@PathVariable("sugerenciaId") String sugerenciaId, HttpServletRequest peticion) {
        return despacharRechazar(sugerenciaId, peticion);
    }

    private ResponseEntity<byte[]> despacharRechazar(String sugerenciaId, HttpServletRequest peticion) {
        return drf.despachar(peticion, VISTA_RECHAZAR, Map.of("POST", () -> {
            Map<String, Object> entrada = SerializadoresEntrada.RECHAZO.validarRaiz(DespachadorDrf.datos(peticion));
            Entradas.Rechazo rechazo = new Entradas.Rechazo((String) entrada.get("motivo"));
            Map<String, Object> sugerencia = servicio.rechazar(Py.parseInt(sugerenciaId), idUsuario(peticion), rechazo.motivo());
            return Respuesta.ok(Serializadores.SUGERENCIA_IA.representar(sugerencia));
        }));
    }

    // =========================================================================
    // Rutas sin barra final (APPEND_SLASH)
    // =========================================================================

    @Hidden
    @RequestMapping(path = {BASE, BASE + "/metricas", BASE + ID, BASE + ID + "/validar", BASE + ID + "/rechazar"},
        method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH,
            RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> sinBarraFinal(HttpServletRequest peticion) {
        return DespachadorDrf.sinBarraFinal(peticion);
    }
}
