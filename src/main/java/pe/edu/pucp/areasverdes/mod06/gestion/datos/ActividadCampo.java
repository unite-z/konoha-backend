package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.LocalDate;

/**
 * Una actividad del personal propio (MOD-04: {@code mod04_actividad} con su tipo, su parte diario,
 * su espacio y su cuadrilla), en solo lectura.
 *
 * @param fecha fecha local (America/Lima) de la actividad
 * @param estado SUPUESTO (pendiente de MOD-04): el estado del parte diario, {@code Abierto} o
 *     {@code Cerrado}; {@code mod04_actividad} no tiene estado propio
 * @param areaVerdeId espacio de la actividad
 */
public record ActividadCampo(
        long id,
        LocalDate fecha,
        String estado,
        String tipoActividad,
        Long areaVerdeId,
        String espacio,
        String cuadrilla) {}
