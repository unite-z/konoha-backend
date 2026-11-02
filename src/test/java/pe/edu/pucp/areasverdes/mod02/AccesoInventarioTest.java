package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Matriz exhaustiva de permisos de cada método y ruta HTTP de MOD-02. */
@SpringBootTest
@AutoConfigureMockMvc
class AccesoInventarioTest {

    private static final String CLAVE = "matriz-inventario-local";
    private static final String ANONIMO = "anónimo";
    private static final String JEFE = "jefe";
    private static final String INGENIERO = "ingeniero";
    private static final String PRACTICANTE = "practicante";
    private static final String CAPATAZ = "capataz";
    private static final String OPERARIO = "operario";
    private static final Set<String> ROLES =
            Set.of(ANONIMO, JEFE, INGENIERO, PRACTICANTE, CAPATAZ, OPERARIO);

    /** status 200/201/400/404/409 significa que el acceso pasó; 401 y 403 son denegaciones. */
    private static final Map<String, Map<String, Integer>> MATRIZ =
            Map.ofEntries(
                    fila("GET /api/v1/inventario/areas-verdes", 200, 200, 200, 200, 200, 403),
                    fila("POST /api/v1/inventario/areas-verdes", 401, 201, 201, 201, 403, 403),
                    fila(
                            "PATCH /api/v1/inventario/areas-verdes/{id}",
                            401,
                            200,
                            200,
                            200,
                            403,
                            403),
                    fila(
                            "PATCH /api/v1/inventario/areas-verdes/{id}/sector",
                            401,
                            200,
                            200,
                            200,
                            403,
                            403),
                    fila(
                            "GET /api/v1/inventario/areas-verdes/{id}/historial-sector",
                            200,
                            200,
                            200,
                            200,
                            200,
                            403),
                    fila("GET /api/v1/inventario/coberturas", 200, 200, 200, 200, 200, 403),
                    fila("POST /api/v1/inventario/coberturas", 401, 201, 403, 403, 403, 403),
                    fila("PATCH /api/v1/inventario/coberturas/{id}", 401, 200, 403, 403, 403, 403),
                    fila("GET /api/v1/inventario/tipos-uso", 200, 200, 200, 200, 200, 403),
                    fila("POST /api/v1/inventario/tipos-uso", 401, 201, 403, 403, 403, 403),
                    fila("PATCH /api/v1/inventario/tipos-uso/{id}", 401, 200, 403, 403, 403, 403),
                    fila("GET /api/v1/inventario/cuarteles", 200, 200, 200, 200, 200, 403),
                    fila("POST /api/v1/inventario/cuarteles", 401, 201, 403, 403, 403, 403),
                    fila("PATCH /api/v1/inventario/cuarteles/{id}", 401, 200, 403, 403, 403, 403),
                    fila("GET /api/v1/inventario/sectores", 200, 200, 200, 200, 200, 403),
                    fila("GET /api/v1/inventario/capataces", 200, 200, 200, 200, 200, 403),
                    fila("POST /api/v1/inventario/sectores", 401, 201, 403, 403, 403, 403),
                    fila("PATCH /api/v1/inventario/sectores/{id}", 401, 200, 403, 403, 403, 403),
                    fila(
                            "PATCH /api/v1/inventario/sectores/{id}/capataz",
                            401,
                            200,
                            403,
                            403,
                            403,
                            403),
                    fila(
                            "GET /api/v1/inventario/sectores/{id}/historial-capataz",
                            200,
                            200,
                            200,
                            200,
                            200,
                            403),
                    fila("GET /api/v1/inventario/ejemplares", 200, 200, 200, 200, 200, 403),
                    fila("POST /api/v1/inventario/ejemplares", 401, 201, 201, 201, 403, 403),
                    fila("PATCH /api/v1/inventario/ejemplares/{id}", 401, 200, 200, 200, 403, 403),
                    fila(
                            "DELETE /api/v1/inventario/ejemplares/{id}/placa-antigua",
                            401,
                            200,
                            200,
                            200,
                            403,
                            403),
                    fila("GET /api/v1/inventario/especies", 200, 200, 200, 200, 200, 403),
                    fila("POST /api/v1/inventario/especies", 401, 201, 403, 403, 403, 403),
                    fila("PATCH /api/v1/inventario/especies/{id}", 401, 200, 403, 403, 403, 403),
                    fila("GET /api/v1/inventario/mapa/usos-fuente", 200, 200, 200, 200, 200, 403),
                    fila("PUT /api/v1/inventario/mapa/usos-fuente", 401, 200, 403, 403, 403, 403),
                    fila("GET /api/v1/inventario/mapa/poligonos", 200, 200, 200, 200, 200, 403),
                    fila(
                            "PUT /api/v1/inventario/mapa/poligonos/{idFuente}",
                            401,
                            200,
                            403,
                            403,
                            403,
                            403));

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry propiedades) {
        String url =
                System.getenv()
                        .getOrDefault(
                                "TEST_DB_URL",
                                System.getenv()
                                        .getOrDefault(
                                                "MOD05_TEST_DB_URL",
                                                "jdbc:postgresql://127.0.0.1:55432/mod05_spring_test"));
        if (!url.endsWith("_test")) {
            throw new IllegalStateException("Las pruebas requieren una base terminada en _test");
        }
        propiedades.add("spring.datasource.url", () -> url);
        propiedades.add(
                "spring.datasource.username",
                () ->
                        System.getenv()
                                .getOrDefault(
                                        "TEST_DB_USER",
                                        System.getenv()
                                                .getOrDefault(
                                                        "MOD05_TEST_DB_USER",
                                                        System.getProperty("user.name"))));
        propiedades.add(
                "spring.datasource.password",
                () ->
                        System.getenv()
                                .getOrDefault(
                                        "TEST_DB_PASSWORD",
                                        System.getenv()
                                                .getOrDefault("MOD05_TEST_DB_PASSWORD", "")));
    }

    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    @BeforeEach
    void prepararUsuarios() {
        jdbc.execute("TRUNCATE core_usuario, mod05_usuario RESTART IDENTITY CASCADE");
        crearUsuario("matriz-jefe", "Jefe de sección", 1);
        crearUsuario("matriz-ingeniero", "Ingeniero de campo", 2);
        crearUsuario("matriz-practicante", "Practicante autorizado", 3);
        crearUsuario("matriz-capataz", "Capataz", 6);
        crearUsuario("matriz-operario", "Operario", 4);
    }

    @Test
    void matrizCubreTodosLosEndpointsYNingunaRutaQuedaSinPermisoDefinido() {
        Set<String> registrados = endpointsInventario();
        assertThat(MATRIZ.keySet())
                .as("La matriz debe contener exactamente las rutas y métodos de MOD-02")
                .containsExactlyInAnyOrderElementsOf(registrados);
        MATRIZ.forEach(
                (endpoint, resultados) ->
                        assertThat(resultados.keySet())
                                .as("La fila %s debe definir todos los roles", endpoint)
                                .containsExactlyInAnyOrderElementsOf(ROLES));
    }

    @Test
    void cadaRolRecibeElResultadoDefinidoParaCadaEndpoint() throws Exception {
        List<String> errores = new ArrayList<>();
        for (var endpoint : MATRIZ.entrySet()) {
            for (String rol : ROLES) {
                int esperado = endpoint.getValue().get(rol);
                int recibido =
                        http.perform(solicitud(endpoint.getKey(), rol))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                boolean autorizado = esperado != 401 && esperado != 403;
                if (autorizado ? recibido == 401 || recibido == 403 : recibido != esperado) {
                    errores.add(
                            endpoint.getKey()
                                    + " con rol "
                                    + rol
                                    + ": esperado "
                                    + esperado
                                    + " y recibido "
                                    + recibido);
                }
            }
        }
        assertThat(errores).as("Diferencias entre la matriz y la API").isEmpty();
    }

    private MockHttpServletRequestBuilder solicitud(String clave, String rol) {
        String[] partes = clave.split(" ", 2);
        HttpMethod metodo = HttpMethod.valueOf(partes[0]);
        String ruta = partes[1].replaceAll("\\{[^/]+}", "1");
        MockHttpServletRequestBuilder solicitud =
                switch (metodo) {
                    case GET -> get(ruta);
                    case POST -> post(ruta);
                    case PATCH -> patch(ruta);
                    case PUT -> put(ruta);
                    case DELETE -> delete(ruta);
                    default -> throw new IllegalArgumentException("Método sin matriz: " + metodo);
                };
        if (metodo == HttpMethod.POST || metodo == HttpMethod.PATCH || metodo == HttpMethod.PUT) {
            solicitud.contentType("application/json").content(cuerpo(clave));
        }
        if (!ANONIMO.equals(rol)) {
            solicitud.with(httpBasic("matriz-" + rol, CLAVE));
        }
        return solicitud;
    }

    private String cuerpo(String endpoint) {
        if (endpoint.contains("areas-verdes")) {
            return "{\"nombre\":\"Área matriz\",\"lugar\":\"Campus\","
                    + "\"ubicacion\":\"Jardín matriz\",\"superficie_m2\":20}";
        }
        if (endpoint.contains("coberturas")) return "{\"nombre\":\"Cobertura matriz\"}";
        if (endpoint.contains("tipos-uso")) return "{\"nombre\":\"Uso matriz\"}";
        if (endpoint.contains("cuarteles")) return "{\"numero\":\"M-1\",\"nombre\":\"Matriz\"}";
        if (endpoint.endsWith("/sectores")) return "{\"nombre\":\"Sector matriz\"}";
        if (endpoint.contains("/capataz")) return "{\"capataz_id\":null}";
        if (endpoint.contains("ejemplares")) return "{\"cantidad\":1}";
        if (endpoint.contains("especies")) {
            return "{\"nombre_comun\":\"Matriz\",\"forma_biologica\":\"árbol\"}";
        }
        if (endpoint.endsWith("/usos-fuente")) {
            return "{\"uso_fuente\":\"Matriz\",\"tipo_uso_id\":null}";
        }
        if (endpoint.contains("/poligonos/")) return "{\"area_verde_id\":null}";
        return "{}";
    }

    private void crearUsuario(String usuario, String rol, long idRol) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario, nombre, password_hash, activo) "
                                + "VALUES (?, ?, ?, true) RETURNING id",
                        Long.class,
                        usuario,
                        usuario,
                        claves.encode(CLAVE));
        jdbc.update("INSERT INTO mod05_usuario_rol (usuario_id, rol) VALUES (?, ?)", id, rol);
        Long rolCore =
                jdbc.queryForObject(
                        "SELECT id FROM core_rol WHERE codigo = ?",
                        Long.class,
                        idRol == 1
                                ? "jefe-seccion"
                                : idRol == 2
                                        ? "ingeniero-campo"
                                        : idRol == 6 ? "capataz" : "practicante");
        jdbc.update(
                "INSERT INTO core_usuario "
                        + "(id, nombre_usuario, nombre, credencial_hash, rol_id, activo) "
                        + "VALUES (?, ?, ?, ?, ?, true)",
                id,
                usuario,
                usuario,
                claves.encode(CLAVE),
                rolCore);
    }

    private static Set<String> endpointsInventario() {
        return List.of(
                        pe.edu.pucp.areasverdes.mod02.api.AreasController.class,
                        pe.edu.pucp.areasverdes.mod02.api.CoberturasController.class,
                        pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.class,
                        pe.edu.pucp.areasverdes.mod02.api.EjemplaresController.class,
                        pe.edu.pucp.areasverdes.mod02.api.MapaAreasController.class)
                .stream()
                .filter(c -> AnnotatedElementUtils.hasAnnotation(c, RestController.class))
                .flatMap(
                        c -> {
                            String base =
                                    AnnotatedElementUtils.findMergedAnnotation(
                                                    c, RequestMapping.class)
                                            .value()[0];
                            return java.util.Arrays.stream(c.getDeclaredMethods())
                                    .flatMap(m -> rutas(c, m, base));
                        })
                .collect(Collectors.toSet());
    }

    private static java.util.stream.Stream<String> rutas(
            Class<?> clase, Method metodo, String base) {
        var get = AnnotatedElementUtils.findMergedAnnotation(metodo, GetMapping.class);
        var post = AnnotatedElementUtils.findMergedAnnotation(metodo, PostMapping.class);
        var patch = AnnotatedElementUtils.findMergedAnnotation(metodo, PatchMapping.class);
        var put = AnnotatedElementUtils.findMergedAnnotation(metodo, PutMapping.class);
        var delete = AnnotatedElementUtils.findMergedAnnotation(metodo, DeleteMapping.class);
        if (get != null) return combinaciones("GET", base, get.value());
        if (post != null) return combinaciones("POST", base, post.value());
        if (patch != null) return combinaciones("PATCH", base, patch.value());
        if (put != null) return combinaciones("PUT", base, put.value());
        if (delete != null) return combinaciones("DELETE", base, delete.value());
        var request = AnnotatedElementUtils.findMergedAnnotation(metodo, RequestMapping.class);
        if (request == null) return java.util.stream.Stream.empty();
        return java.util.Arrays.stream(request.method())
                .flatMap(r -> combinaciones(r.name(), base, request.value()));
    }

    private static java.util.stream.Stream<String> combinaciones(
            String verbo, String base, String[] rutas) {
        return java.util.Arrays.stream(rutas)
                .map(ruta -> (verbo + " " + base + "/" + ruta).replaceAll("/+$", ""));
    }

    private static Map.Entry<String, Map<String, Integer>> fila(
            String endpoint,
            int anonimo,
            int jefe,
            int ingeniero,
            int practicante,
            int capataz,
            int operario) {
        return Map.entry(
                endpoint,
                Map.of(
                        ANONIMO,
                        anonimo,
                        JEFE,
                        jefe,
                        INGENIERO,
                        ingeniero,
                        PRACTICANTE,
                        practicante,
                        CAPATAZ,
                        capataz,
                        OPERARIO,
                        operario));
    }
}
