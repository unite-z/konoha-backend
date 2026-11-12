package pe.edu.pucp.areasverdes.mod05;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Formato estándar del informe del proveedor por tipo de servicio (RF-SER-009, tablas de V73) y los
 * datos del informe final que se registran con él (V77).
 *
 * <p>Solo lee: el formato vigente lo cargan la migración V73 y la precarga dev; MOD-06 lo usa para
 * la extracción con IA (RF-IA-001).
 */
@Service
public class FormatosReporte {

    private static final TypeReference<List<DatosFormatoInforme.Subcampo>> SUBCAMPOS =
            new TypeReference<>() {};
    private static final TypeReference<LinkedHashMap<String, Object>> OBJETO =
            new TypeReference<>() {};

    private final Mod05Repository repo;
    private final ObjectMapper json;

    public FormatosReporte(Mod05Repository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    /** Formato vigente del tipo de servicio, con sus campos en orden; vacío si no tiene. */
    public Optional<DatosFormatoInforme.Formato> vigente(long tipoServicioId) {
        var filas =
                repo.rows(
                        """
                        SELECT f.id, f.version, f.tipo_servicio_id, t.nombre AS tipo_servicio_nombre
                        FROM mod05_formato_reporte_servicio f
                        JOIN mod05_tiposervicio t ON t.id = f.tipo_servicio_id
                        WHERE f.tipo_servicio_id = :tipo AND f.vigente
                        ORDER BY f.version DESC LIMIT 1
                        """,
                        Map.of("tipo", tipoServicioId));
        if (filas.isEmpty()) return Optional.empty();
        var f = filas.getFirst();
        long id = Mod05Repository.number(f, "id");
        var campos =
                repo
                        .rows(
                                """
                                SELECT nombre_campo, tipo_dato, obligatorio, orden,
                                    subcampos::text AS subcampos
                                FROM mod05_campo_formato_reporte
                                WHERE formato_id = :formato ORDER BY orden
                                """,
                                Map.of("formato", id))
                        .stream()
                        .map(
                                c ->
                                        new DatosFormatoInforme.Campo(
                                                (String) c.get("nombre_campo"),
                                                (String) c.get("tipo_dato"),
                                                (boolean) c.get("obligatorio"),
                                                ((Number) c.get("orden")).intValue(),
                                                subcampos((String) c.get("subcampos"))))
                        .toList();
        return Optional.of(
                new DatosFormatoInforme.Formato(
                        id,
                        ((Number) f.get("version")).intValue(),
                        Mod05Repository.number(f, "tipo_servicio_id"),
                        (String) f.get("tipo_servicio_nombre"),
                        campos));
    }

    /**
     * Formato vigente del tipo de servicio de la atención y los datos de la última revisión que se
     * registró con ese mismo formato, para proponerlos al adjuntar una revisión nueva.
     */
    public Map<String, Object> deAtencion(long atencionId) {
        var atencion = repo.get("atenciontercerizada", atencionId);
        var formato = vigente(Mod05Repository.number(atencion, "tipo_servicio_id")).orElse(null);
        Map<String, Object> anteriores = null;
        if (formato != null) {
            var filas =
                    repo.rows(
                            """
                            SELECT revision, datos_formato::text AS datos_formato
                            FROM mod05_reporteproveedor
                            WHERE atencion_id = :atencion AND formato_id = :formato
                                AND datos_formato IS NOT NULL
                            ORDER BY revision DESC LIMIT 1
                            """,
                            Map.of("atencion", atencionId, "formato", formato.id()));
            if (!filas.isEmpty()) {
                anteriores = new LinkedHashMap<>();
                anteriores.put("revision", filas.getFirst().get("revision"));
                anteriores.put("datos", objeto((String) filas.getFirst().get("datos_formato")));
            }
        }
        var respuesta = new LinkedHashMap<String, Object>();
        respuesta.put("formato", formato);
        respuesta.put("datos_anteriores", anteriores);
        return respuesta;
    }

    /** Texto de un objeto JSON ({@code jsonb::text}) como mapa; {@code null} si no hay. */
    Map<String, Object> objeto(String texto) {
        if (texto == null) return null;
        try {
            return json.readValue(texto, OBJETO);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Datos del informe final ilegibles", e);
        }
    }

    private List<DatosFormatoInforme.Subcampo> subcampos(String texto) {
        if (texto == null) return null;
        try {
            return json.readValue(texto, SUBCAMPOS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Subcampos del formato de reporte ilegibles", e);
        }
    }
}
