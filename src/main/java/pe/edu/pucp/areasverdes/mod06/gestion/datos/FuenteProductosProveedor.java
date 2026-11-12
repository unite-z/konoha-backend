package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Productos agronómicos aplicados por los proveedores en las atenciones tercerizadas
 * (CU-MOD05-025), en solo lectura. Las tablas son de MOD-05: el MOD-06 nunca las copia ni escribe
 * en ellas (docs/estandares/base-datos.md).
 */
public interface FuenteProductosProveedor {

    /**
     * Aplicaciones de las atenciones ya iniciadas cuya fecha de inicio cae en {@code periodo}. Si
     * {@code areaVerdeId} no es null, solo las de atenciones con ese espacio en su alcance, directo
     * o a través de un ejemplar. Nunca devuelve null.
     */
    List<ProductoAplicado> aplicaciones(RangoFechas periodo, Long areaVerdeId);
}
