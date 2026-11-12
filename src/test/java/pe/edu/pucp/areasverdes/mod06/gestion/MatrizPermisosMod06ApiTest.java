package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Matriz de permisos de los reportes antiguos y la extracción IA del MOD-06, con sesiones reales de
 * MOD-01 (AV-327). {@code ok} significa que pasa la autorización: la respuesta puede ser un 400 o
 * un 404 porque la petición de ejemplo no lleva datos.
 *
 * <pre>
 * | Endpoint                             | anónimo | jefe | ingeniero | practicante | capataz | inactivo |
 * |--------------------------------------|---------|------|-----------|-------------|---------|----------|
 * | GET  reportes antiguos               |   401   |  ok  |    ok     |     ok      |   403   |   401    |
 * | GET  ia/extracciones/ y su detalle   |   401   |  ok  |    ok     |     ok      |   403   |   401    |
 * | POST ia/extracciones/                |   401   |  ok  |    ok     |     ok      |   403   |   401    |
 * | GET  ia/extracciones/metricas/       |   401   |  ok  |   403     |    403      |   403   |   401    |
 * | POST ia/extracciones/{id}/validar/   |   401   |  ok  |   403     |    403      |   403   |   401    |
 * | POST ia/extracciones/{id}/rechazar/  |   401   |  ok  |   403     |    403      |   403   |   401    |
 * </pre>
 */
public class MatrizPermisosMod06ApiTest extends ApiGestion {

    static final int OK = 0;
    static final String ANONIMO = "anónimo";
    static final List<String> USUARIOS =
            List.of(ANONIMO, "jefe", "ingeniero", "practicante", "capataz", "inactivo");
    static final List<Integer> CONSULTA_O_PROPUESTA = List.of(401, OK, OK, OK, 403, 401);
    static final List<Integer> SOLO_VALIDADOR = List.of(401, OK, 403, 403, 403, 401);

    record Fila(String metodo, String url, List<Integer> esperados) {}

    static final String PERIODO = "?desde=2026-10-01&hasta=2026-10-31";

    static final List<Fila> MATRIZ =
            List.of(
                    new Fila(
                            "GET",
                            "/api/v1/reportes/cumplimiento-corte/" + PERIODO,
                            CONSULTA_O_PROPUESTA),
                    new Fila(
                            "GET",
                            "/api/v1/reportes/distribucion-personal/" + PERIODO,
                            CONSULTA_O_PROPUESTA),
                    new Fila(
                            "GET",
                            "/api/v1/reportes/atenciones-espacio-sector/" + PERIODO,
                            CONSULTA_O_PROPUESTA),
                    new Fila("GET", "/api/v1/ia/extracciones/", CONSULTA_O_PROPUESTA),
                    new Fila("GET", "/api/v1/ia/extracciones/999999/", CONSULTA_O_PROPUESTA),
                    new Fila("POST", "/api/v1/ia/extracciones/", CONSULTA_O_PROPUESTA),
                    new Fila("GET", "/api/v1/ia/extracciones/metricas/", SOLO_VALIDADOR),
                    new Fila("POST", "/api/v1/ia/extracciones/999999/validar/", SOLO_VALIDADOR),
                    new Fila("POST", "/api/v1/ia/extracciones/999999/rechazar/", SOLO_VALIDADOR));

    /** Endpoints de la matriz («MÉTODO url»), para la prueba global de contrato de rutas. */
    public static List<String> endpointsCubiertos() {
        return MATRIZ.stream().map(f -> f.metodo() + " " + f.url()).toList();
    }

    static Stream<Arguments> combinaciones() {
        List<Arguments> casos = new ArrayList<>();
        for (Fila f : MATRIZ) {
            for (int i = 0; i < USUARIOS.size(); i++) {
                casos.add(Arguments.of(f.metodo(), f.url(), USUARIOS.get(i), f.esperados().get(i)));
            }
        }
        return casos.stream();
    }

    @ParameterizedTest(name = "{0} {1} como {2} → {3}")
    @MethodSource("combinaciones")
    void cadaRolRecibeLoQueDiceLaMatriz(String metodo, String url, String usuario, int esperado)
            throws Exception {
        Cookie cookie = usuario.equals(ANONIMO) ? null : sesion(usuario);
        int estado = estado(peticion(metodo, url, cookie).with(csrf()));
        String contexto = metodo + " " + url + " como " + usuario + " respondió " + estado;
        if (esperado == OK) {
            assertTrue(estado != 401 && estado != 403, contexto);
        } else {
            assertEquals(esperado, estado, contexto);
        }
    }

    @Test
    void unaEscrituraConSesionSinTokenCsrfSeRechaza() throws Exception {
        assertEquals(
                403,
                estado(
                        peticion(
                                "POST",
                                "/api/v1/ia/extracciones/999999/rechazar/",
                                sesion("jefe"))));
    }

    @Test
    void perderElPermisoValeDesdeLaSiguientePeticion() throws Exception {
        Cookie cookie = sesion("jefe");
        String url = "/api/v1/ia/extracciones/metricas/";
        assertTrue(estado(peticion("GET", url, cookie)) != 403);

        jdbc.update(
                """
                UPDATE core_usuario SET rol_id = (SELECT id FROM core_rol WHERE codigo = 'ingeniero-campo')
                WHERE nombre_usuario = :u
                """,
                Map.of("u", "jefe" + DOMINIO));

        assertEquals(403, estado(peticion("GET", url, cookie)));
    }

    private MockHttpServletRequestBuilder peticion(String metodo, String url, Cookie cookie) {
        MockHttpServletRequestBuilder peticion =
                request(HttpMethod.valueOf(metodo), url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}");
        return cookie == null ? peticion : peticion.cookie(cookie);
    }

    private int estado(MockHttpServletRequestBuilder peticion) throws Exception {
        return http.perform(peticion).andReturn().getResponse().getStatus();
    }
}
