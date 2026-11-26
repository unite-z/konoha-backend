package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
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

/** AV-445: redibujar el contorno de un área existente, con impacto, confirmación e historial. */
@SpringBootTest
@AutoConfigureMockMvc
public class ContornoAreaApiTest {

    private static final String CLAVE = "prueba-integration-local";
    private static final String API = "/api/v1/inventario";
    private static final String BOSQUE = "d52663f2-83ee-5d20-84f7-65e644611c2a";

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry propiedades) {
        BaseDePruebas.registrar(propiedades);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    private double lon;
    private double lat;
    private String capaOriginal;
    private String bosqueOriginal;

    /** Matriz de permisos de {@code ContornoAreaController} para el contrato de rutas. */
    public static List<String> endpointsCubiertos() {
        String base = API + "/areas-verdes/{id}/contorno";
        return List.of(
                "GET " + base,
                "PUT " + base,
                "POST " + base + "/impacto",
                "GET " + base + "/historial");
    }

    @BeforeEach
    void preparar() {
        jdbc.getJdbcTemplate()
                .execute("TRUNCATE mod02_ejemplar, mod02_especie_vegetal RESTART IDENTITY CASCADE");
        jdbc.getJdbcTemplate()
                .execute("TRUNCATE core_usuario, mod05_usuario RESTART IDENTITY CASCADE");
        crearUsuario("jefe", "Jefe de sección", "jefe-seccion");
        crearUsuario("capataz-a", "Capataz", "capataz");
        Map<String, Object> centro =
                jdbc.queryForMap(
                        "SELECT ST_X(p) lon, ST_Y(p) lat FROM (SELECT ST_PointOnSurface(geometria)"
                                + " p FROM mod02_mapa_geometria WHERE id_fuente ="
                                + " CAST(:id AS uuid)) c",
                        Map.of("id", BOSQUE));
        lon = ((Number) centro.get("lon")).doubleValue();
        lat = ((Number) centro.get("lat")).doubleValue();
        capaOriginal =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT geojson::text FROM mod02_mapa_capa WHERE unica",
                                String.class);
        bosqueOriginal =
                jdbc.queryForObject(
                        "SELECT ST_AsEWKT(geometria) FROM mod02_mapa_geometria WHERE id_fuente ="
                                + " CAST(:id AS uuid)",
                        Map.of("id", BOSQUE),
                        String.class);
    }

    /** El redibujado toca la capa y la geometría del bosque: se dejan como estaban. */
    @AfterEach
    void limpiar() {
        jdbc.update(
                "UPDATE mod02_mapa_capa SET geojson = CAST(:capa AS jsonb) WHERE unica",
                Map.of("capa", capaOriginal));
        jdbc.update(
                "UPDATE mod02_mapa_geometria SET geometria = ST_GeomFromEWKT(:ewkt) WHERE"
                        + " id_fuente = CAST(:id AS uuid)",
                Map.of("ewkt", bosqueOriginal, "id", BOSQUE));
        jdbc.update(
                "DELETE FROM mod02_mapa_poligono p USING mod02_area_verde a WHERE a.id ="
                        + " p.area_verde_id AND a.nombre LIKE '% contorno'",
                Map.of());
        jdbc.getJdbcTemplate()
                .execute(
                        "DELETE FROM mod02_mapa_geometria WHERE id_fuente NOT IN (SELECT"
                                + " CAST(f->'properties'->>'mapa_id' AS uuid) FROM mod02_mapa_capa,"
                                + " jsonb_array_elements(geojson->'features') f)");
    }

    @Test
    void exigeSesionYPermisoDeGestion() throws Exception {
        long area = crearArea("Área permisos contorno");
        String cuerpo = "{\"contorno\":" + cuadrado(0, 0, 0.0001) + "}";
        http.perform(get(API + "/areas-verdes/" + area + "/contorno"))
                .andExpect(status().isUnauthorized());
        http.perform(
                        get(API + "/areas-verdes/" + area + "/contorno")
                                .with(httpBasic("capataz-a", CLAVE)))
                .andExpect(status().isOk());
        http.perform(
                        post(API + "/areas-verdes/" + area + "/contorno/impacto")
                                .with(httpBasic("capataz-a", CLAVE))
                                .contentType("application/json")
                                .content(cuerpo))
                .andExpect(status().isForbidden());
        http.perform(
                        put(API + "/areas-verdes/" + area + "/contorno")
                                .with(httpBasic("capataz-a", CLAVE))
                                .contentType("application/json")
                                .content(cuerpo))
                .andExpect(status().isForbidden());
        http.perform(get(API + "/areas-verdes/999999/contorno").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazaContornosInvalidos() throws Exception {
        long area = crearArea("Área inválida contorno");
        String ruta = API + "/areas-verdes/" + area + "/contorno";
        String corbata =
                "{\"type\":\"Polygon\",\"coordinates\":[[[%s,%s],[%s,%s],[%s,%s],[%s,%s],[%s,%s]]]}"
                        .formatted(
                                lon,
                                lat,
                                lon + 0.0001,
                                lat + 0.0001,
                                lon + 0.0001,
                                lat,
                                lon,
                                lat + 0.0001,
                                lon,
                                lat);
        String abierto =
                "{\"type\":\"Polygon\",\"coordinates\":[[[%s,%s],[%s,%s],[%s,%s],[%s,%s]]]}"
                        .formatted(
                                lon,
                                lat,
                                lon + 0.0001,
                                lat,
                                lon + 0.0001,
                                lat + 0.0001,
                                lon,
                                lat + 0.0001);
        String punto = "{\"type\":\"Point\",\"coordinates\":[" + lon + "," + lat + "]}";
        String lejos = cuadrado(10, 10, 0.0001);
        String fueraDeRango = cuadrado(500, 10, 0.0001);
        Map<String, String> casos =
                Map.of(
                        corbata, "contorno-invalido",
                        abierto, "contorno-no-cerrado",
                        punto, "contorno-invalido",
                        lejos, "contorno-fuera-del-campus",
                        fueraDeRango, "contorno-invalido");
        for (var caso : casos.entrySet()) {
            http.perform(
                            put(ruta)
                                    .with(httpBasic("jefe", CLAVE))
                                    .contentType("application/json")
                                    .content("{\"contorno\":" + caso.getKey() + "}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value(caso.getValue()));
            http.perform(
                            post(ruta + "/impacto")
                                    .with(httpBasic("jefe", CLAVE))
                                    .contentType("application/json")
                                    .content("{\"contorno\":" + caso.getKey() + "}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value(caso.getValue()));
        }
        http.perform(
                        put(ruta)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isBadRequest());
        http.perform(get(ruta).with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.contorno").isEmpty())
                .andExpect(jsonPath("$.poligonos").value(0));
    }

    @Test
    void rechazaElSolapeConOtraAreaMasAllaDeLaTolerancia() throws Exception {
        long vecina = crearArea("Área vecina contorno");
        long area = crearArea("Área solapada contorno");
        redibujar(vecina, cuadrado(0, 0, 0.0001), null).andExpect(status().isOk());
        redibujar(area, cuadrado(0.0001, 0, 0.0001), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("contorno-solapado"))
                .andExpect(
                        jsonPath("$.detalle")
                                .value(org.hamcrest.Matchers.containsString("vecina")));
        redibujar(area, cuadrado(0.0003, 0, 0.0001), null).andExpect(status().isOk());
    }

    @Test
    void impactoDiceQueEjemplaresQuedanDentroYFueraSinGuardar() throws Exception {
        long area = crearArea("Área impacto contorno");
        crearEjemplar(area, lon, lat);
        crearEjemplar(area, lon + 0.001, lat);
        String ruta = API + "/areas-verdes/" + area + "/contorno";
        http.perform(
                        post(ruta + "/impacto")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"contorno\":" + cuadrado(0, 0, 0.0001) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area_anterior_id").value((int) area))
                .andExpect(jsonPath("$.dentro.length()").value(1))
                .andExpect(jsonPath("$.reasignables.length()").value(1))
                .andExpect(jsonPath("$.conflictos").isEmpty())
                .andExpect(jsonPath("$.superficie_anterior_m2").value(85.5))
                .andExpect(jsonPath("$.superficie_nueva_m2").isNumber());
        assertThat(contarGeometrias(area)).isZero();
    }

    @Test
    void sinConfirmacionDaConflictoConElImpactoYConEllaGuardaYDejaHistorial() throws Exception {
        long area = crearArea("Área redibujada contorno");
        long dentro = crearEjemplar(area, lon, lat);
        long fuera = crearEjemplar(area, lon + 0.001, lat);
        String ruta = API + "/areas-verdes/" + area + "/contorno";

        String respuesta =
                redibujar(area, cuadrado(0, 0, 0.0001), null)
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.codigo").value("confirmar-impacto-contorno"))
                        .andExpect(jsonPath("$.impacto.reasignables.length()").value(1))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(JsonPath.<Number>read(respuesta, "$.impacto.reasignables[0].id").longValue())
                .isEqualTo(fuera);
        assertThat(contarGeometrias(area)).isZero();

        redibujar(
                        area,
                        cuadrado(0, 0, 0.0001),
                        "{\"area_anterior_id\":" + area + ",\"ejemplares_ids\":[]}")
                .andExpect(status().isConflict());

        String guardado =
                redibujar(
                                area,
                                cuadrado(0, 0, 0.0001),
                                "{\"area_anterior_id\":"
                                        + area
                                        + ",\"ejemplares_ids\":["
                                        + fuera
                                        + "]}")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.poligonos").value(1))
                        .andExpect(jsonPath("$.contorno.type").value("Polygon"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        double superficie =
                JsonPath.<Number>read(guardado, "$.superficie_calculada_m2").doubleValue();
        assertThat(superficie).isBetween(300.0, 600.0);
        assertThat(JsonPath.<Number>read(guardado, "$.superficie_registrada_m2").doubleValue())
                .isEqualTo(superficie);
        assertThat(areaDe(fuera)).isNull();
        assertThat(areaDe(dentro)).isEqualTo(area);

        http.perform(get(API + "/areas-verdes/" + area).with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.superficie_m2").value(String.valueOf(superficie)));
        http.perform(get(ruta + "/historial").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].usuario_nombre").value("jefe"))
                .andExpect(
                        jsonPath("$[0].fecha")
                                .value(
                                        org.hamcrest.Matchers.matchesPattern(
                                                "\\d{4}-\\d{2}-\\d{2}T.*-05:00")))
                .andExpect(jsonPath("$[0].superficie_anterior_m2").value(85.5))
                .andExpect(jsonPath("$[0].superficie_nueva_m2").value(superficie))
                .andExpect(jsonPath("$[0].ejemplares_desvinculados").value(1));

        // Redibujar otra vez sustituye el polígono y encadena la superficie anterior.
        String segundo =
                redibujar(area, cuadrado(0, 0, 0.0002), null)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.poligonos").value(1))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        double ampliada = JsonPath.<Number>read(segundo, "$.superficie_calculada_m2").doubleValue();
        assertThat(ampliada).isGreaterThan(superficie * 3);
        assertThat(contarGeometrias(area)).isEqualTo(1);
        http.perform(get(ruta + "/historial").with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].superficie_anterior_m2").value(superficie))
                .andExpect(jsonPath("$[0].superficie_nueva_m2").value(ampliada))
                .andExpect(jsonPath("$[0].ejemplares_desvinculados").value(0));

        // Reenviar el mismo contorno no deja otra fila en el historial.
        redibujar(area, cuadrado(0, 0, 0.0002), null).andExpect(status().isOk());
        http.perform(get(ruta + "/historial").with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void elContornoNuevoNoPuedeInvadirEjemplaresDeOtraAreaConSuPropioContorno() throws Exception {
        long ajena = crearArea("Área ajena contorno");
        long area = crearArea("Área invasora contorno");
        redibujar(ajena, cuadrado(0.0003, 0, 0.0001), null).andExpect(status().isOk());
        long ejemplar = crearEjemplar(ajena, lon + 0.0003, lat);
        // Se mueve el punto fuera del contorno de su área, como un dato histórico mal vinculado.
        jdbc.update(
                "UPDATE mod02_ejemplar SET ubicacion_geografica = ST_SetSRID(ST_MakePoint(:lon,"
                        + " :lat), 4326) WHERE id = :id",
                Map.of("lon", lon, "lat", lat, "id", ejemplar));
        redibujar(area, cuadrado(0, 0, 0.0001), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("conflictos-cartograficos"))
                .andExpect(jsonPath("$.impacto.conflictos.length()").value(1));
    }

    @Test
    void elMapaEnsenaElContornoNuevoYElSustituido() throws Exception {
        String capa = API + "/mapa/geometria";
        int antes =
                JsonPath.<Integer>read(
                        http.perform(get(capa).with(httpBasic("jefe", CLAVE)))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.features.length()");

        // Un área sin contorno estrena polígono: aparece como un elemento más de la capa.
        long sinContorno = crearArea("Área nueva en el mapa contorno");
        String creado =
                redibujar(sinContorno, cuadrado(0.0003, 0, 0.0001), null)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String fuente = JsonPath.read(creado, "$.id_fuente");
        String filtro = "$.features[?(@.properties.mapa_id == '" + fuente + "')]";
        http.perform(get(capa).with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.features.length()").value(antes + 1))
                .andExpect(jsonPath(filtro + ".geometry.type").value("MultiPolygon"))
                .andExpect(jsonPath(filtro + ".properties.area_verde_id").value((int) sinContorno));

        // Un área ya vinculada a un polígono de la capa: se sustituye la geometría, no se añade.
        long bosque = crearArea("Área del bosque contorno");
        jdbc.update(
                "INSERT INTO mod02_mapa_poligono (id_fuente, area_verde_id) VALUES (CAST(:id AS"
                        + " uuid), :area) ON CONFLICT (id_fuente) DO UPDATE SET area_verde_id ="
                        + " EXCLUDED.area_verde_id",
                Map.of("id", BOSQUE, "area", bosque));
        String guardado =
                redibujar(bosque, cuadrado(0, 0, 0.00005), null)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id_fuente").value(BOSQUE))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        double superficie =
                JsonPath.<Number>read(guardado, "$.superficie_calculada_m2").doubleValue();
        String elBosque = "$.features[?(@.properties.mapa_id == '" + BOSQUE + "')]";
        String respuesta =
                http.perform(get(capa).with(httpBasic("jefe", CLAVE)))
                        .andExpect(jsonPath("$.features.length()").value(antes + 1))
                        .andExpect(jsonPath(elBosque + ".properties.Nombre").value("Bosque Húmedo"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Number> areas = JsonPath.read(respuesta, elBosque + ".properties['Área']");
        assertThat(areas).singleElement().extracting(Number::doubleValue).isEqualTo(superficie);
        List<List<?>> anillos = JsonPath.read(respuesta, elBosque + ".geometry.coordinates[0][0]");
        assertThat(anillos).hasSize(1);
        assertThat(anillos.get(0)).hasSize(5);
    }

    @Test
    void unAreaConVariosPoligonosNoSeRedibuja() throws Exception {
        long area = crearArea("Área partida contorno");
        for (double desplazamiento : List.of(0.0003, 0.0006)) {
            String id = java.util.UUID.randomUUID().toString();
            jdbc.update(
                    "INSERT INTO mod02_mapa_geometria (id_fuente, geometria) VALUES (CAST(:id AS"
                            + " uuid), ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326))",
                    Map.of("id", id, "g", cuadrado(desplazamiento, 0, 0.0001)));
            jdbc.update(
                    "INSERT INTO mod02_mapa_poligono (id_fuente, area_verde_id) VALUES (CAST(:id"
                            + " AS uuid), :area)",
                    Map.of("id", id, "area", area));
        }
        http.perform(
                        get(API + "/areas-verdes/" + area + "/contorno")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poligonos").value(2))
                .andExpect(jsonPath("$.id_fuente").isEmpty());
        http.perform(
                        post(API + "/areas-verdes/" + area + "/contorno/impacto")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"contorno\":" + cuadrado(0, 0, 0.0001) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("area-con-varios-contornos"));
        redibujar(area, cuadrado(0, 0, 0.0001), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("area-con-varios-contornos"));
    }

    private org.springframework.test.web.servlet.ResultActions redibujar(
            long area, String contorno, String confirmacion) throws Exception {
        String cuerpo =
                "{\"contorno\":"
                        + contorno
                        + (confirmacion == null ? "" : ",\"confirmacion\":" + confirmacion)
                        + "}";
        return http.perform(
                put(API + "/areas-verdes/" + area + "/contorno")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType("application/json")
                        .content(cuerpo));
    }

    /** Cuadrado de lado {@code 2 * medio} grados, desplazado {@code dx}, {@code dy} del centro. */
    private String cuadrado(double dx, double dy, double medio) {
        double x = lon + dx;
        double y = lat + dy;
        return "{\"type\":\"Polygon\",\"coordinates\":[[[%s,%s],[%s,%s],[%s,%s],[%s,%s],[%s,%s]]]}"
                .formatted(
                        x - medio, y - medio, x + medio, y - medio, x + medio, y + medio, x - medio,
                        y + medio, x - medio, y - medio);
    }

    private long crearArea(String nombre) throws Exception {
        String cuerpo =
                http.perform(
                                post(API + "/areas-verdes/")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content(
                                                "{\"nombre\":\""
                                                        + nombre
                                                        + "\",\"lugar\":\"Campus\",\"ubicacion\":"
                                                        + "\"Zona central\",\"superficie_m2\":"
                                                        + "\"85.50\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private long crearEjemplar(long area, double longitud, double latitud) throws Exception {
        String cuerpo =
                http.perform(
                                post(API + "/ejemplares")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content(
                                                "{\"latitud\":%s,\"longitud\":%s,\"area_verde_id\":%d}"
                                                        .formatted(latitud, longitud, area)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private Long areaDe(long ejemplar) {
        return jdbc.queryForObject(
                "SELECT area_verde_inventario_id FROM mod02_ejemplar WHERE id = :id",
                Map.of("id", ejemplar),
                Long.class);
    }

    private int contarGeometrias(long area) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mod02_mapa_poligono p JOIN mod02_mapa_geometria g USING"
                        + " (id_fuente) WHERE p.area_verde_id = :area",
                Map.of("area", area),
                Integer.class);
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
