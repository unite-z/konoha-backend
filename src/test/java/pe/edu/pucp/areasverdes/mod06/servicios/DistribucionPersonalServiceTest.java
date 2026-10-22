package pe.edu.pucp.areasverdes.mod06.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.d;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.l;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.apoyo.Datos;
import pe.edu.pucp.areasverdes.mod06.apoyo.FuenteDatosMemoria;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;
import pe.edu.pucp.areasverdes.mod06.comun.Py;

/**
 * Pruebas de RF-REP-004 (distribución del personal) ({@code tests/test_distribucion_personal.py}).
 *
 * <p>Sin caso de prueba en Casos de Prueba v2: estas pruebas sirven de propuesta para QA (HU-MOD06-002).
 */
@SuppressWarnings("unchecked")
class DistribucionPersonalServiceTest {

    static final Periodo PERIODO = new Periodo(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14));
    private final DistribucionPersonalService dp = new DistribucionPersonalService(null, Clock.system(Fechas.LIMA));
    private Map<String, List<Map<String, Object>>> datosPersonal;

    @BeforeEach
    void datosPersonal() {
        List<Map<String, Object>> personal = l(
            d("id", 2, "nombres", "Andrés", "apellidos", "(ejemplo)", "documento_identidad", "00000000", "telefono", "999"),
            d("id", 3, "nombres", "Oscar", "apellidos", "(ejemplo)", "documento_identidad", "11111111", "telefono", "888"));
        for (int i = 5; i < 12; i++) {
            personal.add(d("id", i, "nombres", String.format("Operario %02d", i), "apellidos", "(ejemplo)", "documento_identidad", "X", "telefono", "Y"));
        }
        datosPersonal = new LinkedHashMap<>();
        datosPersonal.put("Personal", personal);
        datosPersonal.put("Cuadrilla", l(
            d("id", 1, "nombre", "Cuadrilla 1", "capataz_id", 2, "activa", true),
            d("id", 2, "nombre", "Cuadrilla 2", "capataz_id", 3, "activa", true),
            d("id", 3, "nombre", "Cuadrilla 3", "capataz_id", null, "activa", false)));
        datosPersonal.put("AsignacionCuadrilla", l(
            d("id", 1, "operario_id", 5, "cuadrilla_id", 1, "fecha_inicio", "2026-01-01", "fecha_fin", null),
            d("id", 2, "operario_id", 6, "cuadrilla_id", 1, "fecha_inicio", "2026-01-01", "fecha_fin", null),
            d("id", 3, "operario_id", 7, "cuadrilla_id", 1, "fecha_inicio", "2026-01-01", "fecha_fin", null),
            d("id", 4, "operario_id", 8, "cuadrilla_id", 2, "fecha_inicio", "2026-01-01", "fecha_fin", null),
            d("id", 5, "operario_id", 9, "cuadrilla_id", 2, "fecha_inicio", "2026-01-01", "fecha_fin", null),
            // Asignación vencida a la Cuadrilla 1: el 14/10 ya no es vigente
            d("id", 6, "operario_id", 10, "cuadrilla_id", 1, "fecha_inicio", "2026-01-01", "fecha_fin", "2026-10-13")));
        datosPersonal.put("ClaseActividad", l(d("id", 3, "nombre", "Mantenimiento de jardines"), d("id", 6, "nombre", "Riego")));
        datosPersonal.put("TipoActividad", l(
            d("id", 14, "clase_id", 3, "nombre", "Canteo"),
            d("id", 15, "clase_id", 3, "nombre", "Deshierbo"),
            d("id", 38, "clase_id", 6, "nombre", "Riego manual")));
        datosPersonal.put("AreaVerde", l(d("id", 1, "nombre", "Tontódromo"), d("id", 2, "nombre", "Educación"),
            d("id", 4, "nombre", "Biblioteca Central")));
        datosPersonal.put("ParteDiario", l(
            d("id", 1, "fecha", "2026-10-13", "cuadrilla_id", 1, "capataz_id", 2, "estado", "Cerrado"),
            d("id", 2, "fecha", "2026-10-13", "cuadrilla_id", 2, "capataz_id", 3, "estado", "Cerrado"),
            d("id", 3, "fecha", "2026-10-14", "cuadrilla_id", 1, "capataz_id", 2, "estado", "Cerrado"),
            d("id", 9, "fecha", "2026-10-20", "cuadrilla_id", 1, "capataz_id", 2, "estado", "Abierto")));
        datosPersonal.put("Actividad", l(
            // 13/10, Cuadrilla 1: dos actividades, el operario 7 en ambas
            d("id", 1, "parte_id", 1, "tipo_actividad_id", 14, "area_verde_id", 1,
                "fecha_hora_actividad", "2026-10-13T08:00:00-05:00", "superficie_m2", 300.0, "tiempo_minutos", 120),
            d("id", 2, "parte_id", 1, "tipo_actividad_id", 15, "area_verde_id", 2,
                "fecha_hora_actividad", "2026-10-13T10:00:00-05:00", "superficie_m2", 100.0, "tiempo_minutos", 60),
            // misma combinación que la 1 → misma fila
            d("id", 3, "parte_id", 1, "tipo_actividad_id", 14, "area_verde_id", 1,
                "fecha_hora_actividad", "2026-10-13T14:00:00-05:00", "superficie_m2", 50.0, "tiempo_minutos", 30),
            // 13/10, Cuadrilla 2: sin tiempo
            d("id", 4, "parte_id", 2, "tipo_actividad_id", 38, "area_verde_id", 4,
                "fecha_hora_actividad", "2026-10-13T08:00:00-05:00", "superficie_m2", 200.0, "tiempo_minutos", null),
            // 14/10, Cuadrilla 1: el operario 8 (Cuadrilla 2) y el 10 (asignación vencida) apoyan
            d("id", 5, "parte_id", 3, "tipo_actividad_id", 15, "area_verde_id", 1,
                "fecha_hora_actividad", "2026-10-14T08:00:00-05:00", "superficie_m2", 120.0, "tiempo_minutos", 90),
            // fuera del periodo
            d("id", 9, "parte_id", 9, "tipo_actividad_id", 14, "area_verde_id", 1,
                "fecha_hora_actividad", "2026-10-20T08:00:00-05:00", "superficie_m2", 10.0, "tiempo_minutos", 10)));
        datosPersonal.put("ParticipacionActividad", l(
            d("actividad_id", 1, "operario_id", 5), d("actividad_id", 1, "operario_id", 6), d("actividad_id", 1, "operario_id", 7),
            d("actividad_id", 2, "operario_id", 7), d("actividad_id", 3, "operario_id", 5), d("actividad_id", 4, "operario_id", 8),
            d("actividad_id", 4, "operario_id", 9), d("actividad_id", 5, "operario_id", 5), d("actividad_id", 5, "operario_id", 8),
            d("actividad_id", 5, "operario_id", 10), d("actividad_id", 9, "operario_id", 5)));
    }

    private Map<String, Object> calcular(Periodo periodo, Map<String, Object> filtros) {
        return dp.calcular(periodo, new FuenteDatosMemoria(datosPersonal), filtros);
    }

    private static List<Map<String, Object>> lista(Map<String, Object> r, String clave) {
        return (List<Map<String, Object>>) r.get(clave);
    }

    private static Map<String, Object> fila(Map<String, Object> resultado, LocalDate jornada, long cuadrillaId, long tipoId, long areaId) {
        return lista(resultado, "filas").stream()
            .filter(f -> f.get("jornada").equals(jornada) && Py.eq(f.get("cuadrilla_id"), cuadrillaId)
                && Py.eq(f.get("tipo_actividad_id"), tipoId) && Py.eq(f.get("area_verde_id"), areaId))
            .findFirst().orElseThrow();
    }

    private static Map<String, Object> total(Map<String, Object> resultado, LocalDate jornada, long cuadrillaId) {
        return lista(resultado, "totales_por_cuadrilla_jornada").stream()
            .filter(t -> t.get("jornada").equals(jornada) && Py.eq(t.get("cuadrilla_id"), cuadrillaId))
            .findFirst().orElseThrow();
    }

    @Test
    void agregacionPorJornadaCuadrillaTipoYEspacio() {
        Map<String, Object> resultado = calcular(PERIODO, Map.of());
        assertEquals(4, lista(resultado, "filas").size());
        Map<String, Object> f = fila(resultado, LocalDate.of(2026, 10, 13), 1, 14, 1);
        Datos.igual(2, f.get("numero_actividades"));
        Datos.igual(150, f.get("tiempo_minutos"));
        Datos.igual(350.0, f.get("superficie_m2"));
        Datos.igual(3, f.get("numero_operarios"));
        assertEquals("Mantenimiento de jardines", f.get("clase_actividad_nombre"));
        assertEquals("Andrés (ejemplo)", f.get("capataz_nombre"));
        // Orden: jornada, cuadrilla, clase, tipo, espacio
        List<List<Object>> claves = new ArrayList<>();
        for (Map<String, Object> x : lista(resultado, "filas")) {
            claves.add(l(x.get("jornada"), x.get("cuadrilla_nombre"), x.get("clase_actividad_nombre"), x.get("tipo_actividad_nombre")));
        }
        List<List<Object>> ordenadas = new ArrayList<>(claves);
        ordenadas.sort((a, b) -> Py.compare(a, b));
        assertEquals(ordenadas, claves);
    }

    @Test
    void horasPersona() {
        Map<String, Object> resultado = calcular(PERIODO, Map.of());
        // (120 × 3 + 30 × 1) / 60 = 6.5
        assertEquals(6.5, fila(resultado, LocalDate.of(2026, 10, 13), 1, 14, 1).get("horas_persona"));
        // 6.5 + (60 × 1) / 60 = 7.5
        assertEquals(7.5, total(resultado, LocalDate.of(2026, 10, 13), 1).get("horas_persona"));
    }

    @Test
    void operarioEnDosActividadesCuentaUnaVez() {
        Map<String, Object> resultado = calcular(PERIODO, Map.of());
        Datos.igual(3, total(resultado, LocalDate.of(2026, 10, 13), 1).get("numero_operarios")); // 5, 6 y 7
        Datos.igual(6, ((Map<String, Object>) resultado.get("resumen")).get("operarios_distintos")); // 5, 6, 7, 8, 9, 10
    }

    @Test
    void apoyoEventualSeMarca() {
        Map<String, Object> f = fila(calcular(PERIODO, Map.of()), LocalDate.of(2026, 10, 14), 1, 15, 1);
        Map<Object, Object> apoyo = new LinkedHashMap<>();
        for (Map<String, Object> o : (List<Map<String, Object>>) f.get("operarios")) apoyo.put(o.get("operario_id"), o.get("es_apoyo"));
        assertEquals(Map.of(5L, false, 8L, true, 10L, true), apoyo);
    }

    @Test
    void actividadSinTiempo() {
        Map<String, Object> resultado = calcular(PERIODO, Map.of());
        Datos.igual(1, ((Map<String, Object>) resultado.get("resumen")).get("actividades_sin_tiempo"));
        Map<String, Object> f = fila(resultado, LocalDate.of(2026, 10, 13), 2, 38, 4);
        assertNull(f.get("tiempo_minutos"));
        assertNull(f.get("horas_persona"));
    }

    @Test
    void cuadrillasSinParte() {
        List<List<Object>> sinParte = lista(calcular(PERIODO, Map.of()), "cuadrillas_sin_parte").stream()
            .map(c -> (List<Object>) new ArrayList<>(l(c.get("jornada"), c.get("cuadrilla_id"), c.get("estado")))).toList();
        // La Cuadrilla 3 está inactiva: no se lista.
        assertEquals(List.of(List.of(LocalDate.of(2026, 10, 14), 2L, "Sin parte aún")), sinParte);
    }

    @Test
    void filtros() {
        Map<String, Object> porCuadrilla = calcular(PERIODO, d("cuadrilla_id", 2));
        assertEquals(Set.of(2L), lista(porCuadrilla, "filas").stream().map(f -> f.get("cuadrilla_id")).collect(Collectors.toSet()));
        Datos.igual(d("cuadrilla_id", 2), porCuadrilla.get("filtros_aplicados"));
        assertEquals(List.of(List.of(LocalDate.of(2026, 10, 14), 2L)), lista(porCuadrilla, "cuadrillas_sin_parte").stream()
            .map(c -> List.of(c.get("jornada"), c.get("cuadrilla_id"))).toList());

        Map<String, Object> porClase = calcular(PERIODO, d("clase_actividad_id", 6));
        assertEquals(Set.of(38L), lista(porClase, "filas").stream().map(f -> f.get("tipo_actividad_id")).collect(Collectors.toSet()));

        Map<String, Object> porTipo = calcular(PERIODO, d("tipo_actividad_id", 15));
        assertEquals(Set.of(15L), lista(porTipo, "filas").stream().map(f -> f.get("tipo_actividad_id")).collect(Collectors.toSet()));

        Map<String, Object> porArea = calcular(PERIODO, d("area_verde_id", 2));
        assertEquals(List.of(2L), lista(porArea, "filas").stream().map(f -> f.get("area_verde_id")).toList());

        Map<String, Object> sinCoincidencias = calcular(PERIODO, d("area_verde_id", 999));
        assertEquals(true, sinCoincidencias.get("sin_datos"));
    }

    @Test
    void noDevuelveDatosPersonales() {
        String texto = JsonPy.dumpsRespuesta(aTexto(calcular(PERIODO, Map.of())));
        assertFalse(texto.contains("documento_identidad") || texto.contains("telefono"));
        assertFalse(texto.contains("00000000") || texto.contains("11111111"));
    }

    /** {@code json.dumps(asdict(resultado), default=str)}. */
    private static Object aTexto(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            m.forEach((k, x) -> r.put((String) k, aTexto(x)));
            return r;
        }
        if (v instanceof List<?> lista) return lista.stream().map(DistribucionPersonalServiceTest::aTexto).toList();
        if (v instanceof LocalDate f) return f.toString();
        return v;
    }

    @Test
    void sinDatos() {
        Map<String, Object> resultado = calcular(new Periodo(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)), Map.of());
        assertEquals(true, resultado.get("sin_datos"));
        assertEquals("No hay partes diarios registrados en el periodo elegido", resultado.get("mensaje_sin_datos"));
        assertTrue(lista(resultado, "filas").isEmpty());
        assertNull(resultado.get("resumen"));
    }

    @Test
    void noEscribe() {
        FuenteDatosMemoria fuente = new FuenteDatosMemoria(datosPersonal);
        dp.calcular(PERIODO, fuente, Map.of());
        assertTrue(fuente.escrituras.isEmpty());
    }

    static final Comparator<Object> ORDEN_PY = Py::compare;
}
