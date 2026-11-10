package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * {@link FuenteMovimientosMaterial} sobre {@code mod04_material_actividad} (RF-ACT-005), en solo
 * lectura y con SQL parametrizado.
 *
 * <p>Traducción al modelo del reporte:
 *
 * <ul>
 *   <li>Traslado: el material tiene origen y destino. MOD-04 los guarda como texto; el id del
 *       espacio se resuelve por nombre en {@code mod05_areaverde} (puede quedar nulo).
 *   <li>Propagación: material vegetal en una actividad de la clase «Propagación y plantación».
 *   <li>Consumo: el resto.
 *   <li>Categoría: Esquejes y Plantas son «Material vegetal»; Sustrato, Fertilizante y los
 *       materiales escritos a mano («otro») son «Insumo». Un material «otro» lleva como id el
 *       negativo de su fila, para no agruparse con otros distintos.
 *   <li>Especie: MOD-04 no la registra; queda nula.
 * </ul>
 */
@Repository
public class FuenteMovimientosMaterialMod04 implements FuenteMovimientosMaterial {

    /** Clase de actividad «Propagación y plantación» (V54, id fijo del catálogo de MOD-04). */
    static final long CLASE_PROPAGACION = 5;

    private final NamedParameterJdbcTemplate jdbc;

    public FuenteMovimientosMaterialMod04(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Disponibilidad disponibilidad() {
        return Disponibilidad.DISPONIBLE;
    }

    @Override
    public List<MovimientoMaterial> movimientos(RangoFechas periodo, Long areaVerdeId) {
        return jdbc.query(
                """
                WITH m AS (
                    SELECT ma.id, ma.actividad_id,
                           (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date AS fecha,
                           a.area_verde_id, v.nombre AS espacio,
                           COALESCE(ma.material_id, -ma.id) AS tipo_material_id,
                           COALESCE(mc.nombre, ma.descripcion_otro) AS tipo_material,
                           CASE WHEN mc.nombre IN ('Esquejes', 'Plantas') THEN 'Material vegetal'
                                ELSE 'Insumo' END AS categoria,
                           CASE WHEN ma.origen IS NOT NULL THEN 'TRASLADO'
                                WHEN a.clase_actividad_id = :propagacion AND mc.nombre IN ('Esquejes', 'Plantas')
                                    THEN 'PROPAGACION'
                                ELSE 'CONSUMO' END AS tipo_movimiento,
                           ma.cantidad, u.simbolo AS unidad,
                           (SELECT min(o.id) FROM mod05_areaverde o
                            WHERE lower(btrim(o.nombre)) = lower(btrim(ma.origen))) AS origen_area_id,
                           ma.origen,
                           (SELECT min(d.id) FROM mod05_areaverde d
                            WHERE lower(btrim(d.nombre)) = lower(btrim(ma.destino))) AS destino_area_id,
                           ma.destino
                    FROM mod04_material_actividad ma
                    JOIN mod04_actividad a ON a.id = ma.actividad_id
                    JOIN mod04_unidad_medida u ON u.id = ma.unidad_medida_id
                    LEFT JOIN mod04_material_catalogo mc ON mc.id = ma.material_id
                    LEFT JOIN mod05_areaverde v ON v.id = a.area_verde_id
                    WHERE (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                )
                SELECT * FROM m
                WHERE CAST(:area AS bigint) IS NULL
                   OR m.area_verde_id = :area OR m.origen_area_id = :area OR m.destino_area_id = :area
                ORDER BY m.fecha, m.id
                """,
                new MapSqlParameterSource()
                        .addValue("desde", periodo.desde())
                        .addValue("hasta", periodo.hasta())
                        .addValue("area", areaVerdeId)
                        .addValue("propagacion", CLASE_PROPAGACION),
                (rs, n) ->
                        new MovimientoMaterial(
                                rs.getLong("id"),
                                rs.getLong("actividad_id"),
                                rs.getObject("fecha", java.time.LocalDate.class),
                                rs.getObject("area_verde_id", Long.class),
                                rs.getString("espacio"),
                                rs.getLong("tipo_material_id"),
                                rs.getString("tipo_material"),
                                rs.getString("categoria"),
                                null,
                                null,
                                TipoMovimiento.valueOf(rs.getString("tipo_movimiento")),
                                rs.getBigDecimal("cantidad"),
                                rs.getString("unidad"),
                                rs.getObject("origen_area_id", Long.class),
                                rs.getString("origen"),
                                rs.getObject("destino_area_id", Long.class),
                                rs.getString("destino")));
    }
}
