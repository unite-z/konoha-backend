package pe.edu.pucp.areasverdes.mod01.datos;

import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Apariencia de la interfaz que cada usuario elige en Configuración (AV-391): tema y color de
 * acento, en {@code core_usuario}. Es una preferencia personal: no se audita.
 */
@Repository
public class AparienciaRepository {

    /** Tema y acento; cualquiera de los dos puede faltar (la interfaz usa el predeterminado). */
    public record Apariencia(String tema, String acento) {}

    private final NamedParameterJdbcTemplate jdbc;

    public AparienciaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** La del usuario, o {@code null} si nunca la eligió. */
    public Apariencia de(long usuarioId) {
        return jdbc
                .query(
                        "SELECT apariencia_tema, apariencia_acento FROM core_usuario WHERE id = :id"
                            + " AND (apariencia_tema IS NOT NULL OR apariencia_acento IS NOT NULL)",
                        Map.of("id", usuarioId),
                        (rs, n) ->
                                new Apariencia(
                                        rs.getString("apariencia_tema"),
                                        rs.getString("apariencia_acento")))
                .stream()
                .findFirst()
                .orElse(null);
    }

    public void guardar(long usuarioId, Apariencia apariencia) {
        jdbc.update(
                "UPDATE core_usuario SET apariencia_tema = :tema, apariencia_acento = :acento"
                        + " WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", usuarioId)
                        .addValue("tema", apariencia.tema())
                        .addValue("acento", apariencia.acento()));
    }
}
