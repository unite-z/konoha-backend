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
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RF-SER-001, RF-SER-002 y RF-INV-011. Misma base *_test y mismos usuarios que
 * Mod05IntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MaestrosIntegrationTest {
    static final String BASE = "/api/tercerizados/", PASSWORD = "prueba-integration-local";
    static final Path FILES;

    static {
        try {
            FILES = Files.createTempDirectory("mod05-maestros-test-");
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
    long proveedor, tipo, tipoPropio, contrato, area, ejemplar, ejemplar2;

    @BeforeEach
    void setup() {
        // Exclusivamente base *_test: nunca se ejecuta contra la base de desarrollo.
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        "TRUNCATE"
                            + " mod05_usuario,mod05_proveedor,mod05_tiposervicio,mod05_areaverde,mod05_unidadmedida,mod05_unidadinstitucional,mod05_archivo"
                            + " RESTART IDENTITY CASCADE");
        for (String name : List.of("jefe", "ingeniero", "practicante", "sinrol")) {
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
                                    true));
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
        proveedor =
                repo.insert(
                        "proveedor",
                        values("razon_social", "Proveedor Demo", "ruc", null, "activo", true));
        tipo =
                repo.insert(
                        "tiposervicio",
                        values(
                                "nombre",
                                "Poda",
                                "ejecutor",
                                "Tercerizado",
                                "es_riesgo",
                                false,
                                "activo",
                                true));
        tipoPropio =
                repo.insert(
                        "tiposervicio",
                        values(
                                "nombre",
                                "Riego",
                                "ejecutor",
                                "PersonalPropio",
                                "es_riesgo",
                                false,
                                "activo",
                                true));
        contrato =
                repo.insert(
                        "contrato",
                        values(
                                "numero",
                                "CT-PRECARGADO",
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
        ejemplar2 = repo.insert("ejemplar", values("codigo", "E-02", "area_verde_id", area));
    }

    long count(String table) {
        return repo.jdbc()
                .getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM mod05_" + table, Long.class);
    }

    JsonNode send(String method, String path, Object body, String user, int expected)
            throws Exception {
        var request = method.equals("POST") ? post(BASE + path) : get(BASE + path);
        if (user != null) request = request.with(httpBasic(user, PASSWORD));
        if (body != null)
            request =
                    request.contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(body));
        var result = http.perform(request).andExpect(status().is(expected)).andReturn();
        var bytes = result.getResponse().getContentAsByteArray();
        return bytes.length == 0 ? null : json.readTree(bytes);
    }

    Map<String, Object> proveedorNuevo(String ruc) {
        return values(
                "razon_social",
                "Fumigaciones Demo S.A.",
                "ruc",
                ruc,
                "contacto",
                "Rosa Demo · 999 000 000");
    }

    Map<String, Object> contratoNuevo() {
        return values(
                "proveedor_id",
                proveedor,
                "tipo_servicio_id",
                tipo,
                "area_metrado_estimado",
                "120 ejemplares",
                "monto_fijo",
                "12000.00",
                "frecuencia_pactada",
                "4 al año",
                "vigencia_inicio",
                LocalDate.now(Mod05Application.LIMA).toString(),
                "vigencia_fin",
                LocalDate.now(Mod05Application.LIMA).plusYears(1).toString(),
                "condiciones",
                "Pago a 30 días");
    }

    // --- RF-SER-001
    // ---------------------------------------------------------------------------------------------

    @Test
    void proveedorSeRegistraYSeListaConSuVinculo() throws Exception {
        var principal =
                send(
                        "POST",
                        "proveedores/",
                        values(
                                "razon_social",
                                "Servicios Verdes Demo S.A.C.",
                                "ruc",
                                "20000000001",
                                "tipo_vinculo",
                                "Principal"),
                        "jefe",
                        201);
        assertEquals("Principal", principal.get("tipo_vinculo").asText());
        assertTrue(principal.get("activo").asBoolean());
        var subsidiaria =
                send(
                        "POST",
                        "proveedores/",
                        values(
                                "razon_social",
                                "Fumigaciones Demo S.A.",
                                "ruc",
                                "20000000002",
                                "contacto",
                                "Rosa Demo",
                                "tipo_vinculo",
                                "Subsidiaria",
                                "proveedor_relacionado_id",
                                principal.get("id").asLong()),
                        "jefe",
                        201);
        assertEquals(
                "Servicios Verdes Demo S.A.C.",
                subsidiaria.get("proveedor_relacionado_nombre").asText());
        assertEquals("Rosa Demo", subsidiaria.get("contacto").asText());
        send(
                "POST",
                "proveedores/",
                proveedorNuevo("20000000003"),
                "jefe",
                201); // sin vínculo por omisión

        var todos = send("GET", "proveedores/", null, "practicante", 200);
        assertEquals(4, todos.get("count").asInt()); // incluye el proveedor precargado del setup
        assertEquals(
                1,
                send("GET", "proveedores/?tipo_vinculo=Subsidiaria", null, "ingeniero", 200)
                        .get("count")
                        .asInt());
        assertEquals(
                1,
                send("GET", "proveedores/?q=20000000002", null, "jefe", 200).get("count").asInt());
        assertEquals(
                "SinVinculo",
                send("GET", "proveedores/" + proveedor + "/", null, "jefe", 200)
                        .get("tipo_vinculo")
                        .asText());
        send("GET", "proveedores/?tipo_vinculo=Cualquiera", null, "jefe", 400);
        assertEquals(3, count("proveedor_detalle"));
        assertTrue(
                repo.jdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM mod05_registroauditoria WHERE"
                                                + " entidad='Proveedor' AND accion='Alta'",
                                        Map.of(),
                                        Long.class)
                        == 3);
    }

    @Test
    void soloElJefeRegistraProveedores() throws Exception {
        for (String user : List.of("ingeniero", "practicante", "sinrol"))
            send("POST", "proveedores/", proveedorNuevo("20000000009"), user, 403);
        send("POST", "proveedores/", proveedorNuevo("20000000009"), null, 401);
        assertEquals(1, count("proveedor"));
    }

    @Test
    void rucRepetidoInformaLaCoincidenciaSinCrearNada() throws Exception {
        send("POST", "proveedores/", proveedorNuevo("20000000002"), "jefe", 201);
        var error = send("POST", "proveedores/", proveedorNuevo("20000000002"), "jefe", 409);
        assertEquals("ProveedorExistente", error.get("codigo").asText());
        assertEquals(2, count("proveedor"));
        assertEquals(1, count("proveedor_detalle"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "sin_razon",
                "sin_ruc",
                "ruc_corto",
                "ruc_letras",
                "subsidiaria_sin_relacionado",
                "principal_con_relacionado",
                "relacionado_inexistente",
                "vinculo_invalido",
                "campo_extra"
            })
    void proveedorInvalidoNoCreaRegistrosParciales(String caso) throws Exception {
        var data = proveedorNuevo("20000000004");
        switch (caso) {
            case "sin_razon" -> data.put("razon_social", " ");
            case "sin_ruc" -> data.remove("ruc");
            case "ruc_corto" -> data.put("ruc", "2000");
            case "ruc_letras" -> data.put("ruc", "2000000000A");
            case "subsidiaria_sin_relacionado" -> data.put("tipo_vinculo", "Subsidiaria");
            case "principal_con_relacionado" -> {
                data.put("tipo_vinculo", "Principal");
                data.put("proveedor_relacionado_id", proveedor);
            }
            case "relacionado_inexistente" -> {
                data.put("tipo_vinculo", "Vinculada");
                data.put("proveedor_relacionado_id", 999999);
            }
            case "vinculo_invalido" -> data.put("tipo_vinculo", "Hermana");
            case "campo_extra" -> data.put("activo", false);
        }
        send("POST", "proveedores/", data, "jefe", 400);
        assertEquals(1, count("proveedor"));
        assertEquals(0, count("proveedor_detalle"));
    }

    // --- RF-SER-002
    // ---------------------------------------------------------------------------------------------

    @Test
    void contratoSeRegistraConSusCondicionesYAparecePrecargadoSinEllas() throws Exception {
        var creado = send("POST", "contratos/", contratoNuevo(), "jefe", 201);
        assertTrue(creado.get("numero").asText().matches("CT-\\d{4}-\\d{3,}"));
        assertEquals("12000.00", creado.get("monto_fijo").asText());
        assertEquals("4 al año", creado.get("frecuencia_pactada").asText());
        assertEquals("Proveedor Demo", creado.get("proveedor_nombre").asText());
        assertTrue(creado.get("vigente").asBoolean());
        var lista = send("GET", "contratos/", null, "practicante", 200);
        assertEquals(2, lista.get("count").asInt());
        // HU-MOD01-007, «Campos económicos ocultos»: solo el jefe de sección ve el monto.
        String ruta = "contratos/" + creado.get("id").asLong() + "/";
        assertTrue(send("GET", ruta, null, "ingeniero", 200).get("monto_fijo").isNull());
        lista.get("results").forEach(c -> assertTrue(c.get("monto_fijo").isNull()));
        assertEquals("12000.00", send("GET", ruta, null, "jefe", 200).get("monto_fijo").asText());
        var precargado = send("GET", "contratos/" + contrato + "/", null, "jefe", 200);
        assertTrue(precargado.get("monto_fijo").isNull()); // el precargado no trae condiciones
        assertTrue(precargado.get("vigente").asBoolean());
        send("GET", "contratos/?q=poda", null, "jefe", 200);
        assertEquals(
                2, send("GET", "contratos/?vigente=true", null, "jefe", 200).get("count").asInt());
        send("GET", "contratos/?vigente=quizas", null, "jefe", 400);
        // Un contrato nuevo se puede usar en una atención, y los números no se repiten.
        var segundo = send("POST", "contratos/", contratoNuevo(), "jefe", 201);
        assertNotEquals(creado.get("numero").asText(), segundo.get("numero").asText());
        var atencion =
                values(
                        "contrato_id",
                        creado.get("id").asLong(),
                        "descripcion",
                        "Poda de prueba",
                        "origen",
                        "Demanda",
                        "alcances",
                        List.of(values("area_verde_id", area)));
        http.perform(
                        post(BASE + "atenciones/")
                                .with(httpBasic("jefe", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(atencion)))
                .andExpect(status().isCreated());
    }

    @Test
    void contratoVencidoSeConservaComoHistorico() throws Exception {
        var data = contratoNuevo();
        data.put("vigencia_inicio", LocalDate.now(Mod05Application.LIMA).minusYears(2).toString());
        data.put("vigencia_fin", LocalDate.now(Mod05Application.LIMA).minusYears(1).toString());
        var creado = send("POST", "contratos/", data, "jefe", 201);
        assertFalse(creado.get("vigente").asBoolean());
        assertEquals(
                1, send("GET", "contratos/?vigente=false", null, "jefe", 200).get("count").asInt());
        // No se puede usar para una nueva atención (regla existente de RF-SER-003).
        var atencion =
                values(
                        "contrato_id",
                        creado.get("id").asLong(),
                        "descripcion",
                        "Poda",
                        "origen",
                        "Demanda",
                        "alcances",
                        List.of(values("area_verde_id", area)));
        http.perform(
                        post(BASE + "atenciones/")
                                .with(httpBasic("jefe", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(atencion)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloElJefeRegistraContratos() throws Exception {
        for (String user : List.of("ingeniero", "practicante", "sinrol"))
            send("POST", "contratos/", contratoNuevo(), user, 403);
        assertEquals(1, count("contrato"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "sin_proveedor",
                "sin_servicio",
                "sin_area",
                "sin_monto",
                "monto_negativo",
                "monto_decimales",
                "sin_frecuencia",
                "sin_vigencia",
                "fin_antes_de_inicio",
                "proveedor_inexistente",
                "servicio_propio",
                "servicio_inexistente",
                "campo_extra"
            })
    void contratoInvalidoNoCreaRegistrosParciales(String caso) throws Exception {
        var data = contratoNuevo();
        switch (caso) {
            case "sin_proveedor" -> data.remove("proveedor_id");
            case "sin_servicio" -> data.remove("tipo_servicio_id");
            case "sin_area" -> data.put("area_metrado_estimado", "");
            case "sin_monto" -> data.remove("monto_fijo");
            case "monto_negativo" -> data.put("monto_fijo", "-1");
            case "monto_decimales" -> data.put("monto_fijo", "10.123");
            case "sin_frecuencia" -> data.put("frecuencia_pactada", " ");
            case "sin_vigencia" -> data.remove("vigencia_inicio");
            case "fin_antes_de_inicio" ->
                    data.put(
                            "vigencia_fin",
                            LocalDate.now(Mod05Application.LIMA).minusDays(1).toString());
            case "proveedor_inexistente" -> data.put("proveedor_id", 999999);
            case "servicio_propio" -> data.put("tipo_servicio_id", tipoPropio);
            case "servicio_inexistente" -> data.put("tipo_servicio_id", 999999);
            case "campo_extra" -> data.put("numero", "CT-MANUAL");
        }
        send("POST", "contratos/", data, "jefe", 400);
        assertEquals(1, count("contrato"));
        assertEquals(0, count("contrato_detalle"));
    }

    // --- RF-INV-011
    // ---------------------------------------------------------------------------------------------

    long plaga(String nombre) {
        return repo.jdbc()
                .queryForObject(
                        "SELECT id FROM mod05_plaga WHERE nombre=:n",
                        Map.of("n", nombre),
                        Long.class);
    }

    @Test
    void losTresRolesMarcanEjemplaresConPlagaYSeListan() throws Exception {
        long pulgon = plaga("Pulgón"), cochinilla = plaga("Cochinilla");
        var marcado =
                send(
                        "POST",
                        "atenciones-especiales/",
                        values(
                                "ejemplar_id",
                                ejemplar,
                                "plaga_id",
                                pulgon,
                                "aplicaciones_adicionales",
                                2),
                        "ingeniero",
                        201);
        assertEquals("En seguimiento", marcado.get("estado").asText());
        assertEquals("Pulgón", marcado.get("plaga_nombre").asText());
        assertEquals("E-01", marcado.get("ejemplar_codigo").asText());
        assertEquals(0, marcado.get("aplicaciones_realizadas").asInt());
        send(
                "POST",
                "atenciones-especiales/",
                values(
                        "ejemplar_id",
                        ejemplar2,
                        "plaga_id",
                        cochinilla,
                        "aplicaciones_adicionales",
                        1),
                "practicante",
                201);

        var lista = send("GET", "atenciones-especiales/", null, "jefe", 200);
        assertEquals(2, lista.get("count").asInt());
        assertEquals(
                1,
                send("GET", "atenciones-especiales/?plaga_id=" + pulgon, null, "jefe", 200)
                        .get("count")
                        .asInt());
        assertEquals(
                1,
                send("GET", "atenciones-especiales/?q=cochinilla", null, "jefe", 200)
                        .get("count")
                        .asInt());
        assertEquals(
                2,
                send("GET", "atenciones-especiales/?estado=En seguimiento", null, "jefe", 200)
                        .get("count")
                        .asInt());
        assertEquals(
                0,
                send(
                                "GET",
                                "atenciones-especiales/?estado=Aplicaciones completas",
                                null,
                                "jefe",
                                200)
                        .get("count")
                        .asInt());
        send("GET", "atenciones-especiales/?estado=Otro", null, "jefe", 400);
        assertEquals(
                "E-01",
                send(
                                "GET",
                                "atenciones-especiales/" + marcado.get("id").asLong() + "/",
                                null,
                                "jefe",
                                200)
                        .get("ejemplar_codigo")
                        .asText());
        assertTrue(send("GET", "plagas/", null, "practicante", 200).get("count").asInt() >= 3);
        assertTrue(
                repo.jdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM mod05_registroauditoria WHERE"
                                                + " entidad='AtencionEspecial'",
                                        Map.of(),
                                        Long.class)
                        == 2);
    }

    @Test
    void unEjemplarSeMarcaUnaSolaVez() throws Exception {
        var body =
                values(
                        "ejemplar_id",
                        ejemplar,
                        "plaga_id",
                        plaga("Pulgón"),
                        "aplicaciones_adicionales",
                        2);
        send("POST", "atenciones-especiales/", body, "jefe", 201);
        send("POST", "atenciones-especiales/", body, "jefe", 409);
        assertEquals(1, count("atencion_especial"));
    }

    @Test
    void sinRolOSinCredencialesNoMarcanNiConsultan() throws Exception {
        var body =
                values(
                        "ejemplar_id",
                        ejemplar,
                        "plaga_id",
                        plaga("Pulgón"),
                        "aplicaciones_adicionales",
                        1);
        send("POST", "atenciones-especiales/", body, "sinrol", 403);
        send("POST", "atenciones-especiales/", body, null, 401);
        send("GET", "atenciones-especiales/", null, "sinrol", 403);
        send("GET", "plagas/", null, null, 401);
        assertEquals(0, count("atencion_especial"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "sin_ejemplar",
                "sin_plaga",
                "sin_aplicaciones",
                "cero_aplicaciones",
                "tres_aplicaciones",
                "ejemplar_inexistente",
                "plaga_inexistente",
                "plaga_inactiva",
                "campo_extra"
            })
    void marcadoInvalidoNoCreaNada(String caso) throws Exception {
        var data =
                values(
                        "ejemplar_id",
                        ejemplar,
                        "plaga_id",
                        plaga("Pulgón"),
                        "aplicaciones_adicionales",
                        2);
        switch (caso) {
            case "sin_ejemplar" -> data.remove("ejemplar_id");
            case "sin_plaga" -> data.remove("plaga_id");
            case "sin_aplicaciones" -> data.remove("aplicaciones_adicionales");
            case "cero_aplicaciones" -> data.put("aplicaciones_adicionales", 0);
            case "tres_aplicaciones" -> data.put("aplicaciones_adicionales", 3);
            case "ejemplar_inexistente" -> data.put("ejemplar_id", 999999);
            case "plaga_inexistente" -> data.put("plaga_id", 999999);
            case "plaga_inactiva" -> {
                repo.jdbc()
                        .update(
                                "UPDATE mod05_plaga SET activo=false WHERE id=:id",
                                Map.of("id", plaga("Pulgón")));
                try {
                    send("POST", "atenciones-especiales/", data, "jefe", 400);
                } finally {
                    repo.jdbc().update("UPDATE mod05_plaga SET activo=true", Map.of());
                }
                assertEquals(0, count("atencion_especial"));
                return;
            }
            case "campo_extra" -> data.put("aplicaciones_realizadas", 1);
        }
        send("POST", "atenciones-especiales/", data, "jefe", 400);
        assertEquals(0, count("atencion_especial"));
    }
}
