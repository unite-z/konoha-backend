package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod03.ProgramacionServicios;
import pe.edu.pucp.areasverdes.mod03.UsuarioPlanificacion;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;
import pe.edu.pucp.areasverdes.mod06.ia.FabricaClienteIA;

/**
 * RF-IA-003: propuesta de cronograma con IA a partir de los servicios periódicos pendientes, sus
 * frecuencias y duraciones, las restricciones de calendario y los recursos disponibles.
 *
 * <p>El backend calcula qué está pendiente (servicio periódico sin programación en el periodo y con
 * la frecuencia práctica cumplida desde su última programación) y una fecha por defecto; la IA
 * ajusta las fechas para esquivar restricciones y explica cada una. Al validar se crea en MOD-03,
 * con su propio servicio ({@link ProgramacionServicios#crear}), una programación por cada propuesta
 * aceptada o corregida, todas en la misma transacción: o se crean todas o ninguna y la sugerencia
 * sigue en {@code Propuesta}. Una propuesta que cae en una prohibición exige {@code
 * confirma_restriccion} (RI-25); aun confirmada, MOD-03 no programa sobre una prohibición (409
 * {@code programacion-rechazada}) y hay que corregir las fechas. Las advertencias se justifican
 * solas. Validar exige además PLA.PLANIFICACION.GESTIONAR.
 */
@Service
public class CronogramasService {

    public static final String TIPO = "Cronograma";
    public static final String GESTIONAR = "PLA.PLANIFICACION.GESTIONAR";
    static final String RUTA_INSTRUCCION = "prompts/cronograma-servicios.md";
    static final int MAXIMO_DIAS = 92;

    private static final Map<String, Object> ESQUEMA =
            PriorizacionesService.esquema(
                    Map.of(
                            "propuestas",
                                    Map.of(
                                            "type",
                                            "array",
                                            "items",
                                            PriorizacionesService.esquemaObjeto(
                                                    Map.of(
                                                            "clave", Map.of("type", "string"),
                                                            "fecha_inicio",
                                                                    Map.of(
                                                                            "type", "string",
                                                                            "format", "date"),
                                                            "fecha_fin",
                                                                    Map.of(
                                                                            "type", "string",
                                                                            "format", "date"),
                                                            "motivo", Map.of("type", "string")))),
                            "criterios", Map.of("type", "array", "items", Map.of("type", "string")),
                            "explicacion", Map.of("type", "string"),
                            "limitaciones",
                                    Map.of("type", "array", "items", Map.of("type", "string"))));

    private final SugerenciasIa sugerencias;
    private final FabricaClienteIA fabrica;
    private final NamedParameterJdbcTemplate jdbc;
    private final ProgramacionServicios programaciones;

    public CronogramasService(
            SugerenciasIa sugerencias,
            FabricaClienteIA fabrica,
            NamedParameterJdbcTemplate jdbc,
            ProgramacionServicios programaciones) {
        this.sugerencias = sugerencias;
        this.fabrica = fabrica;
        this.jdbc = jdbc;
        this.programaciones = programaciones;
    }

    // ------------------------------------------------------------------ solicitar

