package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** AV-381: rol superadmin oculto y permisos de cada rol editables por él. */
class SuperadminApiTest extends ApiCore {

    static final String PERMISOS_CAPATAZ = "/api/v1/core/roles/capataz/permisos/";

    /** La matriz no se trunca entre pruebas: se deja como estaba. */
    private List<Map<String, Object>> matrizOriginal;

    private List<String> capatazOriginal;

    @BeforeEach
    void superadmin() {
        cuenta("superadmin", "superadmin");
        matrizOriginal =
                jdbc.queryForList("SELECT rol_id, permiso_id FROM core_rol_permiso", Map.of());
        capatazOriginal = permisosDelRol("capataz");
    }

    @AfterEach
    void restaurarMatriz() {
        jdbc.update("DELETE FROM core_rol_permiso", Map.of());
        for (var fila : matrizOriginal) {
            jdbc.update(
                    "INSERT INTO core_rol_permiso (rol_id, permiso_id) VALUES (:rol_id,"
                            + " :permiso_id)",
                    fila);
        }
    }

    @Test
    void quitarUnPermisoQuitaLaAccionEnLosModulosQueAntesDecidianPorRol() throws Exception {
        // AV-382: MOD-05 autoriza por permisos, no por el nombre del rol.
        var ingeniero = sesion("ingeniero");
        http.perform(get("/api/tercerizados/proveedores/").cookie(ingeniero))
                .andExpect(status().isOk());
        List<String> sinContratos = new ArrayList<>(permisosDelRol("ingeniero-campo"));
        sinContratos.remove("SER.CONTRATO.CONSULTAR");
        http.perform(
                        escribir(
                                put("/api/v1/core/roles/ingeniero-campo/permisos/"),
                                sesion("superadmin"),
                                json.writeValueAsString(
                                        Map.of("permisos", sinContratos, "motivo", "Prueba"))))
                .andExpect(status().isOk());
        http.perform(get("/api/tercerizados/proveedores/").cookie(ingeniero))
                .andExpect(status().isForbidden());
    }

    List<String> permisosDelRol(String rol) {
        return jdbc.queryForList(
                """
                SELECT p.codigo FROM core_rol_permiso rp JOIN core_permiso p ON p.id = rp.permiso_id
                JOIN core_rol r ON r.id = rp.rol_id WHERE r.codigo = :rol ORDER BY p.codigo
                """,
                Map.of("rol", rol),
                String.class);
    }

    @Test
    void tieneTodosLosPermisosTambienLosQueSeAnadanDespues() throws Exception {
        jdbc.update(
                "INSERT INTO core_permiso (codigo, modulo, descripcion) VALUES"
                        + " ('CORE.PRUEBA.NUEVO', 'MOD-01', 'Permiso añadido después')",
                Map.of());
        try {
            int total =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM core_permiso", Map.of(), Integer.class);
            http.perform(get("/api/v1/core/auth/sesion/").cookie(sesion("superadmin")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rol.codigo").value("superadmin"))
                    .andExpect(jsonPath("$.permisos.length()").value(total));
        } finally {
            jdbc.update("DELETE FROM core_permiso WHERE codigo = 'CORE.PRUEBA.NUEVO'", Map.of());
        }
    }

    @Test
    void noSeVeNiSeGestionaDesdeLaApp() throws Exception {
        var jefe = sesion("jefe");
        long id = ids.get("superadmin");
        http.perform(get("/api/v1/core/usuarios/").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
        http.perform(get("/api/v1/core/roles/").cookie(jefe))
                .andExpect(jsonPath("$[?(@.codigo == 'superadmin')]").isEmpty());
        http.perform(get("/api/v1/core/usuarios/" + id + "/").cookie(jefe))
                .andExpect(status().isNotFound());
        http.perform(
                        escribir(
                                post("/api/v1/core/usuarios/" + id + "/desactivar/"),
                                jefe,
                                "{\"motivo\": \"Prueba\"}"))
                .andExpect(status().isNotFound());
        http.perform(
                        escribir(
                                put("/api/v1/core/usuarios/" + ids.get("practicante") + "/rol/"),
                                jefe,
                                "{\"rol\": \"superadmin\", \"motivo\": \"Prueba\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("rol-desconocido"));
        http.perform(
                        escribir(
                                post("/api/v1/core/usuarios/"),
                                jefe,
                                "{\"correo\": \"nuevo@prueba.pucp.edu.pe\", \"nombre\": \"Nuevo\","
                                        + " \"rol\": \"superadmin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("rol-desconocido"));
    }

    @Test
    void editaLosPermisosDeUnRolConMotivoYValenEnLaSiguientePeticion() throws Exception {
        var superadmin = sesion("superadmin");
        var capataz = sesion("capataz");
        http.perform(get("/api/v1/core/permisos/").cookie(superadmin))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.codigo == 'CORE.ROL.GESTIONAR')].es_sensible").value(true));
        // Uno que no tiene (añadir) y uno que sí tiene (quitar).
        String anadido = "CORE.CATALOGO.GESTIONAR";
        String quitado = capatazOriginal.getFirst();
        List<String> despues = new ArrayList<>(capatazOriginal);
        despues.add(anadido);
        despues.remove(quitado);
        String cuerpo =
                json.writeValueAsString(
                        Map.of("permisos", despues, "motivo", "Consulta el repositorio en campo"));
        http.perform(escribir(put(PERMISOS_CAPATAZ), superadmin, cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol.codigo").value("capataz"))
                .andExpect(
                        jsonPath("$.historial[0].motivo").value("Consulta el repositorio en campo"))
                .andExpect(jsonPath("$.historial[0].autor").value("superadmin"));
        assertEquals(despues.stream().sorted().toList(), permisosDelRol("capataz"));
        // Misma sesión del capataz, sin volver a entrar: ya tiene los permisos nuevos.
        http.perform(get("/api/v1/core/auth/sesion/").cookie(capataz))
                .andExpect(jsonPath("$.permisos[?(@ == '" + anadido + "')]").isNotEmpty())
                .andExpect(jsonPath("$.permisos[?(@ == '" + quitado + "')]").isEmpty());
        http.perform(escribir(put(PERMISOS_CAPATAZ), superadmin, cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("permisos-sin-cambio"));
    }

    @Test
    void validaElCambioYProtegeLoReservado() throws Exception {
        var superadmin = sesion("superadmin");
        http.perform(
                        escribir(
                                put(PERMISOS_CAPATAZ),
                                superadmin,
                                "{\"permisos\": [\"NO.EXISTE.NUNCA\"], \"motivo\": \"Prueba\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("permiso-desconocido"));
        http.perform(
                        escribir(
                                put(PERMISOS_CAPATAZ),
                                superadmin,
                                "{\"permisos\": [\"CORE.ROL.GESTIONAR\"], \"motivo\": \"Prueba\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("permiso-reservado"));
        http.perform(
                        escribir(
                                put(PERMISOS_CAPATAZ),
                                superadmin,
                                "{\"permisos\": [], \"motivo\": \" \"}"))
                .andExpect(status().isBadRequest());
        http.perform(
                        escribir(
                                put("/api/v1/core/roles/superadmin/permisos/"),
                                superadmin,
                                "{\"permisos\": [], \"motivo\": \"Prueba\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuaComoJefeEnLosModulosQueDecidenPorRol() throws Exception {
        http.perform(get("/api/tercerizados/atenciones/").cookie(sesion("superadmin")))
                .andExpect(status().isOk());
    }
}
