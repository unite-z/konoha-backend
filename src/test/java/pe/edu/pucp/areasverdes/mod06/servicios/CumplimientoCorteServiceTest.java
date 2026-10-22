package pe.edu.pucp.areasverdes.mod06.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.d;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.l;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.apoyo.Datos;
import pe.edu.pucp.areasverdes.mod06.apoyo.FuenteDatosMemoria;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;

/**
 * Pruebas de RF-REP-002 (cumplimiento del corte de césped). Caso de prueba: CP-TR-MOD06-009
 * ({@code tests/test_cumplimiento_corte.py}).
 *
 * <p>HU-MOD06-009: se ven los días entre ciclos y de ejecución frente a los límites; si faltan fechas o límites, se
 * informa de la falta y no se muestra cumplimiento calculado.
 */
class CumplimientoCorteServiceTest {

    private final CumplimientoCorteService cc = new CumplimientoCorteService(null, Clock.system(Fechas.LIMA));
    private Map<String, List<Map<String, Object>>> datosCorte;

    private static Map<String, Object> programacion(int pid, String estacion) {
        return d("id", pid, "tipo_servicio_id", 1, "estacion", estacion);
    }

    private static Map<String, Object> ciclo(int cid, String estado, String inicio, String finCorte, String iniLimp, String finLimp) {
        return d("id", cid, "programacion_id", cid, "ruta_id", 1, "proveedor_id", 1, "estado", estado,
            "fecha_inicio_real", inicio, "fecha_fin_corte", finCorte, "fecha_inicio_limpieza", iniLimp, "fecha_fin_limpieza", finLimp);
    }

    /** Escenarios de la sección 12.3 del superprompt. */
    @BeforeEach
    void datosCorte() {
        datosCorte = new LinkedHashMap<>();
        datosCorte.put("TipoServicio", l(d("id", 1, "nombre", "Corte de césped", "ejecutor", "Tercerizado")));
        List<Map<String, Object>> frecuencias = l();
        int i = 1;
        for (String e : List.of("Verano", "Otoño", "Invierno")) {
            frecuencias.add(d("id", i++, "tipo_servicio_id", 1, "estacion", e, "frecuencia_teorica_dias", 21,
                "frecuencia_practica_dias", 30, "frecuencia_maxima_dias", 45));
        }
        datosCorte.put("FrecuenciaServicio", frecuencias);
        datosCorte.put("DuracionServicio", l(d("id", 1, "tipo_servicio_id", 1, "duracion_maxima_dias", 3, "dias_limpieza", 1)));
        datosCorte.put("Proveedor", l(d("id", 1, "razon_social", "Terraverde Ingenieros SAC")));
        datosCorte.put("RutaCorte", l(d("id", 1, "nombre", "Ruta de jardines 2026")));
        datosCorte.put("DefinicionIndicador", l(d("id", 1, "codigo", "RF-REP-002", "nombre", "Cumplimiento del corte de césped",
            "descripcion_calculo", "…", "meta", null, "unidad", "días", "activo", true)));
        datosCorte.put("ProgramacionServicio", l(programacion(1, "Verano"), programacion(2, "Verano"), programacion(3, "Verano"),
            programacion(4, "Otoño"), programacion(5, "Otoño"), programacion(6, "Primavera"), programacion(7, "Primavera"),
            programacion(8, "Primavera")));
        datosCorte.put("CicloCorte", l(
            ciclo(1, "Cerrado", "2026-01-12", "2026-01-14", "2026-01-15", "2026-01-15"),
            ciclo(2, "Cerrado", "2026-02-09", "2026-02-11", "2026-02-12", "2026-02-12"),
            ciclo(3, "Cerrado", "2026-03-19", "2026-03-21", "2026-03-22", "2026-03-22"),
            ciclo(4, "Cerrado", "2026-05-11", "2026-05-13", "2026-05-14", "2026-05-14"),
            ciclo(5, "Cerrado", "2026-06-08", "2026-06-11", "2026-06-12", "2026-06-13"),
            ciclo(6, "Cerrado", "2026-09-28", "2026-09-30", "2026-10-01", "2026-10-01"),
            ciclo(7, "En corte", "2026-10-19", null, null, null),
            ciclo(8, "Programado", null, null, null, null)));
    }

