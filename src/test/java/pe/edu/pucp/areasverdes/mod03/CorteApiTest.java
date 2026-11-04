package pe.edu.pucp.areasverdes.mod03;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Ciclo de corte con la ruta de MOD-02 (AV-259, RF-COR-001). Zonas: Sociales (2 jardines), Ciencias
 * (1) y «Biblioteca» a demanda (1). El último corte terminó hace 30 días y el máximo es 20.
 */
class CorteApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/";

    @Autowired ObjectMapper json;

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        sql.execute(
                new ClassPathResource("mod03/escenario-programacion.sql")
                                .getContentAsString(StandardCharsets.UTF_8)
                        + "UPDATE mod03_frecuencia_servicio SET frecuencia_teorica_dias = 14,"
                        + " frecuencia_practica_dias = 15, frecuencia_maxima_dias = 20;");
        jardines("Sociales", false, "Jardín de Sociales", "Patio de Sociales");
        jardines("Ciencias", false, "Jardín de Ciencias");
        jardines("Biblioteca", true, "Jardín de la Biblioteca");
    }

    void jardines(String zona, boolean aDemanda, String... nombres) {
        var sql = jdbc.getJdbcTemplate();
        long sector =
                sql.queryForObject(
                        "INSERT INTO mod02_sector (nombre, atencion_a_demanda, usuario_creacion_id,"
                                + " usuario_modificacion_id) VALUES (?, ?, 1, 1) RETURNING id",
                        Long.class,
                        zona,
                        aDemanda);
        for (String nombre : nombres) {
            long area =
                    sql.queryForObject(
                            "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                                    + " usuario_creacion_id, usuario_modificacion_id) VALUES (?, ?,"
                                    + " 'Campus', 100, 1, 1) RETURNING id",
                            Long.class,
                            nombre,
                            nombre);
            sql.update(
                    "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, usuario_id)"
                            + " VALUES (?, ?, 1)",
                    area,
                    sector);
        }
    }

    ResultActions programar(String zonas, String inicio, String fin) throws Exception {
        return http.perform(
                post(RUTA + "programaciones/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                ("{\"tipo_servicio_id\": 1, \"fecha_inicio_prevista\": \"%s\","
                                                + " \"fecha_fin_prevista\": \"%s\", \"zonas\": %s}")
                                        .formatted(inicio, fin, zonas)));
    }

    @Test
    void laRutaPropuestaVaPorZonasYAvisaDeLaFrecuencia() throws Exception {
        http.perform(get(RUTA + "servicios/1/ruta/").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonas.length()").value(3))
                .andExpect(jsonPath("$.zonas[0].zona").value("Biblioteca"))
                .andExpect(jsonPath("$.zonas[0].en_ruta").value(false))
                .andExpect(jsonPath("$.zonas[2].zona").value("Sociales"))
                .andExpect(jsonPath("$.zonas[2].jardines").value(2))
                .andExpect(jsonPath("$.fuera_de_frecuencia.dias_desde_ultimo").value(30))
                .andExpect(jsonPath("$.fuera_de_frecuencia.maximo_dias").value(20));
    }

    @Test
    void programarCopiaLaRutaDeLasZonasElegidas() throws Exception {
        String ciclo =
                programar("[\"Sociales\", \"Ciencias\"]", "2027-01-11", "2027-01-12")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.areas.length()").value(3))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id = json.readTree(ciclo).get("id").asLong();
        http.perform(get(RUTA + "ciclos/" + id + "/").with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programacion_id").value(id))
                .andExpect(jsonPath("$.jardines.length()").value(3))
                .andExpect(jsonPath("$.jardines[0].zona").value("Ciencias"))
                .andExpect(jsonPath("$.jardines[0].codigo").value(startsWith("J-")))
                .andExpect(jsonPath("$.jardines[0].estado").value("Pendiente"))
                .andExpect(jsonPath("$.interrupciones.length()").value(0));
    }

    @Test
    void rechazaZonasQueNoExistenYMasDeTresMasUno() throws Exception {
        programar("[\"Letras\"]", "2027-01-11", "2027-01-12")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("zona-invalida"));
        programar("[\"Sociales\"]", "2027-01-11", "2027-01-15")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("duracion-excedida"));
        http.perform(get(RUTA + "ciclos/999999/").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isNotFound());
    }
}
