package pe.edu.pucp.areasverdes.mod06.servicios;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.SelectorFuenteDatos;

/**
 * Servicio de RF-REP-004: distribución del personal por cuadrilla, actividad, espacio y jornada.
 *
 * <p>Trazabilidad: RF-REP-004 · HU-MOD06-002 · CU-MOD06-002 · sin caso de prueba en Casos de Prueba v2 (las
 * pruebas de {@code DistribucionPersonalServiceTest} sirven de propuesta para QA) · RN-015, RI-04, RI-19, RI-21 ·
 * CU-MOD04-011 (1a), CU-MOD04-013.
 *
 * <p>Texto del requisito (Catálogo v2): «El sistema deberá generar un reporte de distribución del personal por
 * cuadrilla, actividad, espacio y jornada».
 *
 * <p>Reglas [Definición acordada 20/10]:
 * <ol>
 *   <li>Jornada = ParteDiario.fecha (el modelo no tiene turnos).</li>
 *   <li>Entran los partes con fecha en el periodo y sus actividades. Los filtros se aplican antes de agregar.</li>
 *   <li>Fila de detalle: jornada × cuadrilla × tipo de actividad × espacio.</li>
 *   <li>Horas-persona = Σ(tiempo_minutos × participantes de la actividad) / 60, redondeado a 2 decimales. Las
 *   actividades sin {@code tiempo_minutos} no suman y se cuentan en {@code actividades_sin_tiempo}.</li>
 *   <li>Apoyo eventual (CU-MOD04-011 1a, frente a RI-21): participante sin AsignacionCuadrilla vigente en la
 *   cuadrilla del parte en esa fecha. Se marca, no se excluye.</li>
 *   <li>Totales por cuadrilla y jornada: cada operario cuenta una vez aunque participe en varias actividades.</li>
 *   <li>Cuadrillas sin parte: para cada jornada con al menos un parte, cuadrillas activas sin parte → «Sin parte
 *   aún».</li>
 *   <li>Orden: jornada, cuadrilla, clase, tipo y espacio (por nombre).</li>
 * </ol>
 *
 * <p>RNF-008 y RI-34: no se leen ni se devuelven {@code documento_identidad} ni {@code telefono} del Personal.
 * HU-MOD06-002: la consulta no modifica datos operativos; la exportación (RF-REP-009) queda fuera de este corte.
 */
@Service
public class DistribucionPersonalService {

    public static final String MENSAJE_SIN_DATOS = "No hay partes diarios registrados en el periodo elegido";
    public static final String MENSAJE_SIN_ACTIVIDADES_FILTRO =
        "No hay actividades registradas en el periodo elegido con los filtros aplicados";
    public static final String ESTADO_SIN_PARTE = "Sin parte aún";

    private final SelectorFuenteDatos selector;
    private final Clock reloj;

    public DistribucionPersonalService(SelectorFuenteDatos selector, Clock reloj) {
        this.selector = selector;
        this.reloj = reloj;
    }

    /**
     * Genera la distribución del personal en {@code periodo} con los filtros opcionales {@code cuadrilla_id},
     * {@code clase_actividad_id}, {@code tipo_actividad_id} y {@code area_verde_id}.
     *
     * <p>RF-REP-004 · HU-MOD06-002 · CU-MOD06-002. Solo lectura.
     */
    public Map<String, Object> calcular(Periodo periodo, Map<String, Object> filtros) {
        return calcular(periodo, null, filtros);
    }

