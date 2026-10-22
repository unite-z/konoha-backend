package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.ResultActions;

/** HU-MOD03-010: «La fecha propuesta cae en un día restringido». */
class PropuestaApiTest extends ApiPlanificacion {

    static final LocalDate HOY = LocalDate.now(LIMA);

    @BeforeEach
    void escenario() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
    }

    ResultActions proponer(int servicio) throws Exception {
        return http.perform(
                get("/api/v1/planificacion/servicios/{id}/propuesta/", servicio)
                        .with(httpBasic("ingeniero", CLAVE)));
    }

    @Test
    void avisaDelEventoMasivoYProponeLaSiguienteVentanaLibre() throws Exception {
        proponer(1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha_inicio").value(HOY.toString()))
                .andExpect(jsonPath("$.fecha_fin").value(HOY.plusDays(3).toString()))
                .andExpect(jsonPath("$.conflictos[0].tipo").value("Evento masivo"))
                .andExpect(jsonPath("$.ventana_libre_inicio").value(HOY.plusDays(2).toString()));
        proponer(2) // Control fitosanitario: no es ruidoso y el evento solo afecta a los ruidosos.
                .andExpect(jsonPath("$.conflictos.length()").value(0))
                .andExpect(jsonPath("$.ventana_libre_inicio").doesNotExist());
    }
}
