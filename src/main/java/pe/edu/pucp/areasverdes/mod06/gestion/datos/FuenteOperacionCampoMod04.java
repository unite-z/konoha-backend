package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * {@link FuenteOperacionCampo} sobre las tablas de MOD-04 ({@code mod04_actividad}, {@code
 * mod04_parte_diario}, {@code mod04_cuadrilla}, {@code mod04_participacion_actividad} y {@code
 * mod04_personal}), en solo lectura y con SQL parametrizado.
 *
 * <p>El espacio es el de {@code mod05_areaverde}, el mismo id que usan las atenciones tercerizadas
 * del panel. La fecha es la local de America/Lima. SUPUESTO (pendiente de MOD-04): el estado de una
 * actividad es el de su parte diario ({@code Abierto} o {@code Cerrado}).
 */
@Repository
public class FuenteOperacionCampoMod04 implements FuenteOperacionCampo {

    private static final String DESDE =
            """
            FROM mod04_actividad a
            JOIN mod04_parte_diario p ON p.id = a.parte_id
            JOIN mod04_cuadrilla c ON c.id = p.cuadrilla_id
            LEFT JOIN mod05_areaverde v ON v.id = a.area_verde_id
            WHERE (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
              AND a.estado_actividad <> 'Anulada'
              AND (CAST(:area AS bigint) IS NULL OR a.area_verde_id = :area)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public FuenteOperacionCampoMod04(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Disponibilidad disponibilidad() {
        return Disponibilidad.DISPONIBLE;
    }

    @Override
    public List<ActividadCampo> actividades(RangoFechas periodo, Long areaVerdeId) {
        return jdbc.query(
                """
                SELECT a.id, (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date AS fecha,
                       p.estado, a.tipo_actividad_nombre AS tipo_actividad, a.area_verde_id,
                       v.nombre AS espacio, c.nombre AS cuadrilla
                """
                        + DESDE
                        + " ORDER BY a.fecha_hora_actividad, a.id",
                parametros(periodo, areaVerdeId),
                new DataClassRowMapper<>(ActividadCampo.class));
    }

    @Override
    public List<ParticipacionOperario> participaciones(RangoFechas periodo, Long areaVerdeId) {
        return jdbc.query(
                """
                SELECT a.id AS actividad_id, (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date AS fecha,
                       o.id AS operario_id, btrim(o.nombres || ' ' || o.apellidos) AS operario,
                       c.nombre AS cuadrilla, a.area_verde_id, v.nombre AS espacio
                """
                        + DESDE.replace(
                                "WHERE",
                                """
                                JOIN mod04_participacion_actividad pa ON pa.actividad_id = a.id
                                JOIN mod04_personal o ON o.id = pa.operario_id
                                WHERE\
                                """)
                        + " ORDER BY a.fecha_hora_actividad, a.id, o.id",
                parametros(periodo, areaVerdeId),
                new DataClassRowMapper<>(ParticipacionOperario.class));
    }

    private static MapSqlParameterSource parametros(RangoFechas periodo, Long areaVerdeId) {
        return new MapSqlParameterSource()
                .addValue("desde", periodo.desde())
                .addValue("hasta", periodo.hasta())
                .addValue("area", areaVerdeId);
    }
}
