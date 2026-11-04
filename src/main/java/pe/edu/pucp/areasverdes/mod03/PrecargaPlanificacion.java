package pe.edu.pucp.areasverdes.mod03;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Precarga de MOD-03 en el perfil dev, después de la de MOD-05: marca los tipos de servicio
 * periódicos y ruidosos de {@code mod03/TipoServicio.json} (AV-368) y carga los valores iniciales
 * de la sección (AV-363). Solo cambia los tipos que siguen con el valor por defecto, para no pisar
 * lo que haya cambiado alguien.
 */
@Service
public class PrecargaPlanificacion {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ValoresIniciales valoresIniciales;

    public PrecargaPlanificacion(
            NamedParameterJdbcTemplate jdbc, ObjectMapper json, ValoresIniciales valoresIniciales) {
        this.jdbc = jdbc;
        this.json = json;
        this.valoresIniciales = valoresIniciales;
    }

    @Transactional
    public void cargar(Path carpeta) {
        List<Map<String, Object>> tipos;
        try {
            tipos =
                    json.readValue(
                            carpeta.resolve("mod03/TipoServicio.json").toFile(),
                            new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Precarga de MOD-03 inválida", e);
        }
        for (Map<String, Object> tipo : tipos) {
            if (!Boolean.TRUE.equals(tipo.get("es_periodico"))) {
                continue;
            }
            jdbc.update(
                    """
                    UPDATE mod05_tiposervicio SET es_periodico = true, es_ruidoso = :ruidoso
                    WHERE id = :id AND nombre = :nombre AND NOT es_periodico
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", ((Number) tipo.get("id")).longValue())
                            .addValue("nombre", tipo.get("nombre"))
                            .addValue("ruidoso", Boolean.TRUE.equals(tipo.get("es_ruidoso"))));
        }
        valoresIniciales.precargar();
    }
}
