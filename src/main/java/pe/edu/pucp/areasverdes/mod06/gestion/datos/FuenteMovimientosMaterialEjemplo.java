package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * {@link FuenteMovimientosMaterial} con datos de EJEMPLO ({@link EjemplosCampo}), solo para
 * desarrollo: se activa con {@code MOD06_DATOS_EJEMPLO=true} y entonces sustituye a {@link
 * FuenteMovimientosMaterialNoDisponible}. Sin esa variable (producción y pruebas) no existe.
 *
 * <p>TODO: borrar cuando MOD-04 tenga su tabla de movimientos de material (RF-ACT-005) y exista el
 * adaptador real.
 */
@Component
@Primary
@ConditionalOnProperty(name = "mod06.datos-ejemplo", havingValue = "true")
public class FuenteMovimientosMaterialEjemplo implements FuenteMovimientosMaterial {

    private final Clock reloj;

    public FuenteMovimientosMaterialEjemplo(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public Disponibilidad disponibilidad() {
        return Disponibilidad.DISPONIBLE;
    }

    @Override
    public List<MovimientoMaterial> movimientos(RangoFechas periodo, Long areaVerdeId) {
        return EjemplosCampo.movimientos(periodo, areaVerdeId, reloj);
    }
}
