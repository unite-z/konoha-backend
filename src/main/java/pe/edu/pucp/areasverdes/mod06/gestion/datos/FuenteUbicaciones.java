package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collection;
import java.util.Map;

/**
 * Geometría GeoJSON (WGS84, EPSG:4326, [longitud, latitud]; api.md) de los espacios y los
 * ejemplares, en solo lectura desde las tablas de su dueño (MOD-02). Hoy solo existe
 * FuenteUbicacionesNoDisponible.
 */
public interface FuenteUbicaciones {

    Disponibilidad disponibilidad();

    /**
     * Por id de mod05_areaverde (el que usa mod05_alcanceatencion). Sin geometría → sin entrada.
     * Nunca null.
     */
    Map<Long, JsonNode> geometriasDeEspacios(Collection<Long> areaVerdeIds);

    /** Por id de mod05_ejemplar. Sin ubicación → sin entrada. Nunca null. */
    Map<Long, JsonNode> geometriasDeEjemplares(Collection<Long> ejemplarIds);
}
