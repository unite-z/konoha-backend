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

/** Ciclo de vida de una programación: Programada, EnCurso, Completada o Cancelada. */
class EstadosProgramacionApiTest extends ApiPlanificacion {

    @BeforeEach
    void escenario() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                        .getContentAsString(StandardCharsets.UTF_8)
                                + "UPDATE mod03_programacion_servicio SET estado = 'Programada'"
                                + " WHERE id = 1;");
    }

    ResultActions registrar(String usuario, String accion, String fecha) throws Exception {
        return http.perform(
                post("/api/v1/planificacion/programaciones/1/" + accion + "/")
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fecha == null ? "" : "{\"fecha\": \"" + fecha + "\"}"));
    }

    @Test
    void registraElInicioYElFinConSusFechasReales() throws Exception {
        registrar("jefe", "inicio", "2026-10-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EnCurso"))
                .andExpect(jsonPath("$.fecha_inicio_real").value("2026-10-01"));
        registrar("jefe", "fin", "2026-10-04")
                .andExpect(jsonPath("$.estado").value("Completada"))
                .andExpect(jsonPath("$.fecha_fin_real").value("2026-10-04"));
    }

    @Test
    void rechazaTransicionesNoPermitidas() throws Exception {
        registrar("jefe", "fin", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("transicion-invalida"));
        registrar("jefe", "cancelacion", null).andExpect(jsonPath("$.estado").value("Cancelada"));
        registrar("jefe", "inicio", null).andExpect(status().isConflict());
        registrar("practicante", "inicio", null).andExpect(status().isForbidden());
    }
}
