package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.Collection;
import java.util.Map;

/**
 * Tipo de uso de los espacios (AreaVerde.tipo_uso_id, MOD-02; RF-REP-007 v2: filtro por tipo de
 * uso), en solo lectura desde las tablas de su dueño. Hoy solo existe {@link
 * FuenteTiposUsoNoDisponible}.
 */
public interface FuenteTiposUso {

    Disponibilidad disponibilidad();

    /**
     * tipo_uso_id de cada espacio (id de mod05_areaverde). Sin tipo de uso → sin entrada. Nunca
     * null.
     */
    Map<Long, Long> tipoUsoDeEspacios(Collection<Long> areaVerdeIds);
}
