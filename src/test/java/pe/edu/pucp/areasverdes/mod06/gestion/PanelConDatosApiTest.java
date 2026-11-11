package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Panel (RF-REP-001) y material vegetal (RF-REP-005) con las tablas reales de MOD-04 (AV-228):
 * {@code escenario-operacion-campo.sql} sobre {@code escenario-gestion.sql}. Los totales esperados
 * están en la cabecera del escenario.
 */
class PanelConDatosApiTest extends ApiGestion {

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod06/escenario-operacion-campo.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void losIndicadoresDeMod04CuentanLasActividadesDelPeriodo() throws Exception {
        http.perform(
                        get("/api/v1/reportes/panel/?desde=2026-10-01&hasta=2026-10-31")
                                .cookie(sesion("jefe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[0].clave").value("actividades-por-estado"))
                .andExpect(jsonPath("$.indicadores[0].sin_datos").value(false))
                .andExpect(jsonPath("$.indicadores[0].valor").value("2"))
                .andExpect(
                        jsonPath(
                                "$.indicadores[0].desglose[*].etiqueta",
                                containsInAnyOrder(
                                        "Estado: Abierto",
                                        "Estado: Cerrado",
                                        "Tipo: Propagación 06",
                                        "Tipo: Riego 06")))
                .andExpect(jsonPath("$.indicadores[1].clave").value("distribucion-operarios"))
                .andExpect(jsonPath("$.indicadores[1].sin_datos").value(false))
                .andExpect(jsonPath("$.indicadores[3].clave").value("consumo-materiales"))
                .andExpect(jsonPath("$.indicadores[3].sin_datos").value(false));
    }

    @Test
    void elFiltroDeEspacioDejaSoloSusActividades() throws Exception {
        http.perform(
                        get("/api/v1/reportes/panel/?desde=2026-10-01&hasta=2026-10-31&area_verde_id=2")
                                .cookie(sesion("jefe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[0].valor").value("1"));
    }

    @Test
    void elDetalleDeActividadesTraeSuCuadrillaYEspacio() throws Exception {
        http.perform(
                        get("/api/v1/reportes/panel/actividades-por-estado/?desde=2026-10-01&hasta=2026-10-31")
                                .cookie(sesion("ingeniero")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sin_datos").value(false))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.results[0].tipo_actividad").value("Propagación 06"))
                .andExpect(jsonPath("$.results[0].estado").value("Abierto"))
                .andExpect(jsonPath("$.results[0].espacio").value("Plaza de Armas"))
                .andExpect(jsonPath("$.results[0].cuadrilla").value("Cuadrilla Norte"));
    }

    @Test
    void elMaterialVegetalDelMesTieneConsumoPropagacionYTraslado() throws Exception {
        http.perform(get("/api/v1/reportes/material-vegetal/?mes=2026-10").cookie(sesion("jefe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sin_datos").value(false))
                .andExpect(jsonPath("$.consumido").isNotEmpty())
                .andExpect(jsonPath("$.propagado").isNotEmpty())
                .andExpect(jsonPath("$.trasladado").isNotEmpty());
    }
}
