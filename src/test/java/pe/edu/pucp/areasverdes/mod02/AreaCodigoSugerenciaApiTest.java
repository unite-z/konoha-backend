package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** AV-443 (RD-12): código automático e inmutable del área y sugerencia previa al alta. */
@SpringBootTest
@AutoConfigureMockMvc
class AreaCodigoSugerenciaApiTest {

    private static final String CLAVE = "prueba-integration-local";
    private static final String AREAS = "/api/v1/inventario/areas-verdes";
    private static final String CUADRADO =
            "{\"type\":\"Polygon\",\"coordinates\":[[[-77.0800,-12.0700],[-77.0799,-12.0700],"
                    + "[-77.0799,-12.0699],[-77.0800,-12.0699],[-77.0800,-12.0700]]]}";
    // Autointersección en «moño»: cerrado pero no simple.
    private static final String MONO =
            "{\"type\":\"Polygon\",\"coordinates\":[[[-77.0800,-12.0700],[-77.0799,-12.0699],"
                    + "[-77.0799,-12.0700],[-77.0800,-12.0699],[-77.0800,-12.0700]]]}";

    // Anillo sin cerrar: el último vértice no repite el primero.
    private static final String ABIERTO =
            "{\"type\":\"Polygon\",\"coordinates\":[[[-77.0800,-12.0700],[-77.0799,-12.0700],"
                    + "[-77.0799,-12.0699],[-77.0800,-12.0699]]]}";

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry propiedades) {
        BaseDePruebas.registrar(propiedades);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    @BeforeEach
    void prepararBase() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_usuario, mod05_usuario, mod02_sector, mod02_mapa_poligono"
                                + " RESTART IDENTITY CASCADE");
        crearUsuario("jefe", "Jefe de sección", "jefe-seccion");
        crearUsuario("capataz", "Capataz", "capataz");
    }

    @Test
    void cadaAreaNuevaRecibeCodigoCorrelativoYElPatchNoLoCambia() throws Exception {
        String primera = crearArea("Jardín uno", null);
        String segunda = crearArea("Jardín dos", null);
        assertThat((String) JsonPath.read(primera, "$.codigo")).isEqualTo("AREA-0001");
        assertThat((String) JsonPath.read(segunda, "$.codigo")).isEqualTo("AREA-0002");
        long id = ((Number) JsonPath.read(segunda, "$.id")).longValue();

        http.perform(
                        patch(AREAS + "/" + id)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"codigo\":\"X-1\"}"))
                .andExpect(status().isBadRequest());
        http.perform(
                        patch(AREAS + "/" + id)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"nombre\":\"Jardín dos renombrado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("AREA-0002"));
        http.perform(get(AREAS + "/" + id).with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.codigo").value("AREA-0002"));
    }

    @Test
    void laBaseRechazaCambiarElCodigo() {
        crearAreaSql("Jardín base");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE mod02_area_verde SET codigo = 'OTRO-1'", Map.of()))
                .hasMessageContaining("no se puede cambiar");
    }

    @Test
    void codigosUnicosBajoConcurrencia() throws Exception {
        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            List<Callable<String>> tareas = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                String nombre = "Área concurrente " + i;
                tareas.add(() -> JsonPath.read(crearArea(nombre, null), "$.codigo"));
            }
            Set<String> codigos = new java.util.HashSet<>();
            for (Future<String> f : pool.invokeAll(tareas)) {
                codigos.add(f.get());
            }
            assertThat(codigos).hasSize(hilos);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void laMigracionAsignaCodigoPorDefectoALasAreasInsertadasSinEl() {
        crearAreaSql("Alta de la versión anterior");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT codigo FROM mod02_area_verde", Map.of(), String.class))
                .isEqualTo("AREA-0001");
    }

    @Test
    void sugerenciaSinVecinosConSectorVaSinSector() throws Exception {
        http.perform(
                        get(AREAS + "/sugerencia")
                                .queryParam("contorno", CUADRADO)
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("AREA-0001"))
                .andExpect(
                        jsonPath("$.superficie_m2")
                                .value(org.hamcrest.Matchers.matchesRegex("\\d+\\.\\d{2}")))
                .andExpect(jsonPath("$.sector_id").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sector_nombre").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void sugerenciaTomaElSectorDelAreaMasCercanaYElCodigoSiguiente() throws Exception {
        long norte = crearSector("Sector Norte");
        long sur = crearSector("Sector Sur");
        long baja = crearSector("Sector de un área dada de baja");
        // Vecina cercana (norte) y vecina lejana (sur); más cerca aún hay una sin sector y otra
        // dada de baja, que no cuentan.
        vincularGeometria(crearAreaSql("Cerca del contorno"), norte, -77.0798, -12.0700);
        vincularGeometria(crearAreaSql("Lejos del contorno"), sur, -77.0700, -12.0600);
        vincularGeometria(crearAreaSql("Pegada sin sector"), null, -77.07995, -12.07005);
        long inactiva = crearAreaSql("Pegada pero inactiva");
        vincularGeometria(inactiva, baja, -77.07998, -12.07002);
        jdbc.update(
                "UPDATE mod02_area_verde SET activo = false WHERE id = :id",
                Map.of("id", inactiva));

        http.perform(
                        get(AREAS + "/sugerencia")
                                .queryParam("contorno", CUADRADO)
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("AREA-0005"))
                .andExpect(jsonPath("$.sector_id").value(norte))
                .andExpect(jsonPath("$.sector_nombre").value("Sector Norte"))
                .andExpect(jsonPath("$.distancia_sector_m").isNumber());
    }

    @Test
    void sugerenciaIgnoraLasGeometriasVacias() throws Exception {
        long sector = crearSector("Sector con geometría vacía");
        long area = crearAreaSql("Área sin forma");
        UUID fuente = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO mod02_mapa_geometria (id_fuente, geometria) VALUES (:f,"
                        + " ST_GeomFromText('POLYGON EMPTY', 4326))",
                Map.of("f", fuente));
        jdbc.update(
                "INSERT INTO mod02_mapa_poligono (id_fuente, area_verde_id) VALUES (:f, :a)",
                Map.of("f", fuente, "a", area));
        jdbc.update(
                "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, usuario_id)"
                        + " VALUES (:a, :s, :u)",
                Map.of("a", area, "s", sector, "u", idCore("jefe")));

        http.perform(
                        get(AREAS + "/sugerencia")
                                .queryParam("contorno", CUADRADO)
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector_id").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(
                        jsonPath("$.distancia_sector_m").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void elAltaRechazaQueElClienteIndiqueElCodigo() throws Exception {
        http.perform(
                        post(AREAS + "/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"nombre\":\"Jardín\",\"lugar\":\"Campus\","
                                                + "\"ubicacion\":\"Zona\",\"superficie_m2\":"
                                                + "\"10\",\"codigo\":\"AREA-9999\"}"))
                .andExpect(status().isBadRequest());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM mod02_area_verde", Map.of(), Long.class))
                .isZero();
    }

    @Test
    void sugerenciaNoReservaElCodigo() throws Exception {
        for (int i = 0; i < 2; i++) {
            http.perform(
                            get(AREAS + "/sugerencia")
                                    .queryParam("contorno", CUADRADO)
                                    .with(httpBasic("jefe", CLAVE)))
                    .andExpect(jsonPath("$.codigo").value("AREA-0001"));
        }
        assertThat((String) JsonPath.read(crearArea("Jardín real", null), "$.codigo"))
                .isEqualTo("AREA-0001");
    }

    @Test
    void sugerenciaValidaElContornoYLosPermisos() throws Exception {
        http.perform(
                        get(AREAS + "/sugerencia")
                                .queryParam("contorno", "no es json")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("contorno-no-valido"));
        http.perform(
                        get(AREAS + "/sugerencia")
                                .queryParam(
                                        "contorno", "{\"type\":\"Point\",\"coordinates\":[0,0]}")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isBadRequest());
        http.perform(
                        get(AREAS + "/sugerencia")
                                .queryParam("contorno", MONO)
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("contorno-no-valido"));
        http.perform(
                        get(AREAS + "/sugerencia")
                                .queryParam("contorno", ABIERTO)
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isUnprocessableEntity());
        // Una Feature con el polígono dentro también vale.
        http.perform(
                        get(AREAS + "/sugerencia")
                                .queryParam(
                                        "contorno",
                                        "{\"type\":\"Feature\",\"properties\":{},\"geometry\":"
                                                + CUADRADO
                                                + "}")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk());
        http.perform(get(AREAS + "/sugerencia").queryParam("contorno", CUADRADO))
                .andExpect(status().isUnauthorized());
        http.perform(
                        get(AREAS + "/sugerencia")
                                .queryParam("contorno", CUADRADO)
                                .with(httpBasic("capataz", CLAVE)))
                .andExpect(status().isForbidden());
    }

    private String crearArea(String nombre, Long sectorId) throws Exception {
        String cuerpo =
                "{\"nombre\":\""
                        + nombre
                        + "\",\"lugar\":\"Campus\",\"ubicacion\":\"Zona central\","
                        + "\"superficie_m2\":\"85.50\""
                        + (sectorId == null ? "" : ",\"sector_id\":" + sectorId)
                        + "}";
        return http.perform(
                        post(AREAS + "/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** Inserta sin indicar código, como hacía la versión anterior de la api. */
    private long crearAreaSql(String nombre) {
        long jefe = idCore("jefe");
        return jdbc.queryForObject(
                "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                        + " usuario_creacion_id, usuario_modificacion_id) VALUES (:n, 'Campus',"
                        + " 'Zona', 100, :u, :u) RETURNING id",
                Map.of("n", nombre, "u", jefe),
                Long.class);
    }

    private long crearSector(String nombre) {
        return jdbc.queryForObject(
                "INSERT INTO mod02_sector (nombre, usuario_creacion_id, usuario_modificacion_id)"
                        + " VALUES (:n, :u, :u) RETURNING id",
                Map.of("n", nombre, "u", idCore("jefe")),
                Long.class);
    }

    /** Vincula al área un cuadrado de 0,0001 grados con esquina en (lon, lat) y su sector. */
    private void vincularGeometria(long areaId, Long sectorId, double lon, double lat) {
        UUID fuente = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO mod02_mapa_geometria (id_fuente, geometria) VALUES (:f,"
                        + " ST_MakeEnvelope(:x, :y, :x2, :y2, 4326))",
                Map.of("f", fuente, "x", lon, "y", lat, "x2", lon + 0.0001, "y2", lat + 0.0001));
        jdbc.update(
                "INSERT INTO mod02_mapa_poligono (id_fuente, area_verde_id) VALUES (:f, :a)",
                Map.of("f", fuente, "a", areaId));
        if (sectorId != null) {
            jdbc.update(
                    "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id,"
                            + " usuario_id) VALUES (:a, :s, :u)",
                    Map.of("a", areaId, "s", sectorId, "u", idCore("jefe")));
        }
    }

    private long idCore(String nombre) {
        return jdbc.queryForObject(
                "SELECT id FROM core_usuario WHERE nombre_usuario = :n",
                Map.of("n", nombre),
                Long.class);
    }

    private void crearUsuario(String nombre, String rol, String codigoRol) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario, nombre, password_hash, activo) "
                                + "VALUES (:nombre, :nombre, :clave, true) RETURNING id",
                        Map.of("nombre", nombre, "clave", claves.encode(CLAVE)),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod05_usuario_rol (usuario_id, rol) VALUES (:id, :rol)",
                Map.of("id", id, "rol", rol));
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
