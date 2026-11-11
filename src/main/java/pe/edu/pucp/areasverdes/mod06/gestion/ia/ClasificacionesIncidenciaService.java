package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod03.ClasificacionIncidencias;
import pe.edu.pucp.areasverdes.mod03.UsuarioPlanificacion;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Paginacion;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;
import pe.edu.pucp.areasverdes.mod06.ia.FabricaClienteIA;

/**
 * RF-IA-005: la IA sugiere tipo de evento, categoría, severidad, vía de atención y si es sencilla
 * para una incidencia de MOD-03 en estado {@code Registrada}. Al validar se escribe la
 * clasificación en MOD-03 con su propio servicio ({@link ClasificacionIncidencias}), en la misma
 * transacción: la incidencia pasa a {@code Clasificada}. Nunca se deriva. Rechazar no la toca.
 *
 * <p>Correspondencias con MOD-03: el impacto sale de la severidad (Alta → Alto) y la vía
 * «Tercerizado» es «Tercerizada». Tipo de evento, categoría y «sencilla» se guardan en la decisión:
 * {@code mod03_incidencia} aún no tiene columnas para ellos. Exige PLA.INCIDENCIA.CLASIFICAR, el
 * permiso de MOD-03 para clasificar.
 */
@Service
public class ClasificacionesIncidenciaService {

    public static final String TIPO = "ClasificacionIncidencia";
    public static final String CLASIFICAR = "PLA.INCIDENCIA.CLASIFICAR";
    static final List<String> CAMPOS =
            List.of("tipo_evento", "categoria", "severidad", "via_atencion", "es_sencilla");
    static final List<String> OBLIGATORIOS = List.of("tipo_evento", "severidad", "via_atencion");
    static final List<String> TIPOS_EVENTO =
            List.of(
                    "Incidencia",
                    "Reclamo",
                    "No conformidad",
                    "Hallazgo de supervisión",
                    "Hallazgo de auditoría");
    static final String RUTA_INSTRUCCION = "prompts/clasificacion-incidencia.md";

    private static final Map<String, Object> ESQUEMA =
            PriorizacionesService.esquema(
                    Map.of(
                            "sugerencia",
                                    PriorizacionesService.esquemaObjeto(
                                            Map.of(
                                                    "tipo_evento",
                                                            Map.of(
                                                                    "type",
                                                                    "string",
                                                                    "enum",
                                                                    TIPOS_EVENTO),
                                                    "categoria",
                                                            Map.of(
                                                                    "type",
                                                                    List.of("string", "null")),
                                                    "severidad",
                                                            Map.of(
                                                                    "type",
                                                                    "string",
                                                                    "enum",
                                                                    List.of(
                                                                            "Alta", "Media",
                                                                            "Baja")),
                                                    "via_atencion",
                                                            Map.of(
                                                                    "type",
                                                                    "string",
                                                                    "enum",
                                                                    List.of(
                                                                            "PersonalPropio",
                                                                            "Tercerizado")),
                                                    "es_sencilla", Map.of("type", "boolean"))),
                            "motivos",
                                    PriorizacionesService.esquemaObjeto(
                                            Map.of(
                                                    "tipo_evento", Map.of("type", "string"),
                                                    "severidad", Map.of("type", "string"),
                                                    "via_atencion", Map.of("type", "string"))),
                            "explicacion", Map.of("type", "string"),
                            "limitaciones",
                                    Map.of("type", "array", "items", Map.of("type", "string"))));

    private static final String RESUMEN =
            """
            SELECT i.id, i.codigo, i.fecha_registro AS fecha_recepcion, t.nombre AS tipo, i.descripcion,
                   i.canal AS canal_ingreso, a.nombre AS espacio, i.ejemplar_id, e.codigo AS ejemplar_codigo,
                   COALESCE(s.nombre_comun, s.nombre_cientifico) AS especie, i.estado, i.area_verde_id
            FROM mod03_incidencia i
            LEFT JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
            LEFT JOIN mod02_area_verde a ON a.id = i.area_verde_id
            LEFT JOIN mod02_ejemplar e ON e.id = i.ejemplar_id
            LEFT JOIN mod02_especie_vegetal s ON s.id = e.especie_id
            """;

