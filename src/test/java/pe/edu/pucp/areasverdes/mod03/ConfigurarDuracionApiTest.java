package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ConfigurarDuracionApiTest extends ApiPlanificacion {

    @BeforeEach
    void servicio() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_tiposervicio RESTART IDENTITY CASCADE; INSERT INTO"
                                + " mod05_tiposervicio(nombre, ejecutor, es_riesgo, activo,"
                                + " es_periodico) VALUES ('Poda mayor programada', 'Tercerizado',"
                                + " false, true, true)");
    }

    ResultActions configurar(String recurso, String cuerpo) throws Exception {
        return http.perform(
                put("/api/v1/planificacion/servicios/1/" + recurso + "/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    @Test
    void guardaLaDuracionDeLaPodaMayorConAutor() throws Exception {
        configurar("duracion", "{\"duracion_maxima_dias\": 10, \"hora_inicio\": \"06:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracion.duracion_maxima_dias").value(10))
                .andExpect(jsonPath("$.duracion.modificado_por").value("jefe"));
    }

    @Test
    void rechazaCeroDiasYMantieneLaConfiguracionAnterior() throws Exception {
        configurar("duracion", "{\"duracion_maxima_dias\": 10}");
        configurar("duracion", "{\"duracion_maxima_dias\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("validacion"))
                .andExpect(jsonPath("$.errores.duracion_maxima_dias[0]").exists());
        assertEquals(
                10,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT duracion_maxima_dias FROM mod03_duracion_servicio",
                                Integer.class));
    }

    @Test
    void rechazaUnHorarioInvertido() throws Exception {
        configurar(
                        "duracion",
                        "{\"duracion_maxima_dias\": 1, \"hora_inicio\": \"18:00\","
                                + " \"hora_fin\": \"06:00\"}")
                .andExpect(jsonPath("$.codigo").value("horario-invalido"));
    }

    @Test
    void cadaCambioDejaAuditoriaConElValorAnterior() throws Exception {
        configurar("frecuencias/Verano", "{\"frecuencia_teorica_dias\": 30}");
        configurar("duracion", "{\"duracion_maxima_dias\": 10}");
        configurar("duracion", "{\"duracion_maxima_dias\": 12}");
        assertEquals(
                10,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT (valores_anteriores->>'duracion_maxima_dias')::int"
                                        + " FROM mod05_registroauditoria"
                                        + " ORDER BY id DESC LIMIT 1",
                                Integer.class));
        assertEquals(
                3,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod05_registroauditoria", Integer.class));
    }
}
