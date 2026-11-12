package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Resumen de uso y costes de la IA (AV-396), con los precios vigentes en la fecha de cada llamada.
 */
class ResumenUsoIaApiTest extends ApiGestion {

    @BeforeEach
    void llamadas() {
        jdbc.getJdbcTemplate().execute("TRUNCATE mod06_uso_ia");
        // Solo los precios de la migración (V90): otras pruebas pueden dejar precios propios.
        jdbc.getJdbcTemplate()
                .execute(
                        "DELETE FROM mod06_precio_ia WHERE vigente_desde NOT IN (DATE '2026-01-01',"
                                + " DATE '2027-01-01')");
        // 2 llamadas de DeepSeek (1 M de entrada, de ellos 0,5 M en caché, y 0,1 M de salida cada
        // una) y
        // 1 fallo de Gemini, todas el 10/11/2026 en Lima.
        jdbc.getJdbcTemplate()
                .execute(
                        "INSERT INTO mod06_uso_ia (solicitado_en, asistente, proveedor, modelo,"
                            + " duracion_ms, correcto, error, tokens_entrada, tokens_salida,"
                            + " tokens_cache) VALUES ('2026-11-10 10:00-05', 'priorizacion',"
                            + " 'DeepSeek', 'deepseek-flash', 1000, true, null, 1000000, 100000,"
                            + " 500000), ('2026-11-10 11:00-05', 'cronograma', 'DeepSeek',"
                            + " 'deepseek-flash', 3000, true, null, 1000000, 100000, 500000),"
                            + " ('2026-11-10 12:00-05', 'cronograma', 'Gemini (Google AI Studio)',"
                            + " 'gemini-3.8-flash', 20000, false, 'No se pudo', null, null, null)");
    }

    @Test
    void calculaElCosteConLosPreciosDeLaFechaYCuentaLosFallos() throws Exception {
        // Cada llamada: 0,5 M × 0,30 + 0,5 M × 0,006 + 0,1 M × 1,20 = 0,273 USD
        http.perform(
                        get("/api/v1/ia/uso/?desde=2026-11-01&hasta=2026-11-30")
                                .cookie(sesion("jefe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.llamadas").value(3))
                .andExpect(jsonPath("$.totales.fallidas").value(1))
                .andExpect(jsonPath("$.totales.coste_usd").value(0.546))
                .andExpect(jsonPath("$.totales.tokens_entrada").value(2000000))
                .andExpect(jsonPath("$.por_asistente[0].asistente").value("cronograma"))
                .andExpect(jsonPath("$.por_dia[0].fecha").value("2026-11-10"))
                .andExpect(jsonPath("$.historial[0].modelo").value("gemini-3.8-flash"))
                .andExpect(jsonPath("$.historial[0].correcto").value(false));
    }

    @Test
    void unPrecioNuevoSoloAfectaDesdeSuFecha() throws Exception {
        http.perform(
                        put("/api/v1/ia/precios/")
                                .cookie(sesion("jefe"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"modelo\": \"deepseek-flash\", \"vigente_desde\":"
                                                + " \"2026-11-11\", \"usd_millon_entrada\": 3,"
                                                + " \"usd_millon_salida\": 12}"))
                .andExpect(status().isOk());
        http.perform(
                        get("/api/v1/ia/uso/?desde=2026-11-01&hasta=2026-11-30")
                                .cookie(sesion("jefe")))
                .andExpect(jsonPath("$.totales.coste_usd").value(0.546));
        jdbc.getJdbcTemplate()
                .execute("DELETE FROM mod06_precio_ia WHERE vigente_desde = '2026-11-11'");
    }

    @Test
    void validaElModeloYLasFechas() throws Exception {
        http.perform(
                        put("/api/v1/ia/precios/")
                                .cookie(sesion("jefe"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"modelo\": \"gpt\", \"usd_millon_entrada\": 1,"
                                                + " \"usd_millon_salida\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.modelo[0]").value("Elige un modelo de la lista"));
        http.perform(
                        get("/api/v1/ia/uso/?desde=2026-11-30&hasta=2026-11-01")
                                .cookie(sesion("jefe")))
                .andExpect(status().isBadRequest());
    }
}
