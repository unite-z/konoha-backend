package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** RF-T-003; HU-MOD01-006; CU-MOD01-005. */
class RolesApiTest extends ApiCore {

    String rol(long id) {
        return "/api/v1/core/usuarios/" + id + "/rol/";
    }

    @Test
    void elCambioDeRolValeEnLaSiguienteAccionYQuedaRegistrado() throws Exception {
        var practicante = sesion("practicante");
        var jefe = sesion("jefe");
        http.perform(
                        escribir(
                                put(rol(ids.get("practicante"))),
                                jefe,
                                "{\"rol\": \"ingeniero-campo\", \"motivo\": \"Asume la"
                                        + " supervisión técnica\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol.codigo").value("ingeniero-campo"))
                .andExpect(jsonPath("$.historial[0].rol_anterior").value("practicante"))
                .andExpect(jsonPath("$.historial[0].rol_nuevo").value("ingeniero-campo"))
                .andExpect(jsonPath("$.historial[0].autor").value("jefe"))
                .andExpect(jsonPath("$.historial[0].motivo").value("Asume la supervisión técnica"));
        // Misma sesión, sin volver a entrar: ya tiene el rol de ingeniero.
        http.perform(get("/api/v1/core/auth/sesion/").cookie(practicante))
                .andExpect(jsonPath("$.rol.codigo").value("ingeniero-campo"));
    }

    @Test
    void unUsuarioTieneUnSoloRol() throws Exception {
        var jefe = sesion("jefe");
        http.perform(
                        escribir(
                                put(rol(ids.get("capataz"))),
                                jefe,
                                "{\"rol\": \"practicante\", \"motivo\": \"Cambio de puesto\"}"))
                .andExpect(status().isOk());
        Integer roles =
                jdbc.queryForObject(
                        "SELECT count(DISTINCT rol_id) FROM core_usuario WHERE id = :id",
                        Map.of("id", ids.get("capataz")),
                        Integer.class);
        assertTrue(roles == 1);
        http.perform(get(rol(ids.get("capataz"))).cookie(jefe))
                .andExpect(jsonPath("$.rol.codigo").value("practicante"));
    }

    @Test
    void elOperarioEstaPrevistoPeroNoSeAsigna() throws Exception {
        var jefe = sesion("jefe");
        http.perform(get("/api/v1/core/roles/").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[4].codigo").value("operario"))
                .andExpect(jsonPath("$[4].activo").value(false))
                .andExpect(jsonPath("$[4].permisos.length()").value(0));
        http.perform(
                        escribir(
                                put(rol(ids.get("practicante"))),
                                jefe,
                                "{\"rol\": \"operario\", \"motivo\": \"Prueba\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("rol-no-habilitado"));
    }

    @Test
    void laBaseImpideAsignarUnRolInactivo() {
        try {
            jdbc.update(
                    "UPDATE core_usuario SET rol_id = (SELECT id FROM core_rol WHERE codigo ="
                            + " 'operario') WHERE id = :id",
                    Map.of("id", ids.get("practicante")));
            throw new AssertionError("La base debería rechazar el rol operario");
        } catch (DataAccessException esperado) {
            assertTrue(esperado.getMessage().contains("no está habilitado"));
        }
    }

    @Test
    void rechazaSinMotivoMismoRolRolDesconocidoYUltimoJefe() throws Exception {
        var jefe = sesion("jefe");
        http.perform(
                        escribir(
                                put(rol(ids.get("practicante"))),
                                jefe,
                                "{\"rol\": \"capataz\", \"motivo\": \" \"}"))
                .andExpect(status().isBadRequest());
        http.perform(
                        escribir(
                                put(rol(ids.get("practicante"))),
                                jefe,
                                "{\"rol\": \"practicante\", \"motivo\": \"Nada\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("rol-sin-cambio"));
        http.perform(
                        escribir(
                                put(rol(ids.get("practicante"))),
                                jefe,
                                "{\"rol\": \"administrador\", \"motivo\": \"Nada\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("rol-desconocido"));
        http.perform(
                        escribir(
                                put(rol(ids.get("jefe"))),
                                jefe,
                                "{\"rol\": \"practicante\", \"motivo\": \"Nada\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ultimo-jefe-seccion"));
        http.perform(
                        escribir(
                                put(rol(999_999)),
                                jefe,
                                "{\"rol\": \"practicante\", \"motivo\": \"Nada\"}"))
                .andExpect(status().isNotFound());
    }
}
