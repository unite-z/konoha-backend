package pe.edu.pucp.areasverdes.mod03;

import static org.hamcrest.Matchers.containsString;
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

/** CU-003 6a (restricción duplicada) y 7a (ciclos programados que coinciden), AV-257. */
class DuplicadosRestriccionApiTest extends ApiPlanificacion {

    @BeforeEach
    void escenario() throws Exception {
        // Un corte (ruidoso) programado del 16 al 19/11/2027.
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                        .getContentAsString(StandardCharsets.UTF_8)
                                + "INSERT INTO mod03_programacion_servicio (tipo_servicio_id,"
                                + " estacion, fecha_inicio_prevista, fecha_fin_prevista,"
                                + " creado_por_id, usuario_creacion_id, usuario_modificacion_id)"
                                + " VALUES (1, 'Primavera', '2027-11-16', '2027-11-19', 1, 1, 1);");
    }

    ResultActions registrar(String tipo, String resto) throws Exception {
        long id =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT id FROM mod03_tipo_restriccion WHERE nombre = ?",
                                Long.class,
                                tipo);
        return http.perform(
                post("/api/v1/planificacion/restricciones/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo_restriccion_id\": " + id + ", " + resto + "}"));
    }

    static final String JUEVES = "\"fecha_inicio\": \"2027-11-04\", \"fecha_fin\": \"2027-11-04\"";

    @Test
    void elMismoTipoFechaYLugarEsPosibleDuplicadoSalvoQueSeFuerce() throws Exception {
        registrar("Actividad extracurricular", JUEVES).andExpect(status().isCreated());
        registrar("Actividad extracurricular", JUEVES)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("posible-duplicado"));
        registrar("Actividad extracurricular", JUEVES + ", \"forzar\": true")
                .andExpect(status().isCreated());
        registrar("Reserva de jardín", JUEVES).andExpect(status().isCreated());
    }

    @Test
    void enUnaSerieSeComparaCadaFecha() throws Exception {
        registrar(
                        "Actividad extracurricular",
                        "\"fecha_inicio\": \"2027-11-18\", \"fecha_fin\": \"2027-11-18\"")
                .andExpect(status().isCreated());
        registrar(
                        "Actividad extracurricular",
                        JUEVES
                                + ", \"recurrencia\": {\"dias_semana\": [4], \"hasta\":"
                                + " \"2027-11-25\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detalle").value(containsString("2027-11-18")));
    }

    @Test
    void devuelveLosCiclosProgramadosQueCoinciden() throws Exception {
        registrar(
                        "Actividad extracurricular",
                        "\"fecha_inicio\": \"2027-11-17\", \"fecha_fin\": \"2027-11-17\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].tipo").value("Actividad extracurricular"))
                .andExpect(jsonPath("$[0].ciclos_afectados.length()").value(1))
                .andExpect(jsonPath("$[0].ciclos_afectados[0].servicio").value("Corte de césped"))
                .andExpect(
                        jsonPath("$[0].ciclos_afectados[0].fecha_inicio_prevista")
                                .value("2027-11-16"));
        registrar(
                        "Día sin actividad académica",
                        "\"fecha_inicio\": \"2027-11-17\", \"fecha_fin\": \"2027-11-17\"")
                .andExpect(jsonPath("$[0].ciclos_afectados.length()").value(0));
    }
}
