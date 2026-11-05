package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Una cuenta creada solo en MOD-01, sin copia en {@code mod05_usuario}, completa la planificación
 * (AV-287, fase A en MOD-03): configura, registra restricciones y programa. Es el caso de UAT y
 * producción. El id 900 no existe en {@code mod05_usuario}; las cuentas de las demás pruebas
 * comparten id en las dos tablas y por eso no lo detectaban.
 */
class CuentaSoloMod01ApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/";
    static final String CORREO = "jefa.nueva@mod03.prueba.pucp.edu.pe";

    @Autowired ObjectMapper json;

    Cookie sesion;

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        sql.execute(
                new ClassPathResource("mod03/escenario-programacion.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
        jdbc.update(
                """
                INSERT INTO core_usuario (id, nombre_usuario, correo, nombre, credencial_hash, rol_id)
                SELECT 900, :correo, :correo, 'Jefa Nueva', :hash, id
                FROM core_rol WHERE codigo = 'jefe-seccion'
                """,
                Map.of("correo", CORREO, "hash", claves.encode(CLAVE)));
        sesion =
                http.perform(
                                post("/api/v1/core/auth/login/")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"identificador\": \"%s\", \"contrasena\": \"%s\"}"
                                                        .formatted(CORREO, CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getCookie(SeguridadMod01.COOKIE_SESION);
    }

    @Test
    void configuraRegistraRestriccionesYProgramaSinCuentaEnMod05() throws Exception {
        http.perform(
                        put(RUTA + "servicios/2/duracion/")
                                .cookie(sesion)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"duracion_maxima_dias\": 1, \"dias_limpieza\": 0}"))
                .andExpect(status().isOk());
        http.perform(get(RUTA + "servicios/").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.id == 2)].duracion.modificado_por")
                                .value(Matchers.hasItem("Jefa Nueva")));
        http.perform(
                        post(RUTA + "restricciones/")
                                .cookie(sesion)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"tipo_restriccion_id\": 1, \"fecha_inicio\":"
                                            + " \"2027-01-08\", \"fecha_fin\": \"2027-01-08\"}"))
                .andExpect(status().isCreated());
        http.perform(
                        post(RUTA + "programaciones/")
                                .cookie(sesion)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"tipo_servicio_id\": 2, \"fecha_inicio_prevista\":"
                                                + " \"2027-01-11\", \"fecha_fin_prevista\":"
                                                + " \"2027-01-11\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.historial[0].autor").value("Jefa Nueva"));
    }
}
