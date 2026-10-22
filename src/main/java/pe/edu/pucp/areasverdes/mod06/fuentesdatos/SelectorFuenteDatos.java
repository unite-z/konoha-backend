package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

import java.util.Locale;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.config.ConfiguracionInvalida;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;

/**
 * Selección de la fuente de datos del MOD-06 (patrón Estrategia).
 *
 * <p>{@link #obtenerFuenteDatos()} es el <b>único</b> lugar del código que lee {@code DATA_SOURCE}:
 * <ul>
 *   <li>{@code mock} → {@link FuenteDatosMock} (JSON en {@code mock-data/});</li>
 *   <li>{@code db} → {@link FuenteDatosBD} (aún no disponible: responde 503).</li>
 * </ul>
 * Ningún otro componente debe leer {@code DATA_SOURCE} ni abrir los JSON directamente.
 *
 * <p>Como en {@code settings.py}, un valor distinto de {@code mock} o {@code db} impide arrancar.
 */
@Component
public class SelectorFuenteDatos {

    private final Mod06Propiedades propiedades;
    private final String dataSource;

    public SelectorFuenteDatos(Mod06Propiedades propiedades) {
        this.propiedades = propiedades;
        this.dataSource = Py.strip(propiedades.dataSource()).toLowerCase(Locale.ROOT);
        if (!dataSource.equals("mock") && !dataSource.equals("db")) {
            throw new ConfiguracionInvalida(
                "DATA_SOURCE='" + dataSource + "' no es válido. Usa 'mock' o 'db' en backend/.env.");
        }
    }

    /** Devuelve la fuente de datos configurada en {@code DATA_SOURCE} ({@code mock} o {@code db}). */
    public FuenteDatos obtenerFuenteDatos() {
        if (dataSource.equals("db")) return new FuenteDatosBD();
        return new FuenteDatosMock(propiedades.mockDataDir());
    }
}
