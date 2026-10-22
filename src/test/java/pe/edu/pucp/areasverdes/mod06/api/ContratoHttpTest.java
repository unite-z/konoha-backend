package pe.edu.pucp.areasverdes.mod06.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.edu.pucp.areasverdes.mod06.apoyo.DatosIa;
import pe.edu.pucp.areasverdes.mod06.apoyo.FuenteDatosMemoria;
import pe.edu.pucp.areasverdes.mod06.apoyo.PruebaApi;

/**
 * Pruebas adicionales del Java: cuerpos y cabeceras exactos de los casos clave, iguales a las respuestas del Django
 * capturadas en la fase 1 (errores de DRF en español, JSON mal formado, negociación, 301 sin barra final, OPTIONS).
 */
class ContratoHttpTest extends PruebaApi {

    static final String CC = "/api/v1/reportes/cumplimiento-corte/";
    static final String EXT = "/api/v1/ia/extracciones/";

    private MockHttpServletResponse postCrudo(String url, String tipo, String cuerpo) throws Exception {
        var peticion = post(url).content(cuerpo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (tipo != null) peticion = peticion.contentType(tipo);
        return http.perform(peticion).andReturn().getResponse();
    }

    @Test
    void erroresDelPeriodoEnFormatoDrf() throws Exception {
        usarFuente(new FuenteDatosMemoria());
        MockHttpServletResponse r = getUrl(CC);
        assertEquals(400, r.getStatus());
        assertEquals("{\"desde\":[\"Indica la fecha de inicio del periodo (desde)\"],\"hasta\":[\"Indica la fecha de fin del periodo (hasta)\"]}", texto(r));
        assertEquals("GET, HEAD, OPTIONS", r.getHeader("Allow"));
        assertEquals("application/json", r.getContentType());
        r = getUrl("/api/v1/reportes/atenciones-espacio-sector/?desde=2026-09-01&hasta=2026-10-20&orden=x&modalidad=y&sector_id=z&area_verde_id=w");
        assertEquals("{\"sector_id\":[\"Usa el id del sector (número entero) o 'sin_sector'\"],\"modalidad\":[\"Usa PersonalPropio o Tercerizado\"],"
            + "\"area_verde_id\":[\"Usa un número entero positivo\"],\"orden\":[\"Usa asc o desc\"]}", texto(r));
    }

    @Test
    void reporteSinDatosExacto() throws Exception {
        usarFuente(new FuenteDatosMemoria());
        String texto = texto(getUrl(CC + "?desde=2026-12-01&hasta=2026-12-31")).replaceAll("\"generado_en\":\"[^\"]*\"", "\"generado_en\":\"X\"");
        assertEquals("{\"periodo\":{\"desde\":\"2026-12-01\",\"hasta\":\"2026-12-31\"},\"generado_en\":\"X\",\"sin_datos\":true,"
            + "\"mensaje_sin_datos\":\"No hay ciclos de corte con fecha de inicio en el periodo elegido\",\"filtros_aplicados\":{},"
            + "\"indicador\":null,\"ciclos\":[],\"resumen\":null,\"ultimo_ciclo\":null}", texto);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{|JSON parse error - Expecting property name enclosed in double quotes: line 1 column 2 (char 1)",
        "{\"reporte_proveedor_id\": }|JSON parse error - Expecting value: line 1 column 26 (char 25)",
        "{\"reporte_proveedor_id\": 2,}|JSON parse error - Illegal trailing comma before end of object: line 1 column 27 (char 26)",
        "{\"reporte_proveedor_id\": 2} x|JSON parse error - Extra data: line 1 column 29 (char 28)",
        "{\"reporte_proveedor_id\": NaN}|JSON parse error - Out of range float values are not JSON compliant: 'NaN'",
        "[1, 2,]|JSON parse error - Illegal trailing comma before end of array: line 1 column 6 (char 5)"})
    void jsonMalFormado(String cuerpo, String detalle) throws Exception {
        usarFuente(DatosIa.fuente());
        MockHttpServletResponse r = postCrudo(EXT, "application/json", cuerpo);
        assertEquals(400, r.getStatus());
        assertEquals("{\"detail\":\"" + detalle.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}", texto(r));
    }

    @Test
    void cuerposQueNoSonObjeto() throws Exception {
        usarFuente(DatosIa.fuente());
        assertEquals("{\"non_field_errors\":[\"Datos inválidos. Se esperaba un diccionario pero es un list.\"]}",
            texto(postCrudo(EXT, "application/json", "[1, 2]")));
        assertEquals("{\"non_field_errors\":[\"No data provided\"]}", texto(postCrudo(EXT, "application/json", "null")));
        assertEquals("{\"reporte_proveedor_id\":[\"Este campo no puede ser nulo.\"]}",
            texto(postCrudo(EXT, "application/json", "{\"reporte_proveedor_id\": null}")));
        assertEquals("{\"decisiones\":[\"Se esperaba un diccionario de elementos en vez del tipo \\\"list\\\".\"]}",
            texto(postCrudo(EXT + "999/validar/", "application/json", "{\"decisiones\": []}")));
        assertEquals("{\"motivo\":[\"Asegúrese de que este campo no tenga más de 1000 caracteres.\"]}",
            texto(postCrudo(EXT + "999/rechazar/", "application/json", "{\"motivo\": \"" + "x".repeat(1001) + "\"}")));
    }

    @Test
    void tipoDeContenidoNoAdmitido() throws Exception {
        usarFuente(DatosIa.fuente());
        MockHttpServletResponse r = postCrudo(EXT, "text/plain", "reporte_proveedor_id=2");
        assertEquals(415, r.getStatus());
        assertEquals("{\"detail\":\"Tipo de medio \\\"text/plain\\\" incompatible en la solicitud.\"}", texto(r));
        r = postCrudo(EXT, "application/x-www-form-urlencoded", "reporte_proveedor_id=2");
        assertEquals(422, r.getStatus());
    }

    @Test
    void negociacionDeContenido() throws Exception {
        usarFuente(new FuenteDatosMemoria());
        MockHttpServletResponse r = http.perform(get(CC + "?desde=2026-01-01&hasta=2026-10-20").header("Accept", "application/xml"))
            .andReturn().getResponse();
        assertEquals(406, r.getStatus());
        assertEquals("{\"detail\":\"No se ha podido satisfacer la solicitud de cabecera de Accept.\"}", texto(r));
        r = getUrl(CC + "?desde=2026-01-01&hasta=2026-10-20&format=xml");
        assertEquals(404, r.getStatus());
        assertEquals("{\"detail\":\"No encontrado.\"}", texto(r));
    }

    @Test
    void metodosNoPermitidosYOptions() throws Exception {
        usarFuente(new FuenteDatosMemoria());
        MockHttpServletResponse r = http.perform(put(CC)).andReturn().getResponse();
        assertEquals(405, r.getStatus());
        assertEquals("{\"detail\":\"Método \\\"PUT\\\" no permitido.\"}", texto(r));
        r = getUrl(EXT + "1/validar/");
        assertEquals("POST, OPTIONS", r.getHeader("Allow"));
        assertEquals("{\"detail\":\"Método \\\"GET\\\" no permitido.\"}", texto(r));
        r = http.perform(options(CC)).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        assertEquals("{\"name\":\"Cumplimiento Corte\",\"description\":\"\",\"renders\":[\"application/json\",\"text/html\"],"
            + "\"parses\":[\"application/json\",\"application/x-www-form-urlencoded\",\"multipart/form-data\"]}", texto(r));
    }

    @Test
    void sinBarraFinalRedirige() throws Exception {
        MockHttpServletResponse r = getUrl("/api/v1/reportes/cumplimiento-corte?desde=2026-01-01&hasta=2026-10-20");
        assertEquals(301, r.getStatus());
        assertEquals("/api/v1/reportes/cumplimiento-corte/?desde=2026-01-01&hasta=2026-10-20", r.getHeader("Location"));
        assertEquals(301, getUrl("/api/v1/ia/extracciones/1").getStatus());
        assertEquals(404, getUrl("/api/v1/ia/extracciones/abc/").getStatus());
    }

    @Test
    void paginaInvalida() throws Exception {
        usarFuente(new FuenteDatosMemoria());
        MockHttpServletResponse r = getUrl(EXT + "?page=2");
        assertEquals(404, r.getStatus());
        assertEquals("{\"detail\":\"Página inválida.\"}", texto(r));
        assertEquals("{\"count\":0,\"next\":null,\"previous\":null,\"results\":[]}", texto(getUrl(EXT + "?page=")));
    }

    @Test
    void metricasVaciasExactas() throws Exception {
        usarFuente(new FuenteDatosMemoria());
        assertEquals("{\"total_sugerencias\":0,\"por_estado\":{\"Propuesta\":0,\"Validada\":0,\"Rechazada\":0},\"campos_aceptados\":0,"
            + "\"campos_corregidos\":0,\"campos_descartados\":0,\"tasa_aceptacion_sin_cambios\":null,\"duracion_media_ms\":null}",
            texto(getUrl(EXT + "metricas/")));
    }
}
