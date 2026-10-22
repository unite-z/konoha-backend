package pe.edu.pucp.areasverdes.mod04.datos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.AreasVerdesApplication;
import pe.edu.pucp.areasverdes.mod05.ApiError;

@Repository
public class Mod04Repository {
    final NamedParameterJdbcTemplate jdbc;
    final ObjectMapper json;

    public Mod04Repository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    public List<Map<String, Object>> rows(String sql, Map<String, ?> params) {
        return jdbc.query(
                sql,
                params,
                (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        Object value = rs.getObject(i);
                        if (value instanceof Timestamp t)
                            value =
                                    t.toInstant()
                                            .atZone(AreasVerdesApplication.LIMA)
                                            .toOffsetDateTime();
                        else if (value instanceof java.sql.Date d) value = d.toLocalDate();
                        row.put(rs.getMetaData().getColumnLabel(i), value);
                    }
                    return row;
                });
    }

    public Map<String, Object> one(String sql, Map<String, ?> params) {
        var rows = rows(sql, params);
        if (rows.isEmpty()) throw ApiError.missing("No existe el registro solicitado");
        return rows.getFirst();
    }

    // Tabla y columnas son constantes internas; todos los valores son parámetros enlazados.
    private String tabla(String nombre) {
        return switch (nombre) {
            case "areaverde" -> "mod05_areaverde";
            case "personal",
                    "cuadrilla",
                    "asignacion_cuadrilla",
                    "tipo_actividad",
                    "encargo",
                    "parte_diario",
                    "actividad",
                    "participacion_actividad",
                    "registro_auditoria" ->
                    "mod04_" + nombre;
            default -> throw new IllegalArgumentException("Tabla no admitida");
        };
    }

    public Map<String, Object> get(String table, long id, boolean lock) {
        return one(
                "SELECT * FROM " + tabla(table) + " WHERE id=:id" + (lock ? " FOR UPDATE" : ""),
                Map.of("id", id));
    }

    public long insert(String table, Map<String, Object> data) {
        String keys = String.join(",", data.keySet());
        String values = String.join(",", data.keySet().stream().map(k -> ":" + k).toList());
        return jdbc.queryForObject(
                "INSERT INTO "
                        + tabla(table)
                        + " ("
                        + keys
                        + ") VALUES ("
                        + values
                        + ") RETURNING id",
                data,
                Long.class);
    }

    public void update(String table, long id, Map<String, Object> data) {
        var params = new HashMap<>(data);
        params.put("id", id);
        String sets = String.join(",", data.keySet().stream().map(k -> k + "=:" + k).toList());
        jdbc.update("UPDATE " + tabla(table) + " SET " + sets + " WHERE id=:id", params);
    }

    public void audit(
            String entity, long id, String action, long user, Object before, Object after) {
        try {
            jdbc.update(
                    """
                    INSERT INTO mod04_registro_auditoria(entidad,registro_id,accion,usuario_id,valores_anteriores,valores_nuevos)
                    VALUES (:e,:id,:a,:u,CAST(:b AS jsonb),CAST(:n AS jsonb))
                    """,
                    Map.of(
                            "e",
                            entity,
                            "id",
                            id,
                            "a",
                            action,
                            "u",
                            user,
                            "b",
                            json.writeValueAsString(before),
                            "n",
                            json.writeValueAsString(after)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la auditoría", e);
        }
    }

    public static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public static Map<String, Object> values(Object... pairs) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) data.put((String) pairs[i], pairs[i + 1]);
        return data;
    }

    public Map<String, Object> cuadrilla(long id) {
        var row =
                one(
                        """
                        SELECT c.*,p.nombres||' '||p.apellidos capataz_nombre FROM mod04_cuadrilla c
                        JOIN mod04_personal p ON p.id=c.capataz_id WHERE c.id=:id
                        """,
                        Map.of("id", id));
        var members =
                rows(
                        """
                        SELECT p.id,p.nombres,p.apellidos,p.activo FROM mod04_asignacion_cuadrilla a
                        JOIN mod04_personal p ON p.id=a.operario_id
                        WHERE a.cuadrilla_id=:id AND a.fecha_fin IS NULL ORDER BY p.id
                        """,
                        Map.of("id", id));
        row.put("operarios", members);
        row.put("cantidad_operarios", members.size());
        return row;
    }

    public Map<String, Object> encargo(long id) {
        return one(
                """
                SELECT e.*,c.nombre cuadrilla_nombre,p.nombres||' '||p.apellidos capataz_nombre,
                    t.nombre tipo_actividad_nombre,a.nombre area_verde_nombre
                FROM mod04_encargo e JOIN mod04_cuadrilla c ON c.id=e.cuadrilla_id
                JOIN mod04_personal p ON p.id=e.capataz_id JOIN mod04_tipo_actividad t ON t.id=e.tipo_actividad_id
                JOIN mod05_areaverde a ON a.id=e.area_verde_id WHERE e.id=:id
                """,
                Map.of("id", id));
    }

    public Map<String, Object> participantes(long id) {
        var people =
                rows(
                        """
                        SELECT p.id,p.nombres,p.apellidos FROM mod04_participacion_actividad a
                        JOIN mod04_personal p ON p.id=a.operario_id WHERE a.actividad_id=:id ORDER BY p.id
                        """,
                        Map.of("id", id));
        return values("actividad_id", id, "cantidad_operarios", people.size(), "operarios", people);
    }
}
