package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Matriz de permisos de los reportes de gestión del MOD-06 (HU v3, 2.1: «Panel de indicadores y
 * reportes»: consultan Jefe de sección, Ingeniero de campo y Practicante, que tienen el permiso de
 * MOD-01 {@code REP.INDICADOR.CONSULTAR}; el Capataz no lo tiene). Gate de {@code
 * docs/estandares/pruebas.md}: un endpoint de {@code mod06.gestion} sin fila aquí rompe CI.
 *
 * <pre>
 * | Endpoint                                   | anónimo | jefe | ingeniero | practicante | capataz | inactivo |
 * |--------------------------------------------|---------|------|-----------|-------------|---------|----------|
 * | GET /api/v1/reportes/panel/                |   401   | 200  |    200    |     200     |   403   |   401    |
 * | GET /api/v1/reportes/panel/{indicador}/    |   401   | 200  |    200    |     200     |   403   |   401    |
 * | GET /api/v1/reportes/material-vegetal/     |   401   | 200  |    200    |     200     |   403   |   401    |
 * | GET /api/v1/reportes/mapa-atenciones/      |   401   | 200  |    200    |     200     |   403   |   401    |
 * </pre>
 *
 * <p>Se entra con la cookie de sesión de MOD-01. {@code inactivo} da 401: MOD-01 deja de aceptar la
 * sesión de una cuenta desactivada antes de llegar a {@code AccesoGestion}.
 */
class MatrizPermisosGestionApiTest extends ApiGestion {

    static final String ANONIMO = "anónimo";
    static final List<String> USUARIOS =
            List.of(ANONIMO, "jefe", "ingeniero", "practicante", "capataz", "inactivo");

    /** Esperado por usuario, en el orden de {@link #USUARIOS}. */
    static final List<Integer> CONSULTA = List.of(401, 200, 200, 200, 403, 401);

    /** Fila de la matriz: endpoint normalizado (sin barra final), URL de ejemplo y esperados. */
    record Fila(String endpoint, String ejemplo, List<Integer> esperados) {}

    static final List<Fila> MATRIZ =
            List.of(
                    new Fila(
                            "GET /api/v1/reportes/panel",
                            "/api/v1/reportes/panel/?desde=2026-10-01&hasta=2026-10-31",
                            CONSULTA),
                    new Fila(
                            "GET /api/v1/reportes/panel/{indicador}",
                            "/api/v1/reportes/panel/atenciones-por-espacio/?desde=2026-10-01&hasta=2026-10-31",
                            CONSULTA),
                    new Fila(
                            "GET /api/v1/reportes/material-vegetal",
                            "/api/v1/reportes/material-vegetal/?mes=2026-10",
                            CONSULTA),
                    new Fila(
                            "GET /api/v1/reportes/mapa-atenciones",
                            "/api/v1/reportes/mapa-atenciones/?desde=2026-10-01&hasta=2026-10-31",
                            CONSULTA));

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping rutas;

    static Stream<Arguments> combinaciones() {
        List<Arguments> casos = new ArrayList<>();
        for (Fila f : MATRIZ) {
            for (int i = 0; i < USUARIOS.size(); i++) {
                casos.add(
                        Arguments.of(
                                f.endpoint(), f.ejemplo(), USUARIOS.get(i), f.esperados().get(i)));
            }
        }
        return casos.stream();
    }

    @ParameterizedTest(name = "{0} como {2} → {3}")
    @MethodSource("combinaciones")
    void cadaRolRecibeLoQueDiceLaMatriz(
            String endpoint, String ejemplo, String usuario, int esperado) throws Exception {
        MockHttpServletRequestBuilder peticion = get(ejemplo);
        if (!usuario.equals(ANONIMO)) {
            peticion = peticion.cookie(sesion(usuario));
        }
        int estado = http.perform(peticion).andReturn().getResponse().getStatus();
        assertEquals(esperado, estado, endpoint + " como " + usuario);
    }

    @Test
    void todosLosEndpointsTienenFilaYCadaFilaUnEndpoint() {
        Set<String> registrados = new LinkedHashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e :
                rutas.getHandlerMethods().entrySet()) {
            if (!e.getValue()
                    .getBeanType()
                    .getPackageName()
                    .startsWith("pe.edu.pucp.areasverdes.mod06.gestion")) {
                continue;
            }
            RequestMappingInfo info = e.getKey();
            Set<String> patrones =
                    info.getPathPatternsCondition() != null
                            ? info.getPathPatternsCondition().getPatternValues()
                            : info.getPatternsCondition().getPatterns();
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            for (String patron : patrones) {
                for (RequestMethod metodo : metodos) {
                    registrados.add(metodo + " " + sinBarraFinal(patron));
                }
            }
        }
        Map<String, Fila> filas = new LinkedHashMap<>();
        MATRIZ.forEach(f -> filas.put(f.endpoint(), f));
        for (String endpoint : registrados) {
            if (!filas.containsKey(endpoint)) {
                fail("Endpoint sin fila en la matriz de permisos: " + endpoint);
            }
        }
        for (String fila : filas.keySet()) {
            if (!registrados.contains(fila)) {
                fail("Fila de la matriz de permisos sin endpoint: " + fila);
            }
        }
    }

    private static String sinBarraFinal(String patron) {
        return patron.length() > 1 && patron.endsWith("/")
                ? patron.substring(0, patron.length() - 1)
                : patron;
    }
}
