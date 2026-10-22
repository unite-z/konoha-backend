package pe.edu.pucp.areasverdes.mod06.servicios;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
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
 * Servicio de RF-REP-006: atenciones por espacio y sector (agrupador principal: sector).
 *
 * <p>Trazabilidad: RF-REP-006 · HU-MOD06-014 · CU-MOD06-014 · CP-TR-MOD06-014 · CU-MOD06-001 (actualización v2: el
 * sector es el agrupador principal; el cuartel, solo si está registrado) · RS-002, RN-005.
 *
 * <p>Texto del requisito (Catálogo v2): «El sistema deberá mostrar el número de atenciones realizadas por espacio y
 * por sector (y por cuartel cuando esté registrado) en un periodo, para ayudar a priorizar espacios».
 *
 * <p>Fuentes de «atención» y regla anti doble conteo [Definición acordada 20/10]. Cada evento cuenta una sola vez:
 * <pre>
 * | Origen                  | Cuenta si                                         | Fecha                               | Espacio                                     | Modalidad                                            | Tipo                                          |
 * |-------------------------|---------------------------------------------------|-------------------------------------|---------------------------------------------|------------------------------------------------------|-----------------------------------------------|
 * | Actividad (MOD-04)      | siempre                                           | fecha local de fecha_hora_actividad | area_verde_id                               | PersonalPropio                                       | ClaseActividad.nombre                         |
 * | AtencionTercerizada     | estado ejecutado (ver ESTADOS_ATENCION_EJECUTADA) | fecha local de inicio (o de fin)    | AlcanceAtencion.area_verde_id o Ejemplar    | Tercerizado                                          | TipoServicio.nombre                           |
 * | AplicacionFitosanitaria | atencion_id nulo                                  | fecha                               | AplicacionObjetivo.area_verde_id o Ejemplar | TipoServicio.ejecutor o Tercerizado (RS-002)         | TipoServicio.nombre o «Control fitosanitario» |
 * | IntervencionArbolado    | actividad_id y atencion_id nulos, fecha_ejecucion | fecha_ejecucion                     | Ejemplar.area_verde_id                      | Menor → PersonalPropio; otras → Tercerizado (RN-005) | «Poda (&lt;categoria&gt;)»                    |
 * </pre>
 *
 * <p>Cada par (evento, área) cuenta 1: una atención con varias áreas suma 1 en cada área, y dos filas de alcance de la
 * misma atención que apuntan a la misma área cuentan 1.
 *
 * <p>Agrupación: sector (AreaVerde.sector_id; «Sin sector» si es nulo, HU-MOD06-014 y RF-INV-015) → espacio.
 * Los eventos cuyo espacio no se puede resolver van aparte en {@code sin_espacio} y no se reparten entre sectores.
 * El cuartel es secundario: solo áreas con {@code cuartel_id} registrado; no sustituye al sector.
 *
 * <p>Limitación conocida: la agrupación usa el sector vigente del área; el modelo de datos no guarda el historial de
 * área a sector (Modelo de BD v1, decisión 8).
 */
@Service
public class AtencionesEspacioSectorService {

    public static final String CODIGO_INDICADOR = "RF-REP-006";
    public static final String MENSAJE_SIN_DATOS = "No hay atenciones realizadas en el periodo elegido";
    public static final String NOTA_LIMITACION_SECTOR =
        "La agrupación usa el sector vigente del área; el modelo de datos no guarda el historial de área a sector "
            + "(Modelo de BD v1, decisión 8)";

    public static final String PERSONAL_PROPIO = "PersonalPropio";
    public static final String TERCERIZADO = "Tercerizado";
    public static final List<String> MODALIDADES = List.of(PERSONAL_PROPIO, TERCERIZADO);
    public static final String SIN_SECTOR = "sin_sector";
    public static final String NOMBRE_GRUPO_SIN_SECTOR = "Sin sector";
    public static final String ORDEN_ASC = "asc";
    public static final String ORDEN_DESC = "desc";

