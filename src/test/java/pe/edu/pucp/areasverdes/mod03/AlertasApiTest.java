package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.ResultActions;

/** HU-MOD03-006 y HU-MOD03-007. Umbral de aviso por defecto: 7 días antes del máximo. */
class AlertasApiTest extends ApiPlanificacion {

    @BeforeEach
    void escenario() throws Exception {
        // Corte cerrado hace 38 días con máximo de 45 (quedan 7); fitosanitario en curso desde
        // hace 5 días con una duración máxima de 3 + 1 (1 día de exceso).
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                        .getContentAsString(StandardCharsets.UTF_8)
                                + "UPDATE mod03_frecuencia_servicio SET frecuencia_maxima_dias ="
                                + " 45; UPDATE mod03_programacion_servicio SET"
                                + " fecha_inicio_prevista = (now() AT TIME ZONE"
                                + " 'America/Lima')::date - 41, fecha_fin_prevista = (now() AT TIME"
                                + " ZONE 'America/Lima')::date - 38 WHERE tipo_servicio_id = 1;"
                                + " UPDATE mod03_programacion_servicio SET estado = 'EnCurso',"
                                + " fecha_inicio_real = (now() AT TIME ZONE 'America/Lima')::date -"
                                + " 4 WHERE tipo_servicio_id = 2;");
    }

    ResultActions alertas() throws Exception {
        return http.perform(get("/api/v1/planificacion/alertas/").with(httpBasic("jefe", CLAVE)));
    }

    @Test
    void avisaDeLaFrecuenciaProximaYDeLaDuracionExcedida() throws Exception {
        alertas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipo").value("FrecuenciaProxima"))
                .andExpect(jsonPath("$[0].dias").value(7))
                .andExpect(jsonPath("$[1].tipo").value("DuracionExcedida"))
                .andExpect(jsonPath("$[1].dias").value(1));
    }

    @Test
    void sinAlertaDeFrecuenciaSiYaEstaProgramado() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        "INSERT INTO mod03_programacion_servicio(tipo_servicio_id, estacion,"
                                + " fecha_inicio_prevista, fecha_fin_prevista, creado_por_id,"
                                + " usuario_creacion_id, usuario_modificacion_id) VALUES (1,"
                                + " 'Verano', '2030-01-10', '2030-01-12', 1, 1, 1)");
        alertas()
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("DuracionExcedida"));
    }
}
