package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Con {@code MOD06_DATOS_EJEMPLO=true} (solo desarrollo) Spring usa las fuentes de EJEMPLO en lugar
 * de las «no disponibles», y los tres indicadores de MOD-04 del panel tienen datos. Sin esa
 * variable, {@link PanelApiTest} comprueba que siguen en «Sin datos».
 */
@TestPropertySource(properties = "mod06.datos-ejemplo=true")
class PanelConDatosEjemploApiTest extends ApiGestion {

    @Test
    void losIndicadoresDeMod04TienenDatosDeEjemplo() throws Exception {
        http.perform(
                        get("/api/v1/reportes/panel/?desde=2026-10-01&hasta=2026-10-31")
                                .cookie(sesion("jefe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[0].clave").value("actividades-por-estado"))
                .andExpect(jsonPath("$.indicadores[0].sin_datos").value(false))
                .andExpect(jsonPath("$.indicadores[1].clave").value("distribucion-operarios"))
                .andExpect(jsonPath("$.indicadores[1].sin_datos").value(false))
                .andExpect(jsonPath("$.indicadores[3].clave").value("consumo-materiales"))
                .andExpect(jsonPath("$.indicadores[3].sin_datos").value(false));
    }

    @Test
    void elDetalleDeActividadesTieneRegistros() throws Exception {
        http.perform(
                        get("/api/v1/reportes/panel/actividades-por-estado/?desde=2026-10-01&hasta=2026-10-31")
                                .cookie(sesion("ingeniero")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sin_datos").value(false))
                .andExpect(jsonPath("$.results[0].estado").exists())
                .andExpect(jsonPath("$.results[0].tipo_actividad").exists());
    }
}