    public static final Set<String> ESTADOS_ATENCION_EJECUTADA =
        Set.of("Ejecutado pendiente de reporte", "Reporte adjunto", "Observado", "Conforme / cerrado");
    public static final String TIPO_FITOSANITARIO_POR_DEFECTO = "Control fitosanitario";
    public static final Map<String, String> MODALIDAD_PODA =
        Map.of("Menor", PERSONAL_PROPIO, "MayorCampana", TERCERIZADO, "AlturaDemanda", TERCERIZADO);

    /**
     * Un par (evento, área) que cuenta como una atención.
     *
     * @param origen        entidad de origen
     * @param origen_id     id del registro de origen
     * @param fecha         fecha local del evento
     * @param area_verde_id espacio (o {@code null} si no se pudo resolver)
     * @param modalidad     PersonalPropio | Tercerizado
     * @param tipo_atencion tipo de atención
     */
    public record Evento(String origen, Object origen_id, LocalDate fecha, Object area_verde_id, Object modalidad,
                         Object tipo_atencion) {}

    private final SelectorFuenteDatos selector;
    private final Clock reloj;

    public AtencionesEspacioSectorService(SelectorFuenteDatos selector, Clock reloj) {
        this.selector = selector;
        this.reloj = reloj;
    }

    /**
     * Cuenta las atenciones realizadas en {@code periodo} por sector, espacio y cuartel.
     *
     * <p>Filtros opcionales: {@code sector_id} (entero o "sin_sector"), {@code modalidad} ("PersonalPropio" |
     * "Tercerizado"), {@code area_verde_id} (entero) y {@code orden} ("asc" | "desc"; por defecto "desc").
     * RF-REP-006 · HU-MOD06-014 · CP-TR-MOD06-014. Solo lectura.
     */
    public Map<String, Object> calcular(Periodo periodo, Map<String, Object> filtros) {
        return calcular(periodo, null, filtros);
    }

