package pe.edu.pucp.areasverdes.mod01;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * AV-234: MOD-05 y MOD-03 aceptan la sesión de MOD-01, con el rol actual de MOD-01. La aplicación
 * web ya no necesita el ingreso con HTTP Basic de MOD-05.
 */
class SesionCompartidaApiTest extends ApiCore {

    static final String ME = "/api/tercerizados/me/";
    static final String ATENCIONES = "/api/tercerizados/atenciones/";

    @Test
    void conLaCookieDeMod01SeEntraEnMod05ConSuRol() throws Exception {
        var jefe = sesion("jefe");
        http.perform(get(ME).cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ids.get("jefe")))
                .andExpect(jsonPath("$.rol").value("Jefe de sección"));
        http.perform(get(ME).cookie(sesion("practicante")))
                .andExpect(jsonPath("$.rol").value("Practicante autorizado"));
    }

    @Test
    void sinSesionMod05Responde401() throws Exception {
        http.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas o ausentes"));
    }

    @Test
    void elCapatazNoTieneRolEnMod05() throws Exception {
        http.perform(get(ATENCIONES).cookie(sesion("capataz"))).andExpect(status().isForbidden());
    }

    @Test
    void elCambioDeRolYLaDesactivacionValenTambienEnMod05() throws Exception {
        var practicante = sesion("practicante");
        jdbc.update(
                "UPDATE core_usuario SET rol_id = (SELECT id FROM core_rol WHERE codigo ="
                        + " 'jefe-seccion') WHERE id = :id",
                Map.of("id", ids.get("practicante")));
        http.perform(get(ME).cookie(practicante))
                .andExpect(jsonPath("$.rol").value("Jefe de sección"));
        jdbc.update(
                "UPDATE core_usuario SET activo = false WHERE id = :id",
                Map.of("id", ids.get("practicante")));
        http.perform(get(ME).cookie(practicante)).andExpect(status().isUnauthorized());
    }

    @Test
    void lasEscriturasConCookieExigenElTokenCsrf() throws Exception {
        var jefe = sesion("jefe");
        http.perform(
                        post(ATENCIONES)
                                .cookie(jefe)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());
        http.perform(
                        post(ATENCIONES)
                                .cookie(jefe)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laContrasenaTemporalTambienBloqueaMod05() throws Exception {
        jdbc.update(
                "UPDATE core_usuario SET debe_cambiar_contrasena = true WHERE id = :id",
                Map.of("id", ids.get("jefe")));
        http.perform(get(ME).cookie(sesion("jefe")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("cambio-contrasena-obligatorio"));
    }

    @Test
    void conLaCookieDeMod01SeEntraEnMod03() throws Exception {
        http.perform(get("/api/v1/planificacion/no-existe/").cookie(sesion("ingeniero")))
                .andExpect(status().isNotFound());
        http.perform(get("/api/v1/planificacion/no-existe/")).andExpect(status().isUnauthorized());
    }
}
