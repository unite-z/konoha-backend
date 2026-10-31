package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * {@link FuenteMovimientosMaterial} mientras no existan en {@code main} las tablas de movimientos
 * de material de MOD-04 (RF-ACT-005): sin datos y con su motivo (Guía de Estilo Web 5.7), nunca un
 * 0.
 *
 * <p>TODO cuando MOD-04 fusione su tabla de movimientos de material:
 *
 * <ol>
 *   <li>crear {@code FuenteMovimientosMaterialMod04}, con {@code SELECT} parametrizado sobre las
 *       tablas de MOD-04 y la traducción de {@code tipo_movimiento} y {@code categoria};
 *   <li>borrar esta clase.
 * </ol>
 */
@Component
public class FuenteMovimientosMaterialNoDisponible implements FuenteMovimientosMaterial {

    public static final String MOTIVO =
            "El consumo, la propagación y el traslado de material se registran en las actividades"
                    + " de las cuadrillas, que todavía no están disponibles en el sistema";

    @Override
    public Disponibilidad disponibilidad() {
        return Disponibilidad.noDisponible(MOTIVO);
    }

    @Override
    public List<MovimientoMaterial> movimientos(RangoFechas periodo, Long areaVerdeId) {
        return List.of();
    }
}
