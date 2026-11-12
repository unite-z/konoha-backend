package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.util.List;

/**
 * Un producto agronómico aplicado por un proveedor en el mes (RF-REP-005, flujo 3a). La dosis es
 * texto libre en MOD-05, así que no se suma: se listan las distintas.
 *
 * @param aplicaciones atenciones del mes en las que el proveedor aplicó el producto
 * @param dosis dosis distintas registradas, sin las vacías
 * @param atenciones códigos de esas atenciones, en orden de fecha
 */
public record TotalProductoProveedor(
        long productoId,
        String producto,
        String principioActivo,
        long proveedorId,
        String proveedor,
        int aplicaciones,
        List<String> dosis,
        List<String> atenciones) {}
