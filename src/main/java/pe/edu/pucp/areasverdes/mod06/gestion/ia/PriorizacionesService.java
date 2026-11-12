package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;
import pe.edu.pucp.areasverdes.mod06.ia.FabricaClienteIA;

/**
 * RN-IA-002: la IA sugiere qué áreas, jardines o ejemplares priorizar según su historial de
 * atenciones, incidencias y frecuencia de mantenimiento. Validar solo registra la decisión: que
 * MOD-03 use las prioridades al programar es trabajo de su dueño.
 *
 * <p>El backend calcula el sustento de cada elemento con SQL (nunca lo inventa la IA) y la IA
 * asigna prioridad, puntuación y motivos. Un dato que no existe va a {@code null} y a {@code
 * faltantes}, nunca a 0. Se envían a la IA los {@value #MAXIMO_ELEMENTOS} elementos con más
 * señales; el resto queda fuera y se avisa en {@code limitaciones}.
 */
@Service
public class PriorizacionesService {

    public static final String TIPO = "Priorizacion";
    static final int MAXIMO_ELEMENTOS = 40;
    static final String RUTA_INSTRUCCION = "prompts/priorizacion-atenciones.md";
    static final List<String> PRIORIDADES = List.of("Alta", "Media", "Baja");
    static final List<Integer> HORIZONTES = List.of(7, 15, 30);

    private static final Map<String, Object> ESQUEMA =
            esquema(
                    Map.of(
                            "elementos",
                            Map.of(
                                    "type",
                                    "array",
                                    "items",
                                    esquemaObjeto(
                                            Map.of(
                                                    "clave", Map.of("type", "string"),
                                                    "prioridad_sugerida",
                                                            Map.of(
                                                                    "type",
                                                                    "string",
                                                                    "enum",
                                                                    PRIORIDADES),
                                                    "puntuacion", Map.of("type", "integer"),
                                                    "motivos",
                                                            Map.of(
                                                                    "type",
                                                                    "array",
                                                                    "items",
                                                                    Map.of("type", "string"))))),
                            "explicacion",
                            Map.of("type", "string"),
                            "limitaciones",
                            Map.of("type", "array", "items", Map.of("type", "string"))));

    private final SugerenciasIa sugerencias;
    private final FabricaClienteIA fabrica;
    private final NamedParameterJdbcTemplate jdbc;

    public PriorizacionesService(
            SugerenciasIa sugerencias, FabricaClienteIA fabrica, NamedParameterJdbcTemplate jdbc) {
        this.sugerencias = sugerencias;
        this.fabrica = fabrica;
        this.jdbc = jdbc;
    }

