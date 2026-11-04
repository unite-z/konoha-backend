package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Historial de la configuración de un servicio (AV-256), de la auditoría común de MOD-01. */
class HistorialServicioApiTest extends ApiPlanificacion {

    @BeforeEach
    void servicio() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_tiposervicio RESTART IDENTITY CASCADE; INSERT INTO"
                                + " mod05_tiposervicio(nombre, ejecutor, es_riesgo, activo,"
                                + " es_periodico) VALUES ('Corte de césped', 'Tercerizado',"
                                + " false, true, true)");
    }

    ResultActions configurar(String recurso, String cuerpo) throws Exception {
        return http.perform(
                put("/api/v1/planificacion/servicios/1/" + recurso + "/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    ResultActions historial(String usuario) throws Exception {
        return http.perform(
                get("/api/v1/planificacion/servicios/1/historial/")
                        .with(httpBasic(usuario, CLAVE)));
    }

    @Test
    void cadaCambioQuedaConSuValorAnteriorYElNuevo() throws Exception {
        configurar("frecuencias/Verano", "{\"frecuencia_teorica_dias\": 21}")
                .andExpect(status().isOk());
        configurar("duracion", "{\"duracion_maxima_dias\": 4}").andExpect(status().isOk());
        configurar("duracion", "{\"duracion_maxima_dias\": 3}").andExpect(status().isOk());
        configurar(
                        "frecuencias/Verano",
                        "{\"frecuencia_teorica_dias\": 21, \"frecuencia_maxima_dias\": 45}")
                .andExpect(status().isOk());
        historial("ingeniero")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].campo").value("frecuencia_maxima_dias"))
                .andExpect(jsonPath("$[0].estacion").value("Verano"))
                .andExpect(jsonPath("$[0].anterior").isEmpty())
                .andExpect(jsonPath("$[0].nuevo").value("45"))
                .andExpect(jsonPath("$[1].campo").value("duracion_maxima_dias"))
                .andExpect(jsonPath("$[1].anterior").value("4"))
                .andExpect(jsonPath("$[1].nuevo").value("3"))
                .andExpect(jsonPath("$[1].autor").value("jefe"))
                .andExpect(jsonPath("$[1].fecha_hora").exists())
                .andExpect(jsonPath("$[2].campo").value("duracion_maxima_dias"))
                .andExpect(jsonPath("$[2].nuevo").value("4"))
                .andExpect(jsonPath("$[3].campo").value("inicial"));
    }

    @Test
    void sinCambiosElHistorialEstaVacioYSinRolEs403() throws Exception {
        historial("jefe").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        historial("sinrol").andExpect(status().isForbidden());
    }
}
