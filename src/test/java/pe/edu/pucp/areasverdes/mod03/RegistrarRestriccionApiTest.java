package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RegistrarRestriccionApiTest extends ApiPlanificacion {

    ResultActions registrar(String usuario, String tipo, String resto) throws Exception {
        long id =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT id FROM mod03_tipo_restriccion WHERE nombre = ?",
                                Long.class,
                                tipo);
        return http.perform(
                post("/api/v1/planificacion/restricciones/")
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo_restriccion_id\": " + id + ", " + resto + "}"));
    }

    @Test
    void registraUnExamenDeAdmisionConNivelDeProhibicion() throws Exception {
        registrar(
                        "jefe",
                        "Examen de admisión",
                        "\"fecha_inicio\": \"2026-11-08\", \"fecha_fin\": \"2026-11-08\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].efecto").value("Prohibicion"));
    }

    @Test
    void rechazaPeriodosInvertidosYUsuariosSinPermiso() throws Exception {
        registrar(
                        "jefe",
                        "Feriado",
                        "\"fecha_inicio\": \"2026-12-09\", \"fecha_fin\": \"2026-12-08\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("periodo-invalido"));
        registrar(
                        "practicante",
                        "Feriado",
                        "\"fecha_inicio\": \"2026-12-08\", \"fecha_fin\": \"2026-12-08\"")
                .andExpect(status().isForbidden());
    }
}
