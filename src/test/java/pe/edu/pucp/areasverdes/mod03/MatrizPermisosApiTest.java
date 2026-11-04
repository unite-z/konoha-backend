package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Matriz de permisos de MOD-03 con la sesión de MOD-01 (AV-252): los cuatro roles consultan y solo
 * el jefe escribe. Las escrituras llevan cuerpos válidos para que el 403 sea por permiso.
 */
class MatrizPermisosApiTest extends ApiPlanificacion {

    static final String DOMINIO = "@mod03.prueba.pucp.edu.pe";
    static final String RUTA = "/api/v1/planificacion/";
    static final List<String> ROLES =
            List.of("jefe-seccion", "ingeniero-campo", "practicante", "capataz");

    /** Consultas de los endpoints que ya existen. */
    static final List<String> CONSULTAS =
            List.of(
                    "alertas/",
                    "tipos-restriccion/",
                    "restricciones/?desde=2027-01-01&hasta=2027-01-31",
                    "programaciones/?desde=2027-01-01&hasta=2027-01-31",
                    "servicios/",
                    "incidencias/catalogos-registro/",
                    "incidencias/mias/",
                    "incidencias/",
                    "incidencias/posibles-duplicados/?tipo=Rama%20ca%C3%ADda",
                    "servicios/1/historial/",
                    "servicios/1/ruta/",
                    "avisos/",
                    "calendario-cuadrilla/?desde=2027-01-01&hasta=2027-01-31");

    @BeforeEach
    void cuentasDeMod01() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        for (String rol : ROLES) {
            jdbc.update(
                    """
                    INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                    SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                    """,
                    Map.of("correo", rol + DOMINIO, "rol", rol, "hash", claves.encode(CLAVE)));
        }
    }

    Cookie sesion(String rol) throws Exception {
        return http.perform(
                        post("/api/v1/core/auth/login/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"identificador\": \"%s\", \"contrasena\": \"%s\"}"
                                                .formatted(rol + DOMINIO, CLAVE)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(SeguridadMod01.COOKIE_SESION);
    }

    /** Escrituras de los endpoints que ya existen, con un cuerpo que pasa la validación. */
    static List<MockHttpServletRequestBuilder> escrituras() {
        return List.of(
                put(RUTA + "servicios/1/frecuencias/Verano/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frecuencia_teorica_dias\": 21}"),
                put(RUTA + "servicios/1/duracion/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracion_maxima_dias\": 10, \"hora_inicio\": \"06:00\"}"),
                post(RUTA + "restricciones/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_restriccion_id\": 1, \"fecha_inicio\": \"2027-01-08\","
                                        + " \"fecha_fin\": \"2027-01-08\"}"),
                delete(RUTA + "restricciones/1/"),
                post(RUTA + "programaciones/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_servicio_id\": 1, \"fecha_inicio_prevista\":"
                                    + " \"2027-01-11\", \"fecha_fin_prevista\": \"2027-01-12\"}"),
                patch(RUTA + "programaciones/1/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fecha_inicio_prevista\": \"2027-01-11\","
                                        + " \"fecha_fin_prevista\": \"2027-01-12\"}"),
                post(RUTA + "programaciones/1/cancelacion/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Lluvia\"}"),
                get(RUTA + "incidencias/inspectores/"),
                get(RUTA + "incidencias/1/capataces/"),
                post(RUTA + "alertas/1/motivo/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Lluvia\"}"));
    }

    @Test
    void losCuatroRolesConsultanElCalendario() throws Exception {
        for (String rol : ROLES) {
            Cookie cookie = sesion(rol);
            for (String consulta : CONSULTAS) {
                http.perform(get(RUTA + consulta).cookie(cookie)).andExpect(status().isOk());
            }
        }
    }

    @Test
    void soloElJefeEscribe() throws Exception {
        for (String rol : List.of("ingeniero-campo", "practicante", "capataz")) {
            Cookie cookie = sesion(rol);
            for (var escritura : escrituras()) {
                http.perform(escritura.cookie(cookie).with(csrf()))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void elJefePasaElPermisoAlEscribir() throws Exception {
        Cookie cookie = sesion("jefe-seccion");
        for (var escritura : escrituras()) {
            int estado =
                    http.perform(escritura.cookie(cookie).with(csrf()))
                            .andReturn()
                            .getResponse()
                            .getStatus();
            assertNotEquals(403, estado);
            assertNotEquals(401, estado);
        }
    }
}
