package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Las rutas de los reportes de gestión exigen HTTP Basic ({@code SeguridadGestion}) y el resto del
 * MOD-06 y de MOD-03 sigue igual (AGENTS.md: «Seguridad abierta: no empeorar»).
 */
class SeguridadGestionApiTest extends ApiGestion {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/reportes/panel",
                "/api/v1/reportes/panel/",
                "/api/v1/reportes/material-vegetal",
                "/api/v1/reportes/material-vegetal/",
                "/api/v1/reportes/mapa-atenciones",
                "/api/v1/reportes/mapa-atenciones/"
            })
    void lasRutasNuevasExigenCredenciales(String ruta) throws Exception {
        http.perform(get(ruta))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("no-autenticado"));
        http.perform(get(ruta).with(httpBasic("jefe", "otra-clave")))
                .andExpect(status().isUnauthorized());
    }

    /** Rutas que nunca existirán: la prueba no cambia cuando se añadan los endpoints. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/reportes/panel/no-existe/otra/",
                "/api/v1/reportes/material-vegetal/no-existe/",
                "/api/v1/reportes/mapa-atenciones/no-existe/"
            })
    void conCredencialesPasaLaAutenticacion(String ruta) throws Exception {
        http.perform(get(ruta).with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void losReportesAntiguosSiguenAbiertos() throws Exception {
        int estado =
                http.perform(
                                get(
                                        "/api/v1/reportes/cumplimiento-corte/?desde=2026-01-01&hasta=2026-01-31"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        assertNotEquals(401, estado);
    }

    @Test
    void elRestoDeApiV1SigueSinCambios() throws Exception {
        http.perform(get("/api/v1/no-existe/")).andExpect(status().isNotFound());
    }

    @Test
    void laPlanificacionSigueExigiendoCredenciales() throws Exception {
        http.perform(get("/api/v1/planificacion/alertas/")).andExpect(status().isUnauthorized());
    }
}
