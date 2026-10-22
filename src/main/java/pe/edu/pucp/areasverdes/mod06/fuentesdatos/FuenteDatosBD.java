package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.comun.errores.FuenteDatosNoDisponible;

/**
 * Fuente de datos real ({@code DATA_SOURCE=db}): <b>aún no disponible</b>.
 *
 * <p>Plan:
 * <ul>
 *   <li>Cada método consultará las tablas del módulo propietario de la entidad (Modelo de BD v1, tabla 2) y
 *   devolverá {@code Map} con los mismos nombres de atributo que hoy usan los JSON de {@code mock-data/}. Así los
 *   servicios no cambian.</li>
 *   <li>{@code crear} y {@code actualizar} seguirán limitados a SugerenciaIA y ReporteGenerado (entidades propias del
 *   MOD-06).</li>
 *   <li>El contenido de los archivos se leerá del almacenamiento de RT-003 (servicio de objetos, por definir).</li>
 * </ul>
 *
 * <p>Mientras tanto, todos los métodos lanzan {@link FuenteDatosNoDisponible} (HTTP 503).
 * TODO: implementar cuando existan la base PostgreSQL con PostGIS y los modelos de los módulos propietarios.
 */
public class FuenteDatosBD implements FuenteDatos {

    public static final String MENSAJE_BD_NO_DISPONIBLE =
        "La fuente de datos 'db' aún no está disponible: faltan los modelos de los módulos propietarios "
            + "y la base PostgreSQL con PostGIS. Usa DATA_SOURCE=mock";

    private static FuenteDatosNoDisponible noDisponible() {
        return new FuenteDatosNoDisponible(MENSAJE_BD_NO_DISPONIBLE);
    }

    @Override
    public List<Map<String, Object>> listar(String entidad, Map<String, Object> igualA) {
        throw noDisponible();
    }

    @Override
    public Map<String, Object> obtener(String entidad, Object id) {
        throw noDisponible();
    }

    @Override
    public Map<String, Object> crear(String entidad, Map<String, Object> datos) {
        throw noDisponible();
    }

    @Override
    public Map<String, Object> actualizar(String entidad, Object id, Map<String, Object> cambios) {
        throw noDisponible();
    }

    @Override
    public byte[] leerContenidoArchivo(Map<String, Object> archivo) {
        throw noDisponible();
    }
}
