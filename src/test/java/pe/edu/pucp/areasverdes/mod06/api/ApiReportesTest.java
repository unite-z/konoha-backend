package pe.edu.pucp.areasverdes.mod06.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.d;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.l;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.edu.pucp.areasverdes.mod06.apoyo.Datos;
import pe.edu.pucp.areasverdes.mod06.apoyo.FuenteDatosMemoria;
import pe.edu.pucp.areasverdes.mod06.apoyo.PruebaApi;
import pe.edu.pucp.areasverdes.mod06.permisos.Permisos;
import pe.edu.pucp.areasverdes.mod06.permisos.Usuario;

/**
 * Pruebas de la capa {@code api/} para los reportes (RF-REP-002, RF-REP-004 y RF-REP-006): validación del periodo,
 * permisos, estado vacío, solo lectura y esquema OpenAPI ({@code tests/test_api.py}).
 */
class ApiReportesTest extends PruebaApi {

    static final List<String> RUTAS_REPORTES = List.of(
        "/api/v1/reportes/cumplimiento-corte/",
        "/api/v1/reportes/distribucion-personal/",
        "/api/v1/reportes/atenciones-espacio-sector/");
    static final String PERIODO = "desde=2026-01-01&hasta=2026-10-20";

    static Map<String, List<Map<String, Object>>> datosMinimos() {
        return Map.ofEntries(
            Map.entry("TipoServicio", l(d("id", 1, "nombre", "Corte de césped", "ejecutor", "Tercerizado"))),
            Map.entry("ProgramacionServicio", l(d("id", 1, "tipo_servicio_id", 1, "estacion", "Verano"))),
            Map.entry("FrecuenciaServicio", l(d("id", 1, "tipo_servicio_id", 1, "estacion", "Verano", "frecuencia_teorica_dias", 21,
                "frecuencia_practica_dias", 30, "frecuencia_maxima_dias", 45))),
            Map.entry("DuracionServicio", l(d("id", 1, "tipo_servicio_id", 1, "duracion_maxima_dias", 3, "dias_limpieza", 1))),
            Map.entry("CicloCorte", l(d("id", 1, "programacion_id", 1, "ruta_id", null, "proveedor_id", null, "estado", "Cerrado",
                "fecha_inicio_real", "2026-01-12", "fecha_fin_corte", "2026-01-14",
                "fecha_inicio_limpieza", "2026-01-15", "fecha_fin_limpieza", "2026-01-15"))),
            Map.entry("Cuadrilla", l(d("id", 1, "nombre", "Cuadrilla 1", "capataz_id", 2, "activa", true))),
            Map.entry("Personal", l(
                d("id", 2, "nombres", "Andrés", "apellidos", "(ejemplo)", "documento_identidad", "12345678", "telefono", "987654321"),
                d("id", 5, "nombres", "Operario 01", "apellidos", "(ejemplo)", "documento_identidad", "87654321", "telefono", "912345678"))),
            Map.entry("AsignacionCuadrilla", l(d("id", 1, "operario_id", 5, "cuadrilla_id", 1, "fecha_inicio", "2026-01-01", "fecha_fin", null))),
            Map.entry("ClaseActividad", l(d("id", 3, "nombre", "Mantenimiento de jardines"))),
            Map.entry("TipoActividad", l(d("id", 14, "clase_id", 3, "nombre", "Canteo"))),
            Map.entry("SectorMantenimiento", l(d("id", 1, "nombre", "Sector 1", "atencion_a_demanda", false))),
            Map.entry("AreaVerde", l(d("id", 1, "nombre", "Tontódromo", "sector_id", 1, "cuartel_id", null))),
            Map.entry("ParteDiario", l(d("id", 1, "fecha", "2026-10-13", "cuadrilla_id", 1, "capataz_id", 2, "estado", "Cerrado"))),
            Map.entry("Actividad", l(d("id", 1, "parte_id", 1, "tipo_actividad_id", 14, "area_verde_id", 1,
                "fecha_hora_actividad", "2026-10-13T08:00:00-05:00", "superficie_m2", 100.0, "tiempo_minutos", 60))),
            Map.entry("ParticipacionActividad", l(d("actividad_id", 1, "operario_id", 5))));
    }

