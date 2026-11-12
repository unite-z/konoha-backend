package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.LocalDate;

/**
 * Filtros aplicados al mapa de atenciones (HU-MOD06-003, criterio 1, y v2); los no indicados van a
 * {@code null}. «Actividad» es {@code tipoServicioId} y «nivel de riesgo», {@code esRiesgo}.
 */
public record FiltrosMapa(
        LocalDate desde,
        LocalDate hasta,
        String tipoAtencion,
        Long tipoUsoId,
        Long areaVerdeId,
        Long tipoServicioId,
        String estado,
        String modalidad,
        Boolean esRiesgo) {}
