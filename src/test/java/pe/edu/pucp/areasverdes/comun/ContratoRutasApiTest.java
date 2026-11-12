package pe.edu.pucp.areasverdes.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import pe.edu.pucp.areasverdes.mod01.Mod01PermisosApiTest;
import pe.edu.pucp.areasverdes.mod03.MatrizPermisosApiTest;
import pe.edu.pucp.areasverdes.mod06.gestion.MatrizPermisosGestionApiTest;
import pe.edu.pucp.areasverdes.mod06.gestion.MatrizPermisosIaApiTest;
import pe.edu.pucp.areasverdes.mod06.gestion.MatrizPermisosMod06ApiTest;

/**
 * Contrato de rutas (AV-244): recorre todos los endpoints registrados en Spring y exige que cada
 * uno (1) cuelgue de {@code /api/v1/<modulo>/}, (2) lleve {@code @Operation} y (3) figure en la
 * matriz de permisos de su módulo. Complementa a {@link PermisosGlobalesApiTest}, que comprueba que
 * ninguno quede sin proteger.
 *
 * <p>Cada matriz declara sus endpoints en {@code endpointsCubiertos()}; añade ahí la matriz de un
 * módulo nuevo. Los handlers {@code @Hidden} (los 405 de MOD-06 y el comodín de {@code /api/v1}) no
 * son contrato y se saltan.
 */
class ContratoRutasApiTest extends BasePruebaGlobal {

    /** Endpoint registrado: método, patrón sin barra final, controlador y si lleva @Operation. */
    record Endpoint(String metodo, String patron, String controlador, boolean conOperation) {
        String clave() {
            return metodo + " " + patron;
        }
    }

    /** Anteriores al contrato: sin prefijo {@code /api/v1/<modulo>/} ni {@code @Operation}. */
    static final List<String> ANTERIORES = List.of("/api/tercerizados/", "/api/health");

    /**
     * Documentación y errores, públicos y sin matriz (los cubre {@link PermisosGlobalesApiTest}).
     */
    static final List<String> SIN_MATRIZ =
            List.of(
                    "/api/docs",
                    "/api/schema",
                    "/api/tercerizados/docs",
                    "/api/tercerizados/schema",
                    "/error");

    /**
     * Controladores con endpoints todavía sin fila en la matriz de su módulo. Es deuda: al cubrir
     * todos los de un controlador, la prueba exige quitarlo de aquí. Un controlador nuevo no puede
     * entrar en la lista.
     */
    static final Set<String> SIN_MATRIZ_AUN =
            Set.of(
                    // MOD-05: no tiene matriz
                    "AtencionController",
                    "AtencionEspecialController",
                    "MaestrosController",
                    "ObservacionPlagaController",
                    "PlanFitosanitarioController",
                    "VerificacionSeguridadController",
                    // MOD-02: no tiene matriz
                    "AreasController",
                    "CampanasPodaController",
                    "CatalogosMod02Controller",
                    "CoberturasController",
                    "CuartelesController",
                    "EjemplaresController",
                    "EspeciesController",
                    "IntervencionesPodaController",
                    "MapaAreasController",
                    "TiposIntervencionController",
                    // MOD-04: no tiene matriz
                    "OperacionCampoController");

    @Test
    void todoEndpointCumpleElContrato() {
        List<String> cubiertos = new ArrayList<>();
        cubiertos.addAll(Mod01PermisosApiTest.endpointsCubiertos());
        cubiertos.addAll(MatrizPermisosApiTest.endpointsCubiertos());
        cubiertos.addAll(MatrizPermisosGestionApiTest.endpointsCubiertos());
        cubiertos.addAll(MatrizPermisosIaApiTest.endpointsCubiertos());
        cubiertos.addAll(MatrizPermisosMod06ApiTest.endpointsCubiertos());

        List<Endpoint> endpoints = endpointsRegistrados();
        assertEquals(
                List.of(),
                infracciones(endpoints, filasDeMatriz(endpoints, cubiertos), SIN_MATRIZ_AUN),
                "Endpoints fuera del contrato («método ruta: motivo»)");
    }

    @Test
    void laPruebaDiceQueEndpointFallaYPorQue() {
        List<Endpoint> falsos =
                List.of(
                        new Endpoint("GET", "/api/v1/prueba/sin-fila", "PruebaController", true),
                        new Endpoint("GET", "/api/otra/ruta", "PruebaController", true),
                        new Endpoint(
                                "POST", "/api/v1/prueba/sin-operation", "PruebaController", false),
                        new Endpoint("GET", "/api/tercerizados/viejo", "ViejoController", false),
                        new Endpoint("GET", "/api/health", "SaludController", false));
        Set<String> cubiertos =
                Set.of(
                        "GET /api/otra/ruta",
                        "POST /api/v1/prueba/sin-operation",
                        "GET /api/tercerizados/viejo",
                        "GET /api/health");
        assertEquals(
                List.of(
                        "GET /api/otra/ruta: debe colgar de /api/v1/<modulo>/",
                        "GET /api/v1/prueba/sin-fila: no figura en la matriz de permisos de su"
                                + " módulo",
                        "POST /api/v1/prueba/sin-operation: le falta @Operation"),
                infracciones(falsos, cubiertos, Set.of()));
    }

