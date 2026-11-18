package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import static pe.edu.pucp.areasverdes.mod06.gestion.ia.ClasificacionesIncidenciaService.mapa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Paginacion;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;
import pe.edu.pucp.areasverdes.mod06.ia.FabricaClienteIA;

/**
 * RF-IA-004: la IA estima operarios, duración e insumos de una actividad de MOD-04 (o de una
 * descripción) a partir de actividades parecidas ya ejecutadas. Validar no escribe en MOD-04:
 * guarda lo confirmado y su {@code diferencia} con lo estimado, para compararlo después con lo real
 * (postcondición del caso de uso).
 *
 * <p>{@code diferencia = confirmado − estimado}, como {@code calcularDiferencia} del frontend: la
 * duración en la unidad de la estimación con 1 jornada = 8 h (SUPUESTO); los insumos se emparejan
 * por nombre y unidad sin distinguir mayúsculas; uno nuevo suma entero, uno quitado resta entero y
 * los que no cambian no aparecen. Las unidades de los insumos son texto libre (SUPUESTO).
 */
@Service
public class EstimacionesService {

    public static final String TIPO = "EstimacionRecursos";
    static final BigDecimal HORAS_POR_JORNADA = BigDecimal.valueOf(8);
    static final String RUTA_INSTRUCCION = "prompts/estimacion-recursos.md";

    private static final Map<String, Object> INSUMO =
            PriorizacionesService.esquemaObjeto(
                    Map.of(
                            "nombre",
                            Map.of("type", "string"),
                            "cantidad",
                            Map.of("type", "number"),
                            "unidad",
                            Map.of("type", "string")));
    private static final Map<String, Object> ESQUEMA =
            PriorizacionesService.esquema(
                    Map.of(
                            "estimacion",
                                    PriorizacionesService.esquemaObjeto(
                                            Map.of(
                                                    "operarios_min", Map.of("type", "integer"),
                                                    "operarios_max", Map.of("type", "integer"),
                                                    "operarios_sugeridos",
                                                            Map.of("type", "integer"),
                                                    "duracion", Map.of("type", "number"),
                                                    "unidad_duracion",
                                                            Map.of(
                                                                    "type",
                                                                    "string",
                                                                    "enum",
                                                                    List.of("horas", "jornadas")),
                                                    "insumos",
                                                            Map.of(
                                                                    "type", "array", "items",
                                                                    INSUMO))),
                            "variables",
                                    Map.of(
                                            "type",
                                            "array",
                                            "items",
                                            PriorizacionesService.esquemaObjeto(
                                                    Map.of(
                                                            "nombre",
                                                            Map.of("type", "string"),
                                                            "valor",
                                                            Map.of("type", "string")))),
                            "explicacion", Map.of("type", "string"),
                            "limitaciones",
                                    Map.of("type", "array", "items", Map.of("type", "string"))));

    private static final String ACTIVIDAD =
            """
            SELECT a.id AS actividad_id, 'ACT-' || a.id AS codigo, a.tipo_actividad_nombre AS tipo_actividad,
                   v.nombre AS espacio,
                   COALESCE(a.fecha_solicitud, (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date) AS fecha_prevista,
                   a.tipo_actividad_id, a.area_verde_id, a.superficie_m2
            FROM mod04_actividad a LEFT JOIN mod05_areaverde v ON v.id = a.area_verde_id
            """;

    private final SugerenciasIa sugerencias;
    private final FabricaClienteIA fabrica;
    private final NamedParameterJdbcTemplate jdbc;

    public EstimacionesService(
            SugerenciasIa sugerencias, FabricaClienteIA fabrica, NamedParameterJdbcTemplate jdbc) {
        this.sugerencias = sugerencias;
        this.fabrica = fabrica;
        this.jdbc = jdbc;
    }

