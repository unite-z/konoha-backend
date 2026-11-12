package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

/**
 * Total de los traslados por origen, destino, tipo de material y unidad (HU-MOD05-029 v2: «el
 * material trasladado entre espacios, con su origen y destino»). Un origen o destino nulo sale como
 * «Sin espacio».
 */
public record TotalTraslado(
        Long origenAreaId,
        String origen,
        Long destinoAreaId,
        String destino,
        String tipoMaterial,
        String categoria,
        String unidad,
        String cantidad,
        int movimientos) {}
