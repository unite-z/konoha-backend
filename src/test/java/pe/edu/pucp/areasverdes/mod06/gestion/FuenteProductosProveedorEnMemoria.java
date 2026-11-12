package pe.edu.pucp.areasverdes.mod06.gestion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteProductosProveedor;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ProductoAplicado;

/**
 * Doble de {@link FuenteProductosProveedor} en memoria: filtra por periodo y guarda las llamadas.
 * El filtro por espacio lo resuelve la consulta real; aquí solo se registra el espacio pedido.
 */
class FuenteProductosProveedorEnMemoria implements FuenteProductosProveedor {

    private static final AtomicLong IDS = new AtomicLong();

    private final List<ProductoAplicado> datos;
    final List<RangoFechas> periodosPedidos = new ArrayList<>();
    final List<Long> areasPedidas = new ArrayList<>();

    private FuenteProductosProveedorEnMemoria(List<ProductoAplicado> datos) {
        this.datos = List.copyOf(datos);
    }

    static FuenteProductosProveedorEnMemoria con(ProductoAplicado... datos) {
        return new FuenteProductosProveedorEnMemoria(List.of(datos));
    }

    static FuenteProductosProveedorEnMemoria vacia() {
        return con();
    }

    @Override
    public List<ProductoAplicado> aplicaciones(RangoFechas periodo, Long areaVerdeId) {
        periodosPedidos.add(periodo);
        areasPedidas.add(areaVerdeId);
        return datos.stream().filter(a -> periodo.contiene(a.fecha())).toList();
    }

    // --- Constructores de datos ------------------------------------------------------------------

    static ProductoAplicado aplicacion(
            String fecha,
            long atencionId,
            String producto,
            String principioActivo,
            String dosis,
            String proveedor) {
        return new ProductoAplicado(
                IDS.incrementAndGet(),
                atencionId,
                "AT-" + atencionId,
                LocalDate.parse(fecha),
                producto.hashCode(),
                producto,
                principioActivo,
                dosis,
                proveedor.hashCode(),
                proveedor);
    }
}
