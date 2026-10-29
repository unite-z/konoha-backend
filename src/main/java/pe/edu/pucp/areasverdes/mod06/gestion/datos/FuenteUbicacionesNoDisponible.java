package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link FuenteUbicaciones} mientras las geometrías de MOD-02 no estén en {@code main}: las
 * atenciones se muestran en la lista (CU-MOD06-003, flujo A1).
 *
 * <p>TODO cuando se fusione MOD-02, para el adaptador de solo lectura sobre sus tablas:
 *
 * <ol>
 *   <li>Ids distintos: {@code mod05_alcanceatencion.area_verde_id} apunta a {@code
 *       mod05_areaverde}, no a {@code mod02_area_verde}; hay que resolver la correspondencia (deuda
 *       «consolidar las tablas duplicadas» de AGENTS.md).
 *   <li>En la rama {@code mod02}, la geometría está en el GeoJSON del frontend y {@code
 *       mod02_mapa_poligono} relaciona cada polígono con un área.
 *   <li>Si se lee de PostGIS, con {@code ST_AsGeoJSON} en SRID 4326.
 * </ol>
 *
 * Después, borrar esta clase.
 */
@Component
public class FuenteUbicacionesNoDisponible implements FuenteUbicaciones {

    public static final String MOTIVO =
            "Las ubicaciones de los espacios y de los ejemplares todavía no están disponibles en el"
                    + " sistema: las atenciones se muestran en la lista";

    @Override
    public Disponibilidad disponibilidad() {
        return Disponibilidad.noDisponible(MOTIVO);
    }

    @Override
    public Map<Long, JsonNode> geometriasDeEspacios(Collection<Long> areaVerdeIds) {
        return Map.of();
    }

    @Override
    public Map<Long, JsonNode> geometriasDeEjemplares(Collection<Long> ejemplarIds) {
        return Map.of();
    }
}
