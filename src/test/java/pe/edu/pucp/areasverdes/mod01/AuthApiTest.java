package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/** HU-MOD01-001 y 003; CP-MOD01-001 a 003. */
class AuthApiTest extends ApiCore {

    static final String GENERICO = "Correo o contraseña incorrectos";

    @Test
    void accesoCorrectoAbreSesionConLosPermisosDelRolYLaRegistra() throws Exception {
        var r =
                http.perform(login("practicante" + DOMINIO, CLAVE))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.rol.codigo").value("practicante"))
                        .andExpect(jsonPath("$.debe_cambiar_contrasena").value(false))
                        .andReturn();
        var cookie = r.getResponse().getCookie(SeguridadMod01.COOKIE_SESION);
        assertTrue(cookie != null);
        String cabecera =
                r.getResponse().getHeaders("Set-Cookie").stream()
                        .filter(c -> c.startsWith(SeguridadMod01.COOKIE_SESION + "="))
                        .findFirst()
                        .orElseThrow();
        assertTrue(cabecera.contains("HttpOnly") && cabecera.contains("SameSite=Strict"));
        var permisos = cuerpo(r).get("permisos").toString();
        assertTrue(permisos.contains("CORE.REPOSITORIO.CONSULTAR"));
        assertTrue(!permisos.contains("CORE.USUARIO.GESTIONAR"));
        http.perform(get("/api/v1/core/auth/sesion/").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.nombre").value("practicante"));
        assertEquals(1, contarAccesos("Exitoso"));
        Object ultimo =
                jdbc.queryForObject(
                        "SELECT ultimo_acceso FROM core_usuario WHERE id = :id",
                        Map.of("id", ids.get("practicante")),
                        Object.class);
        assertTrue(ultimo != null);
    }

    @Test
    void sinSesionSoloHayPantallaDeAcceso() throws Exception {
        http.perform(get("/api/v1/core/auth/sesion/"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("no-autenticado"));
        http.perform(get("/api/v1/core/usuarios/")).andExpect(status().isUnauthorized());
    }

    @Test
    void credencialesIncorrectasDanUnMensajeGenericoYCuentanElIntento() throws Exception {
        http.perform(login("practicante" + DOMINIO, "otra-clave-9"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detalle").value(GENERICO));
        http.perform(login("nadie" + DOMINIO, "otra-clave-9"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detalle").value(GENERICO));
        assertEquals(2, contarAccesos("Fallido"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT intentos_fallidos FROM core_usuario WHERE id = :id",
                        Map.of("id", ids.get("practicante")),
                        Integer.class));
    }

    @Test
    void elQuintoFalloBloqueaLaCuentaQuinceMinutos() throws Exception {
        for (int i = 0; i < 5; i++) {
            http.perform(login("ingeniero" + DOMINIO, "otra-clave-9"))
                    .andExpect(status().isUnauthorized());
        }
        http.perform(login("ingeniero" + DOMINIO, CLAVE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("cuenta-bloqueada"));
        assertTrue(contarAccesos("Bloqueado") >= 1);
        Boolean quinceMinutos =
                jdbc.queryForObject(
                        "SELECT bloqueado_hasta BETWEEN now() + interval '14 minutes' AND now() +"
                                + " interval '16 minutes' FROM core_usuario WHERE id = :id",
                        Map.of("id", ids.get("ingeniero")),
                        Boolean.class);
        assertTrue(quinceMinutos);
    }

    @Test
    void unaCuentaVencidaNoEntraYSeLePideContactarAlJefe() throws Exception {
        jdbc.update(
                "UPDATE core_usuario SET fecha_fin_vigencia = " + HOY_LIMA + " - 1 WHERE id = :id",
                Map.of("id", ids.get("practicante")));
        http.perform(login("practicante" + DOMINIO, CLAVE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("cuenta-no-activa"))
                .andExpect(
                        jsonPath("$.detalle")
                                .value(
                                        "Tu cuenta no está activa. Contacta con el jefe de"
                                                + " sección"));
        assertEquals(1, contarAccesos("Denegado"));
    }

    @Test
    void laContrasenaTemporalObligaACambiarlaYRechazaLasDebiles() throws Exception {
        jdbc.update(
                "UPDATE core_usuario SET debe_cambiar_contrasena = true WHERE id = :id",
                Map.of("id", ids.get("jefe")));
        var sesion = sesion("jefe");
        http.perform(get("/api/v1/core/usuarios/").cookie(sesion))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("cambio-contrasena-obligatorio"));
        http.perform(
                        escribir(
                                post("/api/v1/core/auth/contrasena/"),
                                sesion,
                                cambio(CLAVE, "abcdef", "abcdef")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("contrasena-debil"))
                .andExpect(jsonPath("$.reglas.length()").value(2));
        http.perform(
                        escribir(
                                post("/api/v1/core/auth/contrasena/"),
                                sesion,
                                cambio(CLAVE, CLAVE, CLAVE)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.reglas[0]").value("Debe ser distinta de la contraseña actual"));
        http.perform(
                        escribir(
                                post("/api/v1/core/auth/contrasena/"),
                                sesion,
                                cambio(CLAVE, "nueva-clave-22", "nueva-clave-22")))
                .andExpect(status().isNoContent());
        http.perform(get("/api/v1/core/usuarios/").cookie(sesion)).andExpect(status().isOk());
    }

    @Test
    void cambiarLaContrasenaCierraLasDemasSesiones() throws Exception {
        var movil = sesion("ingeniero");
        var oficina = sesion("ingeniero");
        http.perform(
                        escribir(
                                post("/api/v1/core/auth/contrasena/"),
                                oficina,
                                cambio(CLAVE, "nueva-clave-22", "nueva-clave-22")))
                .andExpect(status().isNoContent());
        http.perform(get("/api/v1/core/auth/sesion/").cookie(movil))
                .andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/core/auth/sesion/").cookie(oficina)).andExpect(status().isOk());
    }

    @Test
    void cerrarSesionLaInvalidaYNoSePuedeReutilizar() throws Exception {
        var sesion = sesion("capataz");
        http.perform(escribir(post("/api/v1/core/auth/logout/"), sesion, null))
                .andExpect(status().isNoContent());
        http.perform(get("/api/v1/core/auth/sesion/").cookie(sesion))
                .andExpect(status().isUnauthorized());
        assertEquals(1, contarAccesos("CierreSesion"));
    }

    @Test
    void laSesionDeOficinaCaducaTrasTreintaMinutosSinActividad() throws Exception {
        var sesion = sesion("practicante");
        jdbc.update(
                "UPDATE core_sesion SET ultima_actividad = now() - interval '31 minutes'",
                Map.of());
        http.perform(get("/api/v1/core/auth/sesion/").cookie(sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mantenerSesionSoloValeEnElMovilDeCampo() throws Exception {
        var campo =
                http.perform(
                                post("/api/v1/core/auth/login/")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"identificador\": \"capataz"
                                                        + DOMINIO
                                                        + "\", \"contrasena\": \""
                                                        + CLAVE
                                                        + "\", \"canal\": \"campo\","
                                                        + " \"mantener_sesion\": true}"))
                        .andExpect(status().isOk())
                        .andReturn();
        assertTrue(campo.getResponse().getCookie(SeguridadMod01.COOKIE_SESION).getMaxAge() > 0);
        jdbc.update(
                "UPDATE core_sesion SET ultima_actividad = now() - interval '2 hours'", Map.of());
        http.perform(
                        get("/api/v1/core/auth/sesion/")
                                .cookie(
                                        campo.getResponse()
                                                .getCookie(SeguridadMod01.COOKIE_SESION)))
                .andExpect(status().isOk());
    }

    @Test
    void lasEscriturasSinTokenCsrfSeRechazan() throws Exception {
        var sesion = sesion("jefe");
        http.perform(post("/api/v1/core/auth/logout/").cookie(sesion))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("csrf-invalido"));
    }

    private static String cambio(String actual, String nueva, String confirmacion) {
        return "{\"contrasena_actual\": \""
                + actual
                + "\", \"contrasena_nueva\": \""
                + nueva
                + "\", \"confirmacion\": \""
                + confirmacion
                + "\"}";
    }
}
