package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link FuenteTiposUso} mientras el tipo de uso de MOD-02 no esté en {@code main}.
 *
 * <p>TODO cuando se fusione MOD-02, para el adaptador de solo lectura sobre {@code mod02_tipo_uso}
 * y {@code mod02_area_verde.tipo_uso_id} (hoy solo en la rama {@code mod02}, {@code V8}): {@code
 * mod05_alcanceatencion.area_verde_id} apunta a {@code mod05_areaverde}, no a {@code
 * mod02_area_verde}; hay que resolver la correspondencia (deuda «consolidar las tablas duplicadas»
 * de AGENTS.md). Después, borrar esta clase.
 */
@Component
public class FuenteTiposUsoNoDisponible implements FuenteTiposUso {

    public static final String MOTIVO =
            "El tipo de uso de los espacios todavía no está disponible en el sistema: quita el"
                    + " filtro de tipo de uso para ver las atenciones";

    @Override
    public Disponibilidad disponibilidad() {
        return Disponibilidad.noDisponible(MOTIVO);
    }

    @Override
    public Map<Long, Long> tipoUsoDeEspacios(Collection<Long> areaVerdeIds) {
        return Map.of();
    }
}