    @Test
    void siUnaMatrizYaCubreUnControladorPendienteHayQueQuitarloDeLaLista() {
        List<Endpoint> endpoints =
                List.of(new Endpoint("GET", "/api/v1/prueba/x", "PruebaController", true));
        assertEquals(
                List.of("PruebaController: tiene toda su matriz, quítalo de SIN_MATRIZ_AUN"),
                infracciones(
                        endpoints, Set.of("GET /api/v1/prueba/x"), Set.of("PruebaController")));
    }

    /** Aplica las tres reglas del contrato; devuelve una línea por infracción, ordenada. */
    static List<String> infracciones(
            List<Endpoint> endpoints, Set<String> cubiertos, Set<String> sinMatrizAun) {
        Set<String> resultado = new TreeSet<>();
        Map<String, Integer> pendientesPorControlador = new HashMap<>();
        for (Endpoint e : endpoints) {
            if (SIN_MATRIZ.stream().anyMatch(e.patron()::startsWith)) {
                continue;
            }
            boolean anterior = ANTERIORES.stream().anyMatch(e.patron()::startsWith);
            if (!anterior && !e.patron().matches("/api/v1/[a-z0-9-]+/.*")) {
                resultado.add(e.clave() + ": debe colgar de /api/v1/<modulo>/");
            }
            if (!anterior && !e.conOperation()) {
                resultado.add(e.clave() + ": le falta @Operation");
            }
            if (!cubiertos.contains(e.clave())) {
                pendientesPorControlador.merge(e.controlador(), 1, Integer::sum);
                if (!sinMatrizAun.contains(e.controlador())) {
                    resultado.add(e.clave() + ": no figura en la matriz de permisos de su módulo");
                }
            }
        }
        sinMatrizAun.stream()
                .filter(c -> !pendientesPorControlador.containsKey(c))
                .forEach(
                        c ->
                                resultado.add(
                                        c + ": tiene toda su matriz, quítalo de SIN_MATRIZ_AUN"));
        return List.copyOf(resultado);
    }

    private List<Endpoint> endpointsRegistrados() {
        Map<String, Endpoint> unicos = new java.util.TreeMap<>();
        rutas.getHandlerMethods()
                .forEach(
                        (info, metodo) -> {
                            if (esHidden(metodo)) {
                                return;
                            }
                            boolean conOperation =
                                    AnnotatedElementUtils.hasAnnotation(
                                            metodo.getMethod(), Operation.class);
                            for (String patron : info.getPatternValues()) {
                                for (RequestMethod m : info.getMethodsCondition().getMethods()) {
                                    Endpoint e =
                                            new Endpoint(
                                                    m.name(),
                                                    sinBarraFinal(patron),
                                                    metodo.getBeanType().getSimpleName(),
                                                    conOperation);
                                    unicos.merge(
                                            e.clave(),
                                            e,
                                            (a, b) ->
                                                    new Endpoint(
                                                            a.metodo(),
                                                            a.patron(),
                                                            a.controlador(),
                                                            a.conOperation() || b.conOperation()));
                                }
                            }
                        });
        return List.copyOf(unicos.values());
    }

    private static boolean esHidden(org.springframework.web.method.HandlerMethod metodo) {
        return AnnotatedElementUtils.hasAnnotation(metodo.getMethod(), Hidden.class)
                || AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), Hidden.class);
    }

    private static String sinBarraFinal(String ruta) {
        return ruta.length() > 1 ? ruta.replaceAll("/$", "") : ruta;
    }

    /**
     * Traduce las filas de las matrices («MÉTODO url de ejemplo») al endpoint registrado que
     * resuelve cada una, el de patrón más específico.
     */
    static Set<String> filasDeMatriz(List<Endpoint> endpoints, List<String> filas) {
        PathPatternParser parser = new PathPatternParser();
        Set<String> claves = new TreeSet<>();
        for (String fila : filas) {
            String[] partes = fila.split(" ", 2);
            String url = sinBarraFinal(partes[1].replaceAll("\\?.*", ""));
            PathContainer ruta = PathContainer.parsePath(url);
            Optional<Endpoint> elegido =
                    endpoints.stream()
                            .filter(e -> e.metodo().equals(partes[0]))
                            .filter(e -> parser.parse(e.patron()).matches(ruta))
                            .min(
                                    Comparator.comparing(
                                            e -> parser.parse(e.patron()),
                                            PathPattern.SPECIFICITY_COMPARATOR));
            elegido.ifPresent(e -> claves.add(e.clave()));
        }
        return claves;
    }
}
