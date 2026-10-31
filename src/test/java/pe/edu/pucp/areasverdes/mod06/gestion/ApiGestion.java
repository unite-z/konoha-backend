package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Base de las pruebas de API de los reportes de gestión: base PostGIS real terminada en {@code
 * _test} y una cuenta de MOD-01 por rol ({@code jefe}, {@code ingeniero}, {@code practicante},
 * {@code capataz}) más {@code inactivo} (jefe dado de baja tras iniciar sesión), todas con {@link
 * #CLAVE}. Se entra con la cookie de sesión de MOD-01 ({@link #sesion}).
 *
 * <p>MOD-06 lee datos de MOD-05: el escenario necesita un {@code mod05_usuario} con id 1 como autor
 * de las atenciones. Es solo un dato, no sirve para autenticarse.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiGestion {

    static final String CLAVE = "clave-prueba-1";
    static final String DOMINIO = "@prueba.pucp.edu.pe";

    /**
     * Base común de pruebas. El pool se limita a 2 conexiones: Spring guarda vivo cada contexto de
     * prueba y, con el tamaño por defecto (10), la suma de contextos agota las conexiones de
     * PostgreSQL en CI ("too many clients already").
     */
    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
        p.add("spring.datasource.hikari.maximum-pool-size", () -> 2);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    private String hashClave;

    @BeforeEach
    void usuarios() {
        jdbc.getJdbcTemplate().execute("TRUNCATE mod05_usuario RESTART IDENTITY CASCADE");
        jdbc.getJdbcTemplate()
                .execute(
                        "INSERT INTO mod05_usuario(usuario, nombre, password_hash, activo)"
                                + " VALUES ('autor-escenario', 'Autor del escenario', '', true)");
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        if (hashClave == null) {
            hashClave = claves.encode(CLAVE);
        }
        cuenta("jefe", "jefe-seccion");
        cuenta("ingeniero", "ingeniero-campo");
        cuenta("practicante", "practicante");
        cuenta("capataz", "capataz");
        cuenta("inactivo", "jefe-seccion");
    }

    /** Carga src/test/resources/mod06/escenario-gestion.sql. */
    void cargarEscenario() throws IOException {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod06/escenario-gestion.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * Inicia sesión en MOD-01 y devuelve la cookie {@code av_sesion}. Para {@code inactivo}, la
     * cuenta se desactiva después de entrar: la cookie existe pero ya no vale.
     */
    Cookie sesion(String nombre) throws Exception {
        Cookie cookie =
                http.perform(
                                post("/api/v1/core/auth/login/")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"identificador\": \""
                                                        + nombre
                                                        + DOMINIO
                                                        + "\", \"contrasena\": \""
                                                        + CLAVE
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getCookie(SeguridadMod01.COOKIE_SESION);
        if (nombre.equals("inactivo")) {
            jdbc.update(
                    "UPDATE core_usuario SET activo = false WHERE nombre_usuario = :u",
                    Map.of("u", nombre + DOMINIO));
        }
        return cookie;
    }

    private void cuenta(String nombre, String rol) {
        jdbc.update(
                """
                INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                SELECT :correo, :correo, :nombre, :hash, id FROM core_rol WHERE codigo = :rol
                """,
                Map.of(
                        "correo", nombre + DOMINIO,
                        "nombre", nombre,
                        "hash", hashClave,
                        "rol", rol));
    }
}
