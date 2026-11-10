package pe.edu.pucp.areasverdes.mod06.gestion.exportaciones;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** {@code mod06_reporte_generado} (V72): alta y consulta del historial de exportaciones. */
@Repository
public class ReportesGeneradosRepository {

    /** Una fila de la tabla con el nombre de quien exportó (MOD-01). */
    public record Fila(
            long id,
            String tipo,
            String formato,
            Map<String, Object> parametros,
            String resumenParametros,
            int filas,
            String archivoClave,
            String archivoNombre,
            String archivoTipoMime,
            long archivoTamanoBytes,
            String usuario,
            OffsetDateTime fechaHora) {}

    /** Lo que se guarda al exportar. */
    public record Nuevo(
            String tipo,
            String formato,
            Map<String, Object> parametros,
            String resumenParametros,
            int filas,
            String archivoClave,
            String archivoNombre,
            String archivoTipoMime,
            long archivoTamanoBytes,
            long usuarioId,
            OffsetDateTime fechaHora) {}

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

    private static final String SELECT =
            """
            SELECT r.id, r.tipo, r.formato, r.parametros::text AS parametros, r.resumen_parametros, r.filas,
                   r.archivo_clave, r.archivo_nombre, r.archivo_tipo_mime, r.archivo_tamano_bytes,
                   u.nombre AS usuario, r.fecha_hora
            FROM mod06_reporte_generado r
            JOIN core_usuario u ON u.id = r.usuario_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ReportesGeneradosRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public long insertar(Nuevo n) {
        try {
            return jdbc.queryForObject(
                    """
                    INSERT INTO mod06_reporte_generado (tipo, formato, parametros, resumen_parametros, filas,
                        archivo_clave, archivo_nombre, archivo_tipo_mime, archivo_tamano_bytes, usuario_id, fecha_hora)
                    VALUES (:tipo, :formato, CAST(:parametros AS jsonb), :resumen, :filas, :clave, :nombre, :mime,
                        :tamano, :usuario, :fecha)
                    RETURNING id
                    """,
                    new MapSqlParameterSource()
                            .addValue("tipo", n.tipo())
                            .addValue("formato", n.formato())
                            .addValue("parametros", json.writeValueAsString(n.parametros()))
                            .addValue("resumen", n.resumenParametros())
                            .addValue("filas", n.filas())
                            .addValue("clave", n.archivoClave())
                            .addValue("nombre", n.archivoNombre())
                            .addValue("mime", n.archivoTipoMime())
                            .addValue("tamano", n.archivoTamanoBytes())
                            .addValue("usuario", n.usuarioId())
                            .addValue("fecha", n.fechaHora()),
                    Long.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Parámetros de exportación no serializables", e);
        }
    }

    public Optional<Fila> obtener(long id) {
        return jdbc.query(SELECT + " WHERE r.id = :id", Map.of("id", id), this::fila).stream()
                .findFirst();
    }

    public long contar(String tipo) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mod06_reporte_generado WHERE CAST(:tipo AS varchar) IS NULL"
                        + " OR tipo = :tipo",
                new MapSqlParameterSource("tipo", tipo),
                Long.class);
    }

    /** Historial, del más reciente al más antiguo. */
    public List<Fila> listar(String tipo, int limite, long desplazamiento) {
        return jdbc.query(
                SELECT
                        + """
                         WHERE CAST(:tipo AS varchar) IS NULL OR r.tipo = :tipo
                         ORDER BY r.fecha_hora DESC, r.id DESC
                         LIMIT :limite OFFSET :desplazamiento
                        """,
                new MapSqlParameterSource()
                        .addValue("tipo", tipo)
                        .addValue("limite", limite)
                        .addValue("desplazamiento", desplazamiento),
                this::fila);
    }

    private Fila fila(ResultSet rs, int n) throws SQLException {
        try {
            return new Fila(
                    rs.getLong("id"),
                    rs.getString("tipo"),
                    rs.getString("formato"),
                    json.readValue(rs.getString("parametros"), MAPA),
                    rs.getString("resumen_parametros"),
                    rs.getInt("filas"),
                    rs.getString("archivo_clave"),
                    rs.getString("archivo_nombre"),
                    rs.getString("archivo_tipo_mime"),
                    rs.getLong("archivo_tamano_bytes"),
                    rs.getString("usuario"),
                    rs.getObject("fecha_hora", OffsetDateTime.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Parámetros de exportación no válidos en la base", e);
        }
    }
}
