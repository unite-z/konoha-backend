package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

/** AV-430: contorno dibujado al crear un área, validación y superficie en UTM 18S. */
@SpringBootTest
@AutoConfigureMockMvc
class AreaContornoIntegrationTest {

    private static final String CLAVE = "prueba-contorno-local";
    private static final String AREAS = "/api/v1/inventario/areas-verdes";
    private static final double LADO = 0.0002; // unos 22 m

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry propiedades) {
        BaseDePruebas.registrar(propiedades);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    double lon;
    double lat;

    @BeforeEach
    void preparar() {
        jdbc.getJdbcTemplate().execute("TRUNCATE mod02_area_verde RESTART IDENTITY CASCADE");
        jdbc.getJdbcTemplate().execute("DELETE FROM mod02_mapa_geometria WHERE origen = 'dibujo'");
        jdbc.getJdbcTemplate()
                .execute("TRUNCATE core_usuario, mod05_usuario RESTART IDENTITY CASCADE");
        crearUsuario("jefe", "Jefe de sección", "jefe-seccion");
        // Registra áreas, pero no gestiona el catálogo (no vincula polígonos del mapa).
        crearUsuario("ingeniero", "Ingeniero de campo", "ingeniero-campo");
        // Punto interior del polígono más grande de la capa: está dentro del campus.
        var punto =
                jdbc.queryForMap(
                        "SELECT ST_X(p) lon, ST_Y(p) lat FROM (SELECT ST_PointOnSurface(geometria)"
                                + " p FROM mod02_mapa_geometria WHERE origen = 'capa' ORDER BY"
                                + " ST_Area(geometria) DESC LIMIT 1) x",
                        Map.of());
        lon = ((Number) punto.get("lon")).doubleValue();
        lat = ((Number) punto.get("lat")).doubleValue();
    }

    /**
     * Los contornos dibujados se solapan con la capa del campus: si quedaran en la base, otras
     * suites verían dos polígonos donde esperan uno (p. ej. {@code nombre_poligono}).
     */
    @AfterEach
    void limpiar() {
        jdbc.getJdbcTemplate().execute("TRUNCATE mod02_area_verde RESTART IDENTITY CASCADE");
        jdbc.getJdbcTemplate().execute("DELETE FROM mod02_mapa_geometria WHERE origen = 'dibujo'");
    }

    @Test
    void contornoValidoCalculaSuperficieYApareceEnLaGeometria() throws Exception {
        String cuerpo = http(contorno(0, 0, LADO), null);
        String respuesta =
                http.perform(
                                post(AREAS)
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content(cuerpo))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.contorno.type").value("Polygon"))
                        .andExpect(jsonPath("$.poligono_mapa_id").isNotEmpty())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        BigDecimal devuelta = new BigDecimal(JsonPath.<String>read(respuesta, "$.superficie_m2"));
        BigDecimal esperada =
                jdbc.queryForObject(
                        "SELECT round(ST_Area(ST_Transform(geometria, 32718))::numeric, 2) FROM"
                                + " mod02_mapa_geometria WHERE id_fuente = CAST(:id AS uuid)",
                        Map.of("id", JsonPath.<String>read(respuesta, "$.poligono_mapa_id")),
                        BigDecimal.class);
        // Tolerancia de test: 0,01 m² (redondeo a dos decimales del servidor).
        assertThat(devuelta.subtract(esperada).abs()).isLessThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(devuelta).isBetween(new BigDecimal("400"), new BigDecimal("600"));

        String capa =
                http.perform(
                                get("/api/v1/inventario/mapa/geometria")
                                        .with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String idFuente = JsonPath.read(respuesta, "$.poligono_mapa_id");
        java.util.List<Map<String, Object>> nuevos =
                JsonPath.read(capa, "$.features[?(@.properties.mapa_id == '" + idFuente + "')]");
        assertThat(nuevos).hasSize(1);
        assertThat(nuevos.get(0).get("properties").toString())
                .contains("Nombre=Área dibujada")
                .contains("origen=dibujo");
    }

    @Test
    void contornoNoSimpleOFueraDelCampusOSolapadoDa422() throws Exception {
        // Corbata: los lados se cruzan.
        String corbata =
                "{\"type\":\"Polygon\",\"coordinates\":[[["
                        + lon
                        + ","
                        + lat
                        + "],["
                        + (lon + LADO)
                        + ","
                        + (lat + LADO)
                        + "],["
                        + (lon + LADO)
                        + ","
                        + lat
                        + "],["
                        + lon
                        + ","
                        + (lat + LADO)
                        + "],["
                        + lon
                        + ","
                        + lat
                        + "]]]}";
        crear(http(corbata, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("contorno-invalido"));

        String abierto =
                "{\"type\":\"Polygon\",\"coordinates\":[[["
                        + lon
                        + ","
                        + lat
                        + "],["
                        + (lon + LADO)
                        + ","
                        + lat
                        + "],["
                        + (lon + LADO)
                        + ","
                        + (lat + LADO)
                        + "],["
                        + lon
                        + ","
                        + (lat + LADO)
                        + "]]]}";
        crear(http(abierto, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("contorno-no-cerrado"));

        crear(http(contorno(5, 5, LADO), null)) // a cientos de kilómetros del campus
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("contorno-fuera-del-campus"));

        crear(http(contorno(0, 0, LADO), null)).andExpect(status().isCreated());
        crear(http(contorno(LADO / 2, 0, LADO), null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("contorno-solapado"))
                .andExpect(
                        jsonPath("$.detalle")
                                .value(org.hamcrest.Matchers.containsString("Área dibujada")));
        // Contiguo, sin solape: se admite.
        crear(http(contorno(LADO, 0, LADO), null)).andExpect(status().isCreated());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM mod02_area_verde", Map.of(), Integer.class))
                .isEqualTo(2);
    }

    @Test
    void sinContornoSigueComoHoy() throws Exception {
        crear(http(null, "85.50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.superficie_m2").value("85.50"))
                .andExpect(jsonPath("$.contorno").doesNotExist());
        // «"contorno": null» es lo mismo que no enviarlo (Jackson lo entrega como NullNode).
        crear(http("null", "12.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.superficie_m2").value("12.00"))
                .andExpect(jsonPath("$.poligono_mapa_id").doesNotExist());
        crear(http("null", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("superficie-obligatoria"));
        crear(http(null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("superficie-obligatoria"));
        // Quien registra áreas sin gestionar el catálogo sigue pudiendo darlas de alta sin mapa.
        crear(http(null, "40.00"), "ingeniero").andExpect(status().isCreated());
    }

    @Test
    void dibujarElContornoExigeElPermisoDeVincularPoligonos() throws Exception {
        crear(http(contorno(0, 0, LADO), null), "ingeniero")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("sin-permiso"));
        assertThat(contar("SELECT count(*) FROM mod02_area_verde")).isZero();
        assertThat(contar("SELECT count(*) FROM mod02_mapa_geometria WHERE origen = 'dibujo'"))
                .isZero();
    }

    @Test
    void elContornoQuedaVinculadoAlAreaEnElMapa() throws Exception {
        String respuesta =
                crear(http(contorno(0, 0, LADO), null))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        int areaId = JsonPath.read(respuesta, "$.id");
        String idFuente = JsonPath.read(respuesta, "$.poligono_mapa_id");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT g.origen || ':' || p.area_verde_id FROM"
                                        + " mod02_mapa_geometria g JOIN mod02_mapa_poligono p"
                                        + " USING (id_fuente) WHERE id_fuente = CAST(:id AS uuid)",
                                Map.of("id", idFuente),
                                String.class))
                .isEqualTo("dibujo:" + areaId);
        // Un punto en el centro del contorno cae en el área nueva.
        http.perform(
                        get("/api/v1/inventario/mapa/ubicacion")
                                .with(httpBasic("jefe", CLAVE))
                                .param("latitud", String.valueOf(lat + LADO / 2))
                                .param("longitud", String.valueOf(lon + LADO / 2))
                                .param("area_verde_id", String.valueOf(areaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("dentro"));
    }

    @Test
    void contornoYPoligonoALaVezDa422YElPoligonoDeLaCapaCuentaParaElSolape() throws Exception {
        String poligono =
                jdbc.queryForObject(
                        "SELECT id_fuente::text FROM mod02_mapa_geometria WHERE origen = 'capa'"
                                + " ORDER BY ST_Area(geometria) DESC LIMIT 1",
                        Map.of(),
                        String.class);
        crear(http(contorno(0, 0, LADO), null, poligono))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("contorno-y-poligono"));
        // Con un polígono existente, la respuesta también trae su contorno.
        crear(http(null, "900.00", poligono))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poligono_mapa_id").value(poligono))
                .andExpect(jsonPath("$.contorno.type").isNotEmpty());
        // El contorno dibujado cae dentro de ese polígono, ya vinculado a un área activa.
        crear(http(contorno(0, 0, LADO), null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("contorno-solapado"));
        assertThat(contar("SELECT count(*) FROM mod02_area_verde")).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions crear(String cuerpo)
            throws Exception {
        return crear(cuerpo, "jefe");
    }

    private org.springframework.test.web.servlet.ResultActions crear(String cuerpo, String usuario)
            throws Exception {
        return http.perform(
                post(AREAS)
                        .with(httpBasic(usuario, CLAVE))
                        .contentType("application/json")
                        .content(cuerpo));
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    private void crearUsuario(String nombre, String rol, String codigoRol) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario, nombre, password_hash, activo) VALUES"
                                + " (:nombre, :nombre, :clave, true) RETURNING id",
                        Map.of("nombre", nombre, "clave", claves.encode(CLAVE)),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod05_usuario_rol (usuario_id, rol) VALUES (:id, :rol)",
                Map.of("id", id, "rol", rol));
        jdbc.update(
                "INSERT INTO core_usuario (id, nombre_usuario, nombre, credencial_hash, rol_id,"
                        + " activo) VALUES (:id, :nombre, :nombre, :hash, (SELECT id FROM core_rol"
                        + " WHERE codigo = :rol), true)",
                Map.of("id", id, "nombre", nombre, "hash", claves.encode(CLAVE), "rol", codigoRol));
    }

    /** Cuadrado de {@code lado} grados con la esquina inferior izquierda desplazada. */
    private String contorno(double dLon, double dLat, double lado) {
        double x = lon + dLon;
        double y = lat + dLat;
        return "{\"type\":\"Polygon\",\"coordinates\":[[["
                + x
                + ","
                + y
                + "],["
                + (x + lado)
                + ","
                + y
                + "],["
                + (x + lado)
                + ","
                + (y + lado)
                + "],["
                + x
                + ","
                + (y + lado)
                + "],["
                + x
                + ","
                + y
                + "]]]}";
    }

    private String http(String contorno, String superficie) {
        return http(contorno, superficie, null);
    }

    private String http(String contorno, String superficie, String poligonoMapaId) {
        return "{\"nombre\":\"Área dibujada\",\"lugar\":\"Campus\",\"ubicacion\":\"Zona de prueba\""
                + (superficie == null ? "" : ",\"superficie_m2\":\"" + superficie + "\"")
                + (contorno == null ? "" : ",\"contorno\":" + contorno)
                + (poligonoMapaId == null ? "" : ",\"poligono_mapa_id\":\"" + poligonoMapaId + "\"")
                + "}";
    }
}
