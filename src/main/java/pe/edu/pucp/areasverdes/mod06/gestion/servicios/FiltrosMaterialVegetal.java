package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

/**
 * Filtros aplicados al reporte de material vegetal; los no indicados van a {@code null}.
 *
 * @param mes AAAA-MM
 */
public record FiltrosMaterialVegetal(String mes, Long areaVerdeId) {}
