package pe.edu.pucp.areasverdes.mod06.ia;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Proveedor y modelo de IA elegidos en Configuración (AV-395), en {@code mod06_configuracion_ia}.
 * Sin fila manda el {@code api.env}.
 */
@Repository
public class ConfiguracionIaRepository {

    /** Modelos que se pueden elegir por proveedor: lista cerrada para no guardar erratas. */
    public static final Map<String, List<String>> MODELOS =
            Map.of(
                    "gemini",
                    List.of(
                            "gemini-3.8-flash",
                            "gemini-3.7-flash",
                            "gemini-3.6-flash",
                            "gemini-3.5-flash"),
                    "deepseek",
                    List.of("deepseek-flash", "deepseek-v4-pro"));

    public record Elegida(
            String proveedor, String modelo, Long actualizadoPor, OffsetDateTime actualizadoEn) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ConfiguracionIaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Elegida> leer() {
        return jdbc
                .query(
                        "SELECT proveedor, modelo, actualizado_por, actualizado_en"
                                + " FROM mod06_configuracion_ia WHERE id = 1",
                        Map.of(),
                        (fila, n) ->
                                new Elegida(
                                        fila.getString("proveedor"),
                                        fila.getString("modelo"),
                                        fila.getObject("actualizado_por", Long.class),
                                        fila.getObject("actualizado_en", OffsetDateTime.class)))
                .stream()
                .findFirst();
    }

    public void guardar(String proveedor, String modelo, long usuarioId) {
        jdbc.update(
                "INSERT INTO mod06_configuracion_ia (id, proveedor, modelo, actualizado_por)"
                        + " VALUES (1, :proveedor, :modelo, :usuario)"
                        + " ON CONFLICT (id) DO UPDATE SET proveedor = EXCLUDED.proveedor,"
                        + " modelo = EXCLUDED.modelo, actualizado_por = EXCLUDED.actualizado_por,"
                        + " actualizado_en = now()",
                Map.of("proveedor", proveedor, "modelo", modelo, "usuario", usuarioId));
    }
}
