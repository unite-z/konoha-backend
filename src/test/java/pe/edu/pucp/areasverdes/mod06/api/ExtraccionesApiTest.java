package pe.edu.pucp.areasverdes.mod06.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.edu.pucp.areasverdes.mod06.apoyo.ClienteIAFalso;
import pe.edu.pucp.areasverdes.mod06.apoyo.Datos;
import pe.edu.pucp.areasverdes.mod06.apoyo.DatosIa;
import pe.edu.pucp.areasverdes.mod06.apoyo.FuenteDatosMemoria;
import pe.edu.pucp.areasverdes.mod06.apoyo.PruebaApi;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;

/**
 * Pruebas de los endpoints de RF-IA-001 ({@code tests/test_extraccion_reportes.py}, parte de endpoints), con las
 * variantes obligatorias de IA: fallo de IA (CP-TR-MOD06-006), acceso sin permiso (CP-TR-MOD06-007) y validación
 * (CP-TR-MOD06-008). Ninguna prueba llama a Gemini.
 */
@SuppressWarnings("unchecked")
class ExtraccionesApiTest extends PruebaApi {

    static final String URL = "/api/v1/ia/extracciones/";

    private FuenteDatosMemoria apiIa() {
        return usarFuente(DatosIa.fuente());
    }

    private static Map<String, Object> propuestaCompleta() {
        return DatosIa.propuestaCompleta();
    }

    private Map<String, Object> crearPorApi() throws Exception {
        MockHttpServletResponse r = postJson(URL, d("reporte_proveedor_id", 1));
        assertEquals(201, r.getStatus(), texto(r));
        return json(r);
    }

