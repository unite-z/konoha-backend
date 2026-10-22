package pe.edu.pucp.areasverdes.mod05;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class Mod05IntegrationTest {
    static final String BASE = "/api/tercerizados/atenciones/",
            PASSWORD = "prueba-integration-local";
    static final Path FILES;

    static {
        try {
            FILES = Files.createTempDirectory("mod05-spring-test-");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        String url =
                System.getenv()
                        .getOrDefault(
                                "MOD05_TEST_DB_URL",
                                "jdbc:postgresql://127.0.0.1:55432/mod05_spring_test");
        if (!url.endsWith("_test"))
            throw new IllegalStateException(
                    "Las pruebas requieren una base exclusiva terminada en _test");
        p.add("spring.datasource.url", () -> url);
        p.add(
                "spring.datasource.username",
                () ->
                        System.getenv()
                                .getOrDefault(
                                        "MOD05_TEST_DB_USER", System.getProperty("user.name")));
        p.add(
                "spring.datasource.password",
                () -> System.getenv().getOrDefault("MOD05_TEST_DB_PASSWORD", ""));
        p.add("mod05.archivos", () -> FILES.toString());
        p.add("mod05.max-archivo-bytes", () -> 1024);
    }

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired Mod05Repository repo;
    @Autowired PasswordEncoder passwords;
    @Autowired AtencionService service;
    @Autowired ReporteService reports;
    @Autowired ArchivoStorage storage;
    @Autowired PrecargaService seed;
    @Autowired PlatformTransactionManager transactions;
    long contrato, area, ejemplar, unidad;
    Usuario jefe;

    @BeforeEach
    void setup() {
        // Exclusivamente base *_test: nunca se ejecuta contra la base de desarrollo.
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        "TRUNCATE"
                            + " mod05_usuario,mod05_proveedor,mod05_tiposervicio,mod05_areaverde,mod05_unidadmedida,mod05_unidadinstitucional,mod05_archivo"
                            + " RESTART IDENTITY CASCADE");
        for (String name : List.of("jefe", "ingeniero", "practicante", "sinrol", "inactivo")) {
            long id =
                    repo.insert(
                            "usuario",
                            values(
                                    "usuario",
                                    name,
                                    "nombre",
                                    "Nombre " + name,
                                    "password_hash",
                                    passwords.encode(PASSWORD),
                                    "activo",
                                    !name.equals("inactivo")));
            String role =
                    switch (name) {
                        case "jefe" -> Usuario.JEFE;
                        case "ingeniero" -> Usuario.ROLES.get(1);
                        case "practicante" -> Usuario.ROLES.get(2);
                        default -> null;
                    };
            if (role != null)
                repo.jdbc()
                        .update(
                                "INSERT INTO mod05_usuario_rol(usuario_id,rol) VALUES (:id,:rol)",
                                Map.of("id", id, "rol", role));
        }
        jefe = repo.usuario("jefe").orElseThrow();
        long proveedor =
                repo.insert(
                        "proveedor",
                        values("razon_social", "Proveedor Demo", "ruc", null, "activo", true));
        long tipo =
                repo.insert(
                        "tiposervicio",
                        values(
                                "nombre",
                                "Corte de césped",
                                "ejecutor",
                                "Tercerizado",
                                "es_riesgo",
                                false,
                                "activo",
                                true));
        contrato =
                repo.insert(
                        "contrato",
                        values(
                                "numero",
                                "CT-TEST",
                                "proveedor_id",
                                proveedor,
                                "tipo_servicio_id",
                                tipo,
                                "vigencia_inicio",
                                LocalDate.now(Mod05Application.LIMA).minusDays(10),
                                "vigencia_fin",
                                LocalDate.now(Mod05Application.LIMA).plusDays(10)));
        area = repo.insert("areaverde", values("nombre", "Jardín A", "activo", true));
        ejemplar = repo.insert("ejemplar", values("codigo", "E-01", "area_verde_id", area));
        unidad = repo.insert("unidadmedida", values("nombre", "Metro cuadrado", "simbolo", "m2"));
    }

    Map<String, Object> input() {
        return values(
                "contrato_id",
                contrato,
                "descripcion",
                "Corte de jardines",
                "origen",
                "Demanda",
                "alcances",
                List.of(
                        values(
                                "area_verde_id",
                                area,
                                "metrado",
                                "120.00",
                                "unidad_medida_id",
                                unidad),
                        values("ejemplar_id", ejemplar)));
    }

    JsonNode create() throws Exception {
        var result =
                http.perform(
                                post(BASE)
                                        .with(httpBasic("jefe", PASSWORD))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(input())))
                        .andExpect(status().isCreated())
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    JsonNode patchAction(long id, String action, Object body, String user, int expected)
            throws Exception {
        var result =
                http.perform(
                                patch(BASE + id + "/" + action + "/")
                                        .with(httpBasic(user, PASSWORD))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(body)))
                        .andExpect(status().is(expected))
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    void execute(long id) {
        service.orden(id, new Entradas.Orden("OC-001", null), jefe);
        service.inicio(id, new Entradas.Inicio(OffsetDateTime.now().minusHours(2)), jefe);
        service.fin(id, new Entradas.Fin(OffsetDateTime.now().minusHours(1)), jefe);
    }

    JsonNode upload(long id, String docs, byte[] file, int expected) throws Exception {
        var result =
                http.perform(
                                multipart(BASE + id + "/reportes/")
                                        .file(
                                                new MockMultipartFile(
                                                        "archivos",
                                                        "reporte.pdf",
                                                        "application/pdf",
                                                        file))
                                        .param("documentos", docs)
                                        .param(
                                                "fecha_recepcion",
                                                LocalDate.now(Mod05Application.LIMA).toString())
                                        .with(httpBasic("jefe", PASSWORD)))
                        .andExpect(status().is(expected))
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    long count(String table) {
        return repo.jdbc()
                .getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM mod05_" + table, Long.class);
    }

    @Test
    void flujoCompletoYContratoFrontend() throws Exception {
        var a = create();
        long id = a.get("id").asLong();
        assertEquals("120.00", a.at("/alcances/0/metrado").asText());
        assertEquals("Jardín A", a.at("/alcances/0/area_verde_nombre").asText());
        assertEquals("Nombre jefe", a.get("solicitado_por_nombre").asText());
        assertEquals("Proveedor Demo", a.get("proveedor_nombre").asText());
        assertTrue(a.get("fecha_creacion").isTextual());
        patchAction(id, "orden-compra", Map.of("numero_orden_compra", "OC-001"), "ingeniero", 200);
        http.perform(
                        post(BASE + id + "/inicio/")
                                .with(httpBasic("practicante", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fecha_hora_inicio\":\"2026-01-02T08:00:00-05:00\"}"))
                .andExpect(status().isOk());
        http.perform(
                        post(BASE + id + "/fin/")
                                .with(httpBasic("ingeniero", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fecha_hora_fin\":\"2026-01-02T10:00:00-05:00\"}"))
                .andExpect(status().isOk());
        var r1 =
                upload(
                        id,
                        "[{\"categoria\":\"InformeFinal\"}]",
                        "%PDF-1.7 reporte".getBytes(),
                        201);
        var r2 =
                upload(
                        id,
                        "[{\"categoria\":\"InformeFinal\"}]",
                        "%PDF-1.7 revisión".getBytes(),
                        201);
        assertEquals(1, r1.get("revision").asInt());
        assertEquals(2, r2.get("revision").asInt());
        assertEquals("Nombre jefe", r1.get("usuario_creacion_nombre").asText());
        assertFalse(r1.at("/documentos/0/archivo").has("ruta_almacenamiento"));
        String download =
                BASE
                        + id
                        + "/reportes/"
                        + r1.get("id").asLong()
                        + "/documentos/"
                        + r1.at("/documentos/0/id").asLong()
                        + "/descarga/";
        http.perform(get(download)).andExpect(status().isForbidden());
        http.perform(get(download).with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().bytes("%PDF-1.7 reporte".getBytes()));
        http.perform(get(BASE + id + "/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(Atencion.REPORTE))
                .andExpect(jsonPath("$.historial.length()").value(5));
        http.perform(get(BASE + id + "/reportes/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.count").value(2));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "alcances_vacios",
                "ambos",
                "ninguno",
                "duplicados",
                "no_existe",
                "metrado_sin_unidad",
                "unidad_sin_metrado",
                "metrado_negativo",
                "metrado_precision",
                "origen_vacio",
                "unidad_falta",
                "unidad_sobra",
                "descripcion_vacia",
                "campo_extra"
            })
    void registroInvalidoAtomico(String caso) throws Exception {
        var data = input();
        int expected = 400;
        switch (caso) {
            case "alcances_vacios" -> data.put("alcances", List.of());
            case "ambos" ->
                    data.put(
                            "alcances",
                            List.of(Map.of("area_verde_id", area, "ejemplar_id", ejemplar)));
            case "ninguno" -> data.put("alcances", List.of(Map.of()));
            case "duplicados" ->
                    data.put(
                            "alcances",
                            List.of(Map.of("area_verde_id", area), Map.of("area_verde_id", area)));
            case "no_existe" -> {
                data.put("alcances", List.of(Map.of("ejemplar_id", 999999)));
                expected = 404;
            }
            case "metrado_sin_unidad" ->
                    data.put("alcances", List.of(Map.of("area_verde_id", area, "metrado", "2.00")));
            case "unidad_sin_metrado" ->
                    data.put(
                            "alcances",
                            List.of(Map.of("area_verde_id", area, "unidad_medida_id", unidad)));
            case "metrado_negativo" ->
                    data.put(
                            "alcances",
                            List.of(
                                    Map.of(
                                            "area_verde_id",
                                            area,
                                            "metrado",
                                            "-2.00",
                                            "unidad_medida_id",
                                            unidad)));
            case "metrado_precision" ->
                    data.put(
                            "alcances",
                            List.of(
                                    Map.of(
                                            "area_verde_id",
                                            area,
                                            "metrado",
                                            "2.001",
                                            "unidad_medida_id",
                                            unidad)));
            case "origen_vacio" -> data.put("origen", "");
            case "unidad_falta" -> data.put("origen", "SolicitudUnidad");
            case "unidad_sobra" -> data.put("unidad_provisional", "Unidad X");
            case "descripcion_vacia" -> data.put("descripcion", "  ");
            case "campo_extra" -> data.put("estado", Atencion.REPORTE);
        }
        http.perform(
                        post(BASE)
                                .with(httpBasic("jefe", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(data)))
                .andExpect(status().is(expected));
        assertEquals(0, count("atenciontercerizada"));
        assertEquals(0, count("alcanceatencion"));
        assertEquals(0, count("registroauditoria"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-30, 30})
    void vigencia(int days) throws Exception {
        repo.update(
                "contrato",
                contrato,
                values(
                        "vigencia_inicio",
                        LocalDate.now().plusDays(days),
                        "vigencia_fin",
                        LocalDate.now().plusDays(days + 1)));
        http.perform(
                        post(BASE)
                                .with(httpBasic("jefe", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(input())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transicionesRiesgoYCorreccionOC() throws Exception {
        long id = create().get("id").asLong();
        assertThrows(
                ApiError.class,
                () -> service.inicio(id, new Entradas.Inicio(OffsetDateTime.now()), jefe));
        patchAction(id, "orden-compra", Map.of("numero_orden_compra", "OC-1"), "jefe", 200);
        patchAction(id, "orden-compra", Map.of("numero_orden_compra", "OC-2"), "jefe", 400);
        patchAction(
                id,
                "orden-compra",
                Map.of("numero_orden_compra", "OC-2", "motivo", "Corrección"),
                "jefe",
                200);
        service.inicio(id, new Entradas.Inicio(OffsetDateTime.now()), jefe);
        assertThrows(
                ApiError.class,
                () -> service.fin(id, new Entradas.Fin(OffsetDateTime.now().minusDays(1)), jefe));
        assertThrows(
                ApiError.class,
                () -> service.inicio(id, new Entradas.Inicio(OffsetDateTime.now()), jefe));
        patchAction(id, "origen", Map.of("origen", "Demanda", "motivo", "Corrección"), "jefe", 409);
        repo.update("tiposervicio", 1, values("es_riesgo", true));
        var risk = create();
        assertTrue(risk.get("es_riesgo").asBoolean());
        service.orden(risk.get("id").asLong(), new Entradas.Orden("OC-R", null), jefe);
        assertThrows(
                ApiError.class,
                () ->
                        service.inicio(
                                risk.get("id").asLong(),
                                new Entradas.Inicio(OffsetDateTime.now()),
                                jefe));
    }

    @Test
    void origenYAcuerdoNoCierranAtencion() throws Exception {
        long id = create().get("id").asLong();
        patchAction(
                id,
                "origen",
                Map.of(
                        "origen",
                        "SolicitudUnidad",
                        "unidad_provisional",
                        "Facultad X",
                        "motivo",
                        "Solicitud recibida"),
                "jefe",
                200);
        assertEquals(1, count("unidadinstitucional"));
        patchAction(
                id,
                "acuerdo-unidad",
                values("acuerdo_unidad", true, "fecha_acuerdo_unidad", LocalDate.now()),
                "jefe",
                409);
        execute(id);
        for (Boolean value : Arrays.asList(true, false, null)) {
            var response =
                    patchAction(
                            id,
                            "acuerdo-unidad",
                            values(
                                    "acuerdo_unidad",
                                    value,
                                    "fecha_acuerdo_unidad",
                                    value == null ? null : LocalDate.now(Mod05Application.LIMA),
                                    "comentarios_acuerdo_unidad",
                                    "Respuesta"),
                            "jefe",
                            200);
            assertEquals(Atencion.EJECUTADO, response.get("estado").asText());
        }
        assertTrue(
                repo.jdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM mod05_registroauditoria WHERE"
                                                + " motivo='Solicitud recibida'",
                                        Map.of(),
                                        Long.class)
                        > 0);
        patchAction(id, "acuerdo-unidad", Map.of(), "jefe", 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jefe", "ingeniero", "practicante", "sinrol", "inactivo"})
    void permisosYMe(String user) throws Exception {
        http.perform(get("/api/tercerizados/me/").with(httpBasic(user, PASSWORD)))
                .andExpect(status().is(user.equals("inactivo") ? 403 : 200));
        http.perform(get(BASE).with(httpBasic(user, PASSWORD)))
                .andExpect(status().is(Set.of("sinrol", "inactivo").contains(user) ? 403 : 200));
        http.perform(
                        post(BASE)
                                .with(httpBasic(user, PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(input())))
                .andExpect(status().is(user.equals("jefe") ? 201 : 403));
    }

    @Test
    void anonimoCredencialInvalidaYJerarquia() throws Exception {
        http.perform(get(BASE)).andExpect(status().isForbidden());
        http.perform(get(BASE).with(httpBasic("jefe", "incorrecta")))
                .andExpect(status().isForbidden());
        repo.jdbc()
                .update(
                        "INSERT INTO mod05_usuario_rol VALUES (:id,:rol)",
                        Map.of("id", jefe.id(), "rol", Usuario.ROLES.get(1)));
        http.perform(get("/api/tercerizados/me/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.rol").value(Usuario.JEFE));
        http.perform(get("/api/tercerizados/me/").with(httpBasic("sinrol", PASSWORD)))
                .andExpect(jsonPath("$.rol").isEmpty());
    }

    @Test
    void busquedaPaginacionYCatalogos() throws Exception {
        for (int i = 0; i < 26; i++)
            service.crear(json.convertValue(input(), Entradas.Crear.class), jefe);
        http.perform(get(BASE).param("q", "Demo jardines").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.count").value(26))
                .andExpect(jsonPath("$.results.length()").value(25))
                .andExpect(jsonPath("$.next").isNotEmpty());
        http.perform(get(BASE).param("page", "2").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.previous").isNotEmpty());
        http.perform(get(BASE).param("estado", Atencion.OC).with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.count").value(0));
        http.perform(get(BASE).param("page", "3").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isNotFound());
        http.perform(get(BASE).param("contrato_id", "abc").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isBadRequest());
        http.perform(get(BASE).param("q", "x".repeat(101)).with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isBadRequest());
        for (String catalog : ConsultasService.CATALOGOS.keySet())
            http.perform(
                            get("/api/tercerizados/catalogos/" + catalog + "/")
                                    .with(httpBasic("jefe", PASSWORD)))
                    .andExpect(status().isOk());
        http.perform(
                        get("/api/tercerizados/catalogos/contratos/")
                                .param("vigente", "true")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].proveedor_nombre").value("Proveedor Demo"));
        http.perform(
                        get("/api/tercerizados/catalogos/areas-verdes/")
                                .param("activo", "abc")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isBadRequest());
        http.perform(get(BASE).param("q", "%' OR 1=1 --").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void validacionAdjuntosYNoCruzarAtenciones() throws Exception {
        long id = create().get("id").asLong(), other = create().get("id").asLong();
        upload(id, "[{\"categoria\":\"InformeFinal\"}]", "%PDF-1.7 reporte".getBytes(), 409);
        execute(id);
        upload(id, "[]", "%PDF-1.7".getBytes(), 400);
        upload(id, "[{\"categoria\":\"RutaRecorrida\"}]", "%PDF-1.7".getBytes(), 400);
        upload(id, "[{\"categoria\":\"InformeFinal\"}]", "falso".getBytes(), 400);
        upload(id, "[{\"categoria\":\"InformeFinal\"}]", new byte[1025], 400);
        long foreign =
                repo.jdbc()
                        .queryForObject(
                                "SELECT min(id) FROM mod05_alcanceatencion WHERE atencion_id=:id",
                                Map.of("id", other),
                                Long.class);
        upload(
                id,
                "[{\"categoria\":\"InformeFinal\",\"alcance_id\":" + foreign + "}]",
                "%PDF-1.7".getBytes(),
                400);
        var r = upload(id, "[{\"categoria\":\"InformeFinal\"}]", "%PDF-1.7".getBytes(), 201);
        http.perform(
                        get(BASE + other + "/reportes/" + r.get("id").asLong() + "/")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rollbackIncluyeArchivosYErroresCommit() throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        long beforeFiles;
        try (var paths = Files.walk(FILES)) {
            beforeFiles = paths.filter(Files::isRegularFile).count();
        }
        var tx = new TransactionTemplate(transactions);
        assertThrows(
                DataAccessException.class,
                () ->
                        tx.execute(
                                status -> {
                                    reports.adjuntar(
                                            id,
                                            LocalDate.now(Mod05Application.LIMA),
                                            "",
                                            List.of(
                                                    new Entradas.Documento(
                                                            "InformeFinal", null, "")),
                                            List.of(
                                                    new MockMultipartFile(
                                                            "archivos",
                                                            "reporte.pdf",
                                                            "application/pdf",
                                                            "%PDF-1.7".getBytes())),
                                            jefe);
                                    // FK diferida: el fallo sucede durante COMMIT, no durante
                                    // adjuntar().
                                    repo.jdbc()
                                            .update(
                                                    "UPDATE mod05_reporteproveedor SET"
                                                            + " usuario_creacion_id=999999 WHERE"
                                                            + " atencion_id=:id",
                                                    Map.of("id", id));
                                    return null;
                                }));
        assertEquals(0, count("reporteproveedor"));
        assertEquals(0, count("archivo"));
        try (var paths = Files.walk(FILES)) {
            assertEquals(beforeFiles, paths.filter(Files::isRegularFile).count());
        }
        assertEquals(Atencion.EJECUTADO, repo.get("atenciontercerizada", id).get("estado"));
    }

    @Test
    void auditoriaInmutable() throws Exception {
        create();
        assertThrows(
                DataAccessException.class,
                () ->
                        repo.jdbc()
                                .getJdbcTemplate()
                                .update("UPDATE mod05_registroauditoria SET motivo='alterado'"));
        assertThrows(
                DataAccessException.class,
                () -> repo.jdbc().getJdbcTemplate().update("DELETE FROM mod05_historialestado"));
    }

    @Test
    void inicioConcurrenteYReportesConsecutivos() throws Exception {
        long id = create().get("id").asLong();
        service.orden(id, new Entradas.Orden("OC-1", null), jefe);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> start =
                    () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        try {
                            service.inicio(
                                    id,
                                    new Entradas.Inicio(OffsetDateTime.now().minusHours(2)),
                                    jefe);
                            return true;
                        } catch (ApiError e) {
                            assertEquals(409, e.status);
                            return false;
                        }
                    };
            var futures = pool.invokeAll(List.of(start, start));
            assertEquals(
                    1,
                    futures.stream()
                            .filter(
                                    f -> {
                                        try {
                                            return f.get();
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .count());
        }
        service.fin(id, new Entradas.Fin(OffsetDateTime.now().minusHours(1)), jefe);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> upload =
                    () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        var r =
                                reports.adjuntar(
                                        id,
                                        LocalDate.now(Mod05Application.LIMA),
                                        "",
                                        List.of(new Entradas.Documento("InformeFinal", null, "")),
                                        List.of(
                                                new MockMultipartFile(
                                                        "archivos",
                                                        "reporte.pdf",
                                                        "application/pdf",
                                                        "%PDF-1.7".getBytes())),
                                        jefe);
                        return ((Number) r.get("revision")).intValue();
                    };
            var revisions = pool.invokeAll(List.of(upload, upload));
            assertEquals(Set.of(1, 2), Set.of(revisions.get(0).get(), revisions.get(1).get()));
        }
        assertEquals(
                1,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM mod05_historialestado WHERE"
                                        + " estado_nuevo=:estado",
                                Map.of("estado", Atencion.REPORTE),
                                Integer.class));
    }

    @Test
    void precargaIdempotenteYConflictos() {
        Path mocks = Path.of("mock-data");
        assertThrows(
                IllegalArgumentException.class,
                () -> seed.cargar(mocks, PASSWORD)); // IDs ya ocupados por referencias distintas.
        assertEquals("Proveedor Demo", repo.get("proveedor", 1).get("razon_social"));
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        "TRUNCATE"
                            + " mod05_usuario,mod05_proveedor,mod05_tiposervicio,mod05_areaverde,mod05_unidadmedida,mod05_unidadinstitucional,mod05_archivo"
                            + " RESTART IDENTITY CASCADE");
        seed.cargar(mocks, PASSWORD);
        long providers = count("proveedor");
        String hash = repo.usuario("mod05_jefe").orElseThrow().passwordHash();
        seed.cargar(mocks, "otra-clave-no-sobrescribe");
        assertEquals(hash, repo.usuario("mod05_jefe").orElseThrow().passwordHash());
        assertEquals(providers, count("proveedor"));
        assertEquals(0, count("atenciontercerizada"));
        assertTrue(
                repo.insert("proveedor", values("razon_social", "Nuevo", "activo", true))
                        > providers);
    }

    @Test
    void openapiDisponible() throws Exception {
        http.perform(get("/api/tercerizados/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }
}
