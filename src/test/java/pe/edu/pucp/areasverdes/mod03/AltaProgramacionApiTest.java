package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AltaProgramacionApiTest extends ApiPlanificacion {

    @BeforeEach
    void escenario() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
    }

    ResultActions programar(String usuario, int servicio, String inicio, String fin)
            throws Exception {
        return http.perform(
                post("/api/v1/planificacion/programaciones/")
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_servicio_id\": %d, \"fecha_inicio_prevista\": \"%s\","
                                                .formatted(servicio, inicio)
                                        + " \"fecha_fin_prevista\": \"%s\"}".formatted(fin)));
    }

    @Test
    void elJefeProgramaYCincoDiasDeCorteSuperanTresMasUno() throws Exception {
        programar("jefe", 2, "2027-01-11", "2027-01-14")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.servicio").value("Control fitosanitario"))
                .andExpect(jsonPath("$.estacion").value("Verano"))
                .andExpect(jsonPath("$.estado").value("Programada"));
        programar("jefe", 1, "2027-01-11", "2027-01-15")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("duracion-excedida"));
    }

    @Test
    void sinDuracionConfiguradaNoSeProgramaYSoloElJefePrograma() throws Exception {
        programar("jefe", 3, "2027-01-11", "2027-01-11")
                .andExpect(jsonPath("$.codigo").value("configuracion-incompleta"));
        programar("ingeniero", 2, "2027-01-11", "2027-01-12").andExpect(status().isForbidden());
    }
}
