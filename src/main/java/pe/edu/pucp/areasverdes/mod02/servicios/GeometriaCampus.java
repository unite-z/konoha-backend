package pe.edu.pucp.areasverdes.mod02.servicios;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;

@Service
public class GeometriaCampus {
    private final NamedParameterJdbcTemplate jdbc;

    public GeometriaCampus(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Capa de referencia del campus más los contornos dibujados de áreas activas (AV-430). En
     * estos, {@code Uso} lleva el tipo de uso del sistema (no hay uso original de la capa) y {@code
     * tipo_uso_id} y {@code origen = 'dibujo'} permiten distinguirlos.
     */
    public String geojson() {
        return jdbc.getJdbcTemplate()
                .queryForObject(
                        """
                        SELECT jsonb_set(c.geojson, '{features}', (c.geojson->'features') || coalesce((
                            SELECT jsonb_agg(jsonb_build_object(
                                'type', 'Feature',
                                'properties', jsonb_build_object(
                                    'Nombre', a.nombre, 'Área', a.superficie_m2, 'Uso', tu.nombre,
                                    'mapa_id', g.id_fuente, 'area_verde_id', a.id,
                                    'tipo_uso_id', a.tipo_uso_id, 'origen', g.origen),
                                'geometry', ST_AsGeoJSON(g.geometria)::jsonb)
                                ORDER BY g.fecha_creacion, g.id_fuente)
                            FROM mod02_mapa_geometria g
                            JOIN mod02_mapa_poligono p USING (id_fuente)
                            JOIN mod02_area_verde a ON a.id = p.area_verde_id
                            LEFT JOIN mod02_tipo_uso tu ON tu.id = a.tipo_uso_id
                            WHERE g.origen = 'dibujo' AND a.activo), '[]'::jsonb))::text
                        FROM mod02_mapa_capa c WHERE c.unica
                        """,
                        String.class);
    }

    /** Serializa escrituras que afectan conjuntamente a polígonos y ejemplares. */
    public void bloquearCambios() {
        jdbc.query("SELECT pg_advisory_xact_lock(172, 133)", Map.of(), rs -> null);
    }

    public record AreaEnPunto(long id, String nombre) {}

    public record Ubicacion(List<AreaEnPunto> areas, String estado) {}

    public Ubicacion consultar(BigDecimal latitud, BigDecimal longitud, Long areaId) {
        var parametros =
                new MapSqlParameterSource()
                        .addValue("latitud", latitud)
                        .addValue("longitud", longitud);
        var areas =
                jdbc.query(
                        """
                        SELECT DISTINCT a.id, a.nombre
                        FROM mod02_mapa_geometria g
                        JOIN mod02_mapa_poligono p USING (id_fuente)
                        JOIN mod02_area_verde a ON a.id = p.area_verde_id
                        WHERE ST_Covers(g.geometria, ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326))
                            AND (a.activo OR a.id = CAST(:area AS bigint))
                        ORDER BY a.nombre, a.id
                        """,
                        parametros.addValue("area", areaId),
                        new DataClassRowMapper<>(AreaEnPunto.class));
        if (areaId == null || !tieneGeometria(areaId, null)) {
            return new Ubicacion(areas, "pendiente");
        }
        boolean dentro = areas.stream().anyMatch(area -> area.id() == areaId);
        return new Ubicacion(areas, dentro ? "dentro" : "fuera");
    }

    public boolean tieneGeometria(long areaId, UUID excluir) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT EXISTS (
                            SELECT 1 FROM mod02_mapa_poligono p JOIN mod02_mapa_geometria g USING (id_fuente)
                            WHERE p.area_verde_id = :area
                                AND p.id_fuente IS DISTINCT FROM CAST(:excluir AS uuid)
                        )
                        """,
                        new MapSqlParameterSource("area", areaId).addValue("excluir", excluir),
                        Boolean.class));
    }

    /** Se ejecuta dentro de la misma transacción que guarda las coordenadas. */
    public void validarEjemplar(long id) {
        boolean fuera =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
                                SELECT EXISTS (
                                    SELECT 1 FROM mod02_ejemplar e
                                    CROSS JOIN LATERAL (
                                        SELECT count(*) cantidad, bool_or(ST_Covers(g.geometria, e.ubicacion_geografica)) dentro
                                        FROM mod02_mapa_poligono p JOIN mod02_mapa_geometria g USING (id_fuente)
                                        WHERE p.area_verde_id = e.area_verde_inventario_id
                                    ) cobertura
                                    WHERE e.id = :id AND e.ubicacion_geografica IS NOT NULL
                                        AND cobertura.cantidad > 0 AND NOT cobertura.dentro
                                )
                                """,
                                Map.of("id", id),
                                Boolean.class));
        if (fuera) {
            throw new ErrorCoberturas(
                    422,
                    "punto-fuera-del-area",
                    "El punto está fuera del área vinculada. Corrige el punto o el área verde.");
        }
    }
}
