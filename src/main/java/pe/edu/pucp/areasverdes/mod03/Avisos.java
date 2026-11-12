package pe.edu.pucp.areasverdes.mod03;

import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Avisos dentro de la aplicación (AV-264; ADR 0003, sin correo). El destinatario es un usuario o un
 * proveedor; los de un proveedor quedan registrados hasta que tenga cuenta (MOD-05).
 */
@Service
public class Avisos {

    public record Aviso(
            long id,
            String texto,
            String origen,
            long origenId,
            boolean leido,
            OffsetDateTime fechaHora) {}

    private final NamedParameterJdbcTemplate jdbc;

    public Avisos(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    long aUsuario(long usuario, String texto, String origen, long origenId) {
        return crear("destinatario_usuario_id", usuario, texto, origen, origenId);
    }

    long aProveedor(long proveedor, String texto, String origen, long origenId) {
        return crear("destinatario_proveedor_id", proveedor, texto, origen, origenId);
    }

    /** Los avisos del usuario, del más reciente al más antiguo. */
    public List<Aviso> mios(UsuarioPlanificacion usuario) {
        if (usuario == null) {
            throw ErrorPlanificacion.sinPermiso();
        }
        return jdbc.query(
                """
                SELECT id, texto, origen, origen_id, leido, fecha_hora FROM mod03_aviso
                WHERE destinatario_usuario_id = :usuario ORDER BY fecha_hora DESC, id DESC LIMIT 100
                """,
                Map.of("usuario", usuario.id()),
                (rs, n) ->
                        new Aviso(
                                rs.getLong("id"),
                                rs.getString("texto"),
                                rs.getString("origen"),
                                rs.getLong("origen_id"),
                                rs.getBoolean("leido"),
                                rs.getObject("fecha_hora", OffsetDateTime.class)
                                        .atZoneSameInstant(LIMA)
                                        .toOffsetDateTime()));
    }

    /** Marca como leído un aviso propio; 404 si no es suyo. */
    public void marcarLeido(long id, UsuarioPlanificacion usuario) {
        if (usuario == null) {
            throw ErrorPlanificacion.sinPermiso();
        }
        int filas =
                jdbc.update(
                        "UPDATE mod03_aviso SET leido = true WHERE id = :id AND"
                                + " destinatario_usuario_id = :usuario",
                        Map.of("id", id, "usuario", usuario.id()));
        if (filas == 0) {
            throw new ErrorPlanificacion(404, "aviso-no-encontrado", "No existe ese aviso");
        }
    }

    private long crear(
            String columna, long destinatario, String texto, String origen, long origenId) {
        return jdbc.queryForObject(
                "INSERT INTO mod03_aviso (%s, texto, origen, origen_id) VALUES (:destinatario, :texto,"
                                .formatted(columna)
                        + " :origen, :origenId) RETURNING id",
                new MapSqlParameterSource()
                        .addValue("destinatario", destinatario)
                        .addValue("texto", texto.length() > 300 ? texto.substring(0, 300) : texto)
                        .addValue("origen", origen)
                        .addValue("origenId", origenId),
                Long.class);
    }
}
