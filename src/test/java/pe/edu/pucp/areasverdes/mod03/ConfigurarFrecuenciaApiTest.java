package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ConfigurarFrecuenciaApiTest extends ApiPlanificacion {

    @BeforeEach
    void servicios() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_tiposervicio RESTART IDENTITY CASCADE; INSERT INTO"
                            + " mod05_tiposervicio(nombre, ejecutor, es_riesgo, activo,"
                            + " es_periodico, es_ruidoso) VALUES ('Corte de césped', 'Tercerizado',"
                            + " false, true, true, true), ('Poda de altura a demanda',"
                            + " 'Tercerizado', true, true, false, false)");
    }

    ResultActions configurar(String usuario, long servicio, String estacion, String cuerpo)
            throws Exception {
        return http.perform(
                put("/api/v1/planificacion/servicios/{id}/frecuencias/{e}/", servicio, estacion)
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    static final String CORTE_VERANO =
            "{\"frecuencia_teorica_dias\": 21, \"frecuencia_practica_dias\": 30,"
                    + " \"frecuencia_maxima_dias\": 45}";

    @Test
    void elJefeGuardaYActualizaLaFrecuenciaConSuAutor() throws Exception {
        configurar("jefe", 1, "Verano", CORTE_VERANO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuencias[0].frecuencia_practica_dias").value(30))
                .andExpect(jsonPath("$.frecuencias[0].modificado_por").value("jefe"));
        configurar("jefe", 1, "Verano", "{\"frecuencia_teorica_dias\": 25}")
                .andExpect(jsonPath("$.frecuencias.length()").value(1))
                .andExpect(jsonPath("$.frecuencias[0].frecuencia_maxima_dias").doesNotExist());
    }

    @Test
    void rechazaUnaMaximaMenorQueLaHabitual() throws Exception {
        configurar(
                        "jefe",
                        1,
                        "Otoño",
                        "{\"frecuencia_teorica_dias\": 30, \"frecuencia_maxima_dias\": 20}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("frecuencia-maxima-menor"));
    }

    @Test
    void validaLaEstacionYQueElServicioSeaPeriodico() throws Exception {
        configurar("jefe", 1, "Monzón", CORTE_VERANO)
                .andExpect(jsonPath("$.codigo").value("estacion-invalida"));
        configurar("jefe", 2, "Verano", CORTE_VERANO).andExpect(status().isConflict());
        configurar("jefe", 9, "Verano", CORTE_VERANO).andExpect(status().isNotFound());
    }

    @Test
    void soloElJefeConfigura() throws Exception {
        configurar("ingeniero", 1, "Verano", CORTE_VERANO).andExpect(status().isForbidden());
    }
}
