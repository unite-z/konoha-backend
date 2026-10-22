package pe.edu.pucp.areasverdes.mod06.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.d;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.l;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.apoyo.Datos;
import pe.edu.pucp.areasverdes.mod06.apoyo.FuenteDatosMemoria;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService.Evento;

/**
 * Pruebas de RF-REP-006 (atenciones por espacio y sector). Caso de prueba: CP-TR-MOD06-014
 * ({@code tests/test_atenciones_espacio_sector.py}).
 *
 * <p>HU-MOD06-014: indicadores por espacio y sector; los espacios sin sector se conservan en un grupo identificado;
 * el cuartel solo se usa cuando está registrado y no sustituye al sector; sin datos se informa del estado vacío.
 */
@SuppressWarnings("unchecked")
class AtencionesEspacioSectorServiceTest {

    static final Periodo PERIODO = new Periodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 20));
    private final AtencionesEspacioSectorService aes = new AtencionesEspacioSectorService(null, Clock.system(Fechas.LIMA));
    private Map<String, List<Map<String, Object>>> datos;

    private static Map<String, Object> act(int aid, int area, String instante) {
        return act(aid, area, instante, 14, null);
    }

    private static Map<String, Object> act(int aid, int area, String instante, int tipo, Integer ejemplar) {
        return d("id", aid, "parte_id", 1, "tipo_actividad_id", tipo, "area_verde_id", area, "ejemplar_id", ejemplar,
            "fecha_hora_actividad", instante);
    }

    private static Map<String, Object> atencion(int aid, int tipo, String estado, String inicio, String fin) {
        return d("id", aid, "codigo", "AT-" + aid, "tipo_servicio_id", tipo, "estado", estado,
            "fecha_hora_inicio", inicio, "fecha_hora_fin", fin);
    }

    @BeforeEach
    void datosAtenciones() {
        datos = new LinkedHashMap<>();
        datos.put("SectorMantenimiento", l(d("id", 1, "nombre", "Sector 1", "atencion_a_demanda", false),
            d("id", 2, "nombre", "Sector 2", "atencion_a_demanda", false)));
        datos.put("AsignacionSector", l(
            d("id", 1, "sector_id", 1, "capataz_id", 2, "fecha_inicio", "2025-01-01", "fecha_fin", "2025-12-31"),
            d("id", 2, "sector_id", 1, "capataz_id", 3, "fecha_inicio", "2026-01-01", "fecha_fin", null)));
        datos.put("Personal", l(d("id", 2, "nombres", "Andrés", "apellidos", "(ejemplo)"),
            d("id", 3, "nombres", "Oscar", "apellidos", "(ejemplo)")));
        datos.put("Cuartel", l(d("id", 1, "numero", 1, "nombre", "Cuartel 1", "estado", "Vigente")));
        datos.put("AreaVerde", l(
            d("id", 1, "nombre", "Tontódromo", "sector_id", 1, "cuartel_id", 1),
            d("id", 2, "nombre", "Educación", "sector_id", 1, "cuartel_id", null),
            d("id", 3, "nombre", "Biblioteca Central", "sector_id", 2, "cuartel_id", null),
            d("id", 10, "nombre", "Bosque Húmedo", "sector_id", null, "cuartel_id", null)));
        datos.put("Ejemplar", l(d("id", 1, "codigo", "EJ-00412", "area_verde_id", 10), d("id", 2, "codigo", "EJ-00105", "area_verde_id", 1),
            d("id", 3, "codigo", "EJ-00733", "area_verde_id", null)));
        datos.put("ClaseActividad", l(d("id", 3, "nombre", "Mantenimiento de jardines"), d("id", 4, "nombre", "Poda")));
        datos.put("TipoActividad", l(d("id", 14, "clase_id", 3, "nombre", "Canteo"), d("id", 24, "clase_id", 4, "nombre", "Poda de mantenimiento")));
        datos.put("TipoServicio", l(d("id", 2, "nombre", "Control fitosanitario", "ejecutor", "Tercerizado"),
            d("id", 4, "nombre", "Poda de altura a demanda", "ejecutor", "Tercerizado")));
        datos.put("ProgramacionServicio", l(d("id", 9, "tipo_servicio_id", 2, "estacion", "Primavera")));
        datos.put("Actividad", l(
            act(1, 1, "2026-09-10T08:00:00-05:00"),
            act(2, 1, "2026-10-01T08:00:00-05:00"),
            act(3, 2, "2026-10-02T08:00:00-05:00", 24, 2),
            act(4, 3, "2026-10-03T08:00:00-05:00"),
            act(5, 3, "2026-08-20T08:00:00-05:00"))); // fuera del periodo
        datos.put("AtencionTercerizada", l(
            atencion(1, 4, "Conforme / cerrado", "2026-09-15T08:00:00-05:00", null),
            atencion(2, 4, "OC referenciada", "2026-09-20T08:00:00-05:00", null), // no ejecutada
            atencion(3, 2, "Conforme / cerrado", "2026-09-24T07:00:00-05:00", null),
            atencion(4, 4, "Observado", null, "2026-10-10T12:00:00-05:00"))); // fecha por fin
        datos.put("AlcanceAtencion", l(
            d("id", 1, "atencion_id", 1, "area_verde_id", 10, "ejemplar_id", null),
            d("id", 2, "atencion_id", 1, "area_verde_id", null, "ejemplar_id", 1), // misma área: cuenta 1
            d("id", 3, "atencion_id", 2, "area_verde_id", 3, "ejemplar_id", null),
            d("id", 4, "atencion_id", 3, "area_verde_id", 1, "ejemplar_id", null),
            d("id", 5, "atencion_id", 3, "area_verde_id", 3, "ejemplar_id", null),
            d("id", 6, "atencion_id", 4, "area_verde_id", 2, "ejemplar_id", null)));
        datos.put("AplicacionFitosanitaria", l(
            d("id", 1, "programacion_id", 9, "atencion_id", 3, "fecha", "2026-09-24"), // ya contada por la atención
            d("id", 2, "programacion_id", 9, "atencion_id", null, "fecha", "2026-10-05"),
            d("id", 3, "programacion_id", null, "atencion_id", null, "fecha", "2026-10-06")));
        datos.put("AplicacionObjetivo", l(
            d("id", 1, "aplicacion_id", 1, "area_verde_id", 1, "ejemplar_id", null),
            d("id", 2, "aplicacion_id", 1, "area_verde_id", 3, "ejemplar_id", null),
            d("id", 3, "aplicacion_id", 2, "area_verde_id", 2, "ejemplar_id", null),
            d("id", 4, "aplicacion_id", 2, "area_verde_id", null, "ejemplar_id", 2),
            d("id", 5, "aplicacion_id", 3, "area_verde_id", 3, "ejemplar_id", null)));
        datos.put("IntervencionArbolado", l(
            d("id", 1, "ejemplar_id", 2, "categoria", "Menor", "actividad_id", 3, "atencion_id", null, "fecha_ejecucion", "2026-10-02"),
            d("id", 2, "ejemplar_id", 1, "categoria", "AlturaDemanda", "actividad_id", null, "atencion_id", 1, "fecha_ejecucion", "2026-09-15"),
            d("id", 3, "ejemplar_id", 3, "categoria", "Menor", "actividad_id", null, "atencion_id", null, "fecha_ejecucion", "2026-10-02"),
            d("id", 4, "ejemplar_id", 2, "categoria", "MayorCampana", "actividad_id", null, "atencion_id", null, "fecha_ejecucion", "2026-10-06"),
            d("id", 5, "ejemplar_id", 2, "categoria", "Menor", "actividad_id", null, "atencion_id", null, "fecha_ejecucion", null)));
        datos.put("DefinicionIndicador", l(d("id", 2, "codigo", "RF-REP-006", "nombre", "Atenciones por espacio y sector",
            "descripcion_calculo", "…", "meta", null, "unidad", "atenciones")));
    }

    private Map<String, Object> calcular(Periodo periodo, Map<String, Object> filtros) {
        return aes.calcular(periodo, new FuenteDatosMemoria(datos), filtros);
    }

    private static List<Map<String, Object>> sectores(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("por_sector");
    }

    private static Map<String, Object> sector(Map<String, Object> r, Object sectorId) {
        return sectores(r).stream().filter(g -> Py.eq(g.get("sector_id"), Datos.normalizar(sectorId))).findFirst().orElseThrow();
    }

    private static Map<String, Object> espacio(Map<String, Object> r, long areaId) {
        return sectores(r).stream().flatMap(g -> ((List<Map<String, Object>>) g.get("espacios")).stream())
            .filter(e -> Py.eq(e.get("area_verde_id"), areaId)).findFirst().orElseThrow();
    }

    private static List<Object> medidas(Map<String, Object> g) {
        return l(g.get("total"), g.get("personal_propio"), g.get("tercerizado"));
    }

    private static Map<String, Object> resumen(Map<String, Object> r) {
        return (Map<String, Object>) r.get("resumen");
    }

    @Test
    void conteoPorSectorYEspacio() {
        Map<String, Object> r = calcular(PERIODO, Map.of());
        Datos.igual(l(5, 2, 3), medidas(espacio(r, 1)));
        Datos.igual(l(3, 1, 2), medidas(espacio(r, 2)));
        Datos.igual(l(3, 1, 2), medidas(espacio(r, 3)));
        Datos.igual(8, sector(r, 1).get("total"));
        Datos.igual(3, sector(r, 2).get("total"));
        Datos.igual(d("Control fitosanitario", 2, "Mantenimiento de jardines", 2, "Poda (MayorCampana)", 1), espacio(r, 1).get("por_tipo"));
        Datos.igual(13, resumen(r).get("total_atenciones"));
        Datos.igual(4, resumen(r).get("espacios_con_atenciones"));
        Datos.igual(2, resumen(r).get("sectores_con_atenciones"));
        Datos.igual(d("id", 3, "nombre", "Oscar (ejemplo)"), sector(r, 1).get("capataz_vigente"));
        assertEquals("RF-REP-006", ((Map<String, Object>) r.get("indicador")).get("codigo"));
        List<String> notas = (List<String>) r.get("notas");
        assertFalse(notas.isEmpty());
        assertTrue(notas.get(0).contains("historial de área a sector"));
    }

    @Test
    void grupoSinSectorExisteYVaAlFinal() {
        for (String orden : List.of("asc", "desc")) {
            Map<String, Object> r = calcular(PERIODO, d("orden", orden));
            Map<String, Object> ultimo = sectores(r).get(sectores(r).size() - 1);
            assertNull(ultimo.get("sector_id"));
            assertEquals("Sin sector", ultimo.get("sector_nombre"));
            assertEquals(List.of("Bosque Húmedo"),
                ((List<Map<String, Object>>) ultimo.get("espacios")).stream().map(e -> e.get("area_verde_nombre")).toList());
            Datos.igual(1, ultimo.get("total")); // alcance por área y por ejemplar de la misma atención: 1
        }
    }

    @Test
    void sinEspacio() {
        Map<String, Object> sinEspacio = (Map<String, Object>) calcular(PERIODO, Map.of()).get("sin_espacio");
        Datos.igual(1, sinEspacio.get("total"));
        Datos.igual(1, sinEspacio.get("personal_propio"));
        Datos.igual(d("Poda (Menor)", 1), sinEspacio.get("por_tipo"));
    }

    @Test
    void cuartelSoloSiEstaRegistrado() {
        List<Map<String, Object>> cuarteles = (List<Map<String, Object>>) calcular(PERIODO, Map.of()).get("por_cuartel");
        Datos.igual(l(l(1, 1, 5)), cuarteles.stream().map(c -> l(c.get("cuartel_id"), c.get("numero"), c.get("total"))).toList());
    }

    private List<Evento> eventos() {
        return aes.recolectarEventos(new FuenteDatosMemoria(datos), PERIODO);
    }

    private static boolean hay(List<Evento> eventos, String origen, long id) {
        return eventos.stream().anyMatch(e -> e.origen().equals(origen) && Py.eq(e.origen_id(), id));
    }

    @Test
    void sinDobleConteo() {
        List<Evento> eventos = eventos();
        // poda menor con actividad, poda de altura con atención y aplicación con atención: no cuentan por sí mismas
        assertFalse(hay(eventos, "IntervencionArbolado", 1));
        assertFalse(hay(eventos, "IntervencionArbolado", 2));
        assertFalse(hay(eventos, "AplicacionFitosanitaria", 1));
        // alcance duplicado (área y ejemplar de la misma área): un solo par
        assertEquals(1, eventos.stream().filter(e -> e.origen().equals("AtencionTercerizada") && Py.eq(e.origen_id(), 1L)).count());
        // intervención sin fecha de ejecución: no cuenta
        assertFalse(hay(eventos, "IntervencionArbolado", 5));
    }

    @Test
    void atencionNoEjecutadaNoCuenta() {
        assertFalse(hay(eventos(), "AtencionTercerizada", 2));
    }

    @Test
    void modalidadDerivada() {
        Map<List<Object>, Evento> eventos = new LinkedHashMap<>();
        for (Evento e : eventos()) eventos.put(Py.tupla(e.origen(), e.origen_id(), e.area_verde_id()), e);
        assertEquals("PersonalPropio", eventos.get(Py.tupla("Actividad", 1L, 1L)).modalidad());
        assertEquals("Tercerizado", eventos.get(Py.tupla("AtencionTercerizada", 3L, 1L)).modalidad());
        assertEquals("Tercerizado", eventos.get(Py.tupla("AplicacionFitosanitaria", 2L, 2L)).modalidad());
        assertEquals("Control fitosanitario", eventos.get(Py.tupla("AplicacionFitosanitaria", 2L, 2L)).tipo_atencion());
        // sin programación: «Tercerizado» y «Control fitosanitario» (RS-002)
        assertEquals("Tercerizado", eventos.get(Py.tupla("AplicacionFitosanitaria", 3L, 3L)).modalidad());
        assertEquals("Control fitosanitario", eventos.get(Py.tupla("AplicacionFitosanitaria", 3L, 3L)).tipo_atencion());
        assertEquals("PersonalPropio", eventos.get(Py.tupla("IntervencionArbolado", 3L, null)).modalidad());
        assertEquals("Tercerizado", eventos.get(Py.tupla("IntervencionArbolado", 4L, 1L)).modalidad());
        // fecha por fecha_hora_fin cuando no hay inicio
        assertEquals(LocalDate.of(2026, 10, 10), eventos.get(Py.tupla("AtencionTercerizada", 4L, 2L)).fecha());
    }

    @Test
    void ordenAscYDesc() {
        Map<String, Object> desc = calcular(PERIODO, Map.of());
        assertEquals(Arrays.asList(1L, 2L, null), sectores(desc).stream().map(g -> g.get("sector_id")).toList());
        assertEquals(List.of(1L, 2L), ((List<Map<String, Object>>) sector(desc, 1).get("espacios")).stream().map(e -> e.get("area_verde_id")).toList());
        Map<String, Object> asc = calcular(PERIODO, d("orden", "asc"));
        assertEquals(Arrays.asList(2L, 1L, null), sectores(asc).stream().map(g -> g.get("sector_id")).toList());
        assertEquals(List.of(2L, 1L), ((List<Map<String, Object>>) sector(asc, 1).get("espacios")).stream().map(e -> e.get("area_verde_id")).toList());
    }

    @Test
    void filtros() {
        Map<String, Object> propio = calcular(PERIODO, d("modalidad", "PersonalPropio"));
        Datos.igual(5, resumen(propio).get("total_atenciones"));
        Datos.igual(0, resumen(propio).get("tercerizado"));

        Map<String, Object> sinSector = calcular(PERIODO, d("sector_id", "sin_sector"));
        assertEquals(Arrays.asList((Object) null), sectores(sinSector).stream().map(g -> g.get("sector_id")).toList());
        Datos.igual(0, ((Map<String, Object>) sinSector.get("sin_espacio")).get("total"));
        Datos.igual(d("sector_id", "sin_sector"), sinSector.get("filtros_aplicados"));

        Map<String, Object> sector2 = calcular(PERIODO, d("sector_id", 2));
        assertEquals(List.of(2L), sectores(sector2).stream().map(g -> g.get("sector_id")).toList());

        Map<String, Object> area = calcular(PERIODO, d("area_verde_id", 2));
        Datos.igual(3, resumen(area).get("total_atenciones"));
    }

    @Test
    void fechaLocalAmericaLima() {
        // 2026-10-21T02:00Z = 2026-10-20 21:00 en Lima: pertenece al periodo
        datos.get("Actividad").add(act(6, 3, "2026-10-21T02:00:00+00:00"));
        assertTrue(hay(eventos(), "Actividad", 6));
    }

    @Test
    void sinDatos() {
        Map<String, Object> r = calcular(new Periodo(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)), Map.of());
        assertEquals(true, r.get("sin_datos"));
        assertEquals("No hay atenciones realizadas en el periodo elegido", r.get("mensaje_sin_datos"));
        assertTrue(sectores(r).isEmpty());
        assertNull(r.get("resumen"));
        assertNull(r.get("sin_espacio"));
    }

    @Test
    void noEscribe() {
        FuenteDatosMemoria fuente = new FuenteDatosMemoria(datos);
        aes.calcular(PERIODO, fuente, Map.of());
        assertTrue(fuente.escrituras.isEmpty());
    }
}