    public Map<String, Object> calcular(Periodo periodo, FuenteDatos fuente, Map<String, Object> filtros) {
        FuenteDatos f = fuente != null ? fuente : selector.obtenerFuenteDatos();
        Object sectorId = filtros.get("sector_id");
        Object modalidad = filtros.get("modalidad");
        Object areaVerdeId = filtros.get("area_verde_id");
        Object orden = Py.or(filtros.get("orden"), ORDEN_DESC);
        Map<String, Object> filtrosAplicados = Comunes.filtrosAplicados(filtros);

        Map<String, Object> indicador = Comunes.indicadorPorCodigo(f, CODIGO_INDICADOR);
        Map<Object, Map<String, Object>> areas = Py.indexar(f.listar("AreaVerde"), "id");

        List<Evento> eventos = new ArrayList<>();
        for (Evento e : recolectarEventos(f, periodo)) {
            if (pasaFiltros(e, areas, sectorId, modalidad, areaVerdeId)) eventos.add(e);
        }

        if (eventos.isEmpty()) {
            return resultado(periodo, filtrosAplicados, true, MENSAJE_SIN_DATOS, indicador, List.of(), null, List.of(), null);
        }

        Map<Object, Map<String, Object>> sectores = Py.indexar(f.listar("SectorMantenimiento"), "id");
        Map<Object, Map<String, Object>> cuarteles = Py.indexar(f.listar("Cuartel"), "id");
        Map<Object, Map<String, Object>> personal = Py.indexar(f.listar("Personal"), "id");
        List<Map<String, Object>> asignacionesSector = f.listar("AsignacionSector");

        List<Evento> conEspacio = new ArrayList<>();
        List<Evento> sinEspacio = new ArrayList<>();
        for (Evento e : eventos) {
            if (areas.containsKey(Py.k(e.area_verde_id()))) conEspacio.add(e);
            else sinEspacio.add(e);
        }

        // --- Sector → espacio ---------------------------------------------------
        Comunes.Grupos<Evento> porSectorEventos = new Comunes.Grupos<>();
        for (Evento e : conEspacio) porSectorEventos.agregar(areas.get(Py.k(e.area_verde_id())).get("sector_id"), e);

        List<Map<String, Object>> gruposSector = new ArrayList<>();
        for (Map.Entry<Object, List<Evento>> entrada : porSectorEventos.entradas()) {
            Object sid = entrada.getKey();
            List<Evento> eventosSector = entrada.getValue();
            Comunes.Grupos<Evento> porArea = new Comunes.Grupos<>();
            for (Evento e : eventosSector) porArea.agregar(e.area_verde_id(), e);
            List<Map<String, Object>> espacios = new ArrayList<>();
            for (Map.Entry<Object, List<Evento>> area : porArea.entradas()) {
                Map<String, Object> espacio = new LinkedHashMap<>();
                espacio.put("area_verde_id", area.getKey());
                espacio.put("area_verde_nombre", areas.get(Py.k(area.getKey())).get("nombre"));
                espacio.putAll(medidas(area.getValue()));
                espacios.add(espacio);
            }
            espacios.sort((a, b) -> Py.compare(claveOrden(a.get("total"), a.get("area_verde_nombre"), orden),
                claveOrden(b.get("total"), b.get("area_verde_nombre"), orden)));
            Map<String, Object> sector = sid != null ? sectores.get(Py.k(sid)) : null;
            Object sectorNombre;
            if (sid == null) sectorNombre = NOMBRE_GRUPO_SIN_SECTOR;
            else sectorNombre = Py.truthy(sector) ? sector.get("nombre") : null;
            Map<String, Object> grupo = new LinkedHashMap<>();
            grupo.put("sector_id", sid);
            grupo.put("sector_nombre", sectorNombre);
            grupo.put("atencion_a_demanda", Py.truthy(sector) ? sector.get("atencion_a_demanda") : null);
            grupo.put("capataz_vigente", capatazVigente(sid, asignacionesSector, personal));
            grupo.putAll(medidas(eventosSector));
            grupo.put("espacios", espacios);
            gruposSector.add(grupo);
        }
        List<Map<String, Object>> conSector = new ArrayList<>();
        List<Map<String, Object>> gruposSinSector = new ArrayList<>();
        for (Map<String, Object> g : gruposSector) {
            if (g.get("sector_id") != null) conSector.add(g);
        }
        conSector.sort((a, b) -> Py.compare(claveOrden(a.get("total"), a.get("sector_nombre"), orden),
            claveOrden(b.get("total"), b.get("sector_nombre"), orden)));
        for (Map<String, Object> g : gruposSector) {
            if (g.get("sector_id") == null) gruposSinSector.add(g);
        }

        // --- Cuartel (secundario) -----------------------------------------------
        Comunes.Grupos<Evento> porCuartelEventos = new Comunes.Grupos<>();
        for (Evento e : conEspacio) {
            Object cuartelId = areas.get(Py.k(e.area_verde_id())).get("cuartel_id");
            if (cuartelId != null) porCuartelEventos.agregar(cuartelId, e);
        }
        List<Map<String, Object>> gruposCuartel = new ArrayList<>();
        for (Map.Entry<Object, List<Evento>> entrada : porCuartelEventos.entradas()) {
            Object cid = entrada.getKey();
            Map<String, Object> cuartel = cuarteles.get(Py.k(cid));
            if (!Py.truthy(cuartel)) cuartel = Map.of();
            Map<String, Object> m = medidas(entrada.getValue());
            Map<String, Object> grupo = new LinkedHashMap<>();
            grupo.put("cuartel_id", cid);
            grupo.put("numero", cuartel.get("numero"));
            grupo.put("nombre", cuartel.get("nombre"));
            grupo.put("total", m.get("total"));
            grupo.put("personal_propio", m.get("personal_propio"));
            grupo.put("tercerizado", m.get("tercerizado"));
            gruposCuartel.add(grupo);
        }
        gruposCuartel.sort((a, b) -> Py.compare(
            Arrays.asList(a.get("numero") != null ? a.get("numero") : 0L, a.get("cuartel_id")),
            Arrays.asList(b.get("numero") != null ? b.get("numero") : 0L, b.get("cuartel_id"))));

        Map<String, Object> total = medidas(eventos);
        Set<Object> espaciosDistintos = new LinkedHashSet<>();
        for (Evento e : conEspacio) espaciosDistintos.add(Py.k(e.area_verde_id()));
        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("total_atenciones", total.get("total"));
        resumen.put("personal_propio", total.get("personal_propio"));
        resumen.put("tercerizado", total.get("tercerizado"));
        resumen.put("espacios_con_atenciones", (long) espaciosDistintos.size());
        resumen.put("sectores_con_atenciones", (long) conSector.size());

        List<Map<String, Object>> porSector = new ArrayList<>(conSector);
        porSector.addAll(gruposSinSector);
        return resultado(periodo, filtrosAplicados, false, null, indicador, porSector, medidas(sinEspacio), gruposCuartel, resumen);
    }

