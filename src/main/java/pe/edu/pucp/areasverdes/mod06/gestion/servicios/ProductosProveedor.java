package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.util.List;

/**
 * Vista aparte de los productos agronómicos aplicados por los proveedores (RF-REP-005, flujo 3a),
 * sin mezclarlos con el material de las cuadrillas. Conserva {@code sinDatos} y {@code
 * mensajeSinDatos} de la versión anterior del contrato, que solo traía esas dos claves.
 */
public record ProductosProveedor(
        boolean sinDatos, String mensajeSinDatos, List<TotalProductoProveedor> productos) {}
