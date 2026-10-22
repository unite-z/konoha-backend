package pe.edu.pucp.areasverdes.mod06.ia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.d;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.l;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.DocumentContent;
import com.google.genai.gaos.models.interactions.DocumentContentMimeType;
import com.google.genai.gaos.models.interactions.TextContent;
import com.google.genai.gaos.models.interactions.TextResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormatMimeType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import pe.edu.pucp.areasverdes.mod06.apoyo.ClienteIAFalso;
import pe.edu.pucp.areasverdes.mod06.apoyo.Datos;
import pe.edu.pucp.areasverdes.mod06.apoyo.DatosIa;
import pe.edu.pucp.areasverdes.mod06.apoyo.FuenteDatosMemoria;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ConfiguracionFaltante;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ParametroInvalido;
import pe.edu.pucp.areasverdes.mod06.comun.errores.RecursoNoEncontrado;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;
import pe.edu.pucp.areasverdes.mod06.comun.errores.TipoArchivoNoAdmitido;
import pe.edu.pucp.areasverdes.mod06.comun.errores.TransicionNoPermitida;

/**
 * Pruebas de RF-IA-001 (extracción de informes con IA): servicio y adaptador de Gemini. Caso de prueba:
 * CP-TR-MOD06-010, con las variantes obligatorias de IA (CP-TR-MOD06-005 a 008): datos insuficientes, fallo de IA y
 * validación ({@code tests/test_extraccion_reportes.py}, parte de servicio).
 *
 * <p>Ninguna prueba llama a Gemini: se usa {@code ClienteIAFalso} o un doble del SDK.
 */
@SuppressWarnings("unchecked")
class ExtraccionReportesServiceTest {

    static final byte[] PDF = DatosIa.PDF;

    static Map<String, Object> propuestaCompleta() {
        return DatosIa.propuestaCompleta();
    }

    private final ExtraccionReportesService ext = new ExtraccionReportesService(null, null, Clock.system(Fechas.LIMA));
    private FuenteDatosMemoria fuenteIa;

    @BeforeEach
    void fuenteIa() {
        fuenteIa = DatosIa.fuente();
    }

    private Map<String, Object> solicitar(Map<String, Object> propuesta) {
        return ext.solicitarExtraccion(1L, 1L, fuenteIa, new ClienteIAFalso(propuesta != null ? propuesta : propuestaCompleta()));
    }

