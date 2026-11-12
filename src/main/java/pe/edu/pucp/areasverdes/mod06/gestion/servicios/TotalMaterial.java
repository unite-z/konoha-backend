package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

/**
 * Total de un tipo de movimiento (consumido o propagado) por espacio, tipo de material y unidad
 * (CU-MOD05-029, pasos 2 y 3). {@code cantidad}: texto decimal con dos decimales ({@code api.md}).
 * Sin espacio: {@code areaVerdeId} nulo y {@code espacio} «Sin espacio» (flujo 2a).
 */
public record TotalMaterial(
        Long areaVerdeId,
        String espacio,
        String tipoMaterial,
        String categoria,
        String unidad,
        String cantidad,
        int movimientos) {}
