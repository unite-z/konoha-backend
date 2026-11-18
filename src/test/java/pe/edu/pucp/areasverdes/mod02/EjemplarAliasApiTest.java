package pe.edu.pucp.areasverdes.mod02;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.comun.BasePruebaGlobal;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/** Alias (nombre) de los ejemplares (AV-401): alta, búsqueda, cambio y unicidad. */
public class EjemplarAliasApiTest extends BasePruebaGlobal {

    private static final String CLAVE = "prueba-integration-local";
    private static final String CORREO = "jefe-seccion@alias.prueba.pucp.edu.pe";
    private static final String BASE = "/api/v1/inventario/ejemplares";

    private Cookie jefe;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM mod02_ejemplar WHERE alias LIKE 'Prueba %'", Map.of());
        jdbc.update(
                """
                INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                SELECT :correo, :correo, 'jefe-seccion', :hash, id FROM core_rol
                WHERE codigo = 'jefe-seccion'
                """,
                Map.of("correo", CORREO, "hash", claves.encode(CLAVE)));
        jefe = sesion();
    }

    @Test
    void elAliasSeGuardaSeBuscaYSeDevuelve() throws Exception {
        crear("{\"alias\": \"  Prueba Robertito \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value("Prueba Robertito"));

        http.perform(get(BASE).param("q", "prueba robert").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].alias").value("Prueba Robertito"));
    }

    @Test
    void unEjemplarSinAliasLoDevuelveNulo() throws Exception {
        crear("{}").andExpect(status().isCreated()).andExpect(jsonPath("$.alias").isEmpty());
    }

    @Test
    void elAliasNoSePuedeRepetirAunqueCambienLasMayusculas() throws Exception {
        crear("{\"alias\": \"Prueba Repetido\"}").andExpect(status().isCreated());

        crear("{\"alias\": \"prueba REPETIDO\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("alias-repetido"));
    }

    @Test
    void sePuedeCambiarYQuitarElAliasPeroSinRepetirOtro() throws Exception {
        long primero = idDe(crear("{\"alias\": \"Prueba Uno\"}"));
        long segundo = idDe(crear("{\"alias\": \"Prueba Dos\"}"));

        cambiar(primero, "{\"alias\": \"Prueba Tres\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("Prueba Tres"));
        cambiar(segundo, "{\"alias\": \"Prueba Tres\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("alias-repetido"));
        // Reenviar el propio alias no es un conflicto, y una cadena vacía lo quita.
        cambiar(primero, "{\"alias\": \"Prueba Tres\"}").andExpect(status().isOk());
        cambiar(primero, "{\"alias\": \"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").isEmpty());
    }

    @Test
    void rechazaUnAliasDeMasDeCuarentaCaracteres() throws Exception {
        crear("{\"alias\": \"" + "A".repeat(41) + "\"}").andExpect(status().isBadRequest());
    }

    private long idDe(ResultActions respuesta) throws Exception {
        String cuerpo = respuesta.andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(cuerpo)
                .get("id")
                .asLong();
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return http.perform(
                post(BASE)
                        .cookie(jefe)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private ResultActions cambiar(long id, String cuerpo) throws Exception {
        return http.perform(
                patch(BASE + "/" + id)
                        .cookie(jefe)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private Cookie sesion() throws Exception {
        return http.perform(
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
}
