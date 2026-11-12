package pe.edu.pucp.areasverdes.mod05;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Formato estándar inicial del informe del proveedor (RF-SER-009) en el perfil dev. La migración
 * V73 lo carga en las bases cuyos tipos de servicio ya existían; en una base nueva los tipos llegan
 * después, con la precarga de MOD-05, y entonces lo carga esta clase desde {@code
 * mod05/FormatoReporteServicio.json} y {@code CampoFormatoReporte.json}. El tipo de servicio se
 * resuelve por nombre. No toca un tipo que ya tenga formato.
 */
@Service
public class FormatosReporteIniciales {

    private static final TypeReference<List<Map<String, Object>>> LISTA = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public FormatosReporteIniciales(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public void precargar(Path carpeta) {
        List<Map<String, Object>> tipos = leer(carpeta.resolve("mod03/TipoServicio.json"));
        List<Map<String, Object>> formatos =
                leer(carpeta.resolve("mod05/FormatoReporteServicio.json"));
        List<Map<String, Object>> campos = leer(carpeta.resolve("mod05/CampoFormatoReporte.json"));
        Map<Object, Object> nombreTipo = new HashMap<>();
        tipos.forEach(t -> nombreTipo.put(t.get("id"), t.get("nombre")));
        for (Map<String, Object> formato : formatos) {
            Long id =
                    jdbc.query(
                            """
                            INSERT INTO mod05_formato_reporte_servicio (tipo_servicio_id, version, vigente)
                            SELECT t.id, :version, :vigente FROM mod05_tiposervicio t
                            WHERE t.nombre = :tipo
                              AND NOT EXISTS (SELECT 1 FROM mod05_formato_reporte_servicio f WHERE f.tipo_servicio_id = t.id)
                            RETURNING id
                            """,
                            new MapSqlParameterSource()
                                    .addValue(
                                            "tipo", nombreTipo.get(formato.get("tipo_servicio_id")))
                                    .addValue(
                                            "version", ((Number) formato.get("version")).intValue())
                                    .addValue(
                                            "vigente", Boolean.TRUE.equals(formato.get("vigente"))),
                            rs -> rs.next() ? rs.getLong(1) : null);
            if (id == null) continue;
            int orden = 0;
            for (Map<String, Object> campo : campos) {
                if (!formato.get("id").equals(campo.get("formato_id"))) continue;
                jdbc.update(
                        """
                        INSERT INTO mod05_campo_formato_reporte
                            (formato_id, orden, nombre_campo, tipo_dato, obligatorio, subcampos)
                        VALUES (:formato, :orden, :nombre, :tipo, :obligatorio, CAST(:subcampos AS jsonb))
                        """,
                        new MapSqlParameterSource()
                                .addValue("formato", id)
                                .addValue("orden", ++orden)
                                .addValue("nombre", campo.get("nombre_campo"))
                                .addValue("tipo", campo.get("tipo_dato"))
                                .addValue(
                                        "obligatorio",
                                        Boolean.TRUE.equals(campo.get("obligatorio")))
                                .addValue("subcampos", texto(campo.get("subcampos"))));
            }
        }
    }

    private List<Map<String, Object>> leer(Path archivo) {
        try {
            return json.readValue(archivo.toFile(), LISTA);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Precarga del formato de reporte inválida: " + archivo, e);
        }
    }

    private String texto(Object subcampos) {
        if (subcampos == null) return null;
        try {
            return json.writeValueAsString(subcampos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Subcampos del formato de reporte inválidos", e);
        }
    }
}
