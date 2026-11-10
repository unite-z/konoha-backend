package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;
import pe.edu.pucp.areasverdes.mod06.ia.FabricaClienteIA;

/**
 * RF-IA-002: detección de inconsistencias en los datos (duplicados, valores atípicos, campos
 * incompatibles y nombres sin normalizar). Una sugerencia por hallazgo.
 *
 * <p>Los candidatos los detecta el backend con reglas SQL deterministas; la IA explica cada
 * hallazgo, propone la corrección y su confianza. Aceptar solo registra la decisión (SUPUESTO del
 * informe técnico): la corrección la hace el módulo dueño (MOD-02 o MOD-05) en su pantalla.
 */
@Service
public class InconsistenciasService {

    public static final String TIPO = "Inconsistencia";
    static final List<String> CONJUNTOS =
            List.of("Atenciones", "Ejemplares", "AreasVerdes", "Proveedores");
    static final List<String> TIPOS_HALLAZGO =
            List.of("Duplicado", "ValorAtipico", "CamposIncompatibles", "NombreNoNormalizado");
    static final int MAXIMO_HALLAZGOS = 30;
    static final String RUTA_INSTRUCCION = "prompts/inconsistencias_datos.md";

    private static final Map<String, Object> ESQUEMA =
            PriorizacionesService.esquema(
                    Map.of(
                            "hallazgos",
                            Map.of(
                                    "type",
                                    "array",
                                    "items",
                                    PriorizacionesService.esquemaObjeto(
                                            Map.of(
                                                    "indice", Map.of("type", "integer"),
                                                    "descripcion", Map.of("type", "string"),
                                                    "valor_propuesto",
                                                            Map.of(
                                                                    "type",
                                                                    List.of("string", "null")),
                                                    "confianza",
                                                            Map.of(
                                                                    "type",
                                                                    "string",
                                                                    "enum",
                                                                    List.of(
                                                                            "Alta", "Media",
                                                                            "Baja")),
                                                    "explicacion", Map.of("type", "string")))),
                            "limitaciones",
                            Map.of("type", "array", "items", Map.of("type", "string"))));

    /** Un candidato detectado por SQL, antes de pasar por la IA. */
    record Candidato(
            String tipoHallazgo,
            String campoObservado,
            String valorObservado,
            List<Map<String, Object>> registros,
            String campoPropuesto,
            Long conservarId) {}

    private final SugerenciasIa sugerencias;
    private final FabricaClienteIA fabrica;
    private final NamedParameterJdbcTemplate jdbc;

    public InconsistenciasService(
            SugerenciasIa sugerencias, FabricaClienteIA fabrica, NamedParameterJdbcTemplate jdbc) {
        this.sugerencias = sugerencias;
        this.fabrica = fabrica;
        this.jdbc = jdbc;
    }

    public Map<String, Object> analizar(Map<String, Object> cuerpo, long usuario) {
        String conjunto = cuerpo.get("conjunto") instanceof String s ? s : null;
        if (conjunto == null || !CONJUNTOS.contains(conjunto)) {
            throw ErrorGestion.validacion(
                    "conjunto", "Usa uno de estos valores: " + String.join(", ", CONJUNTOS));
        }
        LocalDate desde = fecha(cuerpo.get("desde"), "desde");
        LocalDate hasta = fecha(cuerpo.get("hasta"), "hasta");
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ErrorGestion(
                    400,
                    "periodo-invalido",
                    "La fecha final no puede ser anterior a la inicial",
                    Map.of("hasta", List.of("La fecha final no puede ser anterior a la inicial")));
        }
        ClienteIA cliente = fabrica.obtenerClienteIa();
        List<Candidato> candidatos = candidatos(conjunto, desde, hasta);
        List<String> limitaciones = new ArrayList<>();
        if (candidatos.size() > MAXIMO_HALLAZGOS) {
            limitaciones.add(
                    "Se revisan los primeros %d hallazgos de %d"
                            .formatted(MAXIMO_HALLAZGOS, candidatos.size()));
            candidatos = candidatos.subList(0, MAXIMO_HALLAZGOS);
        }

