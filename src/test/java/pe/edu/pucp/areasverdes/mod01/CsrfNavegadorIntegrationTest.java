package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

/**
 * La cookie {@code XSRF-TOKEN} tal como la usa el navegador, contra el servidor HTTP real: MockMvc
 * no sirve aquí, porque {@code csrf()} de spring-security-test sustituye el repositorio de tokens
 * del contexto. Con sesión sin estado, Spring borraba el token en cada petición autenticada y las
 * escrituras fallaban una sí y otra no (AV-234).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsrfNavegadorIntegrationTest {

    static final String CLAVE = "clave-prueba-1";
    static final String CORREO = "csrf@prueba.pucp.edu.pe";

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
        // http en las pruebas: sin Secure, como en local.
        p.add("core.seguridad.cookie-segura", () -> "false");
    }

    @LocalServerPort int puerto;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    HttpClient cliente;

    @BeforeEach
    void cuenta() {
        cliente = HttpClient.newBuilder().cookieHandler(cookies).build();
        jdbc.update(
                """
                INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                SELECT :c, :c, 'CSRF', :h, id FROM core_rol WHERE codigo = 'jefe-seccion'
                ON CONFLICT DO NOTHING
                """,
                Map.of("c", CORREO, "h", claves.encode(CLAVE)));
        jdbc.update(
                "UPDATE core_usuario SET activo = true, intentos_fallidos = 0, bloqueado_hasta ="
                        + " NULL WHERE nombre_usuario = :c",
                Map.of("c", CORREO));
    }

    @Test
    void elTokenSobreviveALasLecturasYSirveEnMod01YMod05() throws Exception {
        var login =
                enviar(
                        "POST",
                        "/api/v1/core/auth/login/",
                        "{\"identificador\": \""
                                + CORREO
                                + "\", \"contrasena\": \""
                                + CLAVE
                                + "\"}",
                        false);
        assertEquals(200, login.statusCode());
        String token = token();
        assertNotNull(token, "El inicio de sesión entrega el token CSRF");

        assertEquals(200, enviar("GET", "/api/v1/core/auth/sesion/", null, false).statusCode());
        assertEquals(200, enviar("GET", "/api/tercerizados/me/", null, false).statusCode());
        assertEquals(token, token(), "Las lecturas autenticadas no borran ni cambian el token");

        // Pasa el CSRF y llega a la validación (400), en MOD-05 y en MOD-03.
        assertEquals(400, enviar("POST", "/api/tercerizados/atenciones/", "{}", true).statusCode());
        assertEquals(
                400,
                enviar("POST", "/api/v1/planificacion/restricciones/", "{}", true).statusCode());
        // Sin la cabecera, rechazada.
        assertEquals(
                403, enviar("POST", "/api/tercerizados/atenciones/", "{}", false).statusCode());
        assertEquals(204, enviar("POST", "/api/v1/core/auth/logout/", null, true).statusCode());
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo, boolean conToken)
            throws Exception {
        var peticion =
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                        .header("Content-Type", "application/json")
                        .method(
                                metodo,
                                cuerpo == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (conToken) {
            peticion.header("X-XSRF-TOKEN", token());
        }
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
