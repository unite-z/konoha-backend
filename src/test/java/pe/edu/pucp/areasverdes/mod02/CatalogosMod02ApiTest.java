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
import pe.edu.pucp.areasverdes.mod05.Usuario;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogosMod02ApiTest {

    private static final String CLAVE = "prueba-catalogos-local";

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
        jdbc.getJdbcTemplate().execute("TRUNCATE mod05_usuario RESTART IDENTITY CASCADE");
        crearUsuario("jefe", Usuario.JEFE);
        crearUsuario("capataz-a", Usuario.CAPATAZ);
        crearUsuario("capataz-b", Usuario.CAPATAZ);
        long jefe = idUsuario("jefe");
        jdbc.update(
                "INSERT INTO mod02_cobertura_vegetal (nombre, usuario_creacion_id,"
                        + " usuario_modificacion_id) VALUES ('Grass demo', :jefe, :jefe)",
                Map.of("jefe", jefe));
        jdbc.update(
                "INSERT INTO mod02_tipo_uso (nombre, usuario_creacion_id, usuario_modificacion_id) "
                        + "VALUES ('Recreativo', :jefe, :jefe), ('Institucional', :jefe, :jefe), "
                        + "('Ornamental', :jefe, :jefe), ('Deportivo', :jefe, :jefe)",
                Map.of("jefe", jefe));
    }

    @Test
    void creaAreasConCatalogosFiltraYConservaHistorialDeSectorYCapataz() throws Exception {
        String sectorJson =
                http.perform(
                                post("/api/v1/inventario/sectores")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content(
                                                "{\"nombre\":\"Sector"
                                                        + " norte\",\"atencion_demanda\":true}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.atencion_demanda").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long sectorId =
                ((Number) com.jayway.jsonpath.JsonPath.read(sectorJson, "$.id")).longValue();
        long usoId =
                jdbc.queryForObject(
                        "SELECT id FROM mod02_tipo_uso WHERE nombre = 'Recreativo'",
                        Map.of(),
                        Long.class);
        long coberturaId =
                jdbc.queryForObject(
                        "SELECT id FROM mod02_cobertura_vegetal WHERE nombre = 'Grass demo'",
                        Map.of(),
                        Long.class);
        long cuartelId =
                jdbc.queryForObject(
                        "INSERT INTO mod02_cuartel (numero, nombre) VALUES ('C-1', 'Bosque"
                                + " antiguo') RETURNING id",
                        Map.of(),
                        Long.class);
        long areaId =
                ((Number)
                                com.jayway.jsonpath.JsonPath.read(
                                        http.perform(
                                                        post("/api/v1/inventario/areas-verdes/")
                                                                .with(httpBasic("jefe", CLAVE))
                                                                .contentType("application/json")
                                                                .content(
                                                                        "{\"nombre\":\"Jardín"
                                                                            + " norte\",\"lugar\":\"Biblioteca\",\"ubicacion\":\"Frente"
                                                                            + " a la"
                                                                            + " biblioteca\",\"superficie_m2\":180.25,"
                                                                            + "\"tipo_cobertura_id\":"
                                                                                + coberturaId
                                                                                + ",\"tipo_uso_id\":"
                                                                                + usoId
                                                                                + ",\"cuartel_id\":"
                                                                                + cuartelId
                                                                                + ",\"sector_id\":"
                                                                                + sectorId
                                                                                + "}"))
                                                .andExpect(status().isCreated())
                                                .andReturn()
                                                .getResponse()
                                                .getContentAsString(),
                                        "$.id"))
                        .longValue();

        http.perform(
                        get("/api/v1/inventario/areas-verdes/")
                                .param("sector_id", String.valueOf(sectorId))
                                .param("tipo_uso_id", String.valueOf(usoId))
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].tipo_uso_nombre").value("Recreativo"))
                .andExpect(jsonPath("$.results[0].cuartel_numero").value("C-1"))
                .andExpect(jsonPath("$.results[0].sector_nombre").value("Sector norte"));

        http.perform(
                        patch("/api/v1/inventario/sectores/" + sectorId + "/capataz")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"capataz_id\":" + idUsuario("capataz-a") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capataz_nombre").value("capataz-a"));
        http.perform(
                        patch("/api/v1/inventario/sectores/" + sectorId + "/capataz")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"capataz_id\":" + idUsuario("capataz-b") + "}"))
                .andExpect(status().isOk());
        http.perform(
                        get("/api/v1/inventario/sectores/" + sectorId + "/historial-capataz")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        http.perform(
                        patch("/api/v1/inventario/areas-verdes/" + areaId + "/sector/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"sector_id\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector_id").doesNotExist());
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM mod02_historial_area_sector WHERE"
                                        + " area_verde_id = :id",
                                Map.of("id", areaId),
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void unTipoDeUsoReferenciadoSePuedeDesactivarSinEliminarlo() throws Exception {
        long id =
                jdbc.queryForObject(
                        "SELECT id FROM mod02_tipo_uso WHERE nombre = 'Recreativo'",
                        Map.of(),
                        Long.class);
        http.perform(
                        patch("/api/v1/inventario/tipos-uso/" + id)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        org.assertj.core.api.Assertions.assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM mod02_tipo_uso WHERE id = :id",
                                Map.of("id", id),
                                Integer.class))
                .isEqualTo(1);
    }

    private void crearUsuario(String usuario, String rol) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario (usuario, nombre, password_hash, activo) "
                                + "VALUES (:usuario, :usuario, :hash, true) RETURNING id",
                        Map.of("usuario", usuario, "hash", claves.encode(CLAVE)),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod05_usuario_rol (usuario_id, rol) VALUES (:id, :rol)",
                Map.of("id", id, "rol", rol));
    }

    private long idUsuario(String usuario) {
        return jdbc.queryForObject(
                "SELECT id FROM mod05_usuario WHERE usuario = :usuario",
                Map.of("usuario", usuario),
                Long.class);
    }
}
