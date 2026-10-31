package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.LocalDate;

/**
 * Filtros aplicados al panel (HU-MOD06-001, criterio 1); los no indicados van a {@code null}. La
 * cuadrilla y el sector no se declaran: sus datos aún no están en el sistema.
 */
public record FiltrosPanel(
        LocalDate desde,
        LocalDate hasta,
        Long areaVerdeId,
        Long tipoServicioId,
        String estado,
        String modalidad) {}
