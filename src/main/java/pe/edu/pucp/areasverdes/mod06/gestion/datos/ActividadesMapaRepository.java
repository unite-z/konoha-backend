package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Actividades del personal propio (MOD-04) para el mapa de atenciones (RF-REP-007), en solo lectura
 * y con SQL parametrizado. La fecha es la local de America/Lima; el espacio, el de {@code
 * mod05_areaverde} (el mismo id que las atenciones tercerizadas); la clase, la histórica de la
 * actividad o, si no la guarda, la de su tipo.
 */
@Repository
public class ActividadesMapaRepository {

    /** Una actividad con lo que el mapa necesita para clasificarla y ubicarla. */
    public record ActividadMapa(
            long id,
            LocalDate fecha,
            String clase,
            String tipoActividad,
            String estadoParte,
            String cuadrilla,
            Long areaVerdeId,
            String espacio,
            Long ejemplarId,
            String ejemplarCodigo) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ActividadesMapaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Actividades del periodo, de la más reciente a la más antigua; con espacio, solo las suyas.
     */
    public List<ActividadMapa> actividades(RangoFechas periodo, Long areaVerdeId) {
        return jdbc.query(
                """
                SELECT a.id, (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date AS fecha,
                       ca.nombre AS clase, COALESCE(a.tipo_actividad_nombre, t.nombre) AS tipo_actividad,
                       p.estado AS estado_parte, c.nombre AS cuadrilla, a.area_verde_id,
                       v.nombre AS espacio, a.ejemplar_id, e.codigo AS ejemplar_codigo
                FROM mod04_actividad a
                JOIN mod04_parte_diario p ON p.id = a.parte_id
                JOIN mod04_cuadrilla c ON c.id = p.cuadrilla_id
                JOIN mod04_tipo_actividad t ON t.id = a.tipo_actividad_id
                LEFT JOIN mod04_clase_actividad ca ON ca.id = COALESCE(a.clase_actividad_id, t.clase_id)
                LEFT JOIN mod05_areaverde v ON v.id = a.area_verde_id
                LEFT JOIN mod02_ejemplar e ON e.id = a.ejemplar_id
                WHERE (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                  AND (CAST(:area AS bigint) IS NULL OR a.area_verde_id = :area)
                ORDER BY a.fecha_hora_actividad DESC, a.id
                """,
                new MapSqlParameterSource()
                        .addValue("desde", periodo.desde())
                        .addValue("hasta", periodo.hasta())
                        .addValue("area", areaVerdeId),
                new DataClassRowMapper<>(ActividadMapa.class));
    }
}
