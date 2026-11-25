package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.LocalDate;

/**
 * Filtros aplicados al panel (HU-MOD06-001, criterio 1); los no indicados van a {@code null}. El
 * panel todavía no filtra por cuadrilla ni por sector (ver {@link
 * PanelService#NOTA_FILTROS_PENDIENTES}).
 */
public record FiltrosPanel(
        LocalDate desde,
        LocalDate hasta,
        Long areaVerdeId,
        Long tipoServicioId,
        String estado,
        String modalidad) {}
