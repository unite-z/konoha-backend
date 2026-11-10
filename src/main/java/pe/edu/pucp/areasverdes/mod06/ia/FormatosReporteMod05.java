package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;

/**
 * {@link CatalogoFormatos} sobre {@code mod05_formato_reporte_servicio} y {@code
 * mod05_campo_formato_reporte} (V73). Solo lectura y SQL parametrizado. Un id que no es un entero
 * positivo se trata como «sin formato».
 */
@Repository
public class FormatosReporteMod05 implements CatalogoFormatos {

    private final NamedParameterJdbcTemplate jdbc;

    public FormatosReporteMod05(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Object> formatoVigente(Object tipoServicioId) {
        Long tipo = comoId(tipoServicioId);
        if (tipo == null) return null;
        List<Map<String, Object>> filas =
                jdbc.query(
                        """
                        SELECT id, tipo_servicio_id, version, vigente
                        FROM mod05_formato_reporte_servicio
                        WHERE tipo_servicio_id = :tipo AND vigente
                        ORDER BY version DESC, id DESC
                        LIMIT 1
                        """,
                        Map.of("tipo", tipo),
                        (rs, n) -> {
                            Map<String, Object> f = new LinkedHashMap<>();
                            f.put("id", rs.getLong("id"));
                            f.put("tipo_servicio_id", rs.getLong("tipo_servicio_id"));
                            f.put("version", rs.getLong("version"));
                            f.put("vigente", rs.getBoolean("vigente"));
                            return f;
                        });
        return filas.isEmpty() ? null : filas.get(0);
    }

    @Override
    public List<Map<String, Object>> campos(Object formatoId) {
        Long formato = comoId(formatoId);
        if (formato == null) return List.of();
        return jdbc.query(
                """
                SELECT id, formato_id, nombre_campo, tipo_dato, obligatorio, subcampos::text AS subcampos
                FROM mod05_campo_formato_reporte
                WHERE formato_id = :formato
                ORDER BY orden, id
                """,
                Map.of("formato", formato),
                (rs, n) -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", rs.getLong("id"));
                    c.put("formato_id", rs.getLong("formato_id"));
                    c.put("nombre_campo", rs.getString("nombre_campo"));
                    c.put("tipo_dato", rs.getString("tipo_dato"));
                    c.put("obligatorio", rs.getBoolean("obligatorio"));
                    String subcampos = rs.getString("subcampos");
                    c.put("subcampos", subcampos == null ? null : JsonPy.loads(subcampos));
                    return c;
                });
    }

    private static Long comoId(Object valor) {
        if (valor instanceof Number n && !(valor instanceof Double) && !(valor instanceof Float)) {
            return n.longValue() > 0 ? n.longValue() : null;
        }
        if (valor instanceof String s && s.matches("[1-9][0-9]{0,17}")) return Long.parseLong(s);
        return null;
    }
}
