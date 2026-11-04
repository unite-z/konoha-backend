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

    ResultActions programarTercerizado(String inicio, String fin, String ventana) throws Exception {
        long proveedor =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "INSERT INTO mod05_proveedor(razon_social, activo) VALUES"
                                        + " ('Jardines Demo', true) RETURNING id",
                                Long.class);
        return http.perform(
                post("/api/v1/planificacion/programaciones/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                ("{\"tipo_servicio_id\": 2, \"proveedor_id\": %d,"
                                                + " \"fecha_inicio_prevista\": \"%s\","
                                                + " \"fecha_fin_prevista\": \"%s\"%s}")
                                        .formatted(
                                                proveedor,
                                                inicio,
                                                fin,
                                                ventana == null
                                                        ? ""
                                                        : ", \"ventana_mes\": \""
                                                                + ventana
                                                                + "\"")));
    }

    @Test
    void elTercerizadoTieneQueCaerEnSuVentanaMensual() throws Exception {
        programarTercerizado("2027-12-02", "2027-12-03", "2027-11")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fuera-de-ventana"));
        programarTercerizado("2027-11-29", "2027-12-01", null)
                .andExpect(jsonPath("$.codigo").value("fuera-de-ventana"));
        programarTercerizado("2027-11-10", "2027-11-11", "2027-11")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ventana_mes").value("2027-11"))
                .andExpect(jsonPath("$.historial[0].estado").value("Programada"))
                .andExpect(jsonPath("$.historial[0].autor").value("jefe"));
    }

    @Test
    void conPersonalPropioNoHayVentana() throws Exception {
        programar("jefe", 2, "2027-01-11", "2027-01-12")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ventana_mes").doesNotExist());
    }
}
