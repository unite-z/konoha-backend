package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * {@link FuenteOperacionCampo} mientras las tablas de MOD-04 no estén en {@code main}: sin datos y
 * con su motivo (Guía de Estilo Web 5.7), nunca un 0.
 *
 * <p>TODO: las tablas {@code mod04_actividad}, {@code mod04_tipo_actividad}, {@code
 * mod04_participacion_actividad} y {@code mod04_cuadrilla} existen solo en la rama {@code
 * feat/mod04-cuadrillas-encargos-participantes}. Cuando se fusionen, se crea el adaptador JDBC de
 * solo lectura sobre ellas y se borra esta clase.
 *
 * <p>Ya no es un bean: el adaptador real es {@link FuenteOperacionCampoMod04} (AV-228). Se conserva
 * para las pruebas de «fuente no disponible», que comprueban el «Sin datos» con su motivo.
 */
public class FuenteOperacionCampoNoDisponible implements FuenteOperacionCampo {

    public static final String MOTIVO =
            "Las actividades del personal propio y la participación de los operarios todavía no"
                    + " están disponibles en el sistema";

    @Override
    public Disponibilidad disponibilidad() {
        return Disponibilidad.noDisponible(MOTIVO);
    }

    @Override
    public List<ActividadCampo> actividades(RangoFechas periodo, Long areaVerdeId) {
        return List.of();
    }

    @Override
    public List<ParticipacionOperario> participaciones(RangoFechas periodo, Long areaVerdeId) {
        return List.of();
    }
}
