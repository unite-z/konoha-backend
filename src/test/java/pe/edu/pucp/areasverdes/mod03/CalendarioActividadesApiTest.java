package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HU-MOD03-005: calendario de actividades con filtros. */
class CalendarioActividadesApiTest extends ApiPlanificacion {

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        sql.execute("TRUNCATE mod05_proveedor RESTART IDENTITY CASCADE");
        sql.execute(
                new ClassPathResource("mod03/escenario-programacion.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
        sql.execute(
                "INSERT INTO mod05_proveedor(razon_social, activo) VALUES ('Agroservicios Lima"
                        + " SAC', true); UPDATE mod03_programacion_servicio SET proveedor_id = 1"
                        + " WHERE tipo_servicio_id = 1");
    }

    MockHttpServletRequestBuilder calendario() {
        return get("/api/v1/planificacion/programaciones/")
                .param("desde", "2020-01-01")
                .param("hasta", "2020-12-31")
                .with(httpBasic("practicante", CLAVE));
    }

    @Test
    void filtraPorProveedorYServicio() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        "UPDATE mod03_programacion_servicio SET fecha_inicio_prevista ="
                                + " '2020-03-02', fecha_fin_prevista = '2020-03-05'");
        http.perform(calendario())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        http.perform(calendario().param("proveedor_id", "1").param("tipo_servicio_id", "1"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].proveedor").value("Agroservicios Lima SAC"))
                .andExpect(jsonPath("$[0].estado").value("Completada"));
    }

    @Test
    void unFiltroSinResultadosDevuelveUnaListaVacia() throws Exception {
        http.perform(calendario().param("proveedor_id", "1").param("tipo_servicio_id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