    @ParameterizedTest
    @MethodSource("rutasReportes")
    void reportesResponden200YNoEscriben(String ruta) throws Exception {
        FuenteDatosMemoria fuente = usarFuente(new FuenteDatosMemoria(datosMinimos()));
        MockHttpServletResponse r = getUrl(ruta + "?" + PERIODO);
        assertEquals(200, r.getStatus(), texto(r));
        Map<String, Object> cuerpo = json(r);
        Datos.igual(d("desde", "2026-01-01", "hasta", "2026-10-20"), cuerpo.get("periodo"));
        assertEquals(false, cuerpo.get("sin_datos"));
        assertNull(cuerpo.get("mensaje_sin_datos"));
        assertTrue(((String) cuerpo.get("generado_en")).endsWith("-05:00"));
        assertTrue(fuente.escrituras.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("rutasReportes")
    void reportesSoloAdmitenGet(String ruta) throws Exception {
        usarFuente(new FuenteDatosMemoria(datosMinimos()));
        MockHttpServletResponse r = http.perform(post(ruta).contentType("application/json").content("{}")).andReturn().getResponse();
        assertEquals(405, r.getStatus());
        assertEquals("{\"detail\":\"Método \\\"POST\\\" no permitido.\"}", texto(r));
        assertEquals("GET, HEAD, OPTIONS", r.getHeader("Allow"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "/api/v1/reportes/cumplimiento-corte/|No hay ciclos de corte con fecha de inicio en el periodo elegido",
        "/api/v1/reportes/distribucion-personal/|No hay partes diarios registrados en el periodo elegido",
        "/api/v1/reportes/atenciones-espacio-sector/|No hay atenciones realizadas en el periodo elegido"})
    void estadoVacio(String ruta, String mensaje) throws Exception {
        usarFuente(new FuenteDatosMemoria());
        Map<String, Object> cuerpo = json(getUrl(ruta + "?" + PERIODO));
        assertEquals(true, cuerpo.get("sin_datos"));
        assertEquals(mensaje, cuerpo.get("mensaje_sin_datos"));
        assertNull(cuerpo.get("resumen"));
    }

    static Stream<String> rutasReportes() {
        return RUTAS_REPORTES.stream();
    }

    static Stream<Arguments> periodosInvalidos() {
        List<String[]> casos = List.of(
            new String[] {"hasta=2026-10-20", "desde", "Indica la fecha de inicio del periodo (desde)"},
            new String[] {"desde=2026-01-01", "hasta", "Indica la fecha de fin del periodo (hasta)"},
            new String[] {"desde=&hasta=2026-10-20", "desde", "Indica la fecha de inicio del periodo (desde)"},
            new String[] {"desde=20/10/2026&hasta=2026-10-20", "desde", "Usa el formato AAAA-MM-DD"},
            new String[] {"desde=2026-01-01&hasta=2026-13-01", "hasta", "Usa el formato AAAA-MM-DD"},
            new String[] {"desde=2026-10-20&hasta=2026-01-01", "hasta", "La fecha de fin debe ser igual o posterior a la fecha de inicio"});
        return RUTAS_REPORTES.stream().flatMap(ruta -> casos.stream().map(c -> Arguments.of(ruta, c[0], c[1], c[2])));
    }

    @ParameterizedTest
    @MethodSource("periodosInvalidos")
    void periodoInvalido400(String ruta, String parametros, String campo, String mensaje) throws Exception {
        FuenteDatosMemoria fuente = usarFuente(new FuenteDatosMemoria());
        MockHttpServletResponse r = getUrl(ruta + "?" + parametros);
        assertEquals(400, r.getStatus());
        Datos.igual(l(mensaje), json(r).get(campo));
        assertTrue(fuente.escrituras.isEmpty());
    }

    @Test
    void filtrosInvalidos400() throws Exception {
        usarFuente(new FuenteDatosMemoria());
        String base = "/api/v1/reportes/";
        assertEquals(400, getUrl(base + "distribucion-personal/?" + PERIODO + "&cuadrilla_id=abc").getStatus());
        assertEquals(400, getUrl(base + "atenciones-espacio-sector/?" + PERIODO + "&modalidad=Otra").getStatus());
        assertEquals(400, getUrl(base + "atenciones-espacio-sector/?" + PERIODO + "&orden=mayor").getStatus());
        assertEquals(400, getUrl(base + "atenciones-espacio-sector/?" + PERIODO + "&sector_id=x").getStatus());
    }

    @Test
    void filtrosAplicadosSeDevuelven() throws Exception {
        usarFuente(new FuenteDatosMemoria(datosMinimos()));
        Map<String, Object> cuerpo = json(getUrl("/api/v1/reportes/atenciones-espacio-sector/?" + PERIODO + "&sector_id=sin_sector&orden=asc"));
        Datos.igual(d("sector_id", "sin_sector", "orden", "asc"), cuerpo.get("filtros_aplicados"));
        cuerpo = json(getUrl("/api/v1/reportes/distribucion-personal/?" + PERIODO + "&cuadrilla_id=1"));
        Datos.igual(d("cuadrilla_id", 1), cuerpo.get("filtros_aplicados"));
    }

    @Test
    void distribucionSinDatosPersonales() throws Exception {
        usarFuente(new FuenteDatosMemoria(datosMinimos()));
        String texto = texto(getUrl("/api/v1/reportes/distribucion-personal/?" + PERIODO));
        for (String prohibido : List.of("documento_identidad", "telefono", "12345678", "987654321", "87654321", "912345678")) {
            assertFalse(texto.contains(prohibido), prohibido);
        }
    }

    @ParameterizedTest
    @CsvSource({"Jefe de sección,200", "Ingeniero de campo,200", "Practicante,200", "Capataz,403", "Operario,403"})
    void permisosDeConsulta(String rol, int esperado) throws Exception {
        usarFuente(new FuenteDatosMemoria(datosMinimos()));
        autenticar(rol);
        for (String ruta : RUTAS_REPORTES) {
            MockHttpServletResponse r = getUrl(ruta + "?" + PERIODO);
            assertEquals(esperado, r.getStatus());
            if (esperado == 403) assertEquals(MENSAJE_ROL, json(r).get("detail"));
        }
    }

    @Test
    void anonimoPermitidoConAvisoUnico() throws Exception {
        Permisos.reiniciarAviso();
        usarFuente(new FuenteDatosMemoria(datosMinimos()));
        Logger logger = (Logger) LoggerFactory.getLogger(Permisos.class);
        ListAppender<ILoggingEvent> registro = new ListAppender<>();
        registro.start();
        logger.addAppender(registro);
        try {
            for (String ruta : RUTAS_REPORTES) assertEquals(200, getUrl(ruta + "?" + PERIODO).getStatus());
        } finally {
            logger.detachAppender(registro);
        }
        long avisos = registro.list.stream().filter(e -> e.getFormattedMessage().contains("falta la autenticación del MOD-01")).count();
        assertEquals(1, avisos);
    }

    @Test
    void rolDeUsuarioResuelveElNombreDelRol() {
        selector.usar(new FuenteDatosMemoria(Map.of("Rol", l(d("id", 1, "nombre", "Jefe de sección", "descripcion", null, "activo", true)))));
        Permisos reales = new Permisos(selector);
        assertEquals("Jefe de sección", reales.rolDeUsuario(new Usuario(true, null, 1L)));
        assertNull(reales.rolDeUsuario(new Usuario(false, null, 1L)));
        assertNull(Permisos.idDeUsuario(new Usuario(false, 3L, null)));
        assertEquals(3L, Permisos.idDeUsuario(new Usuario(true, 3L, null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/schema"})
    void esquemaOpenapiIncluyeLasCuatroEtiquetas(String ruta) throws Exception {
        MockHttpServletResponse r = http.perform(get(ruta)).andReturn().getResponse();
        assertEquals(200, r.getStatus());
        String texto = texto(r);
        for (String etiqueta : List.of("RF-REP-002 Cumplimiento del corte", "RF-REP-004 Distribución del personal",
            "RF-REP-006 Atenciones por espacio y sector", "RF-IA-001 Extracción de informes con IA")) {
            assertTrue(texto.contains(etiqueta), etiqueta);
        }
    }
}
