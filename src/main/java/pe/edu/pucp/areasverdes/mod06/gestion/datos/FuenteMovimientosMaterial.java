package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Movimientos de material vegetal e insumos de las actividades de las cuadrillas
 * (MovimientoMaterial, RF-ACT-005, MOD-04), en solo lectura. Las tablas son de MOD-04: el MOD-06
 * nunca las copia ni escribe en ellas (docs/estandares/base-datos.md). Hoy solo existe {@link
 * FuenteMovimientosMaterialNoDisponible}.
 *
 * <p>Los literales documentados del tipo de movimiento (Modelo BD v1, enumerado TipoMovimiento =
 * Consumo; Propagacion; Traslado) son los que el adaptador de MOD-04 traducirá a {@link
 * TipoMovimiento}.
 */
public interface FuenteMovimientosMaterial {

    Disponibilidad disponibilidad();

    /**
     * Movimientos cuya actividad tiene fecha en {@code periodo}. Si {@code areaVerdeId} no es null,
     * solo los de ese espacio: el de la actividad o, en un traslado, el de origen o el de destino
     * (CONFIRMADO: cuentan el espacio de la actividad, el de origen y el de destino (decisión del
     * responsable, 28/10/2026)). Nunca devuelve null.
     */
    List<MovimientoMaterial> movimientos(RangoFechas periodo, Long areaVerdeId);
}