    public Map<String, Object> solicitar(Map<String, Object> cuerpo, long usuario) {
        String alcance = cuerpo.get("alcance") instanceof String s ? s : null;
        if (alcance == null || !List.of("Areas", "Ejemplares", "Ambos").contains(alcance)) {
            throw ErrorGestion.validacion(
                    "alcance", "Usa uno de estos valores: Areas, Ejemplares, Ambos");
        }
        Integer horizonte = cuerpo.get("horizonte_dias") instanceof Number n ? n.intValue() : null;
        if (horizonte == null || !HORIZONTES.contains(horizonte)) {
            throw ErrorGestion.validacion("horizonte_dias", "Usa uno de estos valores: 7, 15, 30");
        }
        Long area = id(cuerpo.get("area_verde_id"), "area_verde_id");
        Long tipoServicio = id(cuerpo.get("tipo_servicio_id"), "tipo_servicio_id");
        LocalDate hoy = Fechas.hoy(sugerencias.reloj());

        // El cliente se obtiene antes de leer: si la IA no está configurada, 503 inmediato.
        ClienteIA cliente = fabrica.obtenerClienteIa();
        Integer frecuencia = tipoServicio == null ? null : frecuencia(tipoServicio);
        List<Map<String, Object>> candidatos = new ArrayList<>();
        if (!alcance.equals("Ejemplares")) candidatos.addAll(areas(area, hoy, frecuencia));
        if (!alcance.equals("Areas")) candidatos.addAll(ejemplares(area, hoy, frecuencia));
        candidatos.sort(Comparator.comparingInt(PriorizacionesService::senales).reversed());
        List<Map<String, Object>> enviados =
                candidatos.subList(0, Math.min(MAXIMO_ELEMENTOS, candidatos.size()));

        List<String> faltantesGenerales = new ArrayList<>();
        if (tipoServicio == null) {
            faltantesGenerales.add(
                    "Sin tipo de servicio: no se compara con su frecuencia de mantenimiento");
        } else if (frecuencia == null) {
            faltantesGenerales.add(
                    "El tipo de servicio no tiene frecuencia de mantenimiento configurada");
        }
        List<String> limitaciones = new ArrayList<>();
        if (candidatos.size() > enviados.size()) {
            limitaciones.add(
                    "Se evaluaron los %d elementos con más señales de %d"
                            .formatted(enviados.size(), candidatos.size()));
        }

        Map<String, Object> salida = Map.of();
        String solicitadoEn = sugerencias.ahora();
        long inicio = System.nanoTime();
        if (!enviados.isEmpty()) {
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("fecha_referencia", hoy.toString());
            datos.put("horizonte_dias", horizonte);
            datos.put("elementos", enviados);
            salida =
                    cliente.generarJson(
                            JsonPy.dumpsRespuesta(datos), List.of(), ESQUEMA, instruccion());
        }
        long duracion = (System.nanoTime() - inicio) / 1_000_000;

        Map<String, Map<String, Object>> valoradas = new LinkedHashMap<>();
        if (salida.get("elementos") instanceof List<?> lista) {
            for (Object o : lista) {
                if (o instanceof Map<?, ?> m && m.get("clave") instanceof String clave) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> v = (Map<String, Object>) m;
                    valoradas.put(clave, v);
                }
            }
        }
        List<Map<String, Object>> elementos = new ArrayList<>();
        for (Map<String, Object> e : enviados) {
            Map<String, Object> v = valoradas.get(e.get("clave"));
            Map<String, Object> elemento = new LinkedHashMap<>(e);
            String prioridad =
                    v != null
                                    && v.get("prioridad_sugerida") instanceof String ps
                                    && PRIORIDADES.contains(ps)
                            ? (String) v.get("prioridad_sugerida")
                            : prioridadPorSenales(e);
            elemento.put("prioridad_sugerida", prioridad);
            elemento.put(
                    "puntuacion",
                    v != null && v.get("puntuacion") instanceof Number n
                            ? Math.max(0, Math.min(100, n.intValue()))
                            : Math.min(100, senales(e) * 10));
            elemento.put(
                    "motivos",
                    v != null && v.get("motivos") instanceof List<?> l && !l.isEmpty()
                            ? l
                            : List.of(
                                    "Sin valoración de la IA: prioridad calculada por sus datos"));
            elementos.add(elemento);
        }
        elementos.sort(
                Comparator.comparingInt((Map<String, Object> e) -> (Integer) e.get("puntuacion"))
                        .reversed());
        if (salida.get("limitaciones") instanceof List<?> l)
            l.forEach(x -> limitaciones.add(String.valueOf(x)));

