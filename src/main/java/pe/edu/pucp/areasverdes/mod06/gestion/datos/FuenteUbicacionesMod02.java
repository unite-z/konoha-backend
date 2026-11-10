package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link FuenteUbicaciones} y {@link FuenteTiposUso} sobre las tablas de MOD-02, en solo lectura.
 *
 * <ul>
 *   <li>Espacio: el id que llega es el de {@code mod05_areaverde} (el de las atenciones). Se cruza
 *       con {@code mod02_area_verde} por nombre, como MOD-03 ({@code InterrupcionesCorte}), hasta
 *       que se consoliden las dos tablas. Su geometría es la unión de sus polígonos del campus
 *       ({@code mod02_mapa_poligono} → {@code mod02_mapa_geometria}, V66).
 *   <li>Ejemplar: su punto ({@code mod02_ejemplar.ubicacion_geografica}); el id de {@code
 *       mod05_ejemplar} es el mismo, porque es una vista sobre {@code mod02_ejemplar}.
 * </ul>
 *
 * GeoJSON en EPSG:4326 con {@code ST_AsGeoJSON}. Sin correspondencia o sin geometría, no hay
 * entrada: la atención se muestra en la lista (CU-MOD06-003, flujo A1).
 */
@Repository
public class FuenteUbicacionesMod02 implements FuenteUbicaciones, FuenteTiposUso {

    /** Área de inventario de cada área de MOD-05 pedida, por nombre. */
    private static final String EQUIVALENCIA =
            """
            WITH eq AS (
                SELECT m.id AS espacio_id, min(i.id) AS area_id
                FROM mod05_areaverde m
                JOIN mod02_area_verde i ON lower(btrim(i.nombre)) = lower(btrim(m.nombre))
                WHERE m.id IN (:ids)
                GROUP BY m.id
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public FuenteUbicacionesMod02(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Disponibilidad disponibilidad() {
        return Disponibilidad.DISPONIBLE;
    }

    @Override
    public Map<Long, JsonNode> geometriasDeEspacios(Collection<Long> areaVerdeIds) {
        if (areaVerdeIds.isEmpty()) return Map.of();
        return geometrias(
                EQUIVALENCIA
                        + """
                        SELECT eq.espacio_id AS id, ST_AsGeoJSON(ST_Union(g.geometria)) AS geojson
                        FROM eq
                        JOIN mod02_mapa_poligono p ON p.area_verde_id = eq.area_id
                        JOIN mod02_mapa_geometria g ON g.id_fuente = p.id_fuente
                        GROUP BY eq.espacio_id
                        """,
                areaVerdeIds);
    }

    @Override
    public Map<Long, JsonNode> geometriasDeEjemplares(Collection<Long> ejemplarIds) {
        if (ejemplarIds.isEmpty()) return Map.of();
        return geometrias(
                """
                SELECT id, ST_AsGeoJSON(ubicacion_geografica) AS geojson
                FROM mod02_ejemplar
                WHERE id IN (:ids) AND ubicacion_geografica IS NOT NULL
                """,
                ejemplarIds);
    }

    @Override
    public Map<Long, Long> tipoUsoDeEspacios(Collection<Long> areaVerdeIds) {
        if (areaVerdeIds.isEmpty()) return Map.of();
        Map<Long, Long> r = new HashMap<>();
        jdbc.query(
                EQUIVALENCIA
                        + """
                        SELECT eq.espacio_id, a.tipo_uso_id
                        FROM eq JOIN mod02_area_verde a ON a.id = eq.area_id
                        WHERE a.tipo_uso_id IS NOT NULL
                        """,
                Map.of("ids", areaVerdeIds),
                rs -> {
                    r.put(rs.getLong("espacio_id"), rs.getLong("tipo_uso_id"));
                });
        return r;
    }

    private Map<Long, JsonNode> geometrias(String sql, Collection<Long> ids) {
        Map<Long, JsonNode> r = new HashMap<>();
        jdbc.query(
                sql,
                Map.of("ids", ids),
                rs -> {
                    r.put(rs.getLong("id"), leer(rs.getString("geojson")));
                });
        return r;
    }

    private JsonNode leer(String geojson) {
        try {
            return json.readTree(geojson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("GeoJSON no válido en MOD-02", e);
        }
    }
}
