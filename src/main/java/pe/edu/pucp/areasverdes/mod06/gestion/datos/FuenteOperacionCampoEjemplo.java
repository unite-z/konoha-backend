package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * {@link FuenteOperacionCampo} con datos de EJEMPLO ({@link EjemplosCampo}), solo para desarrollo:
 * se activa con {@code MOD06_DATOS_EJEMPLO=true} y entonces sustituye a {@link
 * FuenteOperacionCampoNoDisponible}. Sin esa variable (producción y pruebas) no existe.
 *
 * <p>TODO: borrar cuando exista el adaptador real sobre las tablas de MOD-04.
 */
@Component
@Primary
@ConditionalOnProperty(name = "mod06.datos-ejemplo", havingValue = "true")
public class FuenteOperacionCampoEjemplo implements FuenteOperacionCampo {

    private final Clock reloj;

    public FuenteOperacionCampoEjemplo(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public Disponibilidad disponibilidad() {
        return Disponibilidad.DISPONIBLE;
    }

    @Override
    public List<ActividadCampo> actividades(RangoFechas periodo, Long areaVerdeId) {
        return EjemplosCampo.actividades(periodo, areaVerdeId, reloj);
    }

    @Override
    public List<ParticipacionOperario> participaciones(RangoFechas periodo, Long areaVerdeId) {
        return EjemplosCampo.participaciones(periodo, areaVerdeId, reloj);
    }
}
