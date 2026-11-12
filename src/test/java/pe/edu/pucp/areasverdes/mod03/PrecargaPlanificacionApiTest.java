package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** AV-368: la precarga dev deja periódicos y ruidosos los servicios de demostración. */
class PrecargaPlanificacionApiTest extends ApiPlanificacion {

    @Autowired PrecargaPlanificacion precarga;

    /** Los tipos tal como los deja la precarga de MOD-05: sin los campos de MOD-03. */
    @BeforeEach
    void tiposDeMod05() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_tiposervicio RESTART IDENTITY CASCADE; INSERT INTO"
                                + " mod05_tiposervicio(id, nombre, ejecutor, es_riesgo, activo)"
                                + " VALUES (1, 'Corte de césped', 'Tercerizado', false, true),"
                                + " (2, 'Control fitosanitario', 'Tercerizado', false, true),"
                                + " (3, 'Poda mayor programada', 'Tercerizado', false, true),"
                                + " (4, 'Poda de altura a demanda', 'Tercerizado', true, true)");
    }

    @Test
    void marcaLosPeriodicosYLosRuidososYCargaLosValoresIniciales() throws Exception {
        precarga.cargar(Path.of("mock-data"));
        precarga.cargar(Path.of("mock-data")); // Repetirla no cambia nada.
        http.perform(get("/api/v1/planificacion/servicios/").with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Corte de césped"))
                .andExpect(jsonPath("$[0].es_ruidoso").value(true))
                .andExpect(jsonPath("$[1].es_ruidoso").value(false));
        http.perform(
                        get("/api/v1/planificacion/servicios/2/valores-iniciales/")
                                .with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracion.hora_inicio").value("06:00:00"));
    }

    @Test
    void noPisaUnTipoQueYaEsPeriodico() throws Exception {
        jdbc.getJdbcTemplate()
                .update("UPDATE mod05_tiposervicio SET es_periodico = true WHERE id = 1");
        precarga.cargar(Path.of("mock-data"));
        http.perform(get("/api/v1/planificacion/servicios/").with(httpBasic("practicante", CLAVE)))
                .andExpect(jsonPath("$[0].es_ruidoso").value(false));
    }
}