    public Map<String, Object> solicitar(Map<String, Object> cuerpo, long usuario) {
        LocalDate desde = fecha(cuerpo.get("desde"), "desde");
        LocalDate hasta = fecha(cuerpo.get("hasta"), "hasta");
        if (desde == null || hasta == null) {
            throw new ErrorGestion(
                    400,
                    "periodo-invalido",
                    "Indica el periodo",
                    Map.of(
                            desde == null ? "desde" : "hasta",
                            List.of("Indica la fecha (AAAA-MM-DD)")));
        }
        if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta) > MAXIMO_DIAS) {
            throw new ErrorGestion(
                    400,
                    "periodo-invalido",
                    "El periodo va de 1 a " + MAXIMO_DIAS + " días",
                    Map.of("hasta", List.of("El periodo va de 1 a " + MAXIMO_DIAS + " días")));
        }
        String modalidad = cuerpo.get("modalidad") instanceof String m ? m : null;
        if (modalidad != null && !List.of("PersonalPropio", "Tercerizado").contains(modalidad)) {
            throw ErrorGestion.validacion("modalidad", "Usa PersonalPropio o Tercerizado");
        }
        List<Long> tipos = new ArrayList<>();
        if (cuerpo.get("tipo_servicio_ids") instanceof List<?> l) {
            for (Object o : l) if (o instanceof Number n) tipos.add(n.longValue());
        }
        ClienteIA cliente = fabrica.obtenerClienteIa("cronograma");

        List<Map<String, Object>> restricciones = restricciones(desde, hasta);
        List<Map<String, Object>> pendientes = pendientes(desde, hasta, tipos, modalidad);
        Map<String, Map<?, ?>> ajustes = new LinkedHashMap<>();
        Map<String, Object> salida = Map.of();
        String solicitadoEn = sugerencias.ahora();
        long inicio = System.nanoTime();
        if (!pendientes.isEmpty()) {
            salida =
                    cliente.generarJson(
                            JsonPy.dumpsRespuesta(
                                    Map.of(
                                            "desde",
                                            desde.toString(),
                                            "hasta",
                                            hasta.toString(),
                                            "pendientes",
                                            pendientes,
                                            "restricciones",
                                            restricciones)),
                            List.of(),
                            ESQUEMA,
                            PriorizacionesService.Prompts.leer(RUTA_INSTRUCCION));
            if (salida.get("propuestas") instanceof List<?> lista) {
                for (Object o : lista)
                    if (o instanceof Map<?, ?> m && m.get("clave") instanceof String c)
                        ajustes.put(c, m);
            }
        }
        long duracion = (System.nanoTime() - inicio) / 1_000_000;

        List<Map<String, Object>> propuestas = new ArrayList<>();
        for (Map<String, Object> p : pendientes) {
            Map<String, Object> propuesta = new LinkedHashMap<>(p);
            propuesta.remove("duracion_dias");
            propuesta.remove("ultima_programacion");
            Map<?, ?> a = ajustes.get(p.get("clave"));
            LocalDate ini = LocalDate.parse((String) p.get("fecha_inicio"));
            LocalDate fin = LocalDate.parse((String) p.get("fecha_fin"));
            if (a != null) {
                LocalDate ai = fechaSegura(a.get("fecha_inicio"));
                LocalDate af = fechaSegura(a.get("fecha_fin"));
                int maximo = ((Number) p.get("duracion_dias")).intValue();
                if (ai != null
                        && af != null
                        && !ai.isBefore(desde)
                        && !af.isAfter(hasta)
                        && !af.isBefore(ai)
                        && ChronoUnit.DAYS.between(ai, af) + 1 <= maximo) {
                    ini = ai;
                    fin = af;
                }
            }
            propuesta.put("fecha_inicio", ini.toString());
            propuesta.put("fecha_fin", fin.toString());
            propuesta.put(
                    "motivo",
                    a != null && a.get("motivo") instanceof String m ? m : p.get("motivo"));
            propuesta.put(
                    "coincidencias",
                    coincidencias(restricciones, ini, fin, propuesta.get("area_verde_id")));
            propuestas.add(propuesta);
        }

        Map<String, Object> entrada = new LinkedHashMap<>();
        entrada.put("desde", desde.toString());
        entrada.put("hasta", hasta.toString());
        if (!tipos.isEmpty()) entrada.put("tipo_servicio_ids", tipos);
        if (modalidad != null) entrada.put("modalidad", modalidad);
        entrada.put("actividades_pendientes", pendientes.size());
        List<String> limitaciones =
                new ArrayList<>(
                        List.of(
                                "Se programa el servicio completo: el reparto por áreas o zonas se"
                                        + " completa en Planificación"));
        if (salida.get("limitaciones") instanceof List<?> l)
            l.forEach(x -> limitaciones.add(String.valueOf(x)));
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("version_contenido", 1);
        contenido.put("ia", sugerencias.llamada(cliente, solicitadoEn, duracion));
        contenido.put(
                "suficiencia",
                pendientes.isEmpty()
                        ? "insuficiente"
                        : ajustes.isEmpty() ? "parcial" : "suficiente");
        contenido.put(
                "faltantes",
                pendientes.isEmpty()
                        ? List.of("No hay servicios periódicos pendientes en el periodo")
                        : List.of());
        contenido.put(
                "datos_utilizados",
                List.of(
                        "Servicios periódicos, frecuencias y duraciones (MOD-03)",
                        "Programaciones anteriores (MOD-03)",
                        "Restricciones de calendario (MOD-03)",
                        "Contratos vigentes (MOD-05) y cuadrillas activas (MOD-04)"));
        contenido.put("limitaciones", limitaciones);
        contenido.put(
                "explicacion",
                salida.get("explicacion") instanceof String x
                        ? x
                        : "Cada servicio se propone cuando vence su frecuencia práctica, evitando"
                                + " las prohibiciones.");
        contenido.put("entrada", entrada);
        contenido.put(
                "criterios",
                salida.get("criterios") instanceof List<?> c && !c.isEmpty()
                        ? c
                        : List.of(
                                "Frecuencia práctica de la estación",
                                "Duración máxima del servicio",
                                "Restricciones de calendario"));
        contenido.put("restricciones", restricciones);
        contenido.put("propuestas", propuestas);
        contenido.put("decision", null);
        return sugerencias.crear(TIPO, null, null, contenido, usuario);
    }

    // ------------------------------------------------------------------ validar

    @Transactional
    public Map<String, Object> validar(long id, Map<String, Object> cuerpo, UsuarioSesion usuario) {
        if (usuario == null || !usuario.tiene(GESTIONAR)) throw ErrorGestion.sinPermiso();
        if (!(cuerpo.get("por_propuesta") instanceof Map<?, ?> porPropuesta)) {
            throw ErrorGestion.validacion("por_propuesta", "Indica una decisión por propuesta");
        }
        return sugerencias.revisar(
                id,
                TIPO,
                usuario.id(),
                contenido -> {
                    Map<String, List<String>> incompletas = new LinkedHashMap<>();
                    Map<String, List<String>> sinConfirmar = new LinkedHashMap<>();
                    Map<String, Object> decisiones = new LinkedHashMap<>();
                    List<String> acciones = new ArrayList<>();
                    List<Map<String, Object>> aCrear = new ArrayList<>();
                    List<?> restricciones =
                            (List<?>) contenido.getOrDefault("restricciones", List.of());
                    for (Object o : (List<?>) contenido.getOrDefault("propuestas", List.of())) {
                        Map<?, ?> p = (Map<?, ?>) o;
                        String clave = String.valueOf(p.get("clave"));
                        Map<?, ?> d = porPropuesta.get(clave) instanceof Map<?, ?> m ? m : null;
                        String accion = d != null && d.get("accion") instanceof String a ? a : null;
                        if (accion == null
                                || !List.of("aceptar", "corregir", "descartar").contains(accion)) {
                            incompletas.put(clave, List.of("Elige aceptar, corregir o descartar"));
                            continue;
                        }
                        Map<String, Object> finales = null;
                        if (!accion.equals("descartar")) {
                            Map<?, ?> v =
                                    accion.equals("corregir")
                                                    && d.get("valores_finales")
                                                            instanceof Map<?, ?> vf
                                            ? vf
                                            : null;
                            if (accion.equals("corregir") && v == null) {
                                incompletas.put(clave, List.of("Indica los valores finales"));
                                continue;
                            }
                            Map<?, ?> recursos = (Map<?, ?>) p.get("recursos");
                            finales = new LinkedHashMap<>();
                            finales.put(
                                    "fecha_inicio",
                                    valor(v, "fecha_inicio", p.get("fecha_inicio")));
                            finales.put("fecha_fin", valor(v, "fecha_fin", p.get("fecha_fin")));
                            finales.put(
                                    "area_verde_id",
                                    valor(v, "area_verde_id", p.get("area_verde_id")));
                            finales.put(
                                    "cuadrilla_id",
                                    valor(v, "cuadrilla_id", recursos.get("cuadrilla_id")));
                            finales.put(
                                    "proveedor_id",
                                    valor(v, "proveedor_id", recursos.get("proveedor_id")));
                            finales.put(
                                    "operarios", valor(v, "operarios", recursos.get("operarios")));
                            LocalDate ini = fechaSegura(finales.get("fecha_inicio"));
                            LocalDate fin = fechaSegura(finales.get("fecha_fin"));
                            if (ini == null || fin == null || fin.isBefore(ini)) {
                                incompletas.put(clave, List.of("Fechas no válidas"));
                                continue;
                            }
                            List<Map<String, Object>> cruces =
                                    coincidencias(
                                            restricciones, ini, fin, finales.get("area_verde_id"));
                            boolean prohibida =
                                    cruces.stream()
                                            .anyMatch(r -> "Prohibicion".equals(r.get("tipo")));
                            if (prohibida && !Boolean.TRUE.equals(d.get("confirma_restriccion"))) {
                                sinConfirmar.put(
                                        clave,
                                        List.of(
                                                "Coincide con una prohibición: confírmala para"
                                                        + " programar"));
                                continue;
                            }
                            Map<String, Object> crear = new LinkedHashMap<>(finales);
                            crear.put("tipo_servicio_id", p.get("tipo_servicio_id"));
                            // MOD-03 exige justificar cualquier restricción que se cruce; una
                            // prohibición la rechaza siempre (y entonces no se crea ninguna).
                            crear.put("con_restriccion", !cruces.isEmpty());
                            aCrear.add(crear);
                        }
                        Map<String, Object> decision = new LinkedHashMap<>();
                        decision.put("accion", accion);
                        decision.put("valores_finales", accion.equals("corregir") ? finales : null);
                        decision.put(
                                "confirma_restriccion",
                                Boolean.TRUE.equals(d.get("confirma_restriccion")));
                        decisiones.put(clave, decision);
                        acciones.add(accion);
                    }
                    if (!incompletas.isEmpty()) {
                        throw new ErrorGestion(
                                400,
                                "decisiones-incompletas",
                                "Decide todas las propuestas",
                                incompletas);
                    }
                    if (!sinConfirmar.isEmpty()) {
                        throw new ErrorGestion(
                                422,
                                "restriccion-sin-confirmar",
                                "Hay propuestas que coinciden con una prohibición sin confirmar",
                                sinConfirmar);
                    }
                    List<Long> creadas = new ArrayList<>();
                    UsuarioPlanificacion planificador =
                            new UsuarioPlanificacion(
                                    usuario.id(), usuario.nombre(), usuario.permisos());
                    for (Map<String, Object> c : aCrear) {
                        LocalDate ini = fechaSegura(c.get("fecha_inicio"));
                        LocalDate fin = fechaSegura(c.get("fecha_fin"));
                        Long area =
                                c.get("area_verde_id") instanceof Number n ? n.longValue() : null;
                        try {
                            creadas.add(
                                    programaciones.crear(
                                            new ProgramacionServicios.NuevaProgramacion(
                                                    ((Number) c.get("tipo_servicio_id"))
                                                            .longValue(),
                                                    c.get("proveedor_id") instanceof Number pr
                                                            ? pr.longValue()
                                                            : null,
                                                    ini,
                                                    fin,
                                                    Boolean.TRUE.equals(c.get("con_restriccion"))
                                                            ? "Restricción revisada al validar la"
                                                                    + " propuesta de IA "
                                                                    + id
                                                                    + " (RF-IA-003)"
                                                            : null,
                                                    YearMonth.from(ini).toString(),
                                                    c.get("proveedor_id") == null
                                                                    && c.get("cuadrilla_id")
                                                                            instanceof Number cu
                                                            ? cu.longValue()
                                                            : null,
                                                    area == null ? null : List.of(area),
                                                    null),
                                            planificador));
                        } catch (ErrorGestion e) {
                            throw e;
                        } catch (RuntimeException e) {
                            // ErrorPlanificacion de MOD-03: no se crea ninguna y la sugerencia
                            // sigue en Propuesta.
                            throw new ErrorGestion(
                                    409,
                                    "programacion-rechazada",
                                    "MOD-03 rechazó una programación: " + e.getMessage());
                        }
                    }
                    Map<String, Object> decision = new LinkedHashMap<>();
                    decision.put("por_propuesta", decisiones);
                    decision.put("resumen", SugerenciasIa.resumen(acciones));
                    decision.put("programaciones_creadas", creadas);
                    decision.put("motivo_rechazo", null);
                    contenido.put("decision", decision);
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
                c -> {
                    Map<String, Object> porPropuesta = new LinkedHashMap<>();
                    List<String> acciones = new ArrayList<>();
                    for (Object o : (List<?>) c.getOrDefault("propuestas", List.of())) {
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("accion", "descartar");
                        d.put("valores_finales", null);
                        d.put("confirma_restriccion", false);
                        porPropuesta.put(String.valueOf(((Map<?, ?>) o).get("clave")), d);
                        acciones.add("descartar");
                    }
                    Map<String, Object> decision = new LinkedHashMap<>();
                    decision.put("por_propuesta", porPropuesta);
                    decision.put("resumen", SugerenciasIa.resumen(acciones));
                    decision.put("programaciones_creadas", List.of());
                    decision.put("motivo_rechazo", motivo);
                    return decision;
                });
    }

    // ------------------------------------------------------------------ datos

    /**
     * Servicios periódicos sin programación en el periodo y con la frecuencia cumplida antes de
     * {@code hasta}.
     */
    private List<Map<String, Object>> pendientes(
            LocalDate desde, LocalDate hasta, List<Long> tipos, String modalidad) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("desde", desde)
                        .addValue("hasta", hasta)
                        .addValue("sinTipos", tipos.isEmpty())
                        .addValue("tipos", tipos.isEmpty() ? List.of(-1L) : tipos)
                        .addValue("ejecutor", modalidad);
        List<Map<String, Object>> r = new ArrayList<>();
        jdbc.query(
                """
                SELECT t.id, t.nombre, t.ejecutor, d.duracion_maxima_dias,
                       (SELECT max(x.fecha_fin_prevista) FROM mod03_programacion_servicio x
                         WHERE x.tipo_servicio_id = t.id AND x.estado <> 'Cancelada') AS ultima,
                       (SELECT min(f.frecuencia_practica_dias) FROM mod03_frecuencia_servicio f WHERE f.tipo_servicio_id = t.id)
                           AS frecuencia,
                       (SELECT c.proveedor_id FROM mod05_contrato c WHERE c.tipo_servicio_id = t.id
                          AND c.vigencia_inicio <= :hasta AND (c.vigencia_fin IS NULL OR c.vigencia_fin >= :desde)
                        ORDER BY c.vigencia_inicio DESC LIMIT 1) AS proveedor_id
                FROM mod05_tiposervicio t
                JOIN mod03_duracion_servicio d ON d.tipo_servicio_id = t.id
                WHERE t.activo AND t.es_periodico
                  AND (:sinTipos OR t.id IN (:tipos))
                  AND (CAST(:ejecutor AS varchar) IS NULL OR t.ejecutor = :ejecutor)
                  AND NOT EXISTS (SELECT 1 FROM mod03_programacion_servicio x WHERE x.tipo_servicio_id = t.id
                      AND x.estado <> 'Cancelada' AND x.fecha_inicio_prevista <= :hasta AND x.fecha_fin_prevista >= :desde)
                ORDER BY t.nombre
                """,
                p,
                rs -> {
                    LocalDate ultima = rs.getObject("ultima", LocalDate.class);
                    Integer frecuencia = rs.getObject("frecuencia", Integer.class);
                    LocalDate vence =
                            ultima == null || frecuencia == null
                                    ? desde
                                    : ultima.plusDays(frecuencia);
                    if (vence.isAfter(hasta)) return;
                    LocalDate inicio = vence.isBefore(desde) ? desde : vence;
                    int duracion = Math.max(1, rs.getInt("duracion_maxima_dias"));
                    LocalDate fin = inicio.plusDays(duracion - 1L);
                    if (fin.isAfter(hasta)) fin = hasta;
                    long tipo = rs.getLong("id");
                    boolean tercerizado = "Tercerizado".equals(rs.getString("ejecutor"));
                    Long proveedor = tercerizado ? rs.getObject("proveedor_id", Long.class) : null;
                    Map<String, Object> recursos = new LinkedHashMap<>();
                    recursos.put("modalidad", tercerizado ? "Tercerizado" : "PersonalPropio");
                    Map<String, Object> cuadrilla = tercerizado ? null : cuadrilla();
                    recursos.put("cuadrilla_id", cuadrilla == null ? null : cuadrilla.get("id"));
                    recursos.put("cuadrilla", cuadrilla == null ? null : cuadrilla.get("nombre"));
                    recursos.put("proveedor_id", proveedor);
                    recursos.put(
                            "proveedor",
                            proveedor == null
                                    ? null
                                    : jdbc.queryForObject(
                                            "SELECT razon_social FROM mod05_proveedor WHERE id ="
                                                    + " :id",
                                            Map.of("id", proveedor),
                                            String.class));
                    recursos.put("operarios", null);
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("clave", "servicio-" + tipo);
                    e.put("tipo_servicio_id", tipo);
                    e.put("actividad", rs.getString("nombre"));
                    e.put("area_verde_id", null);
                    e.put("ubicacion", "Todo el campus");
                    e.put("fecha_inicio", inicio.toString());
                    e.put("fecha_fin", fin.toString());
                    e.put("recursos", recursos);
                    e.put(
                            "motivo",
                            ultima == null
                                    ? "Sin programaciones anteriores"
                                    : "Frecuencia práctica de "
                                            + frecuencia
                                            + " días desde la última programación ("
                                            + ultima
                                            + ")");
                    e.put("duracion_dias", duracion);
                    e.put("ultima_programacion", ultima == null ? null : ultima.toString());
                    r.add(e);
                });
        return r;
    }

    private Map<String, Object> cuadrilla() {
        List<Map<String, Object>> filas =
                jdbc.queryForList(
                        "SELECT id, nombre FROM mod04_cuadrilla WHERE activa ORDER BY id LIMIT 1",
                        Map.of());
        return filas.isEmpty() ? null : filas.get(0);
    }

    private List<Map<String, Object>> restricciones(LocalDate desde, LocalDate hasta) {
        return jdbc.query(
                """
                SELECT r.fecha_inicio, r.fecha_fin, t.efecto AS tipo,
                       COALESCE(NULLIF(btrim(r.descripcion), ''), t.nombre) AS descripcion, v.nombre AS espacio
                FROM mod03_restriccion_calendario r
                JOIN mod03_tipo_restriccion t ON t.id = r.tipo_restriccion_id
                LEFT JOIN mod05_areaverde v ON v.id = r.area_verde_id
                WHERE r.activo AND t.efecto IN ('Prohibicion', 'Advertencia')
                  AND r.fecha_inicio <= :hasta AND r.fecha_fin >= :desde
                ORDER BY r.fecha_inicio, r.id
                """,
                Map.of("desde", desde, "hasta", hasta),
                (rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("fecha_inicio", String.valueOf(rs.getObject("fecha_inicio")));
                    m.put("fecha_fin", String.valueOf(rs.getObject("fecha_fin")));
                    m.put("tipo", rs.getString("tipo"));
                    m.put("descripcion", rs.getString("descripcion"));
                    m.put("espacio", rs.getString("espacio"));
                    return m;
                });
    }

    /**
     * Restricciones que se cruzan con las fechas (las de todo el campus, o todas si no hay área).
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> coincidencias(
            List<?> restricciones, LocalDate inicio, LocalDate fin, Object area) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Object o : restricciones) {
            Map<String, Object> x = (Map<String, Object>) o;
            LocalDate ri = LocalDate.parse(String.valueOf(x.get("fecha_inicio")));
            LocalDate rf = LocalDate.parse(String.valueOf(x.get("fecha_fin")));
            if (!ri.isAfter(fin) && !rf.isBefore(inicio)) r.add(x);
        }
        return r;
    }

    private static Object valor(Map<?, ?> v, String campo, Object porDefecto) {
        return v != null && v.containsKey(campo) ? v.get(campo) : porDefecto;
    }

    private static LocalDate fechaSegura(Object valor) {
        if (!(valor instanceof String s)) return null;
        try {
            return LocalDate.parse(s);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate fecha(Object valor, String campo) {
        if (valor == null) return null;
        LocalDate f = fechaSegura(valor);
        if (f == null) throw ErrorGestion.validacion(campo, "Usa el formato AAAA-MM-DD");
        return f;
    }
}
