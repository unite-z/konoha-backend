package pe.edu.pucp.areasverdes.mod02;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CoberturasApiTest {

    private static final String CLAVE = "prueba-integration-local";
    private static final String COBERTURAS = "/api/v1/inventario/coberturas/";
    private static final String AREAS = "/api/v1/inventario/areas-verdes/";

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry propiedades) {
        String url =
                System.getenv()
                        .getOrDefault(
                                "MOD05_TEST_DB_URL",
                                "jdbc:postgresql://127.0.0.1:55432/mod05_spring_test");
        if (!url.endsWith("_test")) {
            throw new IllegalStateException("Las pruebas requieren una base terminada en _test");
        }
        propiedades.add("spring.datasource.url", () -> url);
        propiedades.add(
                "spring.datasource.username",
                () ->
                        System.getenv()
                                .getOrDefault(
                                        "MOD05_TEST_DB_USER", System.getProperty("user.name")));
        propiedades.add(
                "spring.datasource.password",
                () -> System.getenv().getOrDefault("MOD05_TEST_DB_PASSWORD", ""));
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    @BeforeEach
    void prepararBase() {
        jdbc.getJdbcTemplate()
                .execute("TRUNCATE core_usuario, mod05_usuario RESTART IDENTITY CASCADE");
        crearUsuario("jefe", "Jefe de sección");
        crearUsuario("ingeniero", "Ingeniero de campo");
        crearUsuario("practicante", "Practicante autorizado");
        crearUsuario("sinrol", null);
        long jefe =
                jdbc.queryForObject(
                        "SELECT id FROM mod05_usuario WHERE usuario = 'jefe'",
                        Map.of(),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod02_cobertura_vegetal "
                        + "(nombre, usuario_creacion_id, usuario_modificacion_id) "
                        + "SELECT nombre, :jefe, :jefe FROM (VALUES "
                        + "('Grass americano'), ('Grass bermuda'), ('Paspalum'), "
                        + "('Vegetación herbácea'), ('Setos'), ('Cobertura xerofítica')) "
                        + "AS valores(nombre)",
                Map.of("jefe", jefe));
    }

    @Test
    void crearEditarYDesactivarAuditaYProtegeLaCoberturaUsada() throws Exception {
        var creada =
                http.perform(
                                post(COBERTURAS)
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"Cubresuelo\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.nombre").value("Cubresuelo"))
                        .andExpect(jsonPath("$.activo").value(true))
                        .andReturn();
        Number coberturaIdNumero =
                com.jayway.jsonpath.JsonPath.read(
                        creada.getResponse().getContentAsString(), "$.id");
        long coberturaId = coberturaIdNumero.longValue();
        long usuarioId =
                jdbc.queryForObject(
                        "SELECT id FROM mod05_usuario WHERE usuario = 'jefe'",
                        Map.of(),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2, "
                        + "tipo_cobertura_id, usuario_creacion_id, usuario_modificacion_id) "
                        + "VALUES ('Jardín de prueba', 'Biblioteca', 'Frente a la biblioteca', "
                        + "220.50, :cobertura, :usuario, :usuario)",
                Map.of("cobertura", coberturaId, "usuario", usuarioId));

        http.perform(
                        patch(COBERTURAS + coberturaId)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"nombre\":\"Cubresuelo campus\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cubresuelo campus"));
        http.perform(
                        patch(COBERTURAS + coberturaId)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        http.perform(get(AREAS + "?q=Jardín de prueba").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.results[0].tipo_cobertura_nombre").value("Cubresuelo campus"));
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT activo FROM mod02_cobertura_vegetal WHERE id = :id",
                                Map.of("id", coberturaId),
                                Boolean.class))
                .isFalse();
        http.perform(
                        get(COBERTURAS)
                                .queryParam("incluir_inactivas", "true")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM core_registro_auditoria "
                                        + "WHERE entidad = 'mod02_cobertura_vegetal' "
                                        + "AND registro_id = :id",
                                Map.of("id", coberturaId),
                                Integer.class))
                .isEqualTo(3);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                jdbc.getJdbcTemplate()
                                        .update(
                                                "DELETE FROM mod02_cobertura_vegetal WHERE id = ?",
                                                coberturaId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void soloJefaturaGestionaYSeRechazaElNombreDuplicado() throws Exception {
        http.perform(
                        post(COBERTURAS)
                                .with(httpBasic("ingeniero", CLAVE))
                                .contentType("application/json")
                                .content("{\"nombre\":\"Tipo nuevo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("sin-permiso"));
        http.perform(
                        post(COBERTURAS)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"nombre\":\"Grass americano\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("cobertura-duplicada"));
        http.perform(get(COBERTURAS).with(httpBasic("sinrol", CLAVE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAltaDeAreaAceptaCoberturaActivaYRechazaUnaInactiva() throws Exception {
        long coberturaId =
                jdbc.queryForObject(
                        "SELECT id FROM mod02_cobertura_vegetal "
                                + "WHERE nombre = 'Grass americano'",
                        Map.of(),
                        Long.class);
        http.perform(
                        post(AREAS)
                                .with(httpBasic("ingeniero", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"nombre\":\"Jardín prueba\",\"lugar\":\"Biblioteca\","
                                                + "\"ubicacion\":\"Frente a la biblioteca\","
                                                + "\"superficie_m2\":180.25,"
                                                + "\"tipo_cobertura_id\":"
                                                + coberturaId
                                                + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo_cobertura_nombre").value("Grass americano"));

        http.perform(
                        patch(COBERTURAS + coberturaId)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"activo\":false}"))
                .andExpect(status().isOk());
        http.perform(
                        post(AREAS)
                                .with(httpBasic("ingeniero", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"nombre\":\"Jardín prueba 2\",\"lugar\":\"Biblioteca\","
                                                + "\"ubicacion\":\"Frente a la biblioteca\","
                                                + "\"superficie_m2\":\"180.25\","
                                                + "\"tipo_cobertura_id\":"
                                                + coberturaId
                                                + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("cobertura-no-encontrada"));
    }

    private void crearUsuario(String nombre, String rol) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario, nombre, password_hash, activo) "
                                + "VALUES (:nombre, :nombre, :clave, true) RETURNING id",
                        Map.of("nombre", nombre, "clave", claves.encode(CLAVE)),
                        Long.class);
        if (rol != null) {
            jdbc.update(
                    "INSERT INTO mod05_usuario_rol (usuario_id, rol) VALUES (:id, :rol)",
                    Map.of("id", id, "rol", rol));
            String codigoRol =
                    switch (rol) {
                        case "Jefe de sección" -> "jefe-seccion";
                        case "Ingeniero de campo" -> "ingeniero-campo";
                        case "Practicante autorizado" -> "practicante";
                        default -> throw new IllegalArgumentException("Rol de prueba desconocido");
                    };
            Long rolId =
                    jdbc.queryForObject(
                            "SELECT id FROM core_rol WHERE codigo = :codigo",
                            Map.of("codigo", codigoRol),
                            Long.class);
            jdbc.update(
                    "INSERT INTO core_usuario (id, nombre_usuario, nombre, credencial_hash,"
                            + " rol_id, activo) VALUES (:id, :usuario, :nombre, :hash, :rol, true)",
                    Map.of(
                            "id", id,
                            "usuario", nombre,
                            "nombre", nombre,
                            "hash", claves.encode(CLAVE),
                            "rol", rolId));
        }
    }
}
