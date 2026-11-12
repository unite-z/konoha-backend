package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Uso de la IA (AV-396): una petición sin IA configurada deja su fallo registrado. */
class UsoIaApiTest extends ApiGestion {

    @Test
    void elFalloPorFaltaDeConfiguracionQuedaRegistrado() throws Exception {
        jdbc.getJdbcTemplate().execute("TRUNCATE mod06_uso_ia");
        http.perform(
                        post("/api/v1/ia/priorizaciones/")
                                .cookie(sesion("jefe"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"alcance\": \"Areas\", \"horizonte_dias\": 7}"))
                .andExpect(status().isServiceUnavailable());

        Map<String, Object> fila =
                jdbc.queryForMap(
                        "SELECT asistente, correcto, error, tokens_entrada FROM mod06_uso_ia",
                        Map.of());
        assertEquals("priorizacion", fila.get("asistente"));
        assertEquals(false, fila.get("correcto"));
        assertEquals(
                "El análisis con IA no está configurado; registra los datos manualmente",
                fila.get("error"));
        assertEquals(null, fila.get("tokens_entrada"));
    }
}