    private static Periodo anio() {
        return new Periodo(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 20));
    }

    private Map<String, Object> calcular(Periodo periodo) {
        return cc.calcular(periodo, new FuenteDatosMemoria(datosCorte), Map.of());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> ciclos(Map<String, Object> resultado) {
        return (List<Map<String, Object>>) resultado.get("ciclos");
    }

    private static Map<Long, Map<String, Object>> porId(Map<String, Object> resultado) {
        Map<Long, Map<String, Object>> r = new LinkedHashMap<>();
        for (Map<String, Object> fila : ciclos(resultado)) r.put((Long) fila.get("ciclo_id"), fila);
        return r;
    }

    @SuppressWarnings("unchecked")
    private static List<String> faltantes(Map<String, Object> fila) {
        return (List<String>) fila.get("faltantes");
    }

    @Test
    void estadosDeFrecuenciaYDuracion() {
        Map<Long, Map<String, Object>> filas = porId(calcular(anio()));

        assertEquals("Sin ciclo anterior", filas.get(1L).get("estado_frecuencia"));
        assertNull(filas.get(1L).get("dias_entre_ciclos"));
        Datos.igual(l(28, "Dentro de la frecuencia"), l(filas.get(2L).get("dias_entre_ciclos"), filas.get(2L).get("estado_frecuencia")));
        Datos.igual(l(38, "Tolerado"), l(filas.get(3L).get("dias_entre_ciclos"), filas.get(3L).get("estado_frecuencia")));
        Datos.igual(l(53, "Fuera de frecuencia"), l(filas.get(4L).get("dias_entre_ciclos"), filas.get(4L).get("estado_frecuencia")));
        assertEquals("Límite no configurado", filas.get(6L).get("estado_frecuencia"));
        assertTrue(faltantes(filas.get(6L)).contains("No hay frecuencia configurada para la estación Primavera"));

        // Duración: 3 + 1 días dentro del máximo
        Datos.igual(l(3, 1, 4), l(filas.get(2L).get("dias_corte"), filas.get(2L).get("dias_limpieza"), filas.get(2L).get("dias_total")));
        Datos.igual(4, filas.get(2L).get("maximo_total"));
        assertEquals("Dentro del máximo", filas.get(2L).get("estado_duracion"));
        // 4 días de corte + 2 de limpieza
        Datos.igual(l(4, 2, 6), l(filas.get(5L).get("dias_corte"), filas.get(5L).get("dias_limpieza"), filas.get(5L).get("dias_total")));
        assertEquals("Excede el máximo", filas.get(5L).get("estado_duracion"));
        // Ciclo en curso
        assertNull(filas.get(7L).get("dias_corte"));
        assertNull(filas.get(7L).get("dias_total"));
        assertEquals("Sin fecha de fin de corte", filas.get(7L).get("estado_duracion"));
        assertTrue(faltantes(filas.get(7L)).contains("Falta la fecha de fin de corte"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cicloProgramadoSinFechaSeExcluye() {
        Map<String, Object> resultado = calcular(anio());
        assertFalse(porId(resultado).containsKey(8L));
        Datos.igual(7, ((Map<String, Object>) resultado.get("resumen")).get("total_ciclos"));
    }

    @Test
    void cicloAnteriorFueraDelPeriodoSiSeUsa() {
        Map<String, Object> resultado = calcular(new Periodo(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
        Datos.igual(l(2), ciclos(resultado).stream().map(f -> f.get("ciclo_id")).toList());
        Map<String, Object> fila = ciclos(resultado).get(0);
        Datos.igual(1, fila.get("ciclo_anterior_id"));
        assertEquals(LocalDate.of(2026, 1, 12), fila.get("fecha_inicio_anterior"));
        Datos.igual(28, fila.get("dias_entre_ciclos"));
    }

    @Test
    void sinLimitesNoCalculaCumplimiento() {
        datosCorte.put("DuracionServicio", l());
        datosCorte.put("FrecuenciaServicio", l());
        for (Map<String, Object> fila : ciclos(calcular(anio()))) {
            assertTrue(List.of("Límite no configurado", "Sin ciclo anterior").contains(fila.get("estado_frecuencia")));
            assertEquals("Límite no configurado", fila.get("estado_duracion"));
            assertNull(fila.get("maximo_total"));
            assertTrue(faltantes(fila).contains("No hay duración máxima configurada para el servicio"));
        }
    }

    @Test
    void soloUnLimiteDeFrecuencia() {
        for (Map<String, Object> f : datosCorte.get("FrecuenciaServicio")) f.put("frecuencia_practica_dias", null); // solo máxima (45)
        Map<Long, Map<String, Object>> filas = porId(calcular(anio()));
        assertEquals("Dentro de la frecuencia", filas.get(3L).get("estado_frecuencia")); // 38 ≤ 45
        assertEquals("Fuera de frecuencia", filas.get(4L).get("estado_frecuencia")); // 53 > 45
    }

    @Test
    void periodoSinCiclosDevuelveSinDatos() {
        Map<String, Object> resultado = calcular(new Periodo(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31)));
        assertEquals(true, resultado.get("sin_datos"));
        assertEquals("No hay ciclos de corte con fecha de inicio en el periodo elegido", resultado.get("mensaje_sin_datos"));
        assertTrue(ciclos(resultado).isEmpty());
        assertNull(resultado.get("resumen"));
        assertNull(resultado.get("ultimo_ciclo"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ultimoCicloYResumen() {
        Map<String, Object> resultado = calcular(anio());
        Datos.igual(7, ((Map<String, Object>) resultado.get("ultimo_ciclo")).get("ciclo_id"));
        Map<String, Object> resumen = (Map<String, Object>) resultado.get("resumen");
        Datos.igual(1, ((Map<String, Object>) resumen.get("por_estado_frecuencia")).get("Tolerado"));
        Datos.igual(1, ((Map<String, Object>) resumen.get("por_estado_duracion")).get("Excede el máximo"));
        assertEquals("RF-REP-002", ((Map<String, Object>) resultado.get("indicador")).get("codigo"));
        Map<String, Object> fila = ciclos(resultado).get(0);
        assertEquals("Terraverde Ingenieros SAC", fila.get("proveedor_razon_social"));
        assertEquals("Ruta de jardines 2026", fila.get("ruta_nombre"));
    }

    @Test
    void indicadorAusenteNoEsError() {
        datosCorte.put("DefinicionIndicador", l());
        assertNull(calcular(anio()).get("indicador"));
    }

    @Test
    void noEscribe() {
        FuenteDatosMemoria fuente = new FuenteDatosMemoria(datosCorte);
        cc.calcular(anio(), fuente, Map.of());
        assertTrue(fuente.escrituras.isEmpty());
    }
}
