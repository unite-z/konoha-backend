package pe.edu.pucp.areasverdes.mod03;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** HU-MOD03-003, «Restricción repetida cada jueves». */
class SerieRestriccionesApiTest extends ApiPlanificacion {

    ResultActions juevesDeNoviembre(String hasta) throws Exception {
        long tipo =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT id FROM mod03_tipo_restriccion"
                                        + " WHERE nombre = 'Actividad extracurricular'",
                                Long.class);
        return http.perform(
                post("/api/v1/planificacion/restricciones/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_restriccion_id\": %d, \"fecha_inicio\": \"2026-11-01\","
                                                .formatted(tipo)
                                        + " \"fecha_fin\": \"2026-11-01\", \"recurrencia\":"
                                        + " {\"dias_semana\": [4], \"hasta\": \""
                                        + hasta
                                        + "\"}}"));
    }

    int auditorias(String accion) {
        return jdbc.getJdbcTemplate()
                .queryForObject(
                        "SELECT count(*) FROM mod05_registroauditoria WHERE accion = ?",
                        Integer.class,
                        accion);
    }

    @Test
    void generaUnaRestriccionDeAvisoPorCadaJuevesConAuditoria() throws Exception {
        juevesDeNoviembre("2026-11-30")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].fecha_inicio").value("2026-11-05"))
                .andExpect(jsonPath("$[3].fecha_inicio").value("2026-11-26"))
                .andExpect(jsonPath("$[*].efecto").value(everyItem(is("Advertencia"))));
        assertEquals(4, auditorias("crear"));
    }

    @Test
    void rechazaUnaRepeticionSinDias() throws Exception {
        juevesDeNoviembre("2026-11-03")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("recurrencia-invalida"));
    }
}
