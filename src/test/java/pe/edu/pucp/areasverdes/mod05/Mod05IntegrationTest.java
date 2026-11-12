package pe.edu.pucp.areasverdes.mod05;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.MessageDigest;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

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
        BaseDePruebas.registrar(p);
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
        http.perform(get(download)).andExpect(status().isUnauthorized());
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

    JsonNode verificar(long id, String usuario, String medidas, boolean confirmado, int esperado)
            throws Exception {
        return cuerpo(
                http.perform(
                                post("/api/v1/tercerizados/atenciones/" + id + "/seguridad/")
                                        .with(httpBasic(usuario, PASSWORD))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsBytes(
                                                        values(
                                                                "medidas",
                                                                medidas,
                                                                "confirmado",
                                                                confirmado))))
                        .andExpect(status().is(esperado)));
    }

    @Test
    void seguridadPorRolConservaCorreccionesYPermiteCompletarElCiclo() throws Exception {
        long normal = create().get("id").asLong();
        verificar(normal, "jefe", "EPP", true, 409);
        repo.update("tiposervicio", 1, values("es_riesgo", true));
        long id = create().get("id").asLong();
        http.perform(
                        post("/api/v1/tercerizados/atenciones/" + id + "/seguridad/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsBytes(
                                                values("medidas", "EPP", "confirmado", true))))
                .andExpect(status().isUnauthorized());
        verificar(id, "practicante", "EPP", true, 403);
        verificar(id, "sinrol", "EPP", true, 403);
        verificar(id, "jefe", " ", true, 400);
        verificar(id, "ingeniero", "Casco", false, 400);
        verificar(id, "jefe", "Casco y guantes", true, 200);
        verificar(id, "jefe", "Casco y guantes", true, 200);
        assertEquals(1, count("verificacionseguridad"));
        var corregida = verificar(id, "ingeniero", "Casco, guantes y delimitación", true, 200);
        assertEquals(2, corregida.get("verificaciones_seguridad").size());
        assertEquals(
                "Casco y guantes", corregida.at("/verificaciones_seguridad/0/medidas").asText());
        assertEquals(
                "Nombre ingeniero",
                corregida.at("/verificaciones_seguridad/1/usuario_nombre").asText());
        service.orden(id, new Entradas.Orden("OC-R", null), jefe);
        service.inicio(id, new Entradas.Inicio(OffsetDateTime.now().minusHours(1)), jefe);
        verificar(id, "jefe", "Cambio tardío", true, 409);
        service.fin(id, new Entradas.Fin(OffsetDateTime.now()), jefe);
        enviar("jefe", id, informes(), "Informe", hoy(), pdf("informe.pdf", "A"))
                .andExpect(status().isCreated());
        decidir(id, "jefe", decision("Observada", "Corregir metrado"), 200);
        enviar("ingeniero", id, informes(), "Metrado corregido", hoy(), pdf("informe.pdf", "B"))
                .andExpect(status().isCreated());
        var cerrada = decidir(id, "jefe", decision("Conforme", ""), 200);
        assertEquals(Atencion.CONFORME, cerrada.get("estado").asText());
        assertEquals(2, cerrada.get("verificaciones_seguridad").size());
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
                .andExpect(status().is(user.equals("inactivo") ? 401 : 200));
        http.perform(get(BASE).with(httpBasic(user, PASSWORD)))
                .andExpect(
                        status().is(
                                        user.equals("inactivo")
                                                ? 401
                                                : user.equals("sinrol") ? 403 : 200));
        http.perform(
                        post(BASE)
                                .with(httpBasic(user, PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(input())))
                .andExpect(
                        status().is(
                                        user.equals("jefe")
                                                ? 201
                                                : user.equals("inactivo") ? 401 : 403));
    }

    @Test
    void anonimoCredencialInvalidaYJerarquia() throws Exception {
        http.perform(get(BASE)).andExpect(status().isUnauthorized());
        http.perform(get(BASE).with(httpBasic("jefe", "incorrecta")))
                .andExpect(status().isUnauthorized());
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
            // Contenidos distintos: con el mismo contenido el segundo envío sería un reintento.
            var numero = new java.util.concurrent.atomic.AtomicInteger();
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
                                                        ("%PDF-1.7 " + numero.incrementAndGet())
                                                                .getBytes())),
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
        Map<String, Object> ejemplarTrasPrecarga =
                repo.jdbc()
                        .getJdbcTemplate()
                        .queryForMap(
                                "INSERT INTO mod02_ejemplar (area_verde_id) VALUES (NULL)"
                                        + " RETURNING id, codigo");
        assertTrue(((Number) ejemplarTrasPrecarga.get("id")).longValue() > 3);
        assertEquals("EJ-00734", ejemplarTrasPrecarga.get("codigo"));
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

    // --- RF-SER-006: permisos, descarga e idempotencia de los reportes del proveedor
    // ---------------------------------------------------------------------------------------------

    static final String INFORME = "{\"categoria\":\"InformeFinal\"}";

    String hoy() {
        return LocalDate.now(Mod05Application.LIMA).toString();
    }

    MockMultipartFile pdf(String nombre, String contenido) {
        return new MockMultipartFile(
                "archivos", nombre, "application/pdf", ("%PDF-1.7 " + contenido).getBytes());
    }

    /** Envío multipart; con `user` nulo va sin credenciales. */
    ResultActions enviar(
            String user,
            long id,
            String docs,
            String resumen,
            String fecha,
            MockMultipartFile... files)
            throws Exception {
        var request = multipart(BASE + id + "/reportes/");
        for (var file : files) request.file(file);
        request.param("documentos", docs).param("fecha_recepcion", fecha);
        if (resumen != null) request.param("resumen", resumen);
        if (user != null) request.with(httpBasic(user, PASSWORD));
        return http.perform(request);
    }

    JsonNode cuerpo(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    long archivosEnDisco() throws Exception {
        try (var paths = Files.walk(FILES)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    long alcanceDe(long atencion, String funcion) {
        return repo.jdbc()
                .queryForObject(
                        "SELECT "
                                + funcion
                                + "(id) FROM mod05_alcanceatencion WHERE atencion_id=:id",
                        Map.of("id", atencion),
                        Long.class);
    }

    String sha256(String contenido) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(("%PDF-1.7 " + contenido).getBytes()));
    }

    @SafeVarargs
    private String documentos(Map<String, Object>... docs) throws Exception {
        return json.writeValueAsString(List.of(docs));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jefe", "ingeniero", "practicante", "sinrol", "inactivo"})
    void permisosDeReportesPorRol(String user) throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        int escritura = user.equals("inactivo") ? 401 : user.equals("sinrol") ? 403 : 201;
        enviar(user, id, "[" + INFORME + "]", null, hoy(), pdf("propio.pdf", user))
                .andExpect(status().is(escritura));
        // Un reporte del jefe para que cada usuario intente consultarlo y descargarlo.
        var base =
                reports.adjuntar(
                        id,
                        LocalDate.now(Mod05Application.LIMA),
                        "",
                        List.of(new Entradas.Documento("InformeFinal", null, "")),
                        List.of(pdf("base.pdf", "base de " + user)),
                        jefe);
        long reporte = ((Number) base.get("id")).longValue();
        long documento =
                repo.jdbc()
                        .queryForObject(
                                "SELECT min(id) FROM mod05_archivoasociado WHERE reporte_id=:r",
                                Map.of("r", reporte),
                                Long.class);
        int lectura = user.equals("inactivo") ? 401 : user.equals("sinrol") ? 403 : 200;
        String detalle = BASE + id + "/reportes/" + reporte + "/";
        http.perform(get(BASE + id + "/reportes/").with(httpBasic(user, PASSWORD)))
                .andExpect(status().is(lectura));
        http.perform(get(detalle).with(httpBasic(user, PASSWORD))).andExpect(status().is(lectura));
        var descarga =
                http.perform(
                                get(detalle + "documentos/" + documento + "/descarga/")
                                        .with(httpBasic(user, PASSWORD)))
                        .andExpect(status().is(lectura));
        if (lectura == 200)
            descarga.andExpect(content().bytes(("%PDF-1.7 base de " + user).getBytes()));
    }

    @Test
    void reportesYDescargaExigenCredenciales() throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        var r = upload(id, "[" + INFORME + "]", "%PDF-1.7 reporte".getBytes(), 201);
        enviar(null, id, "[" + INFORME + "]", null, hoy(), pdf("sin-credenciales.pdf", "x"))
                .andExpect(status().isUnauthorized());
        enviar("jefe", id, "[" + INFORME + "]", null, hoy(), pdf("valida.pdf", "x"))
                .andExpect(status().isCreated());
        String detalle = BASE + id + "/reportes/" + r.get("id").asLong() + "/";
        http.perform(get(BASE + id + "/reportes/")).andExpect(status().isUnauthorized());
        http.perform(get(detalle)).andExpect(status().isUnauthorized());
        http.perform(
                        get(
                                detalle
                                        + "documentos/"
                                        + r.at("/documentos/0/id").asLong()
                                        + "/descarga/"))
                .andExpect(status().isUnauthorized());
        assertEquals(2, count("reporteproveedor"));
    }

    @Test
    void descargaNoCruzaAtencionesYFallaSiFaltaElArchivo() throws Exception {
        long id = create().get("id").asLong(), otra = create().get("id").asLong();
        execute(id);
        var r = upload(id, "[" + INFORME + "]", "%PDF-1.7 reporte".getBytes(), 201);
        String ruta =
                BASE
                        + "%d/reportes/"
                        + r.get("id").asLong()
                        + "/documentos/"
                        + r.at("/documentos/0/id").asLong()
                        + "/descarga/";
        // La misma ruta con otra atención no encuentra el documento.
        http.perform(get(ruta.formatted(otra)).with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isNotFound());
        // Si el archivo desapareció del almacenamiento, responde 404 y no un error genérico.
        String almacenado =
                repo.jdbc()
                        .queryForObject(
                                "SELECT ruta_almacenamiento FROM mod05_archivo",
                                Map.of(),
                                String.class);
        Files.delete(FILES.resolve(almacenado));
        http.perform(get(ruta.formatted(id)).with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reenvioIdenticoDevuelveLaUltimaRevisionSinCrearNada() throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        String docs = "[{\"categoria\":\"InformeFinal\",\"descripcion\":\"Informe\"}]";
        var primero =
                cuerpo(
                        enviar("jefe", id, docs, "Resumen", hoy(), pdf("informe.pdf", "A"))
                                .andExpect(status().isCreated()));
        long enDisco = archivosEnDisco(),
                auditoria = count("registroauditoria"),
                historial = count("historialestado");
        // Lo reintenta otro usuario, como tras perder la conexión: mismo contenido, misma revisión.
        var segundo =
                cuerpo(
                        enviar("ingeniero", id, docs, "Resumen", hoy(), pdf("informe.pdf", "A"))
                                .andExpect(status().isOk()));
        assertFalse(primero.get("repetido").asBoolean());
        assertTrue(segundo.get("repetido").asBoolean());
        assertEquals(primero.get("id").asLong(), segundo.get("id").asLong());
        assertEquals(1, segundo.get("revision").asInt());
        assertEquals(sha256("A"), segundo.at("/documentos/0/archivo/hash_contenido").asText());
        assertEquals(1, count("reporteproveedor"));
        assertEquals(1, count("archivoasociado"));
        assertEquals(1, count("archivo"));
        assertEquals(enDisco, archivosEnDisco());
        assertEquals(auditoria, count("registroauditoria"));
        assertEquals(historial, count("historialestado"));
        http.perform(get(BASE + id + "/reportes/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void laHuellaNoDependeDelOrdenNiDelNombreDeLosArchivos() throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        var informe = values("categoria", "InformeFinal");
        var metrado = values("categoria", "Metrado");
        var primero =
                cuerpo(
                        enviar(
                                        "jefe",
                                        id,
                                        documentos(informe, metrado),
                                        null,
                                        hoy(),
                                        pdf("informe.pdf", "A"),
                                        pdf("metrado.pdf", "B"))
                                .andExpect(status().isCreated()));
        // Mismos documentos en otro orden y con otros nombres de archivo.
        var segundo =
                cuerpo(
                        enviar(
                                        "jefe",
                                        id,
                                        documentos(metrado, informe),
                                        null,
                                        hoy(),
                                        pdf("copia-b.pdf", "B"),
                                        pdf("copia-a.pdf", "A"))
                                .andExpect(status().isOk()));
        assertTrue(segundo.get("repetido").asBoolean());
        assertEquals(primero.get("id").asLong(), segundo.get("id").asLong());
        assertEquals(1, count("reporteproveedor"));
        assertEquals(2, count("archivo"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "contenido",
                "categoria",
                "alcance",
                "descripcion",
                "resumen",
                "fecha",
                "documento_extra"
            })
    void cualquierCambioCreaUnaRevisionNueva(String cambio) throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        long alcance = alcanceDe(id, "min"), otroAlcance = alcanceDe(id, "max");
        String categoria = "EvidenciaEspacio",
                descripcion = "Foto",
                contenido = "foto",
                resumen = "Resumen",
                fecha = hoy();
        long alcanceFoto = alcance;
        boolean extra = false;
        var informe = values("categoria", "InformeFinal");
        enviar(
                        "jefe",
                        id,
                        documentos(
                                informe,
                                values(
                                        "categoria",
                                        categoria,
                                        "alcance_id",
                                        alcanceFoto,
                                        "descripcion",
                                        descripcion)),
                        resumen,
                        fecha,
                        pdf("informe.pdf", "informe"),
                        pdf("foto.pdf", contenido))
                .andExpect(status().isCreated());
        switch (cambio) {
            case "contenido" -> contenido = "foto-corregida";
            case "categoria" -> categoria = "RutaRecorrida";
            case "alcance" -> alcanceFoto = otroAlcance;
            case "descripcion" -> descripcion = "Foto del acceso";
            case "resumen" -> resumen = "Resumen corregido";
            case "fecha" -> fecha = LocalDate.now(Mod05Application.LIMA).plusDays(1).toString();
            case "documento_extra" -> extra = true;
        }
        var foto =
                values(
                        "categoria",
                        categoria,
                        "alcance_id",
                        alcanceFoto,
                        "descripcion",
                        descripcion);
        var nueva =
                extra
                        ? cuerpo(
                                enviar(
                                                "jefe",
                                                id,
                                                documentos(
                                                        informe,
                                                        foto,
                                                        values("categoria", "Metrado")),
                                                resumen,
                                                fecha,
                                                pdf("informe.pdf", "informe"),
                                                pdf("foto.pdf", contenido),
                                                pdf("metrado.pdf", "metrado"))
                                        .andExpect(status().isCreated()))
                        : cuerpo(
                                enviar(
                                                "jefe",
                                                id,
                                                documentos(informe, foto),
                                                resumen,
                                                fecha,
                                                pdf("informe.pdf", "informe"),
                                                pdf("foto.pdf", contenido))
                                        .andExpect(status().isCreated()));
        assertEquals(2, nueva.get("revision").asInt());
        assertFalse(nueva.get("repetido").asBoolean());
        assertEquals(2, count("reporteproveedor"));
    }

    @Test
    void volverAUnContenidoAnteriorEsUnaRevisionNuevaYSoloSeComparaConLaUltima() throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        String docs = "[" + INFORME + "]";
        int[] esperado = {1, 2, 3};
        String[] contenidos = {"A", "B", "A"};
        for (int i = 0; i < contenidos.length; i++) {
            var r =
                    cuerpo(
                            enviar("jefe", id, docs, null, hoy(), pdf("r.pdf", contenidos[i]))
                                    .andExpect(status().isCreated()));
            assertEquals(esperado[i], r.get("revision").asInt());
        }
        // El reintento de la última (A) ya no crea otra.
        var repetido =
                cuerpo(
                        enviar("jefe", id, docs, null, hoy(), pdf("r.pdf", "A"))
                                .andExpect(status().isOk()));
        assertEquals(3, repetido.get("revision").asInt());
        assertEquals(3, count("reporteproveedor"));
    }

    @Test
    void elReenvioRepetidoSigueExigiendoLasValidaciones() throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        String docs = "[" + INFORME + "]";
        enviar("jefe", id, docs, null, hoy(), pdf("r.pdf", "A")).andExpect(status().isCreated());
        // Aunque el contenido sea el mismo, un envío inválido sigue siendo inválido.
        enviar("jefe", id, "[{\"categoria\":\"Metrado\"}]", null, hoy(), pdf("r.pdf", "A"))
                .andExpect(status().isBadRequest());
        enviar("jefe", id, docs, null, "2000-01-01", pdf("r.pdf", "A"))
                .andExpect(status().isBadRequest());
        assertEquals(1, count("reporteproveedor"));
    }

    @Test
    void enviosIdenticosSimultaneosCreanUnaSolaRevision() throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> envio =
                    () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        var r =
                                reports.adjuntar(
                                        id,
                                        LocalDate.now(Mod05Application.LIMA),
                                        "",
                                        List.of(new Entradas.Documento("InformeFinal", null, "")),
                                        List.of(pdf("r.pdf", "igual")),
                                        jefe);
                        return (Boolean) r.get("repetido");
                    };
            var resultados = pool.invokeAll(List.of(envio, envio));
            assertEquals(
                    Set.of(true, false), Set.of(resultados.get(0).get(), resultados.get(1).get()));
        }
        assertEquals(1, count("reporteproveedor"));
        assertEquals(1, count("archivo"));
        assertEquals(
                1,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM mod05_historialestado WHERE"
                                        + " estado_nuevo=:estado",
                                Map.of("estado", Atencion.REPORTE),
                                Integer.class));
    }

    // --- Conformidad u observación del informe (RF-SER-007) ---

    String informes() {
        return "[" + INFORME + "]";
    }

    Map<String, Object> decision(String resultado, String comentarios) {
        return values(
                "resultado", resultado, "fecha_conformidad", hoy(), "comentarios", comentarios);
    }

    JsonNode decidir(long id, String user, Object body, int esperado) throws Exception {
        return cuerpo(
                http.perform(
                                post(BASE + id + "/conformidad/")
                                        .with(httpBasic(user, PASSWORD))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(body)))
                        .andExpect(status().is(esperado)));
    }

    /** Atención ejecutada y con la revisión 1 del informe: lista para decidir. */
    long conInforme() throws Exception {
        long id = create().get("id").asLong();
        execute(id);
        enviar("jefe", id, informes(), "Resumen", hoy(), pdf("informe.pdf", "A"))
                .andExpect(status().isCreated());
        return id;
    }

    @Test
    void laConformidadExigeElInformeYSoloLaDaElJefe() throws Exception {
        long id = create().get("id").asLong();
        decidir(id, "jefe", decision("Conforme", ""), 409);
        execute(id);
        // RN-007: ejecutada pero sin informe adjunto.
        decidir(id, "jefe", decision("Conforme", ""), 409);
        enviar("jefe", id, informes(), "Resumen", hoy(), pdf("informe.pdf", "A"))
                .andExpect(status().isCreated());
        http.perform(get(BASE + id + "/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.conformidades").isArray())
                .andExpect(jsonPath("$.conformidades.length()").value(0));
        decidir(id, "ingeniero", decision("Conforme", ""), 403);
        decidir(id, "practicante", decision("Conforme", ""), 403);
        decidir(id, "sinrol", decision("Conforme", ""), 403);
        assertEquals(0, count("conformidad"));

        var cerrada = decidir(id, "jefe", decision("Conforme", ""), 200);
        assertEquals(Atencion.CONFORME, cerrada.get("estado").asText());
        assertEquals(1, cerrada.get("conformidades").size());
        assertEquals("Conforme", cerrada.at("/conformidades/0/resultado").asText());
        assertEquals(1, cerrada.at("/conformidades/0/revision").asInt());
        assertEquals("Nombre jefe", cerrada.at("/conformidades/0/usuario_nombre").asText());
        var historial = cerrada.get("historial");
        assertEquals(
                Atencion.CONFORME,
                historial.get(historial.size() - 1).get("estado_nuevo").asText());
        // Cerrada: ni otra decisión ni más revisiones.
        decidir(id, "jefe", decision("Conforme", ""), 409);
        enviar("jefe", id, informes(), "Resumen", hoy(), pdf("informe.pdf", "B"))
                .andExpect(status().isConflict());
        assertEquals(1, count("reporteproveedor"));
    }

    @Test
    void laDecisionSeValidaAntesDeRegistrarse() throws Exception {
        long id = conInforme();
        decidir(id, "jefe", decision("Observada", "   "), 400);
        decidir(id, "jefe", decision("Quizá", ""), 400);
        decidir(id, "jefe", values("resultado", "Conforme", "comentarios", ""), 400);
        decidir(
                id,
                "jefe",
                values(
                        "resultado",
                        "Conforme",
                        "fecha_conformidad",
                        LocalDate.now(Mod05Application.LIMA).minusDays(1).toString()),
                400);
        assertEquals(0, count("conformidad"));
        assertEquals(Atencion.REPORTE, repo.atencion(id).get("estado"));
    }

    @Test
    void observarDejaLaAtencionObservadaYUnaNuevaRevisionPermiteDecidirDeNuevo() throws Exception {
        long id = conInforme();
        var observada = decidir(id, "jefe", decision("Observada", " Falta el metrado "), 200);
        assertEquals(Atencion.OBSERVADO, observada.get("estado").asText());
        assertEquals("Observada", observada.at("/conformidades/0/resultado").asText());
        assertEquals("Falta el metrado", observada.at("/conformidades/0/comentarios").asText());
        // Observada: espera otra revisión, no se puede decidir sobre la misma.
        decidir(id, "jefe", decision("Conforme", ""), 409);
        // El mismo informe no subsana: se reconoce como repetido y la atención sigue observada.
        enviar("jefe", id, informes(), "Resumen", hoy(), pdf("informe.pdf", "A"))
                .andExpect(status().isOk());
        assertEquals(Atencion.OBSERVADO, repo.atencion(id).get("estado"));
        // Una revisión distinta es la subsanación y devuelve la atención a «Reporte adjunto».
        enviar("sinrol", id, informes(), "Metrado corregido", hoy(), pdf("informe.pdf", "B"))
                .andExpect(status().isForbidden());
        enviar("ingeniero", id, informes(), "   ", hoy(), pdf("informe.pdf", "B"))
                .andExpect(status().isBadRequest());
        var segunda =
                cuerpo(
                        enviar(
                                        "ingeniero",
                                        id,
                                        informes(),
                                        "Metrado corregido",
                                        hoy(),
                                        pdf("informe.pdf", "B"))
                                .andExpect(status().isCreated()));
        assertEquals(2, segunda.get("revision").asInt());
        assertEquals(Atencion.REPORTE, repo.atencion(id).get("estado"));

        assertEquals("Metrado corregido", segunda.get("resumen").asText());
        decidir(id, "ingeniero", decision("Conforme", ""), 403);
        decidir(id, "jefe", decision("Observada", "Falta el plano"), 200);
        decidir(id, "jefe", decision("Conforme", ""), 409);
        var tercera =
                cuerpo(
                        enviar(
                                        "ingeniero",
                                        id,
                                        informes(),
                                        "Adjunto plano",
                                        hoy(),
                                        pdf("plano.pdf", "C"))
                                .andExpect(status().isCreated()));
        assertEquals(3, tercera.get("revision").asInt());
        var conforme = decidir(id, "jefe", decision("Conforme", ""), 200);
        assertEquals(Atencion.CONFORME, conforme.get("estado").asText());
        assertEquals(3, conforme.get("conformidades").size());
        assertEquals("Falta el metrado", conforme.at("/conformidades/0/comentarios").asText());
        assertEquals("Falta el plano", conforme.at("/conformidades/1/comentarios").asText());
        assertEquals(2, conforme.at("/conformidades/1/revision").asInt());
        assertEquals("Conforme", conforme.at("/conformidades/2/resultado").asText());
        assertEquals(3, conforme.at("/conformidades/2/revision").asInt());
    }

    // --- Atenciones programadas y extraordinarias (RF-SER-008) ---

    JsonNode crearComo(String clase, int esperado) throws Exception {
        var cuerpo = input();
        if (clase != null) cuerpo.put("clase", clase);
        return cuerpo(
                http.perform(
                                post(BASE)
                                        .with(httpBasic("jefe", PASSWORD))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(cuerpo)))
                        .andExpect(status().is(esperado)));
    }

    @Test
    void laClaseDeLaAtencionSeGuardaSeFiltraYSeValida() throws Exception {
        assertEquals("ProgramadaContrato", crearComo(null, 201).get("clase").asText());
        assertEquals(
                "ProgramadaContrato", crearComo("ProgramadaContrato", 201).get("clase").asText());
        long extraordinaria = crearComo("Extraordinaria", 201).get("id").asLong();
        crearComo("Otra", 400);
        crearComo("", 400);
        http.perform(get(BASE).param("clase", "Extraordinaria").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(extraordinaria))
                .andExpect(jsonPath("$.results[0].clase").value("Extraordinaria"));
        http.perform(
                        get(BASE)
                                .param("clase", "ProgramadaContrato")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.count").value(2));
        http.perform(get(BASE).param("clase", "Otra").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laExtraordinariaNoIniciaSinSuOrdenDeServicio() throws Exception {
        long id = crearComo("Extraordinaria", 201).get("id").asLong();
        var inicio = values("fecha_hora_inicio", OffsetDateTime.now().minusHours(1).toString());
        http.perform(
                        post(BASE + id + "/inicio/")
                                .with(httpBasic("jefe", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(inicio)))
                .andExpect(status().isConflict());
        var conOrden =
                patchAction(
                        id,
                        "orden-compra",
                        Map.of("numero_orden_compra", "OS-2026-001"),
                        "jefe",
                        200);
        assertEquals(Atencion.OC, conOrden.get("estado").asText());
        assertEquals("OS-2026-001", conOrden.get("numero_orden_compra").asText());
        assertEquals("Extraordinaria", conOrden.get("clase").asText());
        http.perform(
                        post(BASE + id + "/inicio/")
                                .with(httpBasic("jefe", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(inicio)))
                .andExpect(status().isOk());
    }

    @Test
    void laBandejaFiltraPorLosEstadosDeLaConformidad() throws Exception {
        long observada = conInforme();
        decidir(observada, "jefe", decision("Observada", "Falta el metrado"), 200);
        long conforme = conInforme();
        decidir(conforme, "jefe", decision("Conforme", ""), 200);
        http.perform(
                        get(BASE)
                                .param("estado", Atencion.OBSERVADO)
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(observada));
        http.perform(get(BASE).param("estado", Atencion.CONFORME).with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(conforme));
    }

    @Test
    void lasDecisionesDeConformidadSoloAdmitenInserciones() throws Exception {
        long id = conInforme();
        decidir(id, "jefe", decision("Conforme", ""), 200);
        assertThrows(
                DataAccessException.class,
                () ->
                        repo.jdbc()
                                .getJdbcTemplate()
                                .update("UPDATE mod05_conformidad SET comentarios='alterado'"));
        assertThrows(
                DataAccessException.class,
                () -> repo.jdbc().getJdbcTemplate().update("DELETE FROM mod05_conformidad"));
        assertEquals(1, count("conformidad"));
        // Cada decisión deja auditoría.
        assertEquals(
                1,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM mod05_registroauditoria"
                                        + " WHERE entidad='Conformidad'",
                                Map.of(),
                                Integer.class));
    }

    @Test
    void elAcuerdoDeLaUnidadSigueSiendoPosibleTrasLaConformidad() throws Exception {
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
        execute(id);
        enviar("jefe", id, informes(), "Resumen", hoy(), pdf("informe.pdf", "A"))
                .andExpect(status().isCreated());
        decidir(id, "jefe", decision("Conforme", ""), 200);
        var respuesta =
                patchAction(
                        id,
                        "acuerdo-unidad",
                        values(
                                "acuerdo_unidad",
                                false,
                                "fecha_acuerdo_unidad",
                                LocalDate.now(Mod05Application.LIMA),
                                "comentarios_acuerdo_unidad",
                                "Respuesta posterior"),
                        "jefe",
                        200);
        // El acuerdo no reabre ni cambia la decisión del jefe.
        assertEquals(Atencion.CONFORME, respuesta.get("estado").asText());
        assertEquals(1, respuesta.get("conformidades").size());
    }

    // --- RF-SER-009 (AV-320): datos del informe final según el formato estándar del servicio
    // ---------------------------------------------------------------------------------------------

    /**
     * Formato vigente del tipo de servicio del contrato de prueba: un obligatorio de cada clase.
     */
    long formatoDePrueba(int version) {
        long tipo =
                repo.jdbc()
                        .queryForObject(
                                "SELECT tipo_servicio_id FROM mod05_contrato WHERE id=:id",
                                Map.of("id", contrato),
                                Long.class);
        repo.jdbc()
                .update(
                        "UPDATE mod05_formato_reporte_servicio SET vigente=false WHERE"
                                + " tipo_servicio_id=:tipo",
                        Map.of("tipo", tipo));
        long formato =
                repo.jdbc()
                        .queryForObject(
                                "INSERT INTO mod05_formato_reporte_servicio"
                                        + " (tipo_servicio_id, version, vigente) VALUES (:tipo,"
                                        + " :version, true) RETURNING id",
                                Map.of("tipo", tipo, "version", version),
                                Long.class);
        repo.jdbc()
                .update(
                        """
                        INSERT INTO mod05_campo_formato_reporte
                            (formato_id, orden, nombre_campo, tipo_dato, obligatorio, subcampos)
                        VALUES (:f, 1, 'Metrado', 'Decimal', true, NULL),
                            (:f, 2, 'Productos aplicados', 'JSON', true, CAST(:sub AS jsonb)),
                            (:f, 3, 'Ruta recorrida', 'Texto largo', false, NULL)
                        """,
                        Map.of(
                                "f",
                                formato,
                                "sub",
                                "[{\"nombre\": \"producto\", \"tipo_dato\": \"Texto\","
                                        + " \"descripcion\": \"Producto\"}, {\"nombre\":"
                                        + " \"cantidad\", \"tipo_dato\": \"Decimal\","
                                        + " \"descripcion\": \"Cantidad\"}]"));
        return formato;
    }

    static final String DATOS =
            "{\"Metrado\": \"120,5\", \"Productos aplicados\": [{\"producto\": \"Abamectina\","
                    + " \"cantidad\": 2}, {}]}";

    ResultActions enviarConDatos(String user, long id, String datos, String contenido)
            throws Exception {
        var request = multipart(BASE + id + "/reportes/");
        request.file(pdf("informe.pdf", contenido))
                .param("documentos", "[" + INFORME + "]")
                .param("fecha_recepcion", hoy())
                .param("datos_formato", datos);
        if (user != null) request.with(httpBasic(user, PASSWORD));
        return http.perform(request);
    }

    @Test
    void sinFormatoVigenteElReporteSeAdjuntaComoAntes() throws Exception {
        long id = create().get("id").asLong();
        http.perform(get(BASE + id + "/formato-reporte/").with(httpBasic("practicante", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formato").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.datos_anteriores").value(org.hamcrest.Matchers.nullValue()));
        execute(id);
        var reporte =
                cuerpo(
                        enviar("jefe", id, "[" + INFORME + "]", null, hoy(), pdf("a.pdf", "a"))
                                .andExpect(status().isCreated()));
        assertTrue(reporte.get("formato_id").isNull());
        assertTrue(reporte.get("datos_formato").isNull());
        // Enviar datos sin formato vigente es un error y no registra nada.
        enviarConDatos("jefe", id, DATOS, "b")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detalle")
                                .value(org.hamcrest.Matchers.containsString("no tiene formato")));
        assertEquals(1, count("reporteproveedor"));
    }

    @Test
    void elFormatoVigenteSeConsultaYLosDatosSeGuardanConSuVersion() throws Exception {
        long formato = formatoDePrueba(1);
        long id = create().get("id").asLong();
        http.perform(get(BASE + id + "/formato-reporte/").with(httpBasic("ingeniero", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formato.id").value(formato))
                .andExpect(jsonPath("$.formato.version").value(1))
                .andExpect(jsonPath("$.formato.tipo_servicio_nombre").value("Corte de césped"))
                .andExpect(jsonPath("$.formato.campos[0].nombre_campo").value("Metrado"))
                .andExpect(jsonPath("$.formato.campos[0].tipo_dato").value("Decimal"))
                .andExpect(jsonPath("$.formato.campos[0].obligatorio").value(true))
                .andExpect(jsonPath("$.formato.campos[1].subcampos[1].nombre").value("cantidad"))
                .andExpect(jsonPath("$.formato.campos[1].subcampos[1].tipo_dato").value("Decimal"))
                .andExpect(jsonPath("$.datos_anteriores").value(org.hamcrest.Matchers.nullValue()));
        execute(id);
        var reporte =
                cuerpo(enviarConDatos("jefe", id, DATOS, "a").andExpect(status().isCreated()));
        assertEquals(formato, reporte.get("formato_id").asLong());
        assertEquals(1, reporte.get("formato_version").asInt());
        assertEquals("120.5", reporte.at("/datos_formato/Metrado").asText());
        assertEquals(1, reporte.at("/datos_formato/Productos aplicados").size());
        assertEquals("2", reporte.at("/datos_formato/Productos aplicados/0/cantidad").asText());
        assertTrue(reporte.at("/datos_formato/Ruta recorrida").isMissingNode());
        long r = reporte.get("id").asLong();
        http.perform(get(BASE + id + "/reportes/" + r + "/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.datos_formato.Metrado").value("120.5"));
        http.perform(get(BASE + id + "/reportes/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.results[0].formato_version").value(1));
        // La auditoría guarda los datos como objeto, no como el PGobject de jsonb.
        String auditado =
                repo.jdbc()
                        .queryForObject(
                                "SELECT valores_nuevos->'datos_formato'->>'Metrado' FROM"
                                    + " mod05_registroauditoria WHERE entidad='ReporteProveedor'"
                                    + " AND registro_id=:r",
                                Map.of("r", r),
                                String.class);
        assertEquals("120.5", auditado);
        // La siguiente revisión puede partir de estos datos.
        http.perform(get(BASE + id + "/formato-reporte/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.datos_anteriores.revision").value(1))
                .andExpect(jsonPath("$.datos_anteriores.datos.Metrado").value("120.5"));
        // Con un formato nuevo, los datos anteriores ya no se proponen y el informe conserva el
        // suyo.
        formatoDePrueba(2);
        http.perform(get(BASE + id + "/formato-reporte/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.formato.version").value(2))
                .andExpect(jsonPath("$.datos_anteriores").value(org.hamcrest.Matchers.nullValue()));
        http.perform(get(BASE + id + "/reportes/" + r + "/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.formato_version").value(1));
    }

    @Test
    void faltarUnObligatorioRechazaElEnvioEnteroSinDejarRastro() throws Exception {
        formatoDePrueba(1);
        long id = create().get("id").asLong();
        execute(id);
        long enDisco = archivosEnDisco(), auditoria = count("registroauditoria");
        enviarConDatos("jefe", id, "{\"Metrado\": \"3\"}", "a")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detalle")
                                .value(
                                        "Faltan datos obligatorios del informe final: Productos"
                                                + " aplicados"));
        enviarConDatos("jefe", id, "{\"Metrado\": \"3\", \"Otro\": 1}", "a")
                .andExpect(status().isBadRequest());
        enviarConDatos("jefe", id, "no es json", "a").andExpect(status().isBadRequest());
        assertEquals(0, count("reporteproveedor"));
        assertEquals(0, count("archivo"));
        assertEquals(enDisco, archivosEnDisco());
        assertEquals(auditoria, count("registroauditoria"));
        assertEquals(Atencion.EJECUTADO, repo.get("atenciontercerizada", id).get("estado"));
    }

    @Test
    void losDatosEntranEnLaHuellaDelReenvio() throws Exception {
        formatoDePrueba(1);
        long id = create().get("id").asLong();
        execute(id);
        var primero =
                cuerpo(enviarConDatos("jefe", id, DATOS, "a").andExpect(status().isCreated()));
        // Mismo contenido con las claves en otro orden: es el mismo envío.
        String reordenado =
                "{\"Productos aplicados\": [{\"cantidad\": \"2\", \"producto\": \"Abamectina\"}],"
                        + " \"Metrado\": 120.5}";
        var repetido =
                cuerpo(enviarConDatos("ingeniero", id, reordenado, "a").andExpect(status().isOk()));
        assertTrue(repetido.get("repetido").asBoolean());
        assertEquals(primero.get("id").asLong(), repetido.get("id").asLong());
        // Otro dato, o ninguno, es una revisión nueva.
        enviarConDatos("jefe", id, DATOS.replace("120,5", "121"), "a")
                .andExpect(status().isCreated());
        enviar("jefe", id, "[" + INFORME + "]", null, hoy(), pdf("informe.pdf", "a"))
                .andExpect(status().isCreated());
        assertEquals(3, count("reporteproveedor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sinrol", "inactivo"})
    void elFormatoExigeLosPermisosDelCicloDeAtencion(String user) throws Exception {
        formatoDePrueba(1);
        long id = create().get("id").asLong();
        http.perform(get(BASE + id + "/formato-reporte/").with(httpBasic(user, PASSWORD)))
                .andExpect(status().is(user.equals("inactivo") ? 401 : 403));
        http.perform(get(BASE + id + "/formato-reporte/")).andExpect(status().isUnauthorized());
        http.perform(get(BASE + "999999/formato-reporte/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isNotFound());
    }
}
