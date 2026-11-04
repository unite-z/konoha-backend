package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class Mod02CriteriosIntegrationTest {

    private static final String CLAVE = "prueba-integration-local";
    private static final String API = "/api/v1/inventario";

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
                .execute("TRUNCATE mod02_ejemplar, mod02_especie_vegetal RESTART IDENTITY CASCADE");
        jdbc.getJdbcTemplate()
                .execute("TRUNCATE core_usuario, mod05_usuario RESTART IDENTITY CASCADE");
        crearUsuario("jefe", "Jefe de sección");
        crearUsuario("ingeniero", "Ingeniero de campo");
        crearUsuario("capataz-a", "Capataz");
        crearUsuario("capataz-b", "Capataz");
    }

    @Test
    void especiesYPlacasDuplicadasSeAdviertenYLosCodigosSonUnicosBajoConcurrencia()
            throws Exception {
        String nombreCientifico = "Schinus molle MOD02 test " + UUID.randomUUID();
        String placaAntigua = "PLACA-" + UUID.randomUUID();
        String especie =
                http.perform(
                        post(API + "/especies")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"nombre_comun\":\"Molle\",\"nombre_cientifico\":\""
                                        + nombreCientifico
                                        + "\",\"forma_biologica\":\"Árbol\"}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.nombre_cientifico").value(nombreCientifico))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
        long especieId = idJson(especie, "$.id");
        http.perform(
                        post(API + "/especies")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"nombre_comun\":\"Molle duplicado\",\"nombre_cientifico\":\""
                                        + nombreCientifico
                                        + "\",\"forma_biologica\":\"Árbol\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("especie-duplicada"));

        String ejemplar =
                "{\"codigo_placa_antigua\":\""
                        + placaAntigua
                        + "\",\"especie_id\":"
                        + especieId
                        + "}";
        String codigoUno = crearEjemplar(ejemplar);
        String codigoDos = crearEjemplar(ejemplar);
        assertThat(codigoUno).isNotEqualTo(codigoDos);

        for (String codigo : List.of(codigoUno, codigoDos)) {
            http.perform(
                            get(API + "/ejemplares")
                                    .queryParam("q", codigo)
                                    .with(httpBasic("jefe", CLAVE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].codigo_placa_antigua").value(placaAntigua))
                    .andExpect(jsonPath("$[0].advertencia_placa_repetida").value(true))
                    .andExpect(jsonPath("$[0].especie_id").value(especieId));
        }
        List<String> codigosConcurrentes = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(6)) {
            var tareas = new ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 12; i++) {
                tareas.add(executor.submit(() -> crearEjemplar("{}")));
            }
            for (var tarea : tareas) codigosConcurrentes.add(tarea.get());
            assertThat(codigosConcurrentes).doesNotHaveDuplicates();
        }
        for (String codigo : codigosConcurrentes) {
            http.perform(
                            get(API + "/ejemplares")
                                    .queryParam("q", codigo)
                                    .with(httpBasic("jefe", CLAVE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].codigo").value(codigo))
                    .andExpect(jsonPath("$[0].codigo_placa_antigua").value(org.hamcrest.Matchers.nullValue()));
        }
    }

    @Test
    void tipoDeUsoSeAsociaAlAreaYPermiteFiltrarlaSinExigirCuartel() throws Exception {
        String tipoUso =
                http.perform(
                                post(API + "/tipos-uso")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"Administrativo\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long tipoUsoId = idJson(tipoUso, "$.id");
        String area = crearArea("Edificio central", tipoUsoId, null);
        long areaId = idJson(area, "$.id");
        assertThat(JsonPath.<Object>read(area, "$.cuartel_id")).isNull();

        http.perform(
                        get(API + "/areas-verdes")
                                .queryParam("tipo_uso_id", String.valueOf(tipoUsoId))
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(areaId))
                .andExpect(jsonPath("$.results[0].tipo_uso_nombre").value("Administrativo"));
        assertThat(JsonPath.<Object>read(area, "$.sector_id")).isNull();
    }

    @Test
    void coberturaAdministradaSigueAsociadaAlAreaAlDesactivarla() throws Exception {
        String cobertura =
                http.perform(
                                post(API + "/coberturas/")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"Cubresuelo MOD02 test\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long coberturaId = idJson(cobertura, "$.id");
        String cuerpo =
                "{\"nombre\":\"Área con cobertura\",\"lugar\":\"Campus\","
                        + "\"ubicacion\":\"Zona central\",\"superficie_m2\":\"85.50\","
                        + "\"tipo_cobertura_id\":"
                        + coberturaId
                        + "}";
        long areaId =
                idJson(
                        http.perform(
                                        post(API + "/areas-verdes/")
                                                .with(httpBasic("jefe", CLAVE))
                                                .contentType("application/json")
                                                .content(cuerpo))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.id");
        http.perform(
                        patch(API + "/coberturas/" + coberturaId)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        http.perform(
                        get(API + "/areas-verdes")
                                .queryParam("q", "Área con cobertura")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value(areaId))
                .andExpect(jsonPath("$.results[0].tipo_cobertura_id").value(coberturaId))
                .andExpect(jsonPath("$.results[0].tipo_cobertura_nombre").value("Cubresuelo MOD02 test"));
    }

    @Test
    void reasignarAreaYSectorConservaAmbosHistoriales() throws Exception {
        long sectorUno = crearSector("Sector Norte");
        long sectorDos = crearSector("Sector Sur");
        long areaId = idJson(crearArea("Jardín Central", null, sectorUno), "$.id");
        String sectores =
                http.perform(
                                get(API + "/sectores")
                                        .queryParam("incluir_inactivos", "true")
                                        .with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Object> superficies =
                JsonPath.read(
                        sectores,
                        "$[?(@.id == " + sectorUno + ")].superficie_m2");
        assertThat(superficies)
                .hasSize(1)
                .first()
                .satisfies(superficie ->
                        assertThat(new BigDecimal(superficie.toString()))
                                .isEqualByComparingTo("85.50"));

        http.perform(
                        patch(API + "/areas-verdes/" + areaId + "/sector")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"sector_id\":" + sectorDos + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector_id").value(sectorDos));
        http.perform(
                        get(API + "/areas-verdes/" + areaId + "/historial-sector")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sector_id").value(sectorDos))
                .andExpect(jsonPath("$[0].fecha_fin").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].sector_id").value(sectorUno))
                .andExpect(jsonPath("$[1].fecha_fin").isNotEmpty());

        long capatazUno = idCore("capataz-a");
        long capatazDos = idCore("capataz-b");
        asignarCapataz(sectorDos, capatazUno);
        asignarCapataz(sectorDos, capatazDos);
        String historialCapataz =
                http.perform(
                                get(API + "/sectores/" + sectorDos + "/historial-capataz")
                                        .with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Map<String, Object>> asignaciones = JsonPath.read(historialCapataz, "$[*]");
        assertThat(asignaciones)
                .extracting(asignacion -> ((Number) asignacion.get("capataz_id")).longValue())
                .containsExactlyInAnyOrder(capatazUno, capatazDos);
        assertThat(asignaciones)
                .filteredOn(asignacion -> ((Number) asignacion.get("capataz_id")).longValue() == capatazDos)
                .singleElement()
                .satisfies(asignacion -> assertThat(asignacion.get("fecha_fin")).isNull());
        assertThat(asignaciones)
                .filteredOn(asignacion -> ((Number) asignacion.get("capataz_id")).longValue() == capatazUno)
                .singleElement()
                .satisfies(asignacion -> assertThat(asignacion.get("fecha_fin")).isNotNull());
    }

    @Test
    void clasificarUsoDeMapaYVincularPoligonoRequierePermisoYSeConserva() throws Exception {
        String tipoUso =
                http.perform(
                                post(API + "/tipos-uso")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"Recreativo MOD02 test\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long tipoUsoId = idJson(tipoUso, "$.id");
        http.perform(
                        put(API + "/mapa/usos-fuente")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"uso_fuente\":\"Categoría mapa MOD02 test\","
                                                + "\"tipo_uso_id\":"
                                                + tipoUsoId
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo_uso_id").value(tipoUsoId));
        http.perform(
                        put(API + "/mapa/usos-fuente")
                                .with(httpBasic("ingeniero", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"uso_fuente\":\"Categoría mapa MOD02 test\","
                                                + "\"tipo_uso_id\":"
                                                + tipoUsoId
                                                + "}"))
                .andExpect(status().isForbidden());

        long areaId = idJson(crearArea("Área mapa MOD02", tipoUsoId, null), "$.id");
        UUID idFuente = UUID.fromString("00000000-0000-0000-0000-000000000172");
        http.perform(
                        put(API + "/mapa/poligonos/" + idFuente)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"area_verde_id\":" + areaId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area_verde_id").value(areaId));
        String poligonos =
                http.perform(get(API + "/mapa/poligonos").with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Number> areasVinculadas =
                JsonPath.read(
                        poligonos,
                        "$[?(@.id_fuente == '" + idFuente + "')].area_verde_id");
        assertThat(areasVinculadas.stream().map(Number::longValue)).containsExactly(areaId);
    }

    private String crearEjemplar(String cuerpo) throws Exception {
        MvcResult resultado =
                http.perform(
                                post(API + "/ejemplares")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content(cuerpo))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.codigo").isNotEmpty())
                        .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.codigo");
    }

    private String crearArea(String nombre, Long tipoUsoId, Long sectorId) throws Exception {
        StringBuilder cuerpo =
                new StringBuilder(
                        "{\"nombre\":\""
                                + nombre
                                + "\",\"lugar\":\"Campus\",\"ubicacion\":\"Zona central\","
                                + "\"superficie_m2\":\"85.50\"");
        if (tipoUsoId != null) cuerpo.append(",\"tipo_uso_id\":").append(tipoUsoId);
        if (sectorId != null) cuerpo.append(",\"sector_id\":").append(sectorId);
        cuerpo.append('}');
        return http.perform(
                        post(API + "/areas-verdes/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(cuerpo.toString()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long crearSector(String nombre) throws Exception {
        String cuerpo =
                http.perform(
                                post(API + "/sectores")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"" + nombre + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return idJson(cuerpo, "$.id");
    }

    private long idJson(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private void asignarCapataz(long sectorId, long capatazId) throws Exception {
        http.perform(
                        patch(API + "/sectores/" + sectorId + "/capataz")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"capataz_id\":" + capatazId + "}"))
                .andExpect(status().isOk());
    }

    private long idCore(String nombreUsuario) {
        return jdbc.queryForObject(
                "SELECT id FROM core_usuario WHERE nombre_usuario = :nombre",
                Map.of("nombre", nombreUsuario),
                Long.class);
    }

    private void crearUsuario(String nombre, String rol) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario, nombre, password_hash, activo) "
                                + "VALUES (:nombre, :nombre, :clave, true) RETURNING id",
                        Map.of("nombre", nombre, "clave", claves.encode(CLAVE)),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod05_usuario_rol (usuario_id, rol) VALUES (:id, :rol)",
                Map.of("id", id, "rol", rol));
        String codigoRol =
                switch (rol) {
                    case "Jefe de sección" -> "jefe-seccion";
                    case "Ingeniero de campo" -> "ingeniero-campo";
                    case "Capataz" -> "capataz";
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