    private Map<String, Object> resultado(Periodo periodo, Map<String, Object> filtrosAplicados, boolean sinDatos,
                                          String mensaje, Map<String, Object> indicador, List<Map<String, Object>> porSector,
                                          Map<String, Object> sinEspacio, List<Map<String, Object>> porCuartel,
                                          Map<String, Object> resumen) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("periodo", periodo.comoDict());
        r.put("filtros_aplicados", filtrosAplicados);
        r.put("generado_en", Fechas.ahoraIso(reloj));
        r.put("sin_datos", sinDatos);
        r.put("mensaje_sin_datos", mensaje);
        r.put("indicador", indicador);
        r.put("por_sector", porSector);
        r.put("sin_espacio", sinEspacio);
        r.put("por_cuartel", porCuartel);
        r.put("resumen", resumen);
        r.put("notas", List.of(NOTA_LIMITACION_SECTOR));
        return r;
    }

    // --- Recolección de eventos (regla anti doble conteo) -------------------------

    /** Pares (evento, área) del periodo, cada uno una sola vez. */
    public List<Evento> recolectarEventos(FuenteDatos fuente, Periodo periodo) {
        Map<Object, Map<String, Object>> ejemplares = Py.indexar(fuente.listar("Ejemplar"), "id");
        Map<Object, Map<String, Object>> tiposServicio = Py.indexar(fuente.listar("TipoServicio"), "id");
        Map<Object, Map<String, Object>> programaciones = Py.indexar(fuente.listar("ProgramacionServicio"), "id");
        Map<Object, Map<String, Object>> tiposActividad = Py.indexar(fuente.listar("TipoActividad"), "id");
        Map<Object, Map<String, Object>> clases = Py.indexar(fuente.listar("ClaseActividad"), "id");

        Map<Object, Evento> eventos = new LinkedHashMap<>();

        // 1. Actividad (personal propio, MOD-04)
        for (Map<String, Object> actividad : fuente.listar("Actividad")) {
            ZonedDateTime instante = Fechas.aFechaHora(actividad.get("fecha_hora_actividad"));
            Map<String, Object> tipo = tiposActividad.get(Py.k(actividad.get("tipo_actividad_id")));
            Map<String, Object> clase = Py.truthy(tipo) ? clases.get(Py.k(tipo.get("clase_id"))) : null;
            agregar(eventos, periodo, "Actividad", Py.item(actividad, "id"),
                instante != null ? Fechas.fechaLocal(instante) : null,
                Arrays.asList(actividad.get("area_verde_id")),
                PERSONAL_PROPIO,
                Py.or(Py.truthy(clase) ? clase.get("nombre") : null, "Sin clase de actividad"));
        }

        // 2. AtencionTercerizada + AlcanceAtencion
        Comunes.Grupos<Map<String, Object>> alcances = new Comunes.Grupos<>();
        for (Map<String, Object> alcance : fuente.listar("AlcanceAtencion")) alcances.agregar(alcance.get("atencion_id"), alcance);
        for (Map<String, Object> atencion : fuente.listar("AtencionTercerizada")) {
            Object estado = atencion.get("estado");
            if (!(estado instanceof String s && ESTADOS_ATENCION_EJECUTADA.contains(s))) continue;
            ZonedDateTime instante = Fechas.aFechaHora(atencion.get("fecha_hora_inicio"));
            if (instante == null) instante = Fechas.aFechaHora(atencion.get("fecha_hora_fin"));
            if (instante == null) continue;
            Map<String, Object> tipo = tiposServicio.get(Py.k(atencion.get("tipo_servicio_id")));
            List<Object> areasEvento = new ArrayList<>();
            List<Map<String, Object>> deAtencion = alcances.get(Py.item(atencion, "id"));
            if (deAtencion != null) {
                for (Map<String, Object> a : deAtencion) areasEvento.add(areaDe(ejemplares, a.get("area_verde_id"), a.get("ejemplar_id")));
            }
            agregar(eventos, periodo, "AtencionTercerizada", Py.item(atencion, "id"), Fechas.fechaLocal(instante), areasEvento,
                TERCERIZADO, Py.or(Py.truthy(tipo) ? tipo.get("nombre") : null, "Sin tipo de servicio"));
        }

        // 3. AplicacionFitosanitaria sin atención + AplicacionObjetivo
        Comunes.Grupos<Map<String, Object>> objetivos = new Comunes.Grupos<>();
        for (Map<String, Object> objetivo : fuente.listar("AplicacionObjetivo")) objetivos.agregar(objetivo.get("aplicacion_id"), objetivo);
        for (Map<String, Object> aplicacion : fuente.listar("AplicacionFitosanitaria")) {
            if (aplicacion.get("atencion_id") != null) continue; // ya se contó por su atención
            Map<String, Object> programacion = programaciones.get(Py.k(aplicacion.get("programacion_id")));
            Map<String, Object> tipo = Py.truthy(programacion) ? tiposServicio.get(Py.k(programacion.get("tipo_servicio_id"))) : null;
            List<Object> areasEvento = new ArrayList<>();
            List<Map<String, Object>> deAplicacion = objetivos.get(Py.item(aplicacion, "id"));
            if (deAplicacion != null) {
                for (Map<String, Object> o : deAplicacion) areasEvento.add(areaDe(ejemplares, o.get("area_verde_id"), o.get("ejemplar_id")));
            }
            agregar(eventos, periodo, "AplicacionFitosanitaria", Py.item(aplicacion, "id"), Fechas.aFecha(aplicacion.get("fecha")),
                areasEvento,
                Py.or(Py.truthy(tipo) ? tipo.get("ejecutor") : null, TERCERIZADO),
                Py.or(Py.truthy(tipo) ? tipo.get("nombre") : null, TIPO_FITOSANITARIO_POR_DEFECTO));
        }

        // 4. IntervencionArbolado no contada por su origen
        for (Map<String, Object> intervencion : fuente.listar("IntervencionArbolado")) {
            if (intervencion.get("actividad_id") != null || intervencion.get("atencion_id") != null) continue;
            LocalDate fecha = Fechas.aFecha(intervencion.get("fecha_ejecucion"));
            if (fecha == null) continue;
            Object categoria = intervencion.get("categoria");
            if (!(categoria instanceof String c && MODALIDAD_PODA.containsKey(c))) continue;
            agregar(eventos, periodo, "IntervencionArbolado", Py.item(intervencion, "id"), fecha,
                Arrays.asList(areaDe(ejemplares, null, intervencion.get("ejemplar_id"))),
                MODALIDAD_PODA.get(c), "Poda (" + c + ")");
        }

        return new ArrayList<>(eventos.values());
    }

    private static Object areaDe(Map<Object, Map<String, Object>> ejemplares, Object areaVerdeId, Object ejemplarId) {
        if (areaVerdeId != null) return areaVerdeId;
        Map<String, Object> ejemplar = ejemplares.get(Py.k(ejemplarId));
        return Py.truthy(ejemplar) ? ejemplar.get("area_verde_id") : null;
    }

    private static void agregar(Map<Object, Evento> eventos, Periodo periodo, String origen, Object origenId, LocalDate fecha,
                                List<Object> areasEvento, Object modalidad, Object tipo) {
        if (!periodo.contiene(fecha)) return;
        List<Object> areas = areasEvento.isEmpty() ? Arrays.asList((Object) null) : areasEvento;
        for (Object areaId : areas) {
            Object clave = Py.tupla(origen, origenId, areaId);
            eventos.putIfAbsent(clave, new Evento(origen, origenId, fecha, areaId, modalidad, tipo));
        }
    }

    // --- Utilidades ---------------------------------------------------------------

    private static boolean pasaFiltros(Evento evento, Map<Object, Map<String, Object>> areas, Object sectorId, Object modalidad,
                                       Object areaVerdeId) {
        if (modalidad != null && !Py.eq(evento.modalidad(), modalidad)) return false;
        if (areaVerdeId != null && !Py.eq(evento.area_verde_id(), areaVerdeId)) return false;
        if (sectorId != null) {
            Map<String, Object> area = areas.get(Py.k(evento.area_verde_id()));
            if (area == null) return false; // sin espacio: no pertenece a ningún sector
            if (Py.eq(sectorId, SIN_SECTOR)) return area.get("sector_id") == null;
            return Py.eq(area.get("sector_id"), sectorId);
        }
        return true;
    }

    /** {@code {total, personal_propio, tercerizado, por_tipo}} de un grupo de eventos. */
    private static Map<String, Object> medidas(List<Evento> eventos) {
        Map<Object, Object[]> porTipo = new LinkedHashMap<>();
        long propio = 0, tercerizado = 0;
        for (Evento e : eventos) {
            porTipo.computeIfAbsent(Py.k(e.tipo_atencion()), k -> new Object[] {e.tipo_atencion(), 0L});
            Object[] par = porTipo.get(Py.k(e.tipo_atencion()));
            par[1] = (Long) par[1] + 1;
            if (Py.eq(e.modalidad(), PERSONAL_PROPIO)) propio++;
            if (Py.eq(e.modalidad(), TERCERIZADO)) tercerizado++;
        }
        List<Object[]> ordenados = new ArrayList<>(porTipo.values());
        ordenados.sort((a, b) -> Py.compare(Arrays.asList(a), Arrays.asList(b)));
        Map<Object, Object> porTipoOrdenado = new LinkedHashMap<>();
        for (Object[] par : ordenados) porTipoOrdenado.put(par[0], par[1]);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", (long) eventos.size());
        m.put("personal_propio", propio);
        m.put("tercerizado", tercerizado);
        m.put("por_tipo", porTipoOrdenado);
        return m;
    }

    /** Orden por total (asc o desc) y, en empate, por nombre ascendente. */
    private static List<Object> claveOrden(Object total, Object nombre, Object orden) {
        long t = (Long) total;
        return Arrays.asList(Py.eq(orden, ORDEN_ASC) ? t : -t, Py.or(nombre, ""));
    }

    /** AsignacionSector con fecha_fin nula (RI-06: como máximo una). Si hubiera varias, la más reciente. */
    private static Map<String, Object> capatazVigente(Object sectorId, List<Map<String, Object>> asignaciones,
                                                      Map<Object, Map<String, Object>> personal) {
        if (sectorId == null) return null;
        Map<String, Object> vigente = null;
        List<Object> claveVigente = null;
        for (Map<String, Object> a : asignaciones) {
            if (!Py.eq(a.get("sector_id"), sectorId) || a.get("fecha_fin") != null) continue;
            LocalDate inicio = Fechas.aFecha(a.get("fecha_inicio"));
            List<Object> clave = Arrays.asList(inicio != null ? inicio : LocalDate.of(1, 1, 1), Py.or(a.get("id"), 0L));
            if (vigente == null || Py.compare(clave, claveVigente) > 0) {
                vigente = a;
                claveVigente = clave;
            }
        }
        if (vigente == null) return null;
        Object capatazId = vigente.get("capataz_id");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", capatazId);
        r.put("nombre", Comunes.nombreCompleto(personal.get(Py.k(capatazId))));
        return r;
    }
}
