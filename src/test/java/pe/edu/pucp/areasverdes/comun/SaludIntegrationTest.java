package pe.edu.pucp.areasverdes.comun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** El healthcheck responde sin credenciales y sin exponer detalles internos. */
@SpringBootTest
@AutoConfigureMockMvc
class SaludIntegrationTest {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
    }

    @Autowired MockMvc http;

    @Test
    void respondeUpSinCredenciales() throws Exception {
        http.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void noPublicaOtrosEndpointsDeActuator() throws Exception {
        http.perform(get("/api/env")).andExpect(status().is4xxClientError());
        http.perform(get("/api/beans")).andExpect(status().is4xxClientError());
    }
}
