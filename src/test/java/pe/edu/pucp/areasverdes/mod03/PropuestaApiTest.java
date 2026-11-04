package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
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
                .andExpect(jsonPath("$.conflictos[0].tipo").value("Examen de admisión"))
                .andExpect(jsonPath("$.ventana_libre_inicio").value(HOY.plusDays(2).toString()));
        proponer(2) // Control fitosanitario: no es ruidoso y el evento solo afecta a los ruidosos.
                .andExpect(jsonPath("$.conflictos.length()").value(0))
                .andExpect(jsonPath("$.ventana_libre_inicio").doesNotExist());
    }

    @Test
    void laCasillaDeLaRestriccionMandaSobreLaDelTipo() throws Exception {
        long actividad =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT id FROM mod03_tipo_restriccion WHERE nombre = ?",
                                Long.class,
                                "Actividad extracurricular");
        http.perform(
                        post("/api/v1/planificacion/restricciones/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"tipo_restriccion_id\": %d, \"fecha_inicio\": \"%s\","
                                                        .formatted(actividad, HOY.plusDays(1))
                                                + " \"fecha_fin\": \"%s\","
                                                        .formatted(HOY.plusDays(1))
                                                + " \"aplica_servicios_ruidosos\": false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].aplica_servicios_ruidosos").value(false));
        // El tipo solo afecta a los ruidosos, pero esta se guardó para todos los servicios.
        proponer(2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictos.length()").value(1))
                .andExpect(jsonPath("$.conflictos[0].tipo").value("Actividad extracurricular"));
    }
}
