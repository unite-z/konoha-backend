package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.comun.errores.FuenteDatosNoDisponible;
import pe.edu.pucp.areasverdes.mod06.config.ConfiguracionInvalida;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;

/**
 * Selección de la fuente de datos del MOD-06 (patrón Estrategia).
 *
 * <p>{@link #obtenerFuenteDatos()} es el <b>único</b> lugar del código que lee {@code DATA_SOURCE}:
 *
 * <ul>
 *   <li>{@code db} (por defecto) → {@link FuenteDatosBD}: las tablas de los módulos dueños;
 *   <li>{@code mock} → {@link FuenteDatosMock} (JSON en {@code mock-data/}), solo para demos
 *       locales.
 * </ul>
 *
 * Ningún otro componente debe leer {@code DATA_SOURCE} ni abrir los JSON directamente. Un valor
 * distinto de {@code mock} o {@code db} impide arrancar.
 */
@Component
public class SelectorFuenteDatos {

    private final Mod06Propiedades propiedades;
    private final String dataSource;
    private final ObjectProvider<NamedParameterJdbcTemplate> jdbc;

    /** Sin acceso a la base (pruebas que instalan su propia fuente). */
    public SelectorFuenteDatos(Mod06Propiedades propiedades) {
        this(propiedades, null);
    }

    @Autowired
    public SelectorFuenteDatos(
            Mod06Propiedades propiedades, ObjectProvider<NamedParameterJdbcTemplate> jdbc) {
        this.propiedades = propiedades;
        this.jdbc = jdbc;
        this.dataSource = Py.strip(propiedades.dataSource()).toLowerCase(Locale.ROOT);
        if (!dataSource.equals("mock") && !dataSource.equals("db")) {
            throw new ConfiguracionInvalida(
                    "DATA_SOURCE='"
                            + dataSource
                            + "' no es válido. Usa 'mock' o 'db' en backend/.env.");
        }
    }

    /**
     * Devuelve la fuente de datos configurada en {@code DATA_SOURCE} ({@code db} o {@code mock}).
     */
    public FuenteDatos obtenerFuenteDatos() {
        if (dataSource.equals("db")) {
            NamedParameterJdbcTemplate plantilla = jdbc == null ? null : jdbc.getIfAvailable();
            if (plantilla == null) {
                throw new FuenteDatosNoDisponible("La base de datos no está disponible");
            }
            return new FuenteDatosBD(plantilla);
        }
        return new FuenteDatosMock(propiedades.mockDataDir());
    }
}