        Map<String, Object> entrada = new LinkedHashMap<>();
        entrada.put("alcance", alcance);
        if (area != null) entrada.put("area_verde_id", area);
        if (tipoServicio != null) entrada.put("tipo_servicio_id", tipoServicio);
        entrada.put("horizonte_dias", horizonte);
        entrada.put("fecha_referencia", hoy.toString());
        entrada.put("elementos_evaluados", elementos.size());

        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("version_contenido", 1);
        contenido.put("ia", sugerencias.llamada(cliente, solicitadoEn, duracion));
        contenido.put(
                "suficiencia",
                elementos.isEmpty()
                        ? "insuficiente"
                        : faltantesGenerales.isEmpty() ? "suficiente" : "parcial");
        contenido.put(
                "faltantes",
                elementos.isEmpty()
                        ? List.of("No hay espacios ni ejemplares activos con esos filtros")
                        : faltantesGenerales);
        contenido.put(
                "datos_utilizados",
                List.of(
                        "Inventario de áreas verdes y ejemplares (MOD-02)",
                        "Incidencias registradas (MOD-03)",
                        "Actividades de personal propio (MOD-04) y atenciones tercerizadas (MOD-05)"
                                + " de los últimos 12 meses",
                        "Frecuencias de mantenimiento configuradas (MOD-03)"));
        contenido.put("limitaciones", limitaciones);
        contenido.put(
                "explicacion",
                salida.get("explicacion") instanceof String x
                        ? x
                        : "La prioridad combina incidencias abiertas, días sin atención y"
                                + " frecuencia vencida.");
        contenido.put("entrada", entrada);
        contenido.put("elementos", elementos);
        contenido.put("decision", null);
        return sugerencias.crear(TIPO, null, null, contenido, usuario);
    }

    public Map<String, Object> validar(long id, Map<String, Object> cuerpo, long usuario) {
        Object decisiones = cuerpo.get("por_elemento");
        if (!(decisiones instanceof Map<?, ?> porElemento)) {
            throw ErrorGestion.validacion("por_elemento", "Indica una decisión por elemento");
        }
        return sugerencias.revisar(
                id,
                TIPO,
                usuario,
                contenido -> {
                    Map<String, List<String>> errores = new LinkedHashMap<>();
                    Map<String, Object> decision = new LinkedHashMap<>();
                    List<String> acciones = new ArrayList<>();
                    for (Object o : (List<?>) contenido.getOrDefault("elementos", List.of())) {
                        Map<?, ?> e = (Map<?, ?>) o;
                        String clave = String.valueOf(e.get("clave"));
                        Object d = porElemento.get(clave);
                        String accion =
                                d instanceof Map<?, ?> m && m.get("accion") instanceof String a
                                        ? a
                                        : null;
                        if (accion == null
                                || !List.of("aceptar", "corregir", "descartar").contains(accion)) {
                            errores.put(clave, List.of("Elige aceptar, corregir o descartar"));
                            continue;
                        }
                        Object prioridad = ((Map<?, ?>) d).get("prioridad_final");
                        if (accion.equals("corregir")
                                && !(prioridad instanceof String p && PRIORIDADES.contains(p))) {
                            errores.put(
                                    clave,
                                    List.of("Indica la prioridad final: Alta, Media o Baja"));
                            continue;
                        }
                        Map<String, Object> final_ = new LinkedHashMap<>();
                        final_.put("accion", accion);
                        final_.put(
                                "prioridad_final",
                                switch (accion) {
                                    case "aceptar" -> e.get("prioridad_sugerida");
                                    case "corregir" -> prioridad;
                                    default -> null;
                                });
                        decision.put(clave, final_);
                        acciones.add(accion);
                    }
                    if (!errores.isEmpty()) {
                        throw new ErrorGestion(
                                400,
                                "decisiones-incompletas",
                                "Decide todos los elementos",
                                errores);
                    }
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("por_elemento", decision);
                    d.put("resumen", SugerenciasIa.resumen(acciones));
                    d.put("motivo_rechazo", null);
                    contenido.put("decision", d);
                    boolean todoDescartado =
                            !acciones.isEmpty() && acciones.stream().allMatch("descartar"::equals);
                    return new SugerenciasIa.Revision(
                            todoDescartado ? SugerenciasIa.RECHAZADA : SugerenciasIa.VALIDADA,
                            contenido,
                            null);
                });
    }

    public Map<String, Object> rechazar(long id, String motivo, long usuario) {
        return sugerencias.rechazar(
                id,
                TIPO,
                usuario,
                motivo,
                contenido -> {
                    Map<String, Object> porElemento = new LinkedHashMap<>();
                    List<String> acciones = new ArrayList<>();
                    for (Object o : (List<?>) contenido.getOrDefault("elementos", List.of())) {
                        porElemento.put(
                                String.valueOf(((Map<?, ?>) o).get("clave")),
                                Map.of("accion", "descartar"));
                        acciones.add("descartar");
                    }
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("por_elemento", porElemento);
                    d.put("resumen", SugerenciasIa.resumen(acciones));
                    d.put("motivo_rechazo", motivo);
                    return d;
                });
    }

    // ------------------------------------------------------------------ datos

    /** Frecuencia práctica más corta del servicio en cualquier estación, en días. */
    private Integer frecuencia(long tipoServicio) {
        return jdbc.queryForObject(
                "SELECT min(frecuencia_practica_dias) FROM mod03_frecuencia_servicio WHERE"
                        + " tipo_servicio_id = :t",
                Map.of("t", tipoServicio),
                Integer.class);
    }

    /** Áreas de inventario; el filtro de espacio es un área de MOD-05, cruzada por nombre. */
    private List<Map<String, Object>> areas(Long espacio, LocalDate hoy, Integer frecuencia) {
        return jdbc.query(
                """
                WITH equivalente AS (
                    SELECT a.id, m.id AS espacio_id FROM mod02_area_verde a
                    LEFT JOIN LATERAL (SELECT min(m.id) AS id FROM mod05_areaverde m
                        WHERE lower(btrim(m.nombre)) = lower(btrim(a.nombre))) m ON true
                    WHERE a.activo
                ), ultimas AS (
                    SELECT e.id, max(x.dia) AS ultima, count(x.dia) FILTER (WHERE x.dia >= :desde) AS atenciones
                    FROM equivalente e
                    LEFT JOIN LATERAL (
                        SELECT (ac.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date AS dia
                        FROM mod04_actividad ac WHERE ac.area_verde_id = e.espacio_id
                          AND ac.estado_actividad <> 'Anulada'
                        UNION ALL
                        SELECT (at.fecha_hora_inicio AT TIME ZONE 'America/Lima')::date
                        FROM mod05_alcanceatencion al JOIN mod05_atenciontercerizada at ON at.id = al.atencion_id
                        WHERE al.area_verde_id = e.espacio_id AND at.fecha_hora_inicio IS NOT NULL) x ON true
                    GROUP BY e.id
                )
                SELECT a.id, a.nombre, NULL::text AS codigo, a.lugar AS espacio, u.ultima, u.atenciones,
                       (SELECT count(*) FROM mod03_incidencia i WHERE i.area_verde_id = a.id
                          AND i.estado NOT IN ('Atendida', 'Cerrada', 'Descartada')) AS abiertas,
                       (SELECT count(*) FROM mod03_incidencia i WHERE i.area_verde_id = a.id
                          AND i.fecha_registro >= :desde) AS recientes
                FROM mod02_area_verde a
                JOIN equivalente e ON e.id = a.id
                JOIN ultimas u ON u.id = a.id
                WHERE CAST(:espacio AS bigint) IS NULL OR e.espacio_id = :espacio
                """,
                parametros(espacio, hoy),
                (rs, n) ->
                        elemento(
                                "area",
                                "Area",
                                rs.getLong("id"),
                                rs.getString("nombre"),
                                rs.getString("codigo"),
                                rs.getString("espacio"),
                                rs.getObject("ultima", LocalDate.class),
                                rs.getInt("atenciones"),
                                rs.getInt("abiertas"),
                                rs.getInt("recientes"),
                                null,
                                hoy,
                                frecuencia));
    }

    private List<Map<String, Object>> ejemplares(Long espacio, LocalDate hoy, Integer frecuencia) {
        return jdbc.query(
                """
                SELECT e.id, COALESCE(s.nombre_comun, s.nombre_cientifico, 'Ejemplar') AS nombre, e.codigo,
                       a.nombre AS espacio,
                       (SELECT max(x.dia) FROM (
                            SELECT (ac.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date AS dia
                            FROM mod04_actividad ac WHERE ac.ejemplar_id = e.id
                              AND ac.estado_actividad <> 'Anulada'
                            UNION ALL
                            SELECT (at.fecha_hora_inicio AT TIME ZONE 'America/Lima')::date
                            FROM mod05_alcanceatencion al JOIN mod05_atenciontercerizada at ON at.id = al.atencion_id
                            WHERE al.ejemplar_id = e.id AND at.fecha_hora_inicio IS NOT NULL) x) AS ultima,
                       (SELECT count(*) FROM mod04_actividad ac WHERE ac.ejemplar_id = e.id
                          AND ac.estado_actividad <> 'Anulada'
                          AND ac.fecha_hora_actividad >= :desde)
                         + (SELECT count(*) FROM mod05_alcanceatencion al JOIN mod05_atenciontercerizada at
                              ON at.id = al.atencion_id WHERE al.ejemplar_id = e.id AND at.fecha_hora_inicio >= :desde)
                           AS atenciones,
                       (SELECT count(*) FROM mod03_incidencia i WHERE i.ejemplar_id = e.id
                          AND i.estado NOT IN ('Atendida', 'Cerrada', 'Descartada')) AS abiertas,
                       (SELECT count(*) FROM mod03_incidencia i WHERE i.ejemplar_id = e.id
                          AND i.fecha_registro >= :desde) AS recientes,
                       CASE WHEN EXISTS (SELECT 1 FROM mod05_observacion_plaga o WHERE o.ejemplar_id = e.id
                          AND o.fecha_observacion >= :desde) THEN 'Con plaga observada en los últimos 12 meses' END
                           AS estado_sanitario
                FROM mod02_ejemplar e
                LEFT JOIN mod02_especie_vegetal s ON s.id = e.especie_id
                LEFT JOIN mod02_area_verde a ON a.id = e.area_verde_inventario_id
                WHERE e.situacion = 'activo'
                  AND (CAST(:espacio AS bigint) IS NULL OR EXISTS (SELECT 1 FROM mod05_areaverde m
                       WHERE m.id = :espacio AND lower(btrim(m.nombre)) = lower(btrim(a.nombre))))
                """,
                parametros(espacio, hoy),
                (rs, n) ->
                        elemento(
                                "ejemplar",
                                "Ejemplar",
                                rs.getLong("id"),
                                rs.getString("nombre"),
                                rs.getString("codigo"),
                                rs.getString("espacio"),
                                rs.getObject("ultima", LocalDate.class),
                                rs.getInt("atenciones"),
                                rs.getInt("abiertas"),
                                rs.getInt("recientes"),
                                rs.getString("estado_sanitario"),
                                hoy,
                                frecuencia));
    }

    private static MapSqlParameterSource parametros(Long espacio, LocalDate hoy) {
        return new MapSqlParameterSource()
                .addValue("espacio", espacio)
                .addValue(
                        "desde", hoy.minusMonths(12).atStartOfDay(Fechas.LIMA).toOffsetDateTime());
    }

    private static Map<String, Object> elemento(
            String prefijo,
            String tipo,
            long id,
            String nombre,
            String codigo,
            String espacio,
            LocalDate ultima,
            int atenciones,
            int abiertas,
            int recientes,
            String estadoSanitario,
            LocalDate hoy,
            Integer frecuencia) {
        Integer diasSinAtencion =
                ultima == null
                        ? null
                        : (int) java.time.temporal.ChronoUnit.DAYS.between(ultima, hoy);
        List<String> faltantes = new ArrayList<>();
        if (ultima == null) faltantes.add("Sin atenciones registradas");
        if (frecuencia == null) faltantes.add("Sin frecuencia de mantenimiento de referencia");
        Map<String, Object> sustento = new LinkedHashMap<>();
        sustento.put("incidencias_abiertas", abiertas);
        sustento.put("incidencias_ultimos_12_meses", recientes);
        sustento.put("dias_desde_ultima_atencion", diasSinAtencion);
        sustento.put("frecuencia_mantenimiento_dias", frecuencia);
        sustento.put(
                "dias_vencida",
                diasSinAtencion == null || frecuencia == null
                        ? null
                        : diasSinAtencion - frecuencia);
        sustento.put("estado_sanitario", estadoSanitario);
        sustento.put("atenciones_ultimos_12_meses", atenciones);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("clave", prefijo + "-" + id);
        e.put("tipo", tipo);
        e.put("registro_id", id);
        e.put("nombre", nombre);
        e.put("codigo", codigo);
        e.put("espacio", espacio);
        e.put("sustento", sustento);
        e.put("faltantes", faltantes);
        return e;
    }

    /** Señales para ordenar y para la prioridad por defecto si la IA no valora un elemento. */
    static int senales(Map<String, Object> e) {
        Map<?, ?> s = (Map<?, ?>) e.get("sustento");
        int puntos =
                3 * entero(s.get("incidencias_abiertas"))
                        + entero(s.get("incidencias_ultimos_12_meses"));
        Object vencida = s.get("dias_vencida");
        if (vencida instanceof Integer v && v > 0) puntos += 2 + v / 15;
        if (s.get("estado_sanitario") != null) puntos += 2;
        return puntos;
    }

    private static String prioridadPorSenales(Map<String, Object> e) {
        int s = senales(e);
        return s >= 6 ? "Alta" : s >= 2 ? "Media" : "Baja";
    }

    private static int entero(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static Long id(Object valor, String campo) {
        if (valor == null) return null;
        if (valor instanceof Number n && n.longValue() > 0) return n.longValue();
        throw ErrorGestion.validacion(campo, "Debe ser un identificador válido");
    }

    static Map<String, Object> esquema(Map<String, Object> propiedades) {
        return esquemaObjeto(propiedades);
    }

    static Map<String, Object> esquemaObjeto(Map<String, Object> propiedades) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", "object");
        e.put("properties", propiedades);
        e.put("required", new ArrayList<>(propiedades.keySet()));
        return e;
    }

    static String instruccion() {
        return Prompts.leer(RUTA_INSTRUCCION);
    }

    /** Lectura de los prompts de {@code src/main/resources/prompts/}. */
    static final class Prompts {
        static String leer(String ruta) {
            try (InputStream in = Prompts.class.getClassLoader().getResourceAsStream(ruta)) {
                if (in == null) throw new IllegalStateException("Falta classpath:" + ruta);
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
