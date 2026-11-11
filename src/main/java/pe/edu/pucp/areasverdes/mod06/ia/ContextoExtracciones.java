package pe.edu.pucp.areasverdes.mod06.ia;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * AV-355: la atención y el informe de MOD-05 de cada propuesta de extracción (RF-IA-001), leídos de
 * una vez para toda la página. Así el frontend no pide atención por atención ni pagina los informes
 * de cada una (N+1). Solo lectura y SQL parametrizado.
 *
 * <p>Forma: {@code atencion = {id, codigo, estado, tipo_servicio_id, tipo_servicio, proveedor}} e
 * {@code informe = {id, revision, fecha_recepcion, resumen, documento}}, con {@code documento =
 * {archivo_id, nombre, categoria}} o {@code null}: el archivo que analizó la IA.
 */
@Repository
public class ContextoExtracciones {

    /** Atención e informe de una propuesta. */
    public record Contexto(Map<String, Object> atencion, Map<String, Object> informe) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ContextoExtracciones(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Por id de informe ({@code registro_id} de la propuesta); el archivo es el de la propuesta.
     */
    public Map<Long, Contexto> porReporte(
            Collection<Long> reporteIds, Map<Long, Long> archivoPorReporte) {
        Map<Long, Contexto> r = new HashMap<>();
        if (reporteIds.isEmpty()) return r;
        jdbc.query(
                """
                SELECT rp.id AS reporte_id, rp.revision, rp.fecha_recepcion, rp.resumen,
                       a.id AS atencion_id, a.codigo, a.estado, a.tipo_servicio_id, t.nombre AS tipo_servicio,
                       p.razon_social AS proveedor
                FROM mod05_reporteproveedor rp
                JOIN mod05_atenciontercerizada a ON a.id = rp.atencion_id
                LEFT JOIN mod05_tiposervicio t ON t.id = a.tipo_servicio_id
                LEFT JOIN mod05_contrato c ON c.id = a.contrato_id
                LEFT JOIN mod05_proveedor p ON p.id = c.proveedor_id
                WHERE rp.id IN (:ids)
                """,
                Map.of("ids", reporteIds),
                rs -> {
                    long reporte = rs.getLong("reporte_id");
                    Map<String, Object> atencion = new LinkedHashMap<>();
                    atencion.put("id", rs.getLong("atencion_id"));
                    atencion.put("codigo", rs.getString("codigo"));
                    atencion.put("estado", rs.getString("estado"));
                    atencion.put("tipo_servicio_id", rs.getObject("tipo_servicio_id", Long.class));
                    atencion.put("tipo_servicio", rs.getString("tipo_servicio"));
                    atencion.put("proveedor", rs.getString("proveedor"));
                    Map<String, Object> informe = new LinkedHashMap<>();
                    informe.put("id", reporte);
                    informe.put("revision", rs.getInt("revision"));
                    LocalDate recepcion = rs.getObject("fecha_recepcion", LocalDate.class);
                    informe.put("fecha_recepcion", recepcion == null ? null : recepcion.toString());
                    informe.put("resumen", rs.getString("resumen"));
                    informe.put("documento", null);
                    r.put(reporte, new Contexto(atencion, informe));
                });
        if (!archivoPorReporte.isEmpty()) {
            jdbc.query(
                    """
                    SELECT x.reporte_id, x.archivo_id, f.nombre_original AS nombre, x.categoria
                    FROM mod05_archivoasociado x JOIN mod05_archivo f ON f.id = x.archivo_id
                    WHERE x.reporte_id IN (:ids)
                    """,
                    Map.of("ids", reporteIds),
                    rs -> {
                        long reporte = rs.getLong("reporte_id");
                        long archivo = rs.getLong("archivo_id");
                        Contexto c = r.get(reporte);
                        if (c == null
                                || !Long.valueOf(archivo).equals(archivoPorReporte.get(reporte)))
                            return;
                        Map<String, Object> documento = new LinkedHashMap<>();
                        documento.put("archivo_id", archivo);
                        documento.put("nombre", rs.getString("nombre"));
                        documento.put("categoria", rs.getString("categoria"));
                        c.informe().put("documento", documento);
                    });
        }
        return r;
    }
}
