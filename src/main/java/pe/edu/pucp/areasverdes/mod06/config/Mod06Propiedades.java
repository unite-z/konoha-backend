package pe.edu.pucp.areasverdes.mod06.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del MOD-06 (equivale a la parte del MOD-06 de {@code config/settings.py}).
 *
 * <p>Se lee de {@code backend/.env} con los mismos nombres de variables que el Django:
 * {@code DATA_SOURCE}, {@code MOCK_DATA_DIR}, {@code GEMINI_API_KEY} y {@code GEMINI_MODEL}.
 *
 * <ul>
 *   <li>{@code dataSource}: solo lo lee {@code SelectorFuenteDatos}, que valida que sea {@code mock} o {@code db}.</li>
 *   <li>{@code mockDataDir}: relativo a {@code backend/} (el directorio de trabajo al arrancar).</li>
 *   <li>{@code geminiApiKey}: nunca se registra ni se muestra (RNF-008); {@link #toString()} no la incluye.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "mod06")
public class Mod06Propiedades {

    private final String dataSource;
    private final String mockDataDir;
    private final String geminiApiKey;
    private final String geminiModel;

    public Mod06Propiedades(String dataSource, String mockDataDir, String geminiApiKey, String geminiModel) {
        this.dataSource = dataSource == null ? "mock" : dataSource;
        this.mockDataDir = mockDataDir == null ? "../mock-data" : mockDataDir;
        this.geminiApiKey = geminiApiKey == null ? "" : geminiApiKey;
        this.geminiModel = geminiModel == null ? "" : geminiModel;
    }

    /** Valor tal como viene de {@code DATA_SOURCE}. */
    public String dataSource() {
        return dataSource;
    }

    /** {@code (BASE_DIR / MOCK_DATA_DIR).resolve()}: ruta absoluta desde {@code backend/}. */
    public Path mockDataDir() {
        Path ruta = Path.of(System.getProperty("user.dir")).resolve(mockDataDir).toAbsolutePath().normalize();
        try {
            return Files.exists(ruta) ? ruta.toRealPath() : ruta;
        } catch (IOException e) {
            return ruta;
        }
    }

    public String geminiApiKey() {
        return geminiApiKey;
    }

    public String geminiModel() {
        return geminiModel;
    }

    @Override
    public String toString() {
        return "Mod06Propiedades[dataSource=" + dataSource + ", mockDataDir=" + mockDataDir + ", geminiModel="
            + geminiModel + ", geminiApiKey=" + (geminiApiKey.isEmpty() ? "(vacía)" : "(oculta)") + "]";
    }
}
