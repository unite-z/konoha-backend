package pe.edu.pucp.areasverdes.mod06.ia.comun;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;

/**
 * {@link AlmacenSugerencias} sobre {@code mod06_sugerencia_ia} (V72). SQL siempre parametrizado; el
 * contenido se guarda como {@code jsonb} con el mismo JSON que antes iba en {@code
 * mock-data/mod06/SugerenciaIA.json}.
 *
 * <p>Las escrituras deben ejecutarse dentro de la transacción del servicio, para que la sugerencia
 * y su fila de auditoría ({@link RegistradorAuditoria}) se guarden o se descarten juntas.
 */
@Repository
public class SugerenciasIaRepository implements AlmacenSugerencias {

    /** Nombre de la entidad en la auditoría común. */
    public static final String ENTIDAD_AUDITORIA = "mod06_sugerencia_ia";

    private static final String SELECT =
            """
            SELECT s.id, s.tipo, s.entidad, s.registro_id, s.archivo_id, s.contenido::text AS contenido, s.estado,
                   s.validador_id, s.fecha_validacion, s.fecha_creacion, s.usuario_creacion_id, s.fecha_modificacion,
                   s.usuario_modificacion_id, uc.nombre AS usuario_creacion_nombre, uv.nombre AS validador_nombre
            FROM mod06_sugerencia_ia s
            LEFT JOIN core_usuario uc ON uc.id = s.usuario_creacion_id
            LEFT JOIN core_usuario uv ON uv.id = s.validador_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;

    public SugerenciasIaRepository(
            NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional
    public Map<String, Object> crear(Map<String, Object> datos) {
        long usuario = id(datos.get("usuario_creacion_id"));
        Long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod06_sugerencia_ia (tipo, entidad, registro_id, archivo_id, contenido, estado,
                            fecha_creacion, usuario_creacion_id, fecha_modificacion, usuario_modificacion_id)
                        VALUES (:tipo, :entidad, :registro, :archivo, CAST(:contenido AS jsonb), 'Propuesta',
                            :fecha, :usuario, :fecha, :usuario)
                        RETURNING id
                        """,
                        new MapSqlParameterSource()
                                .addValue("tipo", datos.get("tipo"))
                                .addValue("entidad", datos.get("entidad"))
                                .addValue("registro", idNulo(datos.get("registro_id")))
                                .addValue("archivo", idNulo(datos.get("archivo_id")))
                                .addValue(
                                        "contenido", JsonPy.dumpsRespuesta(datos.get("contenido")))
                                .addValue("fecha", instante(datos.get("fecha_creacion")))
                                .addValue("usuario", usuario),
                        Long.class);
        Map<String, Object> creada = obtener(id);
        auditoria.registrarAlta(ENTIDAD_AUDITORIA, id, resumen(creada), usuario);
        return creada;
    }

    @Override
    public Map<String, Object> obtener(long id) {
        List<Map<String, Object>> filas =
                jdbc.query(
                        SELECT + " WHERE s.id = :id",
                        Map.of("id", id),
                        SugerenciasIaRepository::fila);
        return filas.isEmpty() ? null : filas.get(0);
    }

    @Override
    public Map<String, Object> obtenerParaRevisar(long id) {
        // Bloquea solo la fila de la sugerencia (los LEFT JOIN a core_usuario no admiten FOR
        // UPDATE).
        Long bloqueada =
                jdbc.query(
                        "SELECT id FROM mod06_sugerencia_ia WHERE id = :id FOR UPDATE",
                        Map.of("id", id),
                        rs -> rs.next() ? rs.getLong(1) : null);
        return bloqueada == null ? null : obtener(bloqueada);
    }

    @Override
    public List<Map<String, Object>> listar(
            String tipo, String estado, String entidad, Long registroId) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tipo", tipo)
                        .addValue("estado", estado)
                        .addValue("entidad", entidad)
                        .addValue("registro", registroId);
        return jdbc.query(
                SELECT
                        + """
                         WHERE s.tipo = :tipo
                           AND (CAST(:estado AS varchar) IS NULL OR s.estado = :estado)
                           AND (CAST(:entidad AS varchar) IS NULL OR (s.entidad = :entidad AND s.registro_id = :registro))
                         ORDER BY s.fecha_creacion DESC, s.id DESC
                        """,
                p,
                SugerenciasIaRepository::fila);
    }

    @Override
    @Transactional
    public Map<String, Object> guardarRevision(
            long id, Map<String, Object> cambios, String motivo) {
        Map<String, Object> antes = obtener(id);
        long usuario = id(cambios.get("usuario_modificacion_id"));
        jdbc.update(
                """
                UPDATE mod06_sugerencia_ia
                SET estado = :estado, validador_id = :validador, fecha_validacion = :validacion,
                    contenido = CAST(:contenido AS jsonb), fecha_modificacion = :modificacion,
                    usuario_modificacion_id = :usuario
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("estado", cambios.get("estado"))
                        .addValue("validador", idNulo(cambios.get("validador_id")))
                        .addValue("validacion", instante(cambios.get("fecha_validacion")))
                        .addValue("contenido", JsonPy.dumpsRespuesta(cambios.get("contenido")))
                        .addValue("modificacion", instante(cambios.get("fecha_modificacion")))
                        .addValue("usuario", usuario));
        Map<String, Object> despues = obtener(id);
        auditoria.registrarCambio(
                ENTIDAD_AUDITORIA,
                id,
                "CambioEstado",
                resumen(antes),
                resumen(despues),
                motivo,
                usuario);
        return despues;
    }

    // ------------------------------------------------------------------ utilidades

    /**
     * Lo que se guarda en la auditoría: el estado y la decisión, no la salida completa de la IA.
     */
    private static Map<String, Object> resumen(Map<String, Object> s) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tipo", s.get("tipo"));
        r.put("estado", s.get("estado"));
        r.put("entidad", s.get("entidad"));
        r.put("registro_id", s.get("registro_id"));
        if (s.get("contenido") instanceof Map<?, ?> c && c.get("decision") instanceof Map<?, ?> d) {
            r.put("resumen_decision", d.get("resumen"));
        }
        return r;
    }

    private static Map<String, Object> fila(ResultSet rs, int n) throws SQLException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", rs.getLong("id"));
        r.put("tipo", rs.getString("tipo"));
        r.put("entidad", rs.getString("entidad"));
        r.put("registro_id", rs.getObject("registro_id", Long.class));
        r.put("archivo_id", rs.getObject("archivo_id", Long.class));
        r.put("contenido", JsonPy.loads(rs.getString("contenido")));
        r.put("estado", rs.getString("estado"));
        r.put("validador_id", rs.getObject("validador_id", Long.class));
        r.put("validador_nombre", rs.getString("validador_nombre"));
        r.put("fecha_validacion", iso(rs.getObject("fecha_validacion", OffsetDateTime.class)));
        r.put("fecha_creacion", iso(rs.getObject("fecha_creacion", OffsetDateTime.class)));
        r.put("usuario_creacion_id", rs.getObject("usuario_creacion_id", Long.class));
        r.put("usuario_creacion_nombre", rs.getString("usuario_creacion_nombre"));
        r.put("fecha_modificacion", iso(rs.getObject("fecha_modificacion", OffsetDateTime.class)));
        r.put("usuario_modificacion_id", rs.getObject("usuario_modificacion_id", Long.class));
        return r;
    }

    private static String iso(OffsetDateTime instante) {
        return instante == null ? null : Fechas.isoformat(instante.atZoneSameInstant(Fechas.LIMA));
    }

    private static OffsetDateTime instante(Object iso) {
        return iso == null ? null : Fechas.aFechaHora(iso).toOffsetDateTime();
    }

    private static Long idNulo(Object valor) {
        return valor == null ? null : id(valor);
    }

    private static long id(Object valor) {
        if (valor instanceof Number n) return n.longValue();
        if (valor instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Identificador no válido: " + valor);
    }
}
