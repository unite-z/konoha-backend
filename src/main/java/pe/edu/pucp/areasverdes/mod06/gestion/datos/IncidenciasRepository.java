package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Incidencias de MOD-03 ({@code mod03_incidencia}) para la matriz de incidencias de RF-REP-009, en
 * solo lectura y con SQL parametrizado. Una incidencia es del periodo por su fecha de registro en
 * America/Lima.
 *
 * <ul>
 *   <li>Espacio: el área de la incidencia o, si se registró sobre un ejemplar, el área de
 *       inventario del ejemplar; sin ninguna de las dos, «Sin espacio».
 *   <li>Tipo: el del catálogo de MOD-03; sin clasificar, «Sin tipo».
 * </ul>
 */
@Repository
public class IncidenciasRepository {

    public static final String SIN_ESPACIO = "Sin espacio";
    public static final String SIN_TIPO = "Sin tipo";

    /** Cuántas incidencias hay de un espacio, un tipo y un estado. */
    public record Conteo(String espacio, String tipo, String estado, int incidencias) {}

    private final NamedParameterJdbcTemplate jdbc;

    public IncidenciasRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Conteo> conteos(RangoFechas periodo) {
        return jdbc.query(
                """
                SELECT COALESCE(a.nombre, ae.nombre, :sinEspacio) AS espacio,
                       COALESCE(t.nombre, :sinTipo) AS tipo, i.estado, count(*)::int AS incidencias
                FROM mod03_incidencia i
                LEFT JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                LEFT JOIN mod02_area_verde a ON a.id = i.area_verde_id
                LEFT JOIN mod02_ejemplar e ON e.id = i.ejemplar_id
                LEFT JOIN mod02_area_verde ae ON ae.id = e.area_verde_inventario_id
                WHERE (i.fecha_registro AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                GROUP BY 1, 2, 3
                """,
                new MapSqlParameterSource()
                        .addValue("desde", periodo.desde())
                        .addValue("hasta", periodo.hasta())
                        .addValue("sinEspacio", SIN_ESPACIO)
                        .addValue("sinTipo", SIN_TIPO),
                new DataClassRowMapper<>(Conteo.class));
    }
}
