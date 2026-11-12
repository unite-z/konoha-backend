package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.LocalDate;

/**
 * La participación de un operario en una actividad (MOD-04: {@code mod04_participacion_actividad}),
 * con la fecha, la cuadrilla y el espacio de la actividad.
 */
public record ParticipacionOperario(
        long actividadId,
        LocalDate fecha,
        long operarioId,
        String operario,
        String cuadrilla,
        Long areaVerdeId,
        String espacio) {}
