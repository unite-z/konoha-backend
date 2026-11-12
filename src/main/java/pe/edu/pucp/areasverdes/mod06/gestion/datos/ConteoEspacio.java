package pe.edu.pucp.areasverdes.mod06.gestion.datos;

/** Pares (atención, espacio) de un espacio; {@code areaVerdeId} nulo = sin espacio. */
public record ConteoEspacio(Long areaVerdeId, String espacio, long total) {}