    private final SugerenciasIa sugerencias;
    private final FabricaClienteIA fabrica;
    private final NamedParameterJdbcTemplate jdbc;
    private final ClasificacionIncidencias mod03;

    public ClasificacionesIncidenciaService(
            SugerenciasIa sugerencias,
            FabricaClienteIA fabrica,
            NamedParameterJdbcTemplate jdbc,
            ClasificacionIncidencias mod03) {
        this.sugerencias = sugerencias;
        this.fabrica = fabrica;
        this.jdbc = jdbc;
        this.mod03 = mod03;
    }

    // ------------------------------------------------------------------ incidencias pendientes

    public SugerenciasIa.Pagina pendientes(
            String q, Long incidenciaId, String page, Paginacion.EnlacePagina enlace) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue(
                                "q",
                                q == null || q.isBlank()
                                        ? null
                                        : "%" + q.strip().toLowerCase() + "%")
                        .addValue("id", incidenciaId);
        String filtro =
                """
                 WHERE i.estado = 'Registrada'
                   AND (CAST(:id AS bigint) IS NULL OR i.id = :id)
                   AND (CAST(:q AS varchar) IS NULL OR lower(i.codigo) LIKE :q OR lower(i.descripcion) LIKE :q)
                """;
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_incidencia i" + filtro, p, Long.class);
        long pagina = Paginacion.resolver(page, total);
        p.addValue("limite", Paginacion.TAMANO_PAGINA)
                .addValue("desplazamiento", Paginacion.desplazamiento(pagina));
        List<Map<String, Object>> filas =
                jdbc.query(
                        RESUMEN
                                + filtro
                                + " ORDER BY i.fecha_registro DESC, i.id DESC LIMIT :limite OFFSET"
                                + " :desplazamiento",
                        p,
                        (rs, n) -> resumen(rs));
        long ultima = Paginacion.paginas(total);
        return new SugerenciasIa.Pagina(
                total,
                pagina < ultima ? enlace.a(pagina + 1) : null,
                pagina > 1 ? enlace.a(pagina - 1) : null,
                filas);
    }

    // ------------------------------------------------------------------ IA

    public Map<String, Object> solicitar(Map<String, Object> cuerpo, long usuario) {
        long incidenciaId = idObligatorio(cuerpo.get("incidencia_id"), "incidencia_id");
        Map<String, Object> incidencia = registrada(incidenciaId);
        ClienteIA cliente = fabrica.obtenerClienteIa();
        List<Map<String, Object>> antecedentes = antecedentes(incidencia);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("incidencia", incidencia);
        datos.put("antecedentes", antecedentes);
        String solicitadoEn = sugerencias.ahora();
        long inicio = System.nanoTime();
        Map<String, Object> salida =
                cliente.generarJson(
                        JsonPy.dumpsRespuesta(datos),
                        List.of(),
                        ESQUEMA,
                        PriorizacionesService.Prompts.leer(RUTA_INSTRUCCION));
        long duracion = (System.nanoTime() - inicio) / 1_000_000;

        Map<String, Object> sugerida = new LinkedHashMap<>();
        Map<?, ?> s = salida.get("sugerencia") instanceof Map<?, ?> m ? m : Map.of();
        sugerida.put(
                "tipo_evento",
                s.get("tipo_evento") instanceof String te && TIPOS_EVENTO.contains(te) ? te : null);
        sugerida.put("categoria", s.get("categoria") instanceof String c ? c : null);
        sugerida.put(
                "severidad",
                s.get("severidad") instanceof String sev
                                && List.of("Alta", "Media", "Baja").contains(sev)
                        ? sev
                        : null);
        sugerida.put(
                "via_atencion",
                s.get("via_atencion") instanceof String v
                                && List.of("PersonalPropio", "Tercerizado").contains(v)
                        ? v
                        : null);
        sugerida.put("es_sencilla", s.get("es_sencilla") instanceof Boolean b ? b : null);

        Map<String, Object> contenido =
                base(
                        cliente,
                        solicitadoEn,
                        duracion,
                        incidencia,
                        antecedentes,
                        salida.get("explicacion") instanceof String x
                                ? x
                                : "Propuesta según la descripción y los antecedentes.",
                        salida.get("limitaciones") instanceof List<?> l ? l : List.of());
        contenido.put("sugerencia", sugerida);
        contenido.put("motivos", salida.get("motivos") instanceof Map<?, ?> mo ? mo : Map.of());
        return sugerencias.crear(TIPO, "Incidencia", incidenciaId, contenido, usuario);
    }

    @Transactional
    public Map<String, Object> validar(long id, Map<String, Object> cuerpo, UsuarioSesion usuario) {
        exigirClasificar(usuario);
        if (!(cuerpo.get("por_campo") instanceof Map<?, ?> porCampo)) {
            throw ErrorGestion.validacion("por_campo", "Indica una decisión por campo");
        }
        return sugerencias.revisar(
                id,
                TIPO,
                usuario.id(),
                contenido -> {
                    Map<?, ?> sugerida = (Map<?, ?>) contenido.getOrDefault("sugerencia", Map.of());
                    Map<String, Object> decisiones = new LinkedHashMap<>();
                    Map<String, Object> valores = new LinkedHashMap<>();
                    for (String campo : CAMPOS) {
                        Object d = porCampo.get(campo);
                        String accion =
                                d instanceof Map<?, ?> m && m.get("accion") instanceof String a
                                        ? a
                                        : "aceptar";
                        Object valor =
                                switch (accion) {
                                    case "corregir" -> ((Map<?, ?>) d).get("valor_final");
                                    case "descartar" -> null;
                                    default -> sugerida.get(campo);
                                };
                        decisiones.put(campo, mapa("accion", accion, "valor_final", valor));
                        valores.put(campo, valor);
                    }
                    registrarEnMod03(
                            ((Number) contenido.get("incidencia_id")).longValue(),
                            valores,
                            usuario,
                            id);
                    contenido.put(
                            "decision",
                            mapa("por_campo", decisiones, "valores_registrados", valores));
                    return new SugerenciasIa.Revision(SugerenciasIa.VALIDADA, contenido, null);
                });
    }

    public Map<String, Object> rechazar(long id, String motivo, long usuario) {
        return sugerencias.rechazar(
                id,
                TIPO,
                usuario,
                motivo,
                c -> {
                    Map<String, Object> porCampo = new LinkedHashMap<>();
                    CAMPOS.forEach(
                            campo ->
                                    porCampo.put(
                                            campo,
                                            mapa("accion", "descartar", "valor_final", null)));
                    return mapa(
                            "por_campo",
                            porCampo,
                            "valores_registrados",
                            null,
                            "motivo_rechazo",
                            motivo);
                });
    }

    /** Sin IA: el usuario clasifica directamente (E1 y A1). */
    @Transactional
    public Map<String, Object> manual(Map<String, Object> cuerpo, UsuarioSesion usuario) {
        exigirClasificar(usuario);
        long incidenciaId = idObligatorio(cuerpo.get("incidencia_id"), "incidencia_id");
        Map<String, Object> incidencia = registrada(incidenciaId);
        Map<?, ?> v = cuerpo.get("valores") instanceof Map<?, ?> m ? m : Map.of();
        Map<String, Object> valores = new LinkedHashMap<>();
        CAMPOS.forEach(campo -> valores.put(campo, v.get(campo)));
        registrarEnMod03(incidenciaId, valores, usuario, null);
        Map<String, Object> contenido =
                base(
                        null,
                        sugerencias.ahora(),
                        0,
                        incidencia,
                        List.of(),
                        "Clasificación registrada manualmente, sin recomendación de IA.",
                        List.of());
        contenido.put("suficiencia", "insuficiente");
        contenido.put("sugerencia", null);
        contenido.put("motivos", Map.of());
        Map<String, Object> porCampo = new LinkedHashMap<>();
        CAMPOS.forEach(
                campo ->
                        porCampo.put(
                                campo,
                                mapa("accion", "corregir", "valor_final", valores.get(campo))));
        contenido.put("decision", mapa("por_campo", porCampo, "valores_registrados", valores));
        return sugerencias.crearValidada(TIPO, "Incidencia", incidenciaId, contenido, usuario.id());
    }

    // ------------------------------------------------------------------ MOD-03

    private void registrarEnMod03(
            long incidenciaId,
            Map<String, Object> valores,
            UsuarioSesion usuario,
            Long sugerenciaId) {
        Map<String, List<String>> errores = new LinkedHashMap<>();
        for (String campo : OBLIGATORIOS)
            if (valores.get(campo) == null) errores.put(campo, List.of("Obligatorio"));
        if (!errores.isEmpty()) {
            throw new ErrorGestion(
                    400,
                    "clasificacion-incompleta",
                    "Completa el tipo de evento, la severidad y la vía",
                    errores);
        }
        String severidad = String.valueOf(valores.get("severidad"));
        String impacto =
                switch (severidad) {
                    case "Alta" -> "Alto";
                    case "Media" -> "Medio";
                    case "Baja" -> "Bajo";
                    default ->
                            throw new ErrorGestion(
                                    400,
                                    "clasificacion-incompleta",
                                    "Severidad no válida",
                                    Map.of("severidad", List.of("Usa Alta, Media o Baja")));
                };
        String via =
                switch (String.valueOf(valores.get("via_atencion"))) {
                    case "PersonalPropio" -> "PersonalPropio";
                    case "Tercerizado", "Tercerizada" -> "Tercerizada";
                    default ->
                            throw new ErrorGestion(
                                    400,
                                    "clasificacion-incompleta",
                                    "Vía no válida",
                                    Map.of(
                                            "via_atencion",
                                            List.of("Usa PersonalPropio o Tercerizado")));
                };
        String motivo =
                sugerenciaId == null
                        ? "Clasificación manual desde MOD-06 (RF-IA-005)"
                        : "Clasificación validada de la propuesta de IA "
                                + sugerenciaId
                                + " (RF-IA-005)";
        try {
            mod03.clasificar(
                    incidenciaId,
                    new ClasificacionIncidencias.Clasificacion(severidad, impacto, via, motivo),
                    new UsuarioPlanificacion(usuario.id(), usuario.nombre(), usuario.permisos()));
        } catch (RuntimeException e) {
            if (e instanceof ErrorGestion) throw e;
            // ErrorPlanificacion de MOD-03 (estado, valores): la decisión no se guarda.
            throw new ErrorGestion(409, "clasificacion-rechazada", e.getMessage());
        }
    }

    private Map<String, Object> registrada(long incidenciaId) {
        List<Map<String, Object>> filas =
                jdbc.query(
                        RESUMEN + " WHERE i.id = :id",
                        Map.of("id", incidenciaId),
                        (rs, n) -> resumen(rs));
        if (filas.isEmpty()) {
            throw new ErrorGestion(
                    404, "incidencia-no-encontrada", "No existe la incidencia indicada");
        }
        Map<String, Object> incidencia = filas.get(0);
        if (!"Registrada".equals(incidencia.get("estado"))) {
            throw new ErrorGestion(
                    409,
                    "incidencia-no-registrada",
                    "La incidencia ya no está en estado Registrada (estado actual: "
                            + incidencia.get("estado")
                            + ")");
        }
        return incidencia;
    }

    private List<Map<String, Object>> antecedentes(Map<String, Object> incidencia) {
        return jdbc.query(
                """
                SELECT i.codigo, (i.fecha_registro AT TIME ZONE 'America/Lima')::date AS fecha, t.nombre AS tipo, i.severidad
                FROM mod03_incidencia i LEFT JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                WHERE i.id <> :id AND i.severidad IS NOT NULL
                  AND ((CAST(:area AS bigint) IS NOT NULL AND i.area_verde_id = :area)
                       OR (CAST(:ejemplar AS bigint) IS NOT NULL AND i.ejemplar_id = :ejemplar))
                ORDER BY i.fecha_registro DESC LIMIT 10
                """,
                new MapSqlParameterSource()
                        .addValue("id", incidencia.get("id"))
                        .addValue("area", incidencia.get("area_verde_id"))
                        .addValue(
                                "ejemplar",
                                incidencia.get("ejemplar") instanceof Map<?, ?> e
                                        ? e.get("id")
                                        : null),
                (rs, n) ->
                        mapa(
                                "codigo",
                                rs.getString("codigo"),
                                "fecha",
                                String.valueOf(rs.getObject("fecha")),
                                "tipo_evento",
                                tipoEvento(rs.getString("tipo")),
                                "severidad",
                                rs.getString("severidad")));
    }

    private Map<String, Object> base(
            ClienteIA cliente,
            String solicitadoEn,
            long duracion,
            Map<String, Object> incidencia,
            List<Map<String, Object>> antecedentes,
            String explicacion,
            List<?> limitaciones) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("version_contenido", 1);
        c.put("ia", sugerencias.llamada(cliente, solicitadoEn, duracion));
        c.put(
                "suficiencia",
                incidencia.get("descripcion") == null
                        ? "insuficiente"
                        : antecedentes.isEmpty() ? "parcial" : "suficiente");
        c.put(
                "faltantes",
                antecedentes.isEmpty()
                        ? List.of(
                                "Sin incidencias anteriores clasificadas en el mismo espacio o"
                                        + " ejemplar")
                        : List.of());
        c.put(
                "datos_utilizados",
                List.of(
                        "Descripción, canal y tipo de la incidencia (MOD-03)",
                        "Incidencias anteriores del mismo espacio o ejemplar"));
        c.put("limitaciones", new ArrayList<>(limitaciones));
        c.put("explicacion", explicacion);
        c.put("incidencia_id", incidencia.get("id"));
        Map<String, Object> resumen = new LinkedHashMap<>(incidencia);
        resumen.remove("area_verde_id");
        c.put("incidencia", resumen);
        c.put("antecedentes", antecedentes);
        c.put("decision", null);
        return c;
    }

    private Map<String, Object> resumen(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> r = new LinkedHashMap<>();
        long id = rs.getLong("id");
        r.put("id", id);
        r.put("codigo", rs.getString("codigo"));
        java.time.OffsetDateTime recepcion =
                rs.getObject("fecha_recepcion", java.time.OffsetDateTime.class);
        r.put("fecha_recepcion", recepcion == null ? null : recepcion.toString());
        r.put("tipo_evento", tipoEvento(rs.getString("tipo")));
        r.put("descripcion", rs.getString("descripcion"));
        r.put("canal_ingreso", rs.getString("canal_ingreso"));
        r.put("espacio", rs.getString("espacio"));
        Long ejemplar = rs.getObject("ejemplar_id", Long.class);
        r.put(
                "ejemplar",
                ejemplar == null
                        ? null
                        : mapa(
                                "id",
                                ejemplar,
                                "codigo",
                                rs.getString("ejemplar_codigo"),
                                "especie",
                                rs.getString("especie")));
        r.put("fotografias", fotografias(id));
        r.put("estado", rs.getString("estado"));
        r.put("area_verde_id", rs.getObject("area_verde_id", Long.class));
        return r;
    }

    private List<Map<String, Object>> fotografias(long incidenciaId) {
        return jdbc.query(
                """
                SELECT a.archivo_id, f.nombre_original AS nombre FROM core_adjunto a JOIN core_archivo f ON f.id = a.archivo_id
                WHERE a.entidad = 'incidencia' AND a.registro_id = :id ORDER BY a.id
                """,
                Map.of("id", incidenciaId),
                (rs, n) ->
                        mapa(
                                "archivo_id",
                                rs.getLong("archivo_id"),
                                "nombre",
                                rs.getString("nombre"),
                                "url_miniatura",
                                null));
    }

    /** El tipo de MOD-03 si es uno de los tipos de evento del modelo; si no, «Incidencia». */
    private static String tipoEvento(String tipo) {
        return tipo != null && TIPOS_EVENTO.contains(tipo) ? tipo : "Incidencia";
    }

    private static void exigirClasificar(UsuarioSesion u) {
        if (u == null || !u.tiene(CLASIFICAR)) throw ErrorGestion.sinPermiso();
    }

    private static long idObligatorio(Object valor, String campo) {
        if (valor instanceof Number n && n.longValue() > 0) return n.longValue();
        throw ErrorGestion.validacion(campo, "Indica la incidencia");
    }

    static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) r.put((String) pares[i], pares[i + 1]);
        return r;
    }
}