    public Map<String, Object> calcular(Periodo periodo, FuenteDatos fuente, Map<String, Object> filtros) {
        Object cuadrillaId = filtros.get("cuadrilla_id");
        Object claseActividadId = filtros.get("clase_actividad_id");
        Object tipoActividadId = filtros.get("tipo_actividad_id");
        Object areaVerdeId = filtros.get("area_verde_id");
        FuenteDatos f = fuente != null ? fuente : selector.obtenerFuenteDatos();

        Map<String, Object> filtrosAplicados = Comunes.filtrosAplicados(Py.dict(
            "cuadrilla_id", cuadrillaId,
            "clase_actividad_id", claseActividadId,
            "tipo_actividad_id", tipoActividadId,
            "area_verde_id", areaVerdeId));

        List<Map<String, Object>> partesPeriodo = new ArrayList<>();
        for (Map<String, Object> p : f.listar("ParteDiario")) {
            if (periodo.contiene(Fechas.aFecha(p.get("fecha")))) partesPeriodo.add(p);
        }
        Set<LocalDate> jornadasSet = new java.util.TreeSet<>();
        for (Map<String, Object> p : partesPeriodo) jornadasSet.add(Fechas.aFecha(Py.item(p, "fecha")));
        List<LocalDate> jornadasConAlgunParte = new ArrayList<>(jornadasSet);
        List<Map<String, Object>> partes = new ArrayList<>();
        for (Map<String, Object> p : partesPeriodo) {
            if (cuadrillaId == null || Py.eq(p.get("cuadrilla_id"), cuadrillaId)) partes.add(p);
        }

        Map<Object, Map<String, Object>> cuadrillas = Py.indexar(f.listar("Cuadrilla"), "id");
        List<Map<String, Object>> cuadrillasSinParte = cuadrillasSinParte(partesPeriodo, jornadasConAlgunParte, cuadrillas, cuadrillaId);

        if (partes.isEmpty()) return resultadoVacio(periodo, filtrosAplicados, MENSAJE_SIN_DATOS, cuadrillasSinParte);

        // --- Catálogos ----------------------------------------------------------
        Map<Object, Map<String, Object>> tipos = Py.indexar(f.listar("TipoActividad"), "id");
        Map<Object, Map<String, Object>> clases = Py.indexar(f.listar("ClaseActividad"), "id");
        Map<Object, Map<String, Object>> areas = Py.indexar(f.listar("AreaVerde"), "id");
        Map<Object, Map<String, Object>> personal = new LinkedHashMap<>();
        for (Map<String, Object> p : f.listar("Personal")) {
            Object id = Py.item(p, "id");
            personal.put(Py.k(id), Py.dict("id", id, "nombres", p.get("nombres"), "apellidos", p.get("apellidos")));
        }
        List<Map<String, Object>> asignaciones = f.listar("AsignacionCuadrilla");

        // --- Actividades de los partes, con filtros -----------------------------
        Map<Object, Map<String, Object>> partesPorId = Py.indexar(partes, "id");
        List<Map<String, Object>> actividades = new ArrayList<>();
        for (Map<String, Object> actividad : f.listar("Actividad")) {
            if (!partesPorId.containsKey(Py.k(actividad.get("parte_id")))) continue;
            Map<String, Object> tipo = tipos.get(Py.k(actividad.get("tipo_actividad_id")));
            Object claseId = Py.truthy(tipo) ? tipo.get("clase_id") : null;
            if (claseActividadId != null && !Py.eq(claseId, claseActividadId)) continue;
            if (tipoActividadId != null && !Py.eq(actividad.get("tipo_actividad_id"), tipoActividadId)) continue;
            if (areaVerdeId != null && !Py.eq(actividad.get("area_verde_id"), areaVerdeId)) continue;
            actividades.add(actividad);
        }

        if (actividades.isEmpty()) {
            return resultadoVacio(periodo, filtrosAplicados, MENSAJE_SIN_ACTIVIDADES_FILTRO, cuadrillasSinParte);
        }

        Set<Object> idsActividades = new LinkedHashSet<>();
        for (Map<String, Object> a : actividades) idsActividades.add(Py.k(Py.item(a, "id")));
        Map<Object, List<Object>> participantes = new LinkedHashMap<>();
        for (Map<String, Object> participacion : f.listar("ParticipacionActividad")) {
            Object actividadId = participacion.get("actividad_id");
            Object operarioId = participacion.get("operario_id");
            if (idsActividades.contains(Py.k(actividadId))) {
                List<Object> lista = participantes.computeIfAbsent(Py.k(actividadId), x -> new ArrayList<>());
                if (!Py.en(operarioId, lista)) lista.add(operarioId);
            }
        }

        // --- Agregación ---------------------------------------------------------
        Comunes.Grupos<Map<String, Object>> grupos = new Comunes.Grupos<>();
        for (Map<String, Object> actividad : actividades) {
            Map<String, Object> parte = partesPorId.get(Py.k(Py.item(actividad, "parte_id")));
            grupos.agregar(Arrays.asList(Fechas.aFecha(Py.item(parte, "fecha")), parte.get("cuadrilla_id"),
                actividad.get("tipo_actividad_id"), actividad.get("area_verde_id")), actividad);
        }

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map.Entry<Object, List<Map<String, Object>>> grupo : grupos.entradas()) {
            List<?> clave = (List<?>) grupo.getKey();
            LocalDate jornada = (LocalDate) clave.get(0);
            Object cuadId = clave.get(1);
            Object tipoId = clave.get(2);
            Object areaId = clave.get(3);
            List<Map<String, Object>> acts = grupo.getValue();
            Map<String, Object> parte = partesPorId.get(Py.k(Py.item(acts.get(0), "parte_id")));
            Map<String, Object> cuadrilla = cuadrillas.get(Py.k(cuadId));
            Map<String, Object> tipo = tipos.get(Py.k(tipoId));
            Map<String, Object> clase = Py.truthy(tipo) ? clases.get(Py.k(tipo.get("clase_id"))) : null;
            Map<String, Object> area = areas.get(Py.k(areaId));
            Object capatazId = parte.get("capataz_id");

            List<Map<String, Object>> operarios = new ArrayList<>();
            for (Object op : operariosDe(acts, participantes)) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("operario_id", op);
                o.put("nombre_completo", Comunes.nombreCompleto(personal.get(Py.k(op))));
                o.put("es_apoyo", !asignacionVigente(asignaciones, op, cuadId, jornada));
                operarios.add(o);
            }
            operarios.sort((a, b) -> Py.compare(
                Arrays.asList(Py.or(a.get("nombre_completo"), ""), a.get("operario_id")),
                Arrays.asList(Py.or(b.get("nombre_completo"), ""), b.get("operario_id"))));
            Object[] tiempoHoras = tiempoYHoras(acts, participantes);

            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("jornada", jornada);
            fila.put("cuadrilla_id", cuadId);
            fila.put("cuadrilla_nombre", Py.truthy(cuadrilla) ? cuadrilla.get("nombre") : null);
            fila.put("capataz_id", capatazId);
            fila.put("capataz_nombre", Comunes.nombreCompleto(personal.get(Py.k(capatazId))));
            fila.put("clase_actividad_id", Py.truthy(clase) ? clase.get("id") : null);
            fila.put("clase_actividad_nombre", Py.truthy(clase) ? clase.get("nombre") : null);
            fila.put("tipo_actividad_id", tipoId);
            fila.put("tipo_actividad_nombre", Py.truthy(tipo) ? tipo.get("nombre") : null);
            fila.put("area_verde_id", areaId);
            fila.put("area_verde_nombre", Py.truthy(area) ? area.get("nombre") : null);
            fila.put("numero_actividades", (long) acts.size());
            fila.put("operarios", operarios);
            fila.put("numero_operarios", (long) operarios.size());
            fila.put("tiempo_minutos", tiempoHoras[0]);
            fila.put("horas_persona", tiempoHoras[1]);
            fila.put("superficie_m2", sumaONulo(acts, "superficie_m2"));
            filas.add(fila);
        }

        filas.sort((a, b) -> Py.compare(claveFila(a), claveFila(b)));

        // --- Totales por cuadrilla y jornada ------------------------------------
        Comunes.Grupos<Map<String, Object>> porCuadrillaJornada = new Comunes.Grupos<>();
        for (Map<String, Object> actividad : actividades) {
            Map<String, Object> parte = partesPorId.get(Py.k(Py.item(actividad, "parte_id")));
            porCuadrillaJornada.agregar(Arrays.asList(Fechas.aFecha(Py.item(parte, "fecha")), parte.get("cuadrilla_id")), actividad);
        }

        List<Map<String, Object>> totales = new ArrayList<>();
        for (Map.Entry<Object, List<Map<String, Object>>> grupo : porCuadrillaJornada.entradas()) {
            List<?> clave = (List<?>) grupo.getKey();
            Object cuadId = clave.get(1);
            List<Map<String, Object>> acts = grupo.getValue();
            Map<String, Object> cuadrilla = cuadrillas.get(Py.k(cuadId));
            Object[] tiempoHoras = tiempoYHoras(acts, participantes);
            Map<String, Object> total = new LinkedHashMap<>();
            total.put("jornada", clave.get(0));
            total.put("cuadrilla_id", cuadId);
            total.put("cuadrilla_nombre", Py.truthy(cuadrilla) ? cuadrilla.get("nombre") : null);
            total.put("numero_actividades", (long) acts.size());
            total.put("numero_operarios", (long) operariosDe(acts, participantes).size());
            total.put("tiempo_minutos", tiempoHoras[0]);
            total.put("horas_persona", tiempoHoras[1]);
            total.put("superficie_m2", sumaONulo(acts, "superficie_m2"));
            totales.add(total);
        }
        totales.sort((a, b) -> Py.compare(
            Arrays.asList(a.get("jornada"), Py.or(a.get("cuadrilla_nombre"), "")),
            Arrays.asList(b.get("jornada"), Py.or(b.get("cuadrilla_nombre"), ""))));

        // --- Resumen ------------------------------------------------------------
        Object horasTotales = tiempoYHoras(actividades, participantes)[1];
        Set<LocalDate> jornadasPartes = new LinkedHashSet<>();
        for (Map<String, Object> p : partes) jornadasPartes.add(Fechas.aFecha(Py.item(p, "fecha")));
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("jornadas_con_parte", (long) jornadasPartes.size());
        resumen.put("partes", (long) partes.size());
        resumen.put("actividades", (long) actividades.size());
        resumen.put("operarios_distintos", (long) operariosDe(actividades, participantes).size());
        resumen.put("horas_persona", horasTotales);
        resumen.put("actividades_sin_tiempo", actividades.stream().filter(a -> a.get("tiempo_minutos") == null).count());

        return resultado(periodo, filtrosAplicados, false, null, filas, totales, cuadrillasSinParte, resumen);
    }

    private static List<Object> claveFila(Map<String, Object> f) {
        return Arrays.asList(
            f.get("jornada"),
            Py.or(f.get("cuadrilla_nombre"), ""),
            Py.or(f.get("clase_actividad_nombre"), ""),
            Py.or(f.get("tipo_actividad_nombre"), ""),
            Py.or(f.get("area_verde_nombre"), ""));
    }

    private Map<String, Object> resultadoVacio(Periodo periodo, Map<String, Object> filtrosAplicados, String mensaje,
                                               List<Map<String, Object>> cuadrillasSinParte) {
        return resultado(periodo, filtrosAplicados, true, mensaje, List.of(), List.of(), cuadrillasSinParte, null);
    }

    private Map<String, Object> resultado(Periodo periodo, Map<String, Object> filtrosAplicados, boolean sinDatos,
                                          String mensaje, List<Map<String, Object>> filas,
                                          List<Map<String, Object>> totales, List<Map<String, Object>> cuadrillasSinParte,
                                          Map<String, Object> resumen) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("periodo", periodo.comoDict());
        r.put("filtros_aplicados", filtrosAplicados);
        r.put("generado_en", Fechas.ahoraIso(reloj));
        r.put("sin_datos", sinDatos);
        r.put("mensaje_sin_datos", mensaje);
        r.put("filas", filas);
        r.put("totales_por_cuadrilla_jornada", totales);
        r.put("cuadrillas_sin_parte", cuadrillasSinParte);
        r.put("resumen", resumen);
        return r;
    }

    // --- Utilidades ---------------------------------------------------------------

    /** {@code _unicos(op for a in acts for op in participantes.get(a["id"], []))}. */
    private static List<Object> operariosDe(List<Map<String, Object>> acts, Map<Object, List<Object>> participantes) {
        List<Object> vistos = new ArrayList<>();
        for (Map<String, Object> a : acts) {
            for (Object op : participantes.getOrDefault(Py.k(Py.item(a, "id")), List.of())) {
                if (!Py.en(op, vistos)) vistos.add(op);
            }
        }
        return vistos;
    }

    /** {@code true} si el operario tiene una AsignacionCuadrilla vigente en esa cuadrilla en esa fecha (RI-04, RI-21). */
    private static boolean asignacionVigente(List<Map<String, Object>> asignaciones, Object operarioId, Object cuadrillaId,
                                             LocalDate fecha) {
        for (Map<String, Object> asignacion : asignaciones) {
            if (!Py.eq(asignacion.get("operario_id"), operarioId) || !Py.eq(asignacion.get("cuadrilla_id"), cuadrillaId)) {
                continue;
            }
            LocalDate inicio = Fechas.aFecha(asignacion.get("fecha_inicio"));
            LocalDate fin = Fechas.aFecha(asignacion.get("fecha_fin"));
            if (inicio != null && !inicio.isAfter(fecha) && (fin == null || !fin.isBefore(fecha))) return true;
        }
        return false;
    }

    /** Suma de {@code tiempo_minutos} y horas-persona (regla 4). Ambos nulos si ninguna actividad tiene tiempo. */
    private static Object[] tiempoYHoras(List<Map<String, Object>> actividades, Map<Object, List<Object>> participantes) {
        List<Map<String, Object>> conTiempo = new ArrayList<>();
        for (Map<String, Object> a : actividades) if (a.get("tiempo_minutos") != null) conTiempo.add(a);
        if (conTiempo.isEmpty()) return new Object[] {null, null};
        List<Object> tiempos = new ArrayList<>();
        List<Object> minutosPersona = new ArrayList<>();
        for (Map<String, Object> a : conTiempo) {
            tiempos.add(Py.item(a, "tiempo_minutos"));
            long n = participantes.getOrDefault(Py.k(Py.item(a, "id")), List.of()).size();
            minutosPersona.add(Py.mul(Py.item(a, "tiempo_minutos"), n));
        }
        Object tiempo = Py.sum(tiempos);
        double horas = Py.round(Py.truediv(Py.sum(minutosPersona), 60L), 2);
        return new Object[] {tiempo, horas};
    }

    private static Object sumaONulo(List<Map<String, Object>> acts, String campo) {
        List<Object> presentes = new ArrayList<>();
        for (Map<String, Object> a : acts) {
            Object v = a.get(campo);
            if (v != null) presentes.add(v);
        }
        return presentes.isEmpty() ? null : Py.sum(presentes);
    }

    /** Regla 7: cuadrillas activas sin parte en jornadas donde existe al menos un parte. */
    private static List<Map<String, Object>> cuadrillasSinParte(List<Map<String, Object>> partesPeriodo, List<LocalDate> jornadas,
                                                                Map<Object, Map<String, Object>> cuadrillas, Object cuadrillaId) {
        Set<Object> conParte = new LinkedHashSet<>();
        for (Map<String, Object> p : partesPeriodo) conParte.add(Py.tupla(Fechas.aFecha(Py.item(p, "fecha")), p.get("cuadrilla_id")));
        List<Map<String, Object>> candidatas = new ArrayList<>();
        for (Map<String, Object> c : cuadrillas.values()) {
            if (Boolean.TRUE.equals(c.get("activa")) && (cuadrillaId == null || Py.eq(Py.item(c, "id"), cuadrillaId))) {
                candidatas.add(c);
            }
        }
        candidatas.sort((a, b) -> Py.compare(
            Arrays.asList(Py.or(a.get("nombre"), ""), Py.item(a, "id")),
            Arrays.asList(Py.or(b.get("nombre"), ""), Py.item(b, "id"))));
        List<Map<String, Object>> r = new ArrayList<>();
        for (LocalDate jornada : jornadas) {
            for (Map<String, Object> c : candidatas) {
                if (conParte.contains(Py.tupla(jornada, Py.item(c, "id")))) continue;
                Map<String, Object> sinParte = new LinkedHashMap<>();
                sinParte.put("jornada", jornada);
                sinParte.put("cuadrilla_id", Py.item(c, "id"));
                sinParte.put("cuadrilla_nombre", c.get("nombre"));
                sinParte.put("estado", ESTADO_SIN_PARTE);
                r.add(sinParte);
            }
        }
        return r;
    }
}
