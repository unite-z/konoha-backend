package pe.edu.pucp.areasverdes.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Ningún endpoint sin proteger (AV-240): recorre todos los endpoints registrados en Spring, de
 * cualquier módulo, los llama sin credenciales y exige 401 o 403. Cualquier otra respuesta, incluso
 * un 404 o un 400, significa que la petición pasó la seguridad. Solo no cuenta el 405: MOD-06
 * registra sus rutas para todos los métodos y responde así a los que no implementa.
 *
 * <p>Las excepciones están en {@link #PUBLICOS}, cada una con su motivo.
 */
class PermisosGlobalesApiTest extends BasePruebaGlobal {

    /** Endpoints públicos a propósito: «método ruta» y el motivo. */
    static final Map<String, String> PUBLICOS =
            Map.of(
                    "POST /api/v1/core/auth/login/", "Inicio de sesión",
                    "GET /error", "Página de error de Spring, sin datos",
                    "GET /api/docs/", "Swagger, público en todos los entornos",
                    "GET /api/schema", "OpenAPI, público en todos los entornos",
                    "GET /api/schema/", "OpenAPI, público en todos los entornos",
                    "GET /api/schema/swagger-config", "Configuración de Swagger",
                    "GET /api/tercerizados/docs", "Swagger de MOD-05",
                    "GET /api/tercerizados/schema", "OpenAPI de MOD-05",
                    "GET /api/tercerizados/schema/", "OpenAPI de MOD-05");

    /** Rutas comodín de MOD-06: solo responden 404 a lo que no existe. */
    static final Set<String> COMODINES = Set.of("/api/v1", "/api/v1/**");

    /** Los demás métodos no devuelven datos ni escriben: el navegador y Spring los resuelven. */
    static final Set<RequestMethod> METODOS_COMPROBADOS =
            Set.of(
                    RequestMethod.GET,
                    RequestMethod.POST,
                    RequestMethod.PUT,
                    RequestMethod.PATCH,
                    RequestMethod.DELETE);

    @Test
    void ningunEndpointQuedaSinProteger() {
        Map<String, Integer> abiertos = new TreeMap<>();
        Set<String> endpoints = endpointsRegistrados();
        endpoints.forEach(
                endpoint -> {
                    int estado = estadoSinCredenciales(endpoint);
                    if (estaAbierto(endpoint, estado, endpoints)) {
                        abiertos.put(endpoint, estado);
                    }
                });

        Map<String, Integer> sinJustificar = new TreeMap<>(abiertos);
        sinJustificar.keySet().removeAll(PUBLICOS.keySet());
        assertEquals(
                Map.of(),
                sinJustificar,
                "Endpoints que responden sin credenciales («método ruta=estado»): protégelos");

        Set<String> publicosInexistentes = new TreeSet<>(PUBLICOS.keySet());
        publicosInexistentes.removeAll(endpoints);
        assertEquals(Set.of(), publicosInexistentes, "Estos endpoints de PUBLICOS ya no existen");
    }

    /**
     * Si una ruta se registra con y sin barra final, la forma sin barra solo cuenta si responde
     * 2xx: lo demás es la redirección de Spring a la forma con barra, que se comprueba por su lado.
     */
    private static boolean estaAbierto(String endpoint, int estado, Set<String> endpoints) {
        if (estado == 401 || estado == 403 || estado == 405) {
            return false;
        }
        return !endpoints.contains(endpoint + "/") || estado / 100 == 2;
    }

    private Set<String> endpointsRegistrados() {
        Set<String> endpoints = new TreeSet<>();
        rutas.getHandlerMethods()
                .keySet()
                .forEach(
                        info -> {
                            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
                            for (String patron : info.getPatternValues()) {
                                if (COMODINES.contains(patron)) {
                                    continue;
                                }
                                if (metodos.isEmpty()) {
                                    endpoints.add("GET " + patron);
                                }
                                metodos.stream()
                                        .filter(METODOS_COMPROBADOS::contains)
                                        .forEach(m -> endpoints.add(m.name() + " " + patron));
                            }
                        });
        return endpoints;
    }

    /**
     * Lleva token CSRF para que un 403 signifique falta de permiso y no token ausente. Si el
     * controlador lanza una excepción, la petición ya pasó la seguridad: cuenta como abierta.
     */
    private int estadoSinCredenciales(String endpoint) {
        String[] partes = endpoint.split(" ", 2);
        String url = partes[1].replaceAll("\\{[^/]*}", "999999");
        try {
            return http.perform(
                            request(HttpMethod.valueOf(partes[0]), url)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}")
                                    .with(csrf()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception e) {
            return 500;
        }
    }
}
