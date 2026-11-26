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

    /**
     * Tema, acento y diseño nuevo (AV-414: {@code null} = nunca eligió); cualquiera puede faltar
     * (la interfaz usa el predeterminado).
     */
    public record Apariencia(String tema, String acento, Boolean disenoNuevo) {}

    private final NamedParameterJdbcTemplate jdbc;

    public AparienciaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** La del usuario, o {@code null} si nunca la eligió. */
    public Apariencia de(long usuarioId) {
        return jdbc
                .query(
                        "SELECT apariencia_tema, apariencia_acento, apariencia_diseno_nuevo FROM"
                                + " core_usuario WHERE id = :id AND (apariencia_tema IS NOT NULL OR"
                                + " apariencia_acento IS NOT NULL OR apariencia_diseno_nuevo IS NOT"
                                + " NULL)",
                        Map.of("id", usuarioId),
                        (rs, n) ->
                                new Apariencia(
                                        rs.getString("apariencia_tema"),
                                        rs.getString("apariencia_acento"),
                                        (Boolean) rs.getObject("apariencia_diseno_nuevo")))
                .stream()
                .findFirst()
                .orElse(null);
    }

    public void guardar(long usuarioId, Apariencia apariencia) {
        jdbc.update(
                "UPDATE core_usuario SET apariencia_tema = :tema, apariencia_acento = :acento,"
                        + " apariencia_diseno_nuevo = :disenoNuevo WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", usuarioId)
                        .addValue("tema", apariencia.tema())
                        .addValue("acento", apariencia.acento())
                        .addValue("disenoNuevo", apariencia.disenoNuevo()));
    }
}