        Map<Integer, Map<?, ?>> valorados = new LinkedHashMap<>();
        String solicitadoEn = sugerencias.ahora();
        long inicio = System.nanoTime();
        if (!candidatos.isEmpty()) {
            List<Map<String, Object>> enviados = new ArrayList<>();
            for (int i = 0; i < candidatos.size(); i++) {
                Candidato c = candidatos.get(i);
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("indice", i);
                e.put("conjunto", conjunto);
                e.put("tipo_hallazgo", c.tipoHallazgo());
                e.put("campo_observado", c.campoObservado());
                e.put("valor_observado", c.valorObservado());
                e.put("registros", c.registros());
                enviados.add(e);
            }
            Map<String, Object> salida =
                    cliente.generarJson(
                            JsonPy.dumpsRespuesta(Map.of("hallazgos", enviados)),
                            List.of(),
                            ESQUEMA,
                            PriorizacionesService.Prompts.leer(RUTA_INSTRUCCION));
            if (salida.get("hallazgos") instanceof List<?> lista) {
                for (Object o : lista) {
                    if (o instanceof Map<?, ?> m && m.get("indice") instanceof Number n)
                        valorados.put(n.intValue(), m);
                }
            }
            if (salida.get("limitaciones") instanceof List<?> l)
                l.forEach(x -> limitaciones.add(String.valueOf(x)));
        }
        long duracion = (System.nanoTime() - inicio) / 1_000_000;

        List<Map<String, Object>> creadas = new ArrayList<>();
        // Agrupa los hallazgos de esta ejecución (no hay endpoint para consultar un análisis).
        long analisisId = sugerencias.reloj().millis();
        for (int i = 0; i < candidatos.size(); i++) {
            Candidato c = candidatos.get(i);
            Map<?, ?> v = valorados.getOrDefault(i, Map.of());
            Map<String, Object> propuesta = new LinkedHashMap<>();
            propuesta.put(
                    "descripcion",
                    v.get("descripcion") instanceof String d ? d : descripcionPorDefecto(c));
            propuesta.put("campo", c.campoPropuesto());
            propuesta.put(
                    "valor_propuesto", v.get("valor_propuesto") instanceof String p ? p : null);
            propuesta.put("registro_conservar_id", c.conservarId());
            Map<String, Object> contenido = new LinkedHashMap<>();
            contenido.put("version_contenido", 1);
            contenido.put("ia", sugerencias.llamada(cliente, solicitadoEn, duracion));
            contenido.put("suficiencia", v.isEmpty() ? "parcial" : "suficiente");
            contenido.put(
                    "faltantes",
                    v.isEmpty() ? List.of("La IA no valoró este hallazgo") : List.of());
            contenido.put("datos_utilizados", List.of(conjunto + " registrados en el sistema"));
            contenido.put("limitaciones", limitaciones);
            contenido.put(
                    "explicacion",
                    v.get("explicacion") instanceof String x
                            ? x
                            : "Detectado por las reglas de calidad de datos.");
            contenido.put("analisis_id", analisisId);
            contenido.put("conjunto", conjunto);
            contenido.put("tipo_hallazgo", c.tipoHallazgo());
            contenido.put("campo_observado", c.campoObservado());
            contenido.put("valor_observado", c.valorObservado());
            contenido.put("registros", c.registros());
            contenido.put("propuesta", propuesta);
            contenido.put("confianza", v.get("confianza") instanceof String conf ? conf : "Media");
            contenido.put("decision", null);
            Map<?, ?> primero = c.registros().get(0);
            Map<String, Object> s =
                    sugerencias.crear(
                            TIPO,
                            String.valueOf(primero.get("entidad")),
                            ((Number) primero.get("registro_id")).longValue(),
                            contenido,
                            usuario);
            creadas.add(s);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("analisis_id", analisisId);
        r.put("hallazgos", creadas.size());
        r.put("sugerencias", creadas);
        return r;
    }

    public SugerenciasIa.Pagina listar(
            String estado,
            String tipoHallazgo,
            String conjunto,
            String page,
            pe.edu.pucp.areasverdes.mod06.gestion.servicios.Paginacion.EnlacePagina enlace) {
        if (tipoHallazgo == null && conjunto == null)
            return sugerencias.listar(TIPO, estado, page, enlace);
        // Filtros dentro del contenido: se aplican sobre la página completa del tipo.
        SugerenciasIa.Pagina todas = sugerencias.listar(TIPO, estado, null, enlace);
        List<Map<String, Object>> filtradas =
                todas.results().stream()
                        .filter(
                                s -> {
                                    Map<?, ?> c = (Map<?, ?>) s.get("contenido");
                                    return (tipoHallazgo == null
                                                    || tipoHallazgo.equals(c.get("tipo_hallazgo")))
                                            && (conjunto == null
                                                    || conjunto.equals(c.get("conjunto")));
                                })
                        .toList();
        return new SugerenciasIa.Pagina(filtradas.size(), null, null, filtradas);
    }