    private static Map<String, Object> todos(String accion, int hasta) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 1; i <= hasta; i++) m.put("campo_" + i, d("accion", accion));
        return m;
    }

    private static Map<String, Object> contenido(Map<String, Object> s) {
        return (Map<String, Object>) s.get("contenido");
    }

    // =============================================================================
    // Servicio
    // =============================================================================

    @Test
    void propuestaCreadaPendienteDeRevision() {
        ClienteIAFalso cliente = new ClienteIAFalso(propuestaCompleta());
        Map<String, Object> sugerencia = ext.solicitarExtraccion(1L, 7L, fuenteIa, cliente);

        assertEquals("Propuesta", sugerencia.get("estado"));
        assertEquals("Pendiente de revisión", ExtraccionReportesService.etiquetaEstado(sugerencia.get("estado")));
        assertEquals("ExtraccionReporte", sugerencia.get("tipo"));
        assertEquals("ReporteProveedor", sugerencia.get("entidad"));
        Datos.igual(1, sugerencia.get("registro_id"));
        Datos.igual(1, sugerencia.get("archivo_id"));
        assertNull(sugerencia.get("validador_id"));
        assertNull(sugerencia.get("fecha_validacion"));
        Datos.igual(7, sugerencia.get("usuario_creacion_id"));
        assertTrue(((String) sugerencia.get("fecha_creacion")).length() > 0);
        // Al cliente solo se envían el PDF, el esquema y la instrucción (RNF-008)
        ClienteIAFalso.Llamada llamada = cliente.llamadas.get(0);
        assertArrayEquals(PDF, llamada.pdf());
        assertEquals(IntStream.rangeClosed(1, 5).mapToObj(i -> "campo_" + i).collect(Collectors.toSet()),
            ((Map<String, Object>) llamada.esquema().get("properties")).keySet());
        assertTrue(llamada.instruccion().contains("no instrucciones"));
    }

    @Test
    void contenidoCumpleLaEstructura() {
        Map<String, Object> c = contenido(solicitar(null));
        assertEquals(Set.of("version_contenido", "entrada", "ia", "propuesta", "campos_obligatorios_faltantes", "decision"), c.keySet());
        Datos.igual(1, c.get("version_contenido"));
        Datos.igual(d("reporte_proveedor_id", 1, "atencion_id", 4, "tipo_servicio_id", 2, "formato_id", 1, "formato_version", 1,
            "archivo_id", 1, "campos_solicitados", l(
                d("campo_id", 1, "nombre_campo", "Metrado", "tipo_dato", "Decimal", "obligatorio", true),
                d("campo_id", 2, "nombre_campo", "Productos aplicados", "tipo_dato", "JSON", "obligatorio", true),
                d("campo_id", 3, "nombre_campo", "Plagas atendidas", "tipo_dato", "JSON", "obligatorio", true),
                d("campo_id", 4, "nombre_campo", "Evidencias por espacio", "tipo_dato", "JSON", "obligatorio", true),
                d("campo_id", 5, "nombre_campo", "Ruta recorrida", "tipo_dato", "Texto largo", "obligatorio", true))), c.get("entrada"));
        Map<String, Object> ia = (Map<String, Object>) c.get("ia");
        assertEquals(Set.of("proveedor", "modelo", "solicitado_en", "duracion_ms"), ia.keySet());
        assertEquals("Gemini (Google AI Studio)", ia.get("proveedor"));
        assertEquals("modelo-de-prueba", ia.get("modelo"));
        Datos.igual(d("nombre_campo", "Metrado", "valor", 1250.5), ((Map<String, Object>) c.get("propuesta")).get("campo_1"));
        Datos.igual(l(), c.get("campos_obligatorios_faltantes"));
        assertNull(c.get("decision"));
    }

    @Test
    void obligatoriosFaltantes() {
        Map<String, Object> propuesta = propuestaCompleta();
        propuesta.put("campo_1", "mil doscientos");
        propuesta.put("campo_3", null);
        propuesta.put("campo_5", "");
        Map<String, Object> c = contenido(solicitar(propuesta));
        // campo_1 tenía un tipo no válido: se deja sin valor
        assertNull(((Map<String, Object>) ((Map<String, Object>) c.get("propuesta")).get("campo_1")).get("valor"));
        Datos.igual(l("campo_1", "campo_3", "campo_5"), c.get("campos_obligatorios_faltantes"));
    }

    @Test
    void datosInsuficientesTodoNulo() {
        Map<String, Object> vacia = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) vacia.put("campo_" + i, null);
        Map<String, Object> sugerencia = solicitar(vacia);
        assertEquals("Propuesta", sugerencia.get("estado"));
        Datos.igual(l("campo_1", "campo_2", "campo_3", "campo_4", "campo_5"), contenido(sugerencia).get("campos_obligatorios_faltantes"));
    }

    @Test
    void falloDeIaNoCreaSugerencia() {
        ClienteIAFalso cliente = ClienteIAFalso.conError(new ServicioIANoDisponible("No se pudo analizar el informe con IA"));
        assertThrows(ServicioIANoDisponible.class, () -> ext.solicitarExtraccion(1L, 1L, fuenteIa, cliente));
        assertTrue(fuenteIa.datos.get("SugerenciaIA").isEmpty());
        assertTrue(fuenteIa.escrituras.isEmpty());
    }

    @Test
    void erroresDeConfiguracionYArchivo() {
        ClienteIAFalso cliente = new ClienteIAFalso(propuestaCompleta());
        assertThrows(RecursoNoEncontrado.class, () -> ext.solicitarExtraccion(999L, 1L, fuenteIa, cliente));
        assertTrue(assertThrows(ConfiguracionFaltante.class, () -> ext.solicitarExtraccion(2L, 1L, fuenteIa, cliente))
            .mensaje().contains("formato estándar vigente"));
        assertTrue(assertThrows(RecursoNoEncontrado.class, () -> ext.solicitarExtraccion(3L, 1L, fuenteIa, cliente))
            .mensaje().contains("no tiene informe adjunto"));
        assertTrue(assertThrows(TipoArchivoNoAdmitido.class, () -> ext.solicitarExtraccion(4L, 1L, fuenteIa, cliente))
            .mensaje().contains("Solo se admite el informe en PDF"));
        assertTrue(cliente.llamadas.isEmpty());
    }

    @Test
    void esquemaDesdeLosCamposDelFormato() {
        List<Map<String, Object>> campos = l(
            d("id", 1, "nombre_campo", "A", "tipo_dato", "Texto"), d("id", 2, "nombre_campo", "B", "tipo_dato", "Entero"),
            d("id", 3, "nombre_campo", "C", "tipo_dato", "Decimal"), d("id", 4, "nombre_campo", "D", "tipo_dato", "Booleano"),
            d("id", 5, "nombre_campo", "E", "tipo_dato", "Fecha"), d("id", 6, "nombre_campo", "F", "tipo_dato", "JSON"),
            d("id", 7, "nombre_campo", "G", "tipo_dato", "Texto largo"));
        Map<String, Object> esquema = ExtraccionReportesService.construirEsquema(campos);
        Map<String, Object> props = (Map<String, Object>) esquema.get("properties");
        assertEquals("object", esquema.get("type"));
        assertEquals(IntStream.rangeClosed(1, 7).mapToObj(i -> "campo_" + i).toList(), esquema.get("required"));
        assertEquals(List.of("string", "null"), ((Map<String, Object>) props.get("campo_1")).get("type"));
        assertEquals(List.of("integer", "null"), ((Map<String, Object>) props.get("campo_2")).get("type"));
        assertEquals(List.of("number", "null"), ((Map<String, Object>) props.get("campo_3")).get("type"));
        assertEquals(List.of("boolean", "null"), ((Map<String, Object>) props.get("campo_4")).get("type"));
        Datos.igual(d("type", l("string", "null"), "format", "date", "description", "E"), props.get("campo_5"));
        assertEquals(List.of("array", "null"), ((Map<String, Object>) props.get("campo_6")).get("type"));
        Datos.igual(d("type", "object"), ((Map<String, Object>) props.get("campo_6")).get("items"));
        ConfiguracionFaltante e = assertThrows(ConfiguracionFaltante.class,
            () -> ExtraccionReportesService.construirEsquema(l(d("id", 9, "nombre_campo", "Foto", "tipo_dato", "Imagen"))));
        assertTrue(e.mensaje().contains("tipo de dato no admitido"));
    }

    @Test
    void validarAceptarCorregirDescartar() {
        Map<String, Object> sugerencia = solicitar(null);
        Map<String, Object> decisiones = d(
            "campo_1", d("accion", "corregir", "valor_final", 1300),
            "campo_2", d("accion", "aceptar"),
            "campo_3", d("accion", "descartar", "valor_final", "se ignora"),
            "campo_4", d("accion", "aceptar"),
            "campo_5", d("accion", "corregir", "valor_final", "Ruta corregida"));
        Map<String, Object> validada = ext.validar(sugerencia.get("id"), 1L, decisiones, fuenteIa);
        assertEquals("Validada", validada.get("estado"));
        Datos.igual(1, validada.get("validador_id"));
        assertTrue(((String) validada.get("fecha_validacion")).length() > 0);
        Datos.igual(1, validada.get("usuario_modificacion_id"));
        Map<String, Object> decision = (Map<String, Object>) contenido(validada).get("decision");
        Map<String, Object> porCampo = (Map<String, Object>) decision.get("por_campo");
        Datos.igual(d("accion", "corregir", "valor_final", 1300), porCampo.get("campo_1"));
        Datos.igual(propuestaCompleta().get("campo_2"), ((Map<String, Object>) porCampo.get("campo_2")).get("valor_final"));
        Datos.igual(d("accion", "descartar", "valor_final", null), porCampo.get("campo_3"));
        Datos.igual(d("aceptados", 2, "corregidos", 2, "descartados", 1), decision.get("resumen"));
        assertNull(decision.get("motivo_rechazo"));
    }

    @Test
    void todoDescartadoQuedaRechazada() {
        Map<String, Object> sugerencia = solicitar(null);
        assertEquals("Rechazada", ext.validar(sugerencia.get("id"), 1L, todos("descartar", 5), fuenteIa).get("estado"));
    }

    @Test
    void rechazar() {
        Map<String, Object> sugerencia = solicitar(null);
        Map<String, Object> rechazada = ext.rechazar(sugerencia.get("id"), 1L, "El informe no corresponde", fuenteIa);
        assertEquals("Rechazada", rechazada.get("estado"));
        Map<String, Object> decision = (Map<String, Object>) contenido(rechazada).get("decision");
        assertEquals("El informe no corresponde", decision.get("motivo_rechazo"));
        for (Object v : ((Map<String, Object>) decision.get("por_campo")).values()) Datos.igual(d("accion", "descartar", "valor_final", null), v);
        Datos.igual(d("aceptados", 0, "corregidos", 0, "descartados", 5), decision.get("resumen"));
    }

    @Test
    void validarDosVeces() {
        Map<String, Object> sugerencia = solicitar(null);
        ext.validar(sugerencia.get("id"), 1L, todos("aceptar", 5), fuenteIa);
        TransicionNoPermitida e = assertThrows(TransicionNoPermitida.class,
            () -> ext.validar(sugerencia.get("id"), 1L, todos("aceptar", 5), fuenteIa));
        assertTrue(e.mensaje().contains("estado actual: Validada"));
        assertThrows(TransicionNoPermitida.class, () -> ext.rechazar(sugerencia.get("id"), 1L, null, fuenteIa));
    }

    @Test
    void decisionesIncompletasOSobrantes() {
        Map<String, Object> sugerencia = solicitar(null);
        assertTrue(assertThrows(ParametroInvalido.class, () -> ext.validar(sugerencia.get("id"), 1L, todos("aceptar", 4), fuenteIa))
            .mensaje().contains("faltan: campo_5"));
        assertTrue(assertThrows(ParametroInvalido.class, () -> ext.validar(sugerencia.get("id"), 1L, todos("aceptar", 6), fuenteIa))
            .mensaje().contains("sobran: campo_6"));
    }

    @Test
    void corregirSinValorOConTipoErroneo() {
        Map<String, Object> sugerencia = solicitar(null);
        Map<String, Object> base = todos("aceptar", 5);
        Map<String, Object> d1 = new LinkedHashMap<>(base);
        d1.put("campo_1", d("accion", "corregir"));
        assertTrue(assertThrows(ParametroInvalido.class, () -> ext.validar(sugerencia.get("id"), 1L, d1, fuenteIa))
            .mensaje().contains("Indica el valor final del campo 'Metrado'"));
        Map<String, Object> d2 = new LinkedHashMap<>(base);
        d2.put("campo_1", d("accion", "corregir", "valor_final", "x"));
        assertTrue(assertThrows(ParametroInvalido.class, () -> ext.validar(sugerencia.get("id"), 1L, d2, fuenteIa))
            .mensaje().contains("debe ser de tipo Decimal"));
        Map<String, Object> d3 = new LinkedHashMap<>(base);
        d3.put("campo_2", d("accion", "corregir", "valor_final", d("a", 1)));
        assertTrue(assertThrows(ParametroInvalido.class, () -> ext.validar(sugerencia.get("id"), 1L, d3, fuenteIa))
            .mensaje().contains("debe ser de tipo JSON"));
        // La sugerencia sigue en Propuesta
        assertEquals("Propuesta", ext.obtener(sugerencia.get("id"), fuenteIa).get("estado"));
    }

    @Test
    void listarYObtener() {
        Map<String, Object> primera = solicitar(null);
        Map<String, Object> segunda = solicitar(null);
        ext.rechazar(primera.get("id"), 1L, null, fuenteIa);
        Datos.igual(l(segunda.get("id"), primera.get("id")), ext.listar(null, null, fuenteIa).stream().map(s -> s.get("id")).toList());
        Datos.igual(l(primera.get("id")), ext.listar("Rechazada", null, fuenteIa).stream().map(s -> s.get("id")).toList());
        assertEquals(2, ext.listar(null, 1L, fuenteIa).size());
        assertTrue(ext.listar(null, 2L, fuenteIa).isEmpty());
        assertThrows(RecursoNoEncontrado.class, () -> ext.obtener(999L, fuenteIa));
    }

    @Test
    void metricas() {
        Map<String, Object> vacia = ext.metricas(fuenteIa);
        Datos.igual(0, vacia.get("total_sugerencias"));
        assertNull(vacia.get("tasa_aceptacion_sin_cambios"));
        assertNull(vacia.get("duracion_media_ms"));

        Map<String, Object> s1 = solicitar(null);
        Map<String, Object> s2 = solicitar(null);
        solicitar(null);
        ext.validar(s1.get("id"), 1L, d(
            "campo_1", d("accion", "aceptar"), "campo_2", d("accion", "aceptar"), "campo_3", d("accion", "aceptar"),
            "campo_4", d("accion", "corregir", "valor_final", l(d("espacio", "OCAI"))), "campo_5", d("accion", "descartar")), fuenteIa);
        ext.rechazar(s2.get("id"), 1L, null, fuenteIa);
        Map<String, Object> m = ext.metricas(fuenteIa);
        Datos.igual(3, m.get("total_sugerencias"));
        Datos.igual(d("Propuesta", 1, "Validada", 1, "Rechazada", 1), m.get("por_estado"));
        Datos.igual(l(3, 1, 6), l(m.get("campos_aceptados"), m.get("campos_corregidos"), m.get("campos_descartados")));
        assertEquals(0.3, m.get("tasa_aceptacion_sin_cambios"));
        assertInstanceOf(Double.class, m.get("duracion_media_ms"));
    }

    @Test
    void soloEscribeEnSugerenciaIa() {
        Map<String, Object> sugerencia = solicitar(null);
        ext.validar(sugerencia.get("id"), 1L, todos("aceptar", 5), fuenteIa);
        assertFalse(fuenteIa.escrituras.isEmpty());
        Set<String> entidades = new HashSet<>();
        fuenteIa.escrituras.forEach(e -> entidades.add(e.entidad()));
        assertEquals(Set.of("SugerenciaIA"), entidades);
    }

    // =============================================================================
    // Adaptador de Gemini (con un doble del SDK: sin llamadas reales)
    // =============================================================================

    /** Imita {@code client.interactions.create(...)}: guarda los parámetros y devuelve un texto o lanza un error. */
    static final class SdkFalso implements ClienteGemini.Sdk {
        CreateModelInteraction parametros;
        final String texto;
        final RuntimeException error;

        SdkFalso(String texto, RuntimeException error) {
            this.texto = texto;
            this.error = error;
        }

        @Override
        public Optional<String> crearInteraccion(CreateModelInteraction p) {
            this.parametros = p;
            if (error != null) throw error;
            return Optional.ofNullable(texto);
        }
    }

    @Test
    void clienteGeminiEnviaPdfYEsquema() {
        SdkFalso sdk = new SdkFalso(JsonPy.dumpsRespuesta(d("campo_1", 10)), null);
        ClienteGemini cliente = new ClienteGemini("clave-falsa", "modelo-x", sdk);
        Map<String, Object> esquema = d("type", "object", "properties", d("campo_1", d("type", l("number", "null"))));
        Datos.igual(d("campo_1", 10), cliente.extraerJson(PDF, esquema, "instrucción"));
        assertEquals("modelo-x", sdk.parametros.model().orElseThrow().value());
        assertEquals("instrucción", sdk.parametros.systemInstruction().orElseThrow());
        List<?> entrada = sdk.parametros.input().orElseThrow().arrayOfContent().orElseThrow();
        DocumentContent documento = (DocumentContent) entrada.get(0);
        assertEquals(Base64.getEncoder().encodeToString(PDF), documento.data().orElseThrow());
        assertEquals(DocumentContentMimeType.APPLICATION_PDF, documento.mimeType().orElseThrow());
        assertEquals("document", documento.type());
        assertEquals(ClienteGemini.TEXTO_SOLICITUD, ((TextContent) entrada.get(1)).text().orElseThrow());
        TextResponseFormat formato = sdk.parametros.responseFormat().orElseThrow().responseFormat().orElseThrow()
            .textResponseFormat().orElseThrow();
        assertEquals(TextResponseFormatMimeType.APPLICATION_JSON, formato.mimeType().orElseThrow());
        assertEquals(esquema, formato.schema().orElseThrow());
        // Tiempo de espera: el del SDK (la interfaz Sdk no recibe opciones de tiempo).
    }

    static Stream<SdkFalso> sdkConErrores() {
        return Stream.of(
            new SdkFalso(null, new RuntimeException("fallo de red")),
            new SdkFalso(null, null),
            new SdkFalso("   ", null),
            new SdkFalso("no es json", null),
            new SdkFalso("[1, 2]", null));
    }

    @ParameterizedTest
    @MethodSource("sdkConErrores")
    void clienteGeminiErrores(SdkFalso sdk) {
        ClienteGemini cliente = new ClienteGemini("clave-falsa", "modelo-x", sdk);
        ServicioIANoDisponible e = assertThrows(ServicioIANoDisponible.class, () -> cliente.extraerJson(PDF, d(), "instrucción"));
        assertTrue(e.mensaje().contains("registra los datos manualmente"));
    }

    private static ListAppender<ILoggingEvent> capturarLog() {
        Logger raiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> registro = new ListAppender<>();
        registro.start();
        raiz.addAppender(registro);
        return registro;
    }

    private static void soltarLog(ListAppender<ILoggingEvent> registro) {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(registro);
    }

    private static String texto(ListAppender<ILoggingEvent> registro) {
        StringBuilder sb = new StringBuilder();
        registro.list.forEach(e -> sb.append(e.getFormattedMessage()).append('\n'));
        return sb.toString();
    }

    @Test
    void clienteGeminiSinConfiguracionNoMuestraLaClave() {
        ListAppender<ILoggingEvent> registro = capturarLog();
        try {
            assertTrue(assertThrows(ServicioIANoDisponible.class, () -> new ClienteGemini("", "modelo-x", null))
                .mensaje().contains("no está configurado"));
            assertTrue(assertThrows(ServicioIANoDisponible.class, () -> new ClienteGemini("clave-secreta-123", "", null))
                .mensaje().contains("no está configurado"));
        } finally {
            soltarLog(registro);
        }
        assertFalse(texto(registro).contains("clave-secreta-123"));
    }

    @Test
    void clienteGeminiErrorNoExponeLaClave() {
        SdkFalso sdk = new SdkFalso(null, new RuntimeException("detalle con clave-secreta-123"));
        ClienteGemini cliente = new ClienteGemini("clave-secreta-123", "modelo-x", sdk);
        ListAppender<ILoggingEvent> registro = capturarLog();
        ServicioIANoDisponible e;
        try {
            e = assertThrows(ServicioIANoDisponible.class, () -> cliente.extraerJson(PDF, d(), "instrucción"));
        } finally {
            soltarLog(registro);
        }
        assertFalse(texto(registro).contains("clave-secreta-123"));
        assertFalse(e.getMessage().contains("clave-secreta-123"));
        assertNull(e.getCause());
    }
}
