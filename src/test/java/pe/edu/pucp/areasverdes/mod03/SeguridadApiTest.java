package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class SeguridadApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/no-existe/";

    @Test
    void sinCredencialesResponde401() throws Exception {
        http.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("no-autenticado"));
    }

    @Test
    void credencialesInvalidasResponden401() throws Exception {
        http.perform(get(RUTA).with(httpBasic("jefe", "otra-clave")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conCredencialesPasaLaAutenticacion() throws Exception {
        http.perform(get(RUTA).with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void elRestoDeApiV1SigueSinCambios() throws Exception {
        http.perform(get("/api/v1/no-existe/")).andExpect(status().isNotFound());
    }
}