    /** Actividades de MOD-04 sin recursos confirmados (sin tiempo registrado). */
    public SugerenciasIa.Pagina pendientes(String q, String page, Paginacion.EnlacePagina enlace) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue(
                                "q",
                                q == null || q.isBlank()
                                        ? null
                                        : "%" + q.strip().toLowerCase(Locale.ROOT) + "%");
        String filtro =
                """
                 WHERE a.estado_actividad <> 'Anulada' AND a.tiempo_minutos IS NULL
                   AND (CAST(:q AS varchar) IS NULL OR lower(a.tipo_actividad_nombre) LIKE :q
                        OR lower(v.nombre) LIKE :q OR ('act-' || a.id) LIKE :q)
                """;
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod04_actividad a LEFT JOIN mod05_areaverde v ON v.id"
                                + " = a.area_verde_id"
                                + filtro,
                        p,
                        Long.class);
        long pagina = Paginacion.resolver(page, total);
        p.addValue("limite", Paginacion.TAMANO_PAGINA)
                .addValue("desplazamiento", Paginacion.desplazamiento(pagina));
        List<Map<String, Object>> filas =
                jdbc.query(
                        ACTIVIDAD
                                + filtro
                                + " ORDER BY a.fecha_hora_actividad DESC, a.id DESC LIMIT :limite"
                                + " OFFSET :desplazamiento",
                        p,
                        (rs, n) -> estimable(rs));
        long ultima = Paginacion.paginas(total);
        return new SugerenciasIa.Pagina(
                total,
                pagina < ultima ? enlace.a(pagina + 1) : null,
                pagina > 1 ? enlace.a(pagina - 1) : null,
                filas);
    }

    public Map<String, Object> solicitar(Map<String, Object> cuerpo, long usuario) {
        Origen origen = origen(cuerpo);
        ClienteIA cliente = fabrica.obtenerClienteIa("estimacion");
        List<Map<String, Object>> antecedentes = antecedentes(origen);
        Map<String, Object> datos =
                mapa(
                        "actividad",
                        origen.actividad(),
                        "descripcion",
                        origen.descripcion(),
                        "antecedentes",
                        antecedentes);
        String solicitadoEn = sugerencias.ahora();
        long inicio = System.nanoTime();
        Map<String, Object> salida =
                cliente.generarJson(
                        JsonPy.dumpsRespuesta(datos),
                        List.of(),
                        ESQUEMA,
                        PriorizacionesService.Prompts.leer(RUTA_INSTRUCCION));
        long duracion = (System.nanoTime() - inicio) / 1_000_000;

        Map<String, Object> contenido = base(cliente, solicitadoEn, duracion, origen, antecedentes);
        contenido.put(
                "estimacion",
                antecedentes.isEmpty() && !(salida.get("estimacion") instanceof Map<?, ?>)
                        ? null
                        : salida.get("estimacion"));
        contenido.put("variables", salida.get("variables") instanceof List<?> l ? l : List.of());
        if (salida.get("explicacion") instanceof String x) contenido.put("explicacion", x);
        if (salida.get("limitaciones") instanceof List<?> l) contenido.put("limitaciones", l);
        return sugerencias.crear(
                TIPO,
                origen.actividad() == null ? null : "Actividad",
                origen.actividadId(),
                contenido,
                usuario);
    }

    public Map<String, Object> validar(long id, Map<String, Object> cuerpo, long usuario) {
        Map<?, ?> acciones = cuerpo.get("acciones") instanceof Map<?, ?> a ? a : null;
        Map<?, ?> confirmado = cuerpo.get("confirmado") instanceof Map<?, ?> c ? c : null;
        if (acciones == null || confirmado == null) {
            throw ErrorGestion.validacion(
                    "confirmado", "Indica las acciones y los recursos confirmados");
        }
        return sugerencias.revisar(
                id,
                TIPO,
                usuario,
                contenido -> {
                    Map<?, ?> estimado =
                            contenido.get("estimacion") instanceof Map<?, ?> e ? e : Map.of();
                    contenido.put(
                            "decision",
                            mapa(
                                    "acciones",
                                    acciones,
                                    "confirmado",
                                    confirmado,
                                    "diferencia",
                                    diferencia(estimado, confirmado)));
                    return new SugerenciasIa.Revision(SugerenciasIa.VALIDADA, contenido, null);
                });
    }

    public Map<String, Object> rechazar(long id, String motivo, long usuario) {
        return sugerencias.rechazar(
                id,
                TIPO,
                usuario,
                motivo,
                c ->
                        mapa(
                                "acciones",
                                        mapa(
                                                "operarios",
                                                "descartar",
                                                "duracion",
                                                "descartar",
                                                "insumos",
                                                "descartar"),
                                "confirmado",
                                        mapa(
                                                "operarios",
                                                null,
                                                "duracion",
                                                null,
                                                "unidad_duracion",
                                                null,
                                                "insumos",
                                                List.of()),
                                "diferencia",
                                        mapa(
                                                "operarios",
                                                null,
                                                "duracion",
                                                null,
                                                "insumos",
                                                List.of()),
                                "motivo_rechazo", motivo));
    }

    /** Sin IA o «Continuar sin recomendación»: se guarda lo confirmado ya validado. */
    public Map<String, Object> manual(Map<String, Object> cuerpo, long usuario) {
        Origen origen = origen(cuerpo);
        if (!(cuerpo.get("confirmado") instanceof Map<?, ?> confirmado)) {
            throw ErrorGestion.validacion("confirmado", "Indica los recursos confirmados");
        }
        Map<String, Object> contenido = base(null, sugerencias.ahora(), 0, origen, List.of());
        contenido.put("suficiencia", "insuficiente");
        contenido.put("explicacion", "Recursos registrados sin recomendación de IA.");
        contenido.put("estimacion", null);
        contenido.put("variables", List.of());
        contenido.put(
                "decision",
                mapa(
                        "acciones",
                                mapa(
                                        "operarios",
                                        "corregir",
                                        "duracion",
                                        "corregir",
                                        "insumos",
                                        "corregir"),
                        "confirmado", confirmado,
                        "diferencia",
                                mapa("operarios", null, "duracion", null, "insumos", List.of())));
        return sugerencias.crearValidada(
                TIPO,
                origen.actividad() == null ? null : "Actividad",
                origen.actividadId(),
                contenido,
                usuario);
    }

    // ------------------------------------------------------------------ diferencia

    static Map<String, Object> diferencia(Map<?, ?> estimado, Map<?, ?> confirmado) {
        Integer operarios =
                estimado.get("operarios_sugeridos") instanceof Number e
                                && confirmado.get("operarios") instanceof Number c
                        ? c.intValue() - e.intValue()
                        : null;
        BigDecimal duracion = null;
        if (estimado.get("duracion") instanceof Number e
                && confirmado.get("duracion") instanceof Number c) {
            BigDecimal valor = new BigDecimal(c.toString());
            String unidadEstimada = String.valueOf(estimado.get("unidad_duracion"));
            String unidadConfirmada =
                    confirmado.get("unidad_duracion") instanceof String u ? u : unidadEstimada;
            if (!unidadEstimada.equals(unidadConfirmada)) {
                valor =
                        unidadEstimada.equals("horas")
                                ? valor.multiply(HORAS_POR_JORNADA)
                                : valor.divide(HORAS_POR_JORNADA, 4, RoundingMode.HALF_UP);
            }
            duracion = valor.subtract(new BigDecimal(e.toString())).stripTrailingZeros();
        }
        Map<String, BigDecimal[]> porInsumo = new LinkedHashMap<>();
        Map<String, String[]> nombres = new LinkedHashMap<>();
        acumular(estimado.get("insumos"), 0, porInsumo, nombres);
        acumular(confirmado.get("insumos"), 1, porInsumo, nombres);
        List<Map<String, Object>> insumos = new ArrayList<>();
        porInsumo.forEach(
                (clave, v) -> {
                    BigDecimal d = v[1].subtract(v[0]);
                    if (d.signum() != 0) {
                        insumos.add(
                                mapa(
                                        "nombre",
                                        nombres.get(clave)[0],
                                        "diferencia",
                                        d.stripTrailingZeros().doubleValue(),
                                        "unidad",
                                        nombres.get(clave)[1]));
                    }
                });
        return mapa(
                "operarios",
                operarios,
                "duracion",
                duracion == null ? null : duracion.doubleValue(),
                "insumos",
                insumos);
    }

    private static void acumular(
            Object lista,
            int lado,
            Map<String, BigDecimal[]> acumulado,
            Map<String, String[]> nombres) {
        if (!(lista instanceof List<?> l)) return;
        for (Object o : l) {
            if (!(o instanceof Map<?, ?> m) || !(m.get("cantidad") instanceof Number cantidad))
                continue;
            String nombre = String.valueOf(m.get("nombre"));
            String unidad = String.valueOf(m.get("unidad"));
            String clave =
                    nombre.strip().toLowerCase(Locale.ROOT)
                            + "|"
                            + unidad.strip().toLowerCase(Locale.ROOT);
            acumulado.computeIfAbsent(
                    clave, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            acumulado.get(clave)[lado] =
                    acumulado.get(clave)[lado].add(new BigDecimal(cantidad.toString()));
            nombres.putIfAbsent(clave, new String[] {nombre, unidad});
        }
    }

    // ------------------------------------------------------------------ datos

    record Origen(
            Long actividadId,
            Map<String, Object> actividad,
            Map<String, Object> descripcion,
            Long tipoActividadId,
            Long areaVerdeId) {}

    private Origen origen(Map<String, Object> cuerpo) {
        if (cuerpo.get("actividad_id") instanceof Number n) {
            List<Map<String, Object>> filas =
                    jdbc.query(
                            ACTIVIDAD + " WHERE a.estado_actividad <> 'Anulada' AND a.id = :id",
                            Map.of("id", n.longValue()),
                            (rs, i) -> {
                                Map<String, Object> a = estimable(rs);
                                a.put("_tipo", rs.getObject("tipo_actividad_id", Long.class));
                                a.put("_area", rs.getObject("area_verde_id", Long.class));
                                return a;
                            });
            if (filas.isEmpty())
                throw new ErrorGestion(
                        404, "actividad-no-encontrada", "No existe la actividad indicada");
            Map<String, Object> a = filas.get(0);
            Long tipo = (Long) a.remove("_tipo");
            Long area = (Long) a.remove("_area");
            return new Origen(n.longValue(), a, null, tipo, area);
        }
        if (cuerpo.get("descripcion") instanceof Map<?, ?> d) {
            if (!(d.get("tipo_servicio_id") instanceof Number)) {
                throw ErrorGestion.validacion(
                        "descripcion", "Indica el tipo de servicio de la actividad");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> descripcion = new LinkedHashMap<>((Map<String, Object>) d);
            Long area = d.get("area_verde_id") instanceof Number a ? a.longValue() : null;
            return new Origen(null, null, descripcion, null, area);
        }
        throw ErrorGestion.validacion("actividad_id", "Elige una actividad o describe el trabajo");
    }

    /**
     * Actividades parecidas ya ejecutadas: mismo tipo o, sin tipo, mismo espacio; las 10 últimas.
     */
    private List<Map<String, Object>> antecedentes(Origen o) {
        if (o.tipoActividadId() == null && o.areaVerdeId() == null) return List.of();
        return jdbc.query(
                """
                SELECT 'ACT-' || a.id AS codigo, (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date AS fecha,
                       v.nombre AS espacio, (SELECT count(*) FROM mod04_participacion_actividad p WHERE p.actividad_id = a.id)::int AS operarios,
                       round(a.tiempo_minutos / 60.0, 2) AS duracion_horas
                FROM mod04_actividad a LEFT JOIN mod05_areaverde v ON v.id = a.area_verde_id
                WHERE a.estado_actividad <> 'Anulada' AND a.tiempo_minutos IS NOT NULL
                  AND (CAST(:actividad AS bigint) IS NULL OR a.id <> :actividad)
                  AND ((CAST(:tipo AS bigint) IS NOT NULL AND a.tipo_actividad_id = :tipo)
                       OR (CAST(:tipo AS bigint) IS NULL AND a.area_verde_id = :area))
                ORDER BY a.fecha_hora_actividad DESC LIMIT 10
                """,
                new MapSqlParameterSource()
                        .addValue("actividad", o.actividadId())
                        .addValue("tipo", o.tipoActividadId())
                        .addValue("area", o.areaVerdeId()),
                (rs, n) ->
                        mapa(
                                "codigo",
                                rs.getString("codigo"),
                                "fecha",
                                String.valueOf(rs.getObject("fecha")),
                                "espacio",
                                rs.getString("espacio"),
                                "operarios",
                                rs.getInt("operarios"),
                                "duracion_horas",
                                rs.getBigDecimal("duracion_horas").doubleValue()));
    }

    private Map<String, Object> base(
            ClienteIA cliente,
            String solicitadoEn,
            long duracion,
            Origen o,
            List<Map<String, Object>> antecedentes) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("version_contenido", 1);
        c.put("ia", sugerencias.llamada(cliente, solicitadoEn, duracion));
        c.put(
                "suficiencia",
                antecedentes.isEmpty()
                        ? "insuficiente"
                        : antecedentes.size() < 3 ? "parcial" : "suficiente");
        c.put(
                "faltantes",
                antecedentes.isEmpty()
                        ? List.of("No hay actividades parecidas con tiempo registrado")
                        : List.of());
        c.put(
                "datos_utilizados",
                List.of("Actividades ejecutadas por las cuadrillas (MOD-04): tiempo y operarios"));
        c.put(
                "limitaciones",
                List.of("1 jornada = 8 horas", "Las unidades de los insumos son texto libre"));
        c.put("explicacion", "Estimación a partir de actividades parecidas ya ejecutadas.");
        c.put("actividad", o.actividad());
        c.put("descripcion", o.descripcion());
        c.put("antecedentes", antecedentes);
        c.put("decision", null);
        return c;
    }

    private static Map<String, Object> estimable(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return mapa(
                "actividad_id",
                rs.getLong("actividad_id"),
                "codigo",
                rs.getString("codigo"),
                "tipo_actividad",
                rs.getString("tipo_actividad"),
                "espacio",
                rs.getString("espacio"),
                "fecha_prevista",
                rs.getObject("fecha_prevista") == null
                        ? null
                        : String.valueOf(rs.getObject("fecha_prevista")));
    }
}