    private static Map<String, Object> todosAceptar() {
        Map<String, Object> decisiones = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) decisiones.put("campo_" + i, d("accion", "aceptar"));
        return decisiones;
    }

    @Test
    void apiSolicitarCreaPropuesta() throws Exception {
        FuenteDatosMemoria fuente = apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        Map<String, Object> datos = crearPorApi();
        assertEquals("Propuesta", datos.get("estado"));
        assertEquals("Pendiente de revisión", datos.get("etiqueta_estado"));
        Map<String, Object> propuesta = (Map<String, Object>) ((Map<String, Object>) datos.get("contenido")).get("propuesta");
        assertEquals(propuestaCompleta().get("campo_5"), ((Map<String, Object>) propuesta.get("campo_5")).get("valor"));
        assertEquals(1, fuente.datos.get("SugerenciaIA").size());
    }

    @Test
    void apiFalloDeIa503SinSugerencia() throws Exception {
        FuenteDatosMemoria fuente = apiIa();
        usarClienteIa(ClienteIAFalso.conError(new ServicioIANoDisponible(
            "No se pudo analizar el informe con IA; registra los datos manualmente")));
        MockHttpServletResponse r = postJson(URL, d("reporte_proveedor_id", 1));
        assertEquals(503, r.getStatus());
        assertEquals("ServicioIANoDisponible", json(r).get("codigo"));
        assertTrue(fuente.datos.get("SugerenciaIA").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(value = {"'',modelo-x", "clave-falsa,''"})
    void apiSinClaveOModelo503(String clave, String modelo) throws Exception {
        FuenteDatosMemoria fuente = apiIa();
        fabrica.configuracion(clave, modelo);
        MockHttpServletResponse r = postJson(URL, d("reporte_proveedor_id", 1));
        assertEquals(503, r.getStatus());
        Datos.igual(d("codigo", "ServicioIANoDisponible",
            "detalle", "El análisis con IA no está configurado; registra los datos manualmente"), json(r));
        assertTrue(fuente.datos.get("SugerenciaIA").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"2,422,ConfiguracionFaltante", "3,404,RecursoNoEncontrado", "4,415,TipoArchivoNoAdmitido", "999,404,RecursoNoEncontrado"})
    void apiErroresDeSolicitud(long reporteId, int estado, String codigo) throws Exception {
        apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        MockHttpServletResponse r = postJson(URL, d("reporte_proveedor_id", reporteId));
        assertEquals(estado, r.getStatus());
        assertEquals(codigo, json(r).get("codigo"));
    }

    @Test
    void apiSolicitudSinReporte400() throws Exception {
        apiIa();
        MockHttpServletResponse r = postJson(URL, d());
        assertEquals(400, r.getStatus());
        assertTrue(json(r).containsKey("reporte_proveedor_id"));
    }

    @Test
    void apiValidarYVolverAValidar() throws Exception {
        apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        Object sid = crearPorApi().get("id");
        Map<String, Object> cuerpo = d("decisiones", todosAceptar());
        MockHttpServletResponse r = postJson(URL + sid + "/validar/", cuerpo);
        assertEquals(200, r.getStatus());
        assertEquals("Validada", json(r).get("estado"));
        assertEquals("Validada", json(r).get("etiqueta_estado"));
        MockHttpServletResponse otra = postJson(URL + sid + "/validar/", cuerpo);
        assertEquals(409, otra.getStatus());
        assertEquals("Esta propuesta ya fue revisada (estado actual: Validada)", json(otra).get("detalle"));
    }

    @Test
    void apiValidarDecisionesIncompletas400() throws Exception {
        apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        Object sid = crearPorApi().get("id");
        MockHttpServletResponse r = postJson(URL + sid + "/validar/", d("decisiones", d("campo_1", d("accion", "aceptar"))));
        assertEquals(400, r.getStatus());
        assertEquals("ParametroInvalido", json(r).get("codigo"));
        assertTrue(((String) json(r).get("detalle")).contains("campo_2"));
    }

    @Test
    void apiCorregirSinValor400() throws Exception {
        apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        Object sid = crearPorApi().get("id");
        Map<String, Object> decisiones = todosAceptar();
        decisiones.put("campo_1", d("accion", "corregir"));
        MockHttpServletResponse r = postJson(URL + sid + "/validar/", d("decisiones", decisiones));
        assertEquals(400, r.getStatus());
        assertTrue(((String) json(r).get("detalle")).contains("valor final"));
    }

    @Test
    void apiAccionNoValida400() throws Exception {
        apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        Object sid = crearPorApi().get("id");
        MockHttpServletResponse r = postJson(URL + sid + "/validar/", d("decisiones", d("campo_1", d("accion", "borrar"))));
        assertEquals(400, r.getStatus());
        assertEquals("{\"decisiones\":{\"campo_1\":{\"accion\":[\"Usa aceptar, corregir o descartar\"]}}}", texto(r));
    }

    @Test
    void apiRechazar() throws Exception {
        apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        Object sid = crearPorApi().get("id");
        MockHttpServletResponse r = postJson(URL + sid + "/rechazar/", d("motivo", "Informe ilegible"));
        assertEquals(200, r.getStatus());
        assertEquals("Rechazada", json(r).get("estado"));
        Map<String, Object> decision = (Map<String, Object>) ((Map<String, Object>) json(r).get("contenido")).get("decision");
        assertEquals("Informe ilegible", decision.get("motivo_rechazo"));
    }

    @Test
    void apiIngenieroNoPuedeValidar403() throws Exception {
        apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        autenticar("Ingeniero de campo");
        Object sid = crearPorApi().get("id"); // el ingeniero sí puede solicitar (propone)
        MockHttpServletResponse r = postJson(URL + sid + "/validar/", d("decisiones", todosAceptar()));
        assertEquals(403, r.getStatus());
        assertEquals(MENSAJE_ROL, json(r).get("detail"));
        assertEquals(403, postJson(URL + sid + "/rechazar/", d()).getStatus());
        assertEquals(403, getUrl(URL + "metricas/").getStatus());
    }

    @Test
    void apiCapatazNoPuedeSolicitar403() throws Exception {
        apiIa();
        autenticar("Capataz");
        assertEquals(403, postJson(URL, d("reporte_proveedor_id", 1)).getStatus());
        assertEquals(403, getUrl(URL).getStatus());
    }

    @Test
    void apiJefeValidaYRegistraSuId() throws Exception {
        apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        autenticar("Jefe de sección", 1L);
        Object sid = crearPorApi().get("id");
        Map<String, Object> datos = json(postJson(URL + sid + "/validar/", d("decisiones", todosAceptar())));
        Datos.igual(1, datos.get("validador_id"));
        Datos.igual(1, datos.get("usuario_creacion_id"));
    }

    @Test
    void apiListaDetalleYMetricas() throws Exception {
        apiIa();
        usarClienteIa(new ClienteIAFalso(propuestaCompleta()));
        List<Object> ids = new ArrayList<>();
        for (int i = 0; i < 27; i++) ids.add(crearPorApi().get("id"));
        Map<String, Object> pagina1 = json(getUrl(URL));
        Datos.igual(27, pagina1.get("count"));
        assertEquals(25, ((List<?>) pagina1.get("results")).size());
        assertEquals("http://localhost/api/v1/ia/extracciones/?page=2", pagina1.get("next"));
        Map<String, Object> pagina2 = json(getUrl(URL + "?page=2"));
        List<Map<String, Object>> resultados = (List<Map<String, Object>>) pagina2.get("results");
        Datos.igual(ids.get(0), resultados.get(resultados.size() - 1).get("id"));
        assertEquals("http://localhost/api/v1/ia/extracciones/", pagina2.get("previous"));
        Datos.igual(0, json(getUrl(URL + "?estado=Validada")).get("count"));
        assertEquals(400, getUrl(URL + "?estado=Otra").getStatus());
        Datos.igual(ids.get(0), json(getUrl(URL + ids.get(0) + "/")).get("id"));
        assertEquals(404, getUrl(URL + "9999/").getStatus());
        Map<String, Object> metricas = json(getUrl(URL + "metricas/"));
        Datos.igual(27, ((Map<String, Object>) metricas.get("por_estado")).get("Propuesta"));
    }
}
