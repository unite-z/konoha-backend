package pe.edu.pucp.areasverdes.mod01;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Base de las pruebas de API de MOD-01: base PostGIS real terminada en {@code _test} y una cuenta
 * de prueba por rol ({@code jefe}, {@code ingeniero}, {@code practicante}, {@code capataz}), todas
 * con {@link #CLAVE}. Nunca son cuentas reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiCore {

    static final String CLAVE = "clave-prueba-1";
    static final String DOMINIO = "@prueba.pucp.edu.pe";

    /** Los adjuntos de las pruebas van a una carpeta temporal, nunca a {@code private-media}. */
    static final Path ARCHIVOS;

    static {
        try {
            ARCHIVOS = Files.createTempDirectory("core-test-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
        p.add("mod05.archivos", () -> ARCHIVOS.toString());
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;
    @Autowired ObjectMapper json;

    final Map<String, Long> ids = new HashMap<>();
    private String hashClave;

    @BeforeEach
    void cuentas() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_adjunto, core_archivo, core_sesion, core_registro_acceso,"
                            + " core_registro_auditoria, core_usuario RESTART IDENTITY CASCADE");
        if (hashClave == null) {
            hashClave = claves.encode(CLAVE);
        }
        ids.clear();
        cuenta("jefe", "jefe-seccion");
        cuenta("ingeniero", "ingeniero-campo");
        cuenta("practicante", "practicante");
        cuenta("capataz", "capataz");
    }

    long cuenta(String nombre, String rol) {
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash,
                            rol_id)
                        SELECT :correo, :correo, :nombre, :hash, id FROM core_rol WHERE codigo = :rol
                        RETURNING id
                        """,
                        Map.of(
                                "correo",
                                nombre + DOMINIO,
                                "nombre",
                                nombre,
                                "hash",
                                hashClave,
                                "rol",
                                rol),
                        Long.class);
        ids.put(nombre, id);
        return id;
    }

    /** Inicia sesión y devuelve la cookie {@code av_sesion}. */
    Cookie sesion(String nombre) throws Exception {
        MvcResult r =
                http.perform(login(nombre + DOMINIO, CLAVE)).andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie(SeguridadMod01.COOKIE_SESION);
    }

    MockHttpServletRequestBuilder login(String identificador, String contrasena) {
        return post("/api/v1/core/auth/login/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"identificador\": \""
                                + identificador
                                + "\", \"contrasena\": \""
                                + contrasena
                                + "\"}");
    }

    /** Escritura con sesión y token CSRF. */
    MockHttpServletRequestBuilder escribir(
            MockHttpServletRequestBuilder peticion, Cookie sesion, String cuerpo) {
        var p = peticion.cookie(sesion).with(csrf());
        return cuerpo == null ? p : p.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    JsonNode cuerpo(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString());
    }

    int contarAccesos(String resultado) {
        Integer n =
                jdbc.queryForObject(
                        "SELECT count(*) FROM core_registro_acceso WHERE resultado = :r",
                        Map.of("r", resultado),
                        Integer.class);
        return n == null ? 0 : n;
    }
}
