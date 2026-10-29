package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.pucp.areasverdes.mod06.gestion.api.DocumentacionGestion;

/**
 * OpenAPI de los reportes de gestión ({@code docs/estandares/api.md}, «OpenAPI»): rutas, seguridad
 * {@code basicAuth}, respuestas de error, etiquetas y parámetros en snake_case. Solo comprueba las
 * operaciones de este subpaquete.
 */
class OpenApiGestionApiTest extends ApiGestion {

    /** Ruta canónica → etiqueta. */
    static final Map<String, String> OPERACIONES =
            Map.of(
                    "/api/v1/reportes/panel", DocumentacionGestion.TAG_REP_001,
                    "/api/v1/reportes/panel/{indicador}", DocumentacionGestion.TAG_REP_001,
                    "/api/v1/reportes/material-vegetal", DocumentacionGestion.TAG_REP_005,
                    "/api/v1/reportes/mapa-atenciones", DocumentacionGestion.TAG_REP_007);

    @Autowired ObjectMapper json;

    JsonNode esquema;

    @BeforeEach
    void leerEsquema() throws Exception {
        String texto =
                http.perform(get("/api/schema"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        esquema = json.readTree(texto);
    }

    /** La operación GET de la ruta, con o sin barra final (la que publique springdoc). */
    JsonNode operacion(String ruta) {
        JsonNode rutas = esquema.path("paths");
        JsonNode conBarra = rutas.path(ruta + "/").path("get");
        JsonNode sinBarra = rutas.path(ruta).path("get");
        JsonNode op = sinBarra.isMissingNode() ? conBarra : sinBarra;
        assertFalse(op.isMissingNode(), "Falta en OpenAPI: GET " + ruta);
        return op;
    }

    @Test
    void lasRutasEstanDocumentadasConSeguridadEtiquetaYErrores() {
        for (var e : OPERACIONES.entrySet()) {
            JsonNode op = operacion(e.getKey());
            boolean basic = false;
            for (JsonNode requisito : op.path("security")) {
                basic |= requisito.has("basicAuth");
            }
            assertTrue(basic, "Sin basicAuth: " + e.getKey());
            assertTrue(
                    op.path("tags").toString().contains(e.getValue()), "Etiqueta: " + e.getKey());
            for (String codigo : List.of("400", "401", "403")) {
                assertNotNull(op.path("responses").get(codigo), codigo + " en " + e.getKey());
            }
        }
        assertNotNull(operacion("/api/v1/reportes/panel/{indicador}").path("responses").get("404"));
    }

    @Test
    void losParametrosVanEnSnakeCase() {
        Set<String> nombres = new HashSet<>();
        for (String ruta : OPERACIONES.keySet()) {
            for (JsonNode p : operacion(ruta).path("parameters")) {
                nombres.add(p.path("name").asText());
            }
        }
        for (String esperado :
                List.of(
                        "area_verde_id",
                        "tipo_servicio_id",
                        "tipo_atencion",
                        "tipo_uso_id",
                        "es_riesgo",
                        "mes",
                        "desde",
                        "hasta")) {
            assertTrue(nombres.contains(esperado), "Falta el parámetro " + esperado);
        }
        for (String camel :
                List.of("areaVerdeId", "tipoServicioId", "tipoAtencion", "tipoUsoId", "esRiesgo")) {
            assertFalse(nombres.contains(camel), "Parámetro en camelCase: " + camel);
        }
    }
}
