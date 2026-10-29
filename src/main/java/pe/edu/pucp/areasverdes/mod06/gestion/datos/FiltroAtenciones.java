package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/** Filtros comunes de las consultas sobre atenciones tercerizadas. null = sin filtro. */
public record FiltroAtenciones(
        RangoFechas periodo, Long areaVerdeId, Long tipoServicioId, String estado) {}
