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

    public String geojson() {
        return jdbc.getJdbcTemplate()
                .queryForObject("SELECT geojson::text FROM mod02_mapa_capa WHERE unica", String.class);
    }

    /** Serializa escrituras que afectan conjuntamente a polígonos y ejemplares. */
    public void bloquearCambios() {
        jdbc.query("SELECT pg_advisory_xact_lock(172, 133)", Map.of(), rs -> null);
    }

    public record AreaEnPunto(long id, String nombre) {}

    public record Ubicacion(List<AreaEnPunto> areas, String estado) {}

    public Ubicacion consultar(BigDecimal latitud, BigDecimal longitud, Long areaId) {
        var parametros = new MapSqlParameterSource()
                .addValue("latitud", latitud)
                .addValue("longitud", longitud);
        var areas = jdbc.query("""
                SELECT DISTINCT a.id, a.nombre
                FROM mod02_mapa_geometria g
                JOIN mod02_mapa_poligono p USING (id_fuente)
                JOIN mod02_area_verde a ON a.id = p.area_verde_id
                WHERE ST_Covers(g.geometria, ST_SetSRID(ST_MakePoint(:longitud, :latitud), 4326))
                    AND (a.activo OR a.id = CAST(:area AS bigint))
                ORDER BY a.nombre, a.id
                """, parametros.addValue("area", areaId), new DataClassRowMapper<>(AreaEnPunto.class));
        if (areaId == null || !tieneGeometria(areaId, null)) {
            return new Ubicacion(areas, "pendiente");
        }
        boolean dentro = areas.stream().anyMatch(area -> area.id() == areaId);
        return new Ubicacion(areas, dentro ? "dentro" : "fuera");
    }

    public boolean tieneGeometria(long areaId, UUID excluir) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM mod02_mapa_poligono p JOIN mod02_mapa_geometria g USING (id_fuente)
                    WHERE p.area_verde_id = :area
                        AND p.id_fuente IS DISTINCT FROM CAST(:excluir AS uuid)
                )
                """, new MapSqlParameterSource("area", areaId).addValue("excluir", excluir), Boolean.class));
    }

    /** Se ejecuta dentro de la misma transacción que guarda las coordenadas. */
    public void validarEjemplar(long id) {
        boolean fuera = Boolean.TRUE.equals(jdbc.queryForObject("""
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
                """, Map.of("id", id), Boolean.class));
        if (fuera) {
            throw new ErrorCoberturas(422, "punto-fuera-del-area",
                    "El punto está fuera del área vinculada. Corrige el punto o el área verde.");
        }
    }
}