    public Map<String, Object> aceptar(long id, Map<String, Object> cuerpo, long usuario) {
        Object valor = cuerpo.get("valor_final");
        Object comentario = cuerpo.get("comentario");
        return sugerencias.revisar(
                id,
                TIPO,
                usuario,
                c -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("accion", "aceptar");
                    d.put(
                            "valor_final",
                            valor instanceof String v
                                    ? v
                                    : ((Map<?, ?>) c.get("propuesta")).get("valor_propuesto"));
                    d.put("comentario", comentario instanceof String s ? s : null);
                    c.put("decision", d);
                    return new SugerenciasIa.Revision(SugerenciasIa.VALIDADA, c, null);
                });
    }

    public Map<String, Object> descartar(long id, String motivo, long usuario) {
        return sugerencias.rechazar(
                id,
                TIPO,
                usuario,
                motivo,
                c -> {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("accion", "descartar");
                    d.put("valor_final", null);
                    d.put("comentario", motivo);
                    return d;
                });
    }

    // ------------------------------------------------------------------ reglas

    private List<Candidato> candidatos(String conjunto, LocalDate desde, LocalDate hasta) {
        MapSqlParameterSource p =
                new MapSqlParameterSource().addValue("desde", desde).addValue("hasta", hasta);
        return switch (conjunto) {
            case "Proveedores" -> proveedores();
            case "AreasVerdes" -> areas();
            case "Ejemplares" -> ejemplares();
            default -> atenciones(p);
        };
    }

    private List<Candidato> proveedores() {
        List<Candidato> r = new ArrayList<>();
        jdbc.query(
                """
                SELECT a.id AS a_id, a.razon_social AS a_nombre, a.ruc AS a_ruc, b.id AS b_id, b.razon_social AS b_nombre, b.ruc AS b_ruc
                FROM mod05_proveedor a JOIN mod05_proveedor b ON a.id < b.id
                 AND (a.ruc = b.ruc OR lower(regexp_replace(btrim(a.razon_social), '\\s+', ' ', 'g'))
                                     = lower(regexp_replace(btrim(b.razon_social), '\\s+', ' ', 'g')))
                ORDER BY a.id, b.id LIMIT 100
                """,
                Map.of(),
                rs -> {
                    r.add(
                            new Candidato(
                                    "Duplicado",
                                    "RUC o razón social",
                                    rs.getString("a_ruc"),
                                    List.of(
                                            registro(
                                                    "Proveedor",
                                                    rs.getLong("a_id"),
                                                    rs.getString("a_ruc"),
                                                    rs.getString("a_nombre"),
                                                    Map.of(
                                                            "Razón social",
                                                            rs.getString("a_nombre"),
                                                            "RUC",
                                                            rs.getString("a_ruc"))),
                                            registro(
                                                    "Proveedor",
                                                    rs.getLong("b_id"),
                                                    rs.getString("b_ruc"),
                                                    rs.getString("b_nombre"),
                                                    Map.of(
                                                            "Razón social",
                                                            rs.getString("b_nombre"),
                                                            "RUC",
                                                            rs.getString("b_ruc")))),
                                    null,
                                    rs.getLong("a_id")));
                });
        return r;
    }

    private List<Candidato> areas() {
        List<Candidato> r = new ArrayList<>();
        jdbc.query(
                """
                SELECT a.id AS a_id, a.nombre AS a_nombre, b.id AS b_id, b.nombre AS b_nombre
                FROM mod02_area_verde a JOIN mod02_area_verde b ON a.id < b.id AND a.activo AND b.activo
                 AND lower(regexp_replace(btrim(a.nombre), '\\s+', ' ', 'g')) = lower(regexp_replace(btrim(b.nombre), '\\s+', ' ', 'g'))
                ORDER BY a.id LIMIT 100
                """,
                Map.of(),
                rs -> {
                    boolean iguales = rs.getString("a_nombre").equals(rs.getString("b_nombre"));
                    r.add(
                            new Candidato(
                                    iguales ? "Duplicado" : "NombreNoNormalizado",
                                    "Nombre",
                                    rs.getString("b_nombre"),
                                    List.of(
                                            registro(
                                                    "AreaVerde",
                                                    rs.getLong("a_id"),
                                                    null,
                                                    rs.getString("a_nombre"),
                                                    Map.of("Nombre", rs.getString("a_nombre"))),
                                            registro(
                                                    "AreaVerde",
                                                    rs.getLong("b_id"),
                                                    null,
                                                    rs.getString("b_nombre"),
                                                    Map.of("Nombre", rs.getString("b_nombre")))),
                                    iguales ? null : "nombre",
                                    iguales ? rs.getLong("a_id") : null));
                });
        jdbc.query(
                """
                WITH s AS (SELECT avg(superficie_m2) m, stddev_pop(superficie_m2) d FROM mod02_area_verde WHERE activo)
                SELECT a.id, a.nombre, a.superficie_m2 FROM mod02_area_verde a, s
                WHERE a.activo AND s.d > 0 AND abs(a.superficie_m2 - s.m) > 3 * s.d ORDER BY a.id LIMIT 50
                """,
                Map.of(),
                rs -> {
                    r.add(
                            new Candidato(
                                    "ValorAtipico",
                                    "Superficie (m²)",
                                    rs.getBigDecimal("superficie_m2").toPlainString(),
                                    List.of(
                                            registro(
                                                    "AreaVerde",
                                                    rs.getLong("id"),
                                                    null,
                                                    rs.getString("nombre"),
                                                    Map.of(
                                                            "Superficie (m²)",
                                                            rs.getBigDecimal("superficie_m2")
                                                                    .toPlainString()))),
                                    "superficie_m2",
                                    null));
                });
        return r;
    }

    private List<Candidato> ejemplares() {
        List<Candidato> r = new ArrayList<>();
        jdbc.query(
                """
                SELECT a.id AS a_id, a.codigo AS a_codigo, b.id AS b_id, b.codigo AS b_codigo
                FROM mod02_ejemplar a JOIN mod02_ejemplar b ON a.id < b.id AND a.especie_id = b.especie_id
                 AND a.situacion = 'activo' AND b.situacion = 'activo'
                 AND a.ubicacion_geografica IS NOT NULL AND b.ubicacion_geografica IS NOT NULL
                 AND ST_DWithin(a.ubicacion_geografica::geography, b.ubicacion_geografica::geography, 0.5)
                ORDER BY a.id LIMIT 100
                """,
                Map.of(),
                rs -> {
                    r.add(
                            new Candidato(
                                    "Duplicado",
                                    "Ubicación",
                                    "A menos de 0,5 m con la misma especie",
                                    List.of(
                                            registro(
                                                    "Ejemplar",
                                                    rs.getLong("a_id"),
                                                    rs.getString("a_codigo"),
                                                    rs.getString("a_codigo"),
                                                    Map.of("Código", rs.getString("a_codigo"))),
                                            registro(
                                                    "Ejemplar",
                                                    rs.getLong("b_id"),
                                                    rs.getString("b_codigo"),
                                                    rs.getString("b_codigo"),
                                                    Map.of("Código", rs.getString("b_codigo")))),
                                    null,
                                    rs.getLong("a_id")));
                });
        jdbc.query(
                """
                SELECT id, codigo, altura_total_m FROM mod02_ejemplar
                WHERE situacion = 'activo' AND altura_total_m IS NOT NULL AND (altura_total_m <= 0 OR altura_total_m > 60)
                ORDER BY id LIMIT 50
                """,
                Map.of(),
                rs -> {
                    r.add(
                            new Candidato(
                                    "ValorAtipico",
                                    "Altura total (m)",
                                    rs.getBigDecimal("altura_total_m").toPlainString(),
                                    List.of(
                                            registro(
                                                    "Ejemplar",
                                                    rs.getLong("id"),
                                                    rs.getString("codigo"),
                                                    rs.getString("codigo"),
                                                    Map.of(
                                                            "Altura total (m)",
                                                            rs.getBigDecimal("altura_total_m")
                                                                    .toPlainString()))),
                                    "altura_total_m",
                                    null));
                });
        return r;
    }

    private List<Candidato> atenciones(MapSqlParameterSource p) {
        List<Candidato> r = new ArrayList<>();
        String periodo =
                """
                 (CAST(:desde AS date) IS NULL OR (%1$s.fecha_hora_inicio AT TIME ZONE 'America/Lima')::date >= :desde)
                 AND (CAST(:hasta AS date) IS NULL OR (%1$s.fecha_hora_inicio AT TIME ZONE 'America/Lima')::date <= :hasta)
                """;
        jdbc.query(
                """
                SELECT id, codigo, estado, fecha_hora_inicio, fecha_hora_fin FROM mod05_atenciontercerizada a
                WHERE fecha_hora_fin IS NOT NULL AND fecha_hora_inicio IS NOT NULL AND fecha_hora_fin < fecha_hora_inicio
                  AND %s ORDER BY id LIMIT 50
                """
                        .formatted(periodo.formatted("a")),
                p,
                rs -> {
                    r.add(
                            new Candidato(
                                    "CamposIncompatibles",
                                    "Fin anterior al inicio",
                                    String.valueOf(rs.getObject("fecha_hora_fin")),
                                    List.of(
                                            registro(
                                                    "AtencionTercerizada",
                                                    rs.getLong("id"),
                                                    rs.getString("codigo"),
                                                    rs.getString("codigo"),
                                                    Map.of(
                                                            "Inicio",
                                                            String.valueOf(
                                                                    rs.getObject(
                                                                            "fecha_hora_inicio")),
                                                            "Fin",
                                                            String.valueOf(
                                                                    rs.getObject("fecha_hora_fin")),
                                                            "Estado",
                                                            rs.getString("estado")))),
                                    "fecha_hora_fin",
                                    null));
                });
        jdbc.query(
                """
                SELECT a.id AS a_id, a.codigo AS a_codigo, b.id AS b_id, b.codigo AS b_codigo, a.fecha_hora_inicio
                FROM mod05_atenciontercerizada a JOIN mod05_atenciontercerizada b ON a.id < b.id
                 AND a.tipo_servicio_id = b.tipo_servicio_id
                 AND (a.fecha_hora_inicio AT TIME ZONE 'America/Lima')::date = (b.fecha_hora_inicio AT TIME ZONE 'America/Lima')::date
                 AND EXISTS (SELECT 1 FROM mod05_alcanceatencion x JOIN mod05_alcanceatencion y
                             ON y.atencion_id = b.id AND x.area_verde_id = y.area_verde_id WHERE x.atencion_id = a.id)
                WHERE %s ORDER BY a.id LIMIT 50
                """
                        .formatted(periodo.formatted("a")),
                p,
                rs -> {
                    r.add(
                            new Candidato(
                                    "Duplicado",
                                    "Servicio, día y espacio",
                                    String.valueOf(rs.getObject("fecha_hora_inicio")),
                                    List.of(
                                            registro(
                                                    "AtencionTercerizada",
                                                    rs.getLong("a_id"),
                                                    rs.getString("a_codigo"),
                                                    rs.getString("a_codigo"),
                                                    Map.of("Código", rs.getString("a_codigo"))),
                                            registro(
                                                    "AtencionTercerizada",
                                                    rs.getLong("b_id"),
                                                    rs.getString("b_codigo"),
                                                    rs.getString("b_codigo"),
                                                    Map.of("Código", rs.getString("b_codigo")))),
                                    null,
                                    rs.getLong("a_id")));
                });
        return r;
    }

    private static Map<String, Object> registro(
            String entidad, long id, String codigo, String etiqueta, Map<String, String> campos) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("entidad", entidad);
        r.put("registro_id", id);
        r.put("codigo", codigo);
        r.put("etiqueta", etiqueta == null ? entidad + " " + id : etiqueta);
        r.put("campos", new LinkedHashMap<>(campos));
        return r;
    }

    private static String descripcionPorDefecto(Candidato c) {
        return switch (c.tipoHallazgo()) {
            case "Duplicado" -> "Revisa si los registros son el mismo y conserva uno";
            case "NombreNoNormalizado" -> "Unifica la escritura del nombre";
            case "ValorAtipico" -> "Comprueba el valor: se sale de lo habitual";
            default -> "Corrige los campos incompatibles";
        };
    }

    private static LocalDate fecha(Object valor, String campo) {
        if (valor == null) return null;
        try {
            return LocalDate.parse(String.valueOf(valor));
        } catch (java.time.format.DateTimeParseException e) {
            throw ErrorGestion.validacion(campo, "Usa el formato AAAA-MM-DD");
        }
    }
}
