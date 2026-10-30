package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod01.config.PrecargaDevMod01;

/**
 * En una base nueva, la precarga de MOD-05 crea sus cuentas después de las migraciones: la precarga
 * dev de MOD-01 las copia para que se pueda entrar con ellas (mismo id, misma clave, un solo rol).
 */
class PrecargaDevMod01IntegrationTest extends ApiCore {

    @Test
    void copiaLasCuentasDeMod05QueFaltanYSePuedeEntrarConEllas() throws Exception {
        long id = 900_000 + (System.nanoTime() % 90_000);
        String usuario = "mod05_prueba_" + id;
        jdbc.update(
                "INSERT INTO mod05_usuario (id, usuario, nombre, password_hash, activo)"
                        + " VALUES (:id, :u, 'Jefe MOD-05', :h, true)",
                Map.of("id", id, "u", usuario, "h", claves.encode(CLAVE)));
        jdbc.update(
                "INSERT INTO mod05_usuario_rol (usuario_id, rol) VALUES (:id, 'Ingeniero de"
                        + " campo'), (:id, 'Jefe de sección')",
                Map.of("id", id));

        // Copia esta cuenta y cualquier otra de MOD-05 que falte (otras pruebas también crean
        // cuentas).
        assertTrue(PrecargaDevMod01.copiarCuentasMod05(jdbc) >= 1);
        assertEquals(0, PrecargaDevMod01.copiarCuentasMod05(jdbc), "Es idempotente");
        assertEquals(
                "jefe-seccion",
                jdbc.queryForObject(
                        "SELECT r.codigo FROM core_usuario u JOIN core_rol r ON r.id = u.rol_id"
                                + " WHERE u.id = :id",
                        Map.of("id", id),
                        String.class));
        http.perform(login(usuario, CLAVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(id))
                .andExpect(jsonPath("$.rol.codigo").value("jefe-seccion"));
        // La secuencia sigue por encima: una cuenta nueva no choca con la copiada.
        long nueva = cuenta("nueva", "practicante");
        assertTrue(nueva > id);
    }
}
