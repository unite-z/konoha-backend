package pe.edu.pucp.areasverdes.mod06.gestion.datos;

/**
 * Actividades del personal propio, su estado y tipo, y la participación de los operarios
 * (Actividad, TipoActividad, ParticipacionActividad, Cuadrilla: MOD-04). Hoy solo tiene {@link
 * #disponibilidad()}. Las consultas se añadirán, con la forma que fije MOD-04, cuando sus tablas
 * estén en main; hasta entonces no se inventan sus firmas.
 */
public interface FuenteOperacionCampo {

    Disponibilidad disponibilidad();
}
