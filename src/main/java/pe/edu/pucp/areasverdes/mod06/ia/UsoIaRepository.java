package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Registro de cada llamada a la IA en {@code mod06_uso_ia} (AV-396): tiempo, resultado y tokens.
 */
@Repository
public class UsoIaRepository {

    private static final Logger log = LoggerFactory.getLogger(UsoIaRepository.class);

    private final NamedParameterJdbcTemplate jdbc;

    public UsoIaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Nunca rompe la operación de IA: si no se puede registrar, solo queda en el log. */
    public void registrar(
            String asistente,
            String proveedor,
            String modelo,
            long duracionMs,
            String error,
            ClienteIA.Uso uso) {
        Map<String, Object> p = new HashMap<>();
        p.put("asistente", asistente);
        p.put("proveedor", proveedor);
        p.put("modelo", modelo == null ? "" : modelo);
        p.put("duracion", (int) Math.min(duracionMs, Integer.MAX_VALUE));
        p.put("correcto", error == null);
        p.put("error", error == null ? null : error.substring(0, Math.min(error.length(), 300)));
        p.put("entrada", uso == null ? null : uso.entrada());
        p.put("salida", uso == null ? null : uso.salida());
        p.put("cache", uso == null ? null : uso.cache());
        try {
            jdbc.update(
                    "INSERT INTO mod06_uso_ia (asistente, proveedor, modelo, duracion_ms, correcto,"
                            + " error, tokens_entrada, tokens_salida, tokens_cache) VALUES"
                            + " (:asistente, :proveedor, :modelo, :duracion, :correcto, :error,"
                            + " :entrada, :salida, :cache)",
                    p);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el uso de la IA ({})", e.getClass().getSimpleName());
        }
    }
}
