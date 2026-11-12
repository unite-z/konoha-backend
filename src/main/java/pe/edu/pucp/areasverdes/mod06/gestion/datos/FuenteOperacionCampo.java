package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Actividades del personal propio, su estado y tipo, y la participación de los operarios
 * (Actividad, TipoActividad, ParticipacionActividad, Cuadrilla: MOD-04), en solo lectura. Hoy hay
 * {@link FuenteOperacionCampoNoDisponible} y, solo para desarrollo, {@link
 * FuenteOperacionCampoEjemplo}. Las firmas pueden cambiar cuando se programe el adaptador sobre las
 * tablas de MOD-04.
 */
public interface FuenteOperacionCampo {

    Disponibilidad disponibilidad();

    /**
     * Actividades con fecha en {@code periodo}; si {@code areaVerdeId} no es null, solo las de ese
     * espacio. Nunca devuelve null.
     */
    List<ActividadCampo> actividades(RangoFechas periodo, Long areaVerdeId);

    /** Participaciones de operarios en esas mismas actividades. Nunca devuelve null. */
    List<ParticipacionOperario> participaciones(RangoFechas periodo, Long areaVerdeId);
}
