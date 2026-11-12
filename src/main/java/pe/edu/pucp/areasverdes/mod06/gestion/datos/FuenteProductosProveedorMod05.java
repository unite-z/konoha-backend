package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * {@link FuenteProductosProveedor} sobre {@code mod05_atencion_producto} (CU-MOD05-025), en solo
 * lectura y con SQL parametrizado.
 *
 * <ul>
 *   <li>Proveedor: el del contrato de la atención.
 *   <li>Fecha: el inicio de la atención en America/Lima. Las atenciones sin inicio, o que siguen en
 *       «Requerimiento registrado» u «OC referenciada», aún no se han ejecutado y no cuentan.
 *   <li>Espacio: el alcance de la atención; un ejemplar cuenta en su área.
 * </ul>
 */
@Repository
public class FuenteProductosProveedorMod05 implements FuenteProductosProveedor {

    /** Estados de MOD-05 anteriores a la ejecución ({@code mod05.Atencion}). */
    static final List<String> SIN_EJECUTAR = List.of("Requerimiento registrado", "OC referenciada");

    private final NamedParameterJdbcTemplate jdbc;

    public FuenteProductosProveedorMod05(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProductoAplicado> aplicaciones(RangoFechas periodo, Long areaVerdeId) {
        return jdbc.query(
                """
                SELECT ap.id, a.id AS atencion_id, a.codigo,
                       (a.fecha_hora_inicio AT TIME ZONE 'America/Lima')::date AS fecha,
                       pr.id AS producto_id, pr.nombre AS producto, pr.principio_activo, ap.dosis,
                       pv.id AS proveedor_id, pv.razon_social AS proveedor
                FROM mod05_atencion_producto ap
                JOIN mod05_atenciontercerizada a ON a.id = ap.atencion_id
                JOIN mod05_producto_agronomico pr ON pr.id = ap.producto_id
                JOIN mod05_contrato c ON c.id = a.contrato_id
                JOIN mod05_proveedor pv ON pv.id = c.proveedor_id
                WHERE a.fecha_hora_inicio IS NOT NULL
                  AND a.estado NOT IN (:sinEjecutar)
                  AND (a.fecha_hora_inicio AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                  AND (CAST(:area AS bigint) IS NULL OR EXISTS (
                        SELECT 1 FROM mod05_alcanceatencion al
                        LEFT JOIN mod02_ejemplar e ON e.id = al.ejemplar_id
                        WHERE al.atencion_id = a.id
                          AND (al.area_verde_id = :area OR e.area_verde_id = :area)))
                ORDER BY fecha, ap.id
                """,
                new MapSqlParameterSource()
                        .addValue("desde", periodo.desde())
                        .addValue("hasta", periodo.hasta())
                        .addValue("area", areaVerdeId)
                        .addValue("sinEjecutar", SIN_EJECUTAR),
                (rs, n) ->
                        new ProductoAplicado(
                                rs.getLong("id"),
                                rs.getLong("atencion_id"),
                                rs.getString("codigo"),
                                rs.getObject("fecha", java.time.LocalDate.class),
                                rs.getLong("producto_id"),
                                rs.getString("producto"),
                                rs.getString("principio_activo"),
                                rs.getString("dosis"),
                                rs.getLong("proveedor_id"),
                                rs.getString("proveedor")));
    }
}
