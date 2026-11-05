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
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

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
        BaseDePruebas.registrar(p);
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
                            + " mod05_usuario,mod05_proveedor,mod05_tiposervicio,mod05_areaverde,mod05_unidadmedida,mod05_unidadinstitucional,mod05_archivo,mod05_producto_agronomico"
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
        var request =
                switch (method) {
                    case "POST" -> post(BASE + path);
                    case "PUT" -> put(BASE + path);
                    case "PATCH" -> patch(BASE + path);
                    default -> get(BASE + path);
                };
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
                "contacto_nombre",
                "Rosa Demo",
                "contacto_telefono",
                "999 000 000");
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
                "frecuencia_cantidad",
                4,
                "frecuencia_unidad",
                "Anio",
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
                                "contacto_nombre",
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
        assertEquals("Rosa Demo", subsidiaria.get("contacto_nombre").asText());
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

    @Test
    void elJefeEditaRazonSocialRucContactoYVinculo() throws Exception {
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
        // Un proveedor creado por la API (con su fila de detalle), no el precargado del setup.
        long id = send("POST", "proveedores/", proveedorNuevo("20000000004"), "jefe", 201).get("id").asLong();
        var editado =
                send(
                        "PUT",
                        "proveedores/" + id + "/",
                        values(
                                "razon_social",
                                "Proveedor Demo Editado",
                                "ruc",
                                "20000000005",
                                "contacto_nombre",
                                "Nueva Rosa",
                                "contacto_telefono",
                                "999 111 222",
                                "contacto_correo",
                                "rosa@demo.pe",
                                "tipo_vinculo",
                                "Subsidiaria",
                                "proveedor_relacionado_id",
                                principal.get("id").asLong()),
                        "jefe",
                        200);
        assertEquals("Proveedor Demo Editado", editado.get("razon_social").asText());
        assertEquals("20000000005", editado.get("ruc").asText());
        assertEquals("Nueva Rosa", editado.get("contacto_nombre").asText());
        assertEquals("Subsidiaria", editado.get("tipo_vinculo").asText());
        assertEquals(
                "Servicios Verdes Demo S.A.C.", editado.get("proveedor_relacionado_nombre").asText());
        assertEquals(
                "Proveedor Demo Editado",
                send("GET", "proveedores/" + id + "/", null, "jefe", 200).get("razon_social").asText());
        assertEquals(2, count("proveedor_detalle")); // la edición actualiza el detalle, no lo duplica
        assertTrue(
                repo.jdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM mod05_registroauditoria WHERE"
                                                + " entidad='Proveedor' AND accion='Edición'",
                                        Map.of(),
                                        Long.class)
                        == 1);
    }

    @Test
    void soloElJefeEditaProveedores() throws Exception {
        for (String user : List.of("ingeniero", "practicante", "sinrol"))
            send("PUT", "proveedores/" + proveedor + "/", proveedorNuevo("20000000009"), user, 403);
        send("PUT", "proveedores/" + proveedor + "/", proveedorNuevo("20000000009"), null, 401);
        assertEquals(
                "Proveedor Demo",
                send("GET", "proveedores/" + proveedor + "/", null, "jefe", 200)
                        .get("razon_social")
                        .asText());
    }

    @Test
    void editarProveedorConRucDeOtroInformaLaCoincidenciaSinCambiarNada() throws Exception {
        send("POST", "proveedores/", proveedorNuevo("20000000002"), "jefe", 201);
        var error =
                send(
                        "PUT",
                        "proveedores/" + proveedor + "/",
                        proveedorNuevo("20000000002"),
                        "jefe",
                        409);
        assertEquals("ProveedorExistente", error.get("codigo").asText());
        assertEquals(
                "Proveedor Demo",
                send("GET", "proveedores/" + proveedor + "/", null, "jefe", 200)
                        .get("razon_social")
                        .asText());
    }

    @Test
    void editarProveedorInexistenteDa404() throws Exception {
        send("PUT", "proveedores/999999/", proveedorNuevo("20000000006"), "jefe", 404);
    }

    @Test
    void proveedorNoPuedeVincularseConsigoMismoAlEditar() throws Exception {
        var data = proveedorNuevo("20000000007");
        data.put("tipo_vinculo", "Subsidiaria");
        data.put("proveedor_relacionado_id", proveedor);
        send("PUT", "proveedores/" + proveedor + "/", data, "jefe", 400);
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
        assertEquals("4 veces al año", creado.get("frecuencia_pactada").asText());
        assertEquals(4, creado.get("frecuencia_cantidad").asInt());
        assertEquals("Anio", creado.get("frecuencia_unidad").asText());
        assertEquals("Proveedor Demo", creado.get("proveedor_nombre").asText());
        assertTrue(creado.get("vigente").asBoolean());
        assertEquals(0, creado.get("atenciones").asInt());
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
        assertEquals(
                1,
                send("GET", "contratos/" + creado.get("id").asLong() + "/", null, "jefe", 200)
                        .get("atenciones")
                        .asInt());
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

    @Test
    void contratoAceptaElTextoLibreDeFrecuenciaDeClientesAnteriores() throws Exception {
        var data = contratoNuevo();
        data.remove("frecuencia_cantidad");
        data.remove("frecuencia_unidad");
        data.put("frecuencia_pactada", "4 al año");
        var creado = send("POST", "contratos/", data, "jefe", 201);
        assertEquals("4 al año", creado.get("frecuencia_pactada").asText());
        assertTrue(creado.get("frecuencia_cantidad").isNull());
        assertTrue(creado.get("frecuencia_unidad").isNull());
    }

    @Test
    void elJefeEditaUnContrato() throws Exception {
        var creado = send("POST", "contratos/", contratoNuevo(), "jefe", 201);
        long id = creado.get("id").asLong();
        long otroProveedor =
                send(
                                "POST",
                                "proveedores/",
                                values("razon_social", "Otro Proveedor S.A.", "ruc", "20000000008"),
                                "jefe",
                                201)
                        .get("id")
                        .asLong();
        var data = contratoNuevo();
        data.put("proveedor_id", otroProveedor);
        data.put("area_metrado_estimado", "200 ejemplares");
        data.put("monto_fijo", "15000.00");
        data.put("frecuencia_cantidad", 2);
        data.put("frecuencia_unidad", "Mes");
        data.put("condiciones", "Pago a 15 días");
        var editado = send("PUT", "contratos/" + id + "/", data, "jefe", 200);
        assertEquals(otroProveedor, editado.get("proveedor_id").asLong());
        assertEquals("200 ejemplares", editado.get("area_metrado_estimado").asText());
        assertEquals("15000.00", editado.get("monto_fijo").asText());
        assertEquals("2 veces al mes", editado.get("frecuencia_pactada").asText());
        assertEquals("Pago a 15 días", editado.get("condiciones").asText());
        assertEquals(creado.get("numero").asText(), editado.get("numero").asText());
        assertEquals(
                creado.get("numero").asText(),
                send("GET", "contratos/" + id + "/", null, "jefe", 200).get("numero").asText());
        assertEquals(1, count("contrato_detalle"));
        assertTrue(
                repo.jdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM mod05_registroauditoria WHERE"
                                                + " entidad='Contrato' AND accion='Edición'",
                                        Map.of(),
                                        Long.class)
                        == 1);
    }

    @Test
    void soloElJefeEditaContratos() throws Exception {
        var creado = send("POST", "contratos/", contratoNuevo(), "jefe", 201);
        long id = creado.get("id").asLong();
        for (String user : List.of("ingeniero", "practicante", "sinrol"))
            send("PUT", "contratos/" + id + "/", contratoNuevo(), user, 403);
        send("PUT", "contratos/" + id + "/", contratoNuevo(), null, 401);
        assertEquals(
                creado.get("area_metrado_estimado").asText(),
                send("GET", "contratos/" + id + "/", null, "jefe", 200)
                        .get("area_metrado_estimado")
                        .asText());
    }

    @Test
    void editarContratoInexistenteDa404() throws Exception {
        send("PUT", "contratos/999999/", contratoNuevo(), "jefe", 404);
    }

    @Test
    void editarContratoConFinAnteriorAlInicioEsInvalido() throws Exception {
        var creado = send("POST", "contratos/", contratoNuevo(), "jefe", 201);
        var data = contratoNuevo();
        data.put("vigencia_fin", LocalDate.now(Mod05Application.LIMA).minusDays(1).toString());
        send("PUT", "contratos/" + creado.get("id").asLong() + "/", data, "jefe", 400);
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
                "frecuencia_cantidad_sin_unidad",
                "frecuencia_unidad_sin_cantidad",
                "frecuencia_fuera_de_rango",
                "frecuencia_unidad_invalida",
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
            case "sin_frecuencia" -> {
                data.remove("frecuencia_cantidad");
                data.remove("frecuencia_unidad");
            }
            case "frecuencia_cantidad_sin_unidad" -> data.remove("frecuencia_unidad");
            case "frecuencia_unidad_sin_cantidad" -> data.remove("frecuencia_cantidad");
            case "frecuencia_fuera_de_rango" -> data.put("frecuencia_cantidad", 400);
            case "frecuencia_unidad_invalida" -> data.put("frecuencia_unidad", "Hora");
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

    // --- RF-FIT-003
    // ---------------------------------------------------------------------------------------------

    Map<String, Object> productoNuevo(String nombre) {
        return values(
                "nombre", nombre, "principio_activo", "Imidacloprid", "presentacion", "Frasco 250 ml");
    }

    @Test
    void elJefeRegistraYEditaUnProductoAgronomico() throws Exception {
        var creado = send("POST", "productos-agronomicos/", productoNuevo("Insecticida Demo"), "jefe", 201);
        assertEquals("Insecticida Demo", creado.get("nombre").asText());
        assertEquals("Imidacloprid", creado.get("principio_activo").asText());
        assertTrue(creado.get("activo").asBoolean());
        long id = creado.get("id").asLong();

        var editado =
                send(
                        "PUT",
                        "productos-agronomicos/" + id + "/",
                        values("nombre", "Insecticida Editado", "principio_activo", "Abamectina", "presentacion", "Bidón 1 L"),
                        "jefe",
                        200);
        assertEquals("Insecticida Editado", editado.get("nombre").asText());
        assertEquals("Abamectina", editado.get("principio_activo").asText());
        assertEquals(
                "Insecticida Editado",
                send("GET", "productos-agronomicos/" + id + "/", null, "practicante", 200).get("nombre").asText());

        var lista = send("GET", "productos-agronomicos/", null, "ingeniero", 200);
        assertEquals(1, lista.get("count").asInt());
        assertTrue(
                repo.jdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM mod05_registroauditoria WHERE"
                                                + " entidad='ProductoAgronomico' AND accion='Alta'",
                                        Map.of(),
                                        Long.class)
                        == 1);
    }

    @Test
    void soloElJefeRegistraYEditaProductosAgronomicos() throws Exception {
        long id =
                send("POST", "productos-agronomicos/", productoNuevo("Producto Demo"), "jefe", 201)
                        .get("id")
                        .asLong();
        for (String user : List.of("ingeniero", "practicante", "sinrol")) {
            send("POST", "productos-agronomicos/", productoNuevo("Otro " + user), user, 403);
            send("PUT", "productos-agronomicos/" + id + "/", productoNuevo("Otro " + user), user, 403);
        }
        assertEquals(1, count("producto_agronomico"));
    }

    @Test
    void nombreRepetidoInformaLaCoincidenciaSinCrearNada() throws Exception {
        send("POST", "productos-agronomicos/", productoNuevo("Insecticida Demo"), "jefe", 201);
        var error = send("POST", "productos-agronomicos/", productoNuevo("Insecticida Demo"), "jefe", 409);
        assertEquals("ProductoExistente", error.get("codigo").asText());
        assertEquals(1, count("producto_agronomico"));
    }

    @Test
    void elJefeInhabilitaYHabilitaUnProductoAgronomico() throws Exception {
        long id =
                send("POST", "productos-agronomicos/", productoNuevo("Producto Demo"), "jefe", 201)
                        .get("id")
                        .asLong();
        var inhabilitado =
                send("PATCH", "productos-agronomicos/" + id + "/estado/", values("activo", false), "jefe", 200);
        assertFalse(inhabilitado.get("activo").asBoolean());
        var habilitado =
                send("PATCH", "productos-agronomicos/" + id + "/estado/", values("activo", true), "jefe", 200);
        assertTrue(habilitado.get("activo").asBoolean());
    }

    @Test
    void laAtencionRegistraLosProductosAplicadosConDosisYLosConservaSiElProductoSeDesactiva()
            throws Exception {
        long producto =
                send("POST", "productos-agronomicos/", productoNuevo("Insecticida Demo"), "jefe", 201)
                        .get("id")
                        .asLong();
        var atencion =
                values(
                        "contrato_id",
                        contrato,
                        "descripcion",
                        "Control fitosanitario de prueba",
                        "origen",
                        "Demanda",
                        "alcances",
                        List.of(values("area_verde_id", area)),
                        "productos",
                        List.of(values("producto_id", producto, "dosis", "5 ml por litro")));
        var creada = send("POST", "atenciones/", atencion, "jefe", 201);
        assertEquals(1, creada.get("productos").size());
        assertEquals("Insecticida Demo", creada.get("productos").get(0).get("producto_nombre").asText());
        assertEquals("5 ml por litro", creada.get("productos").get(0).get("dosis").asText());

        send("PATCH", "productos-agronomicos/" + producto + "/estado/", values("activo", false), "jefe", 200);
        var detalle = send("GET", "atenciones/" + creada.get("id").asLong() + "/", null, "jefe", 200);
        assertEquals("Insecticida Demo", detalle.get("productos").get(0).get("producto_nombre").asText());
        assertFalse(detalle.get("productos").get(0).get("producto_activo").asBoolean());
    }

    @Test
    void noSePuedeAplicarUnProductoInactivoNiRepetirloEnLaMismaAtencion() throws Exception {
        long producto =
                send("POST", "productos-agronomicos/", productoNuevo("Insecticida Demo"), "jefe", 201)
                        .get("id")
                        .asLong();
        send("PATCH", "productos-agronomicos/" + producto + "/estado/", values("activo", false), "jefe", 200);
        var conProductoInactivo =
                values(
                        "contrato_id",
                        contrato,
                        "descripcion",
                        "Control fitosanitario",
                        "origen",
                        "Demanda",
                        "alcances",
                        List.of(values("area_verde_id", area)),
                        "productos",
                        List.of(values("producto_id", producto, "dosis", "")));
        send("POST", "atenciones/", conProductoInactivo, "jefe", 400);

        send("PATCH", "productos-agronomicos/" + producto + "/estado/", values("activo", true), "jefe", 200);
        var conProductoRepetido =
                values(
                        "contrato_id",
                        contrato,
                        "descripcion",
                        "Control fitosanitario",
                        "origen",
                        "Demanda",
                        "alcances",
                        List.of(values("area_verde_id", area)),
                        "productos",
                        List.of(
                                values("producto_id", producto, "dosis", "5 ml"),
                                values("producto_id", producto, "dosis", "otra dosis")));
        send("POST", "atenciones/", conProductoRepetido, "jefe", 400);
        assertEquals(0, count("atencion_producto"));
    }

    // --- RF-FIT-004
    // ---------------------------------------------------------------------------------------------

    @Test
    void laAtencionRegistraLasPlagasControladasYLasConservaSiLaPlagaSeDesactiva() throws Exception {
        long pulgon = plaga("Pulgón"), cochinilla = plaga("Cochinilla");
        var atencion =
                values(
                        "contrato_id",
                        contrato,
                        "descripcion",
                        "Control fitosanitario de prueba",
                        "origen",
                        "Demanda",
                        "alcances",
                        List.of(values("area_verde_id", area)),
                        "plagas_controladas",
                        List.of(pulgon, cochinilla));
        var creada = send("POST", "atenciones/", atencion, "jefe", 201);
        assertEquals(2, creada.get("plagas").size());

        repo.jdbc().update("UPDATE mod05_plaga SET activo=false WHERE id=:id", Map.of("id", pulgon));
        try {
            var detalle = send("GET", "atenciones/" + creada.get("id").asLong() + "/", null, "jefe", 200);
            var nombres =
                    List.of(
                            detalle.get("plagas").get(0).get("plaga_nombre").asText(),
                            detalle.get("plagas").get(1).get("plaga_nombre").asText());
            assertTrue(nombres.containsAll(List.of("Pulgón", "Cochinilla")));
            boolean algunaInactiva = false;
            for (var p : detalle.get("plagas")) if (!p.get("plaga_activo").asBoolean()) algunaInactiva = true;
            assertTrue(algunaInactiva);
        } finally {
            repo.jdbc().update("UPDATE mod05_plaga SET activo=true", Map.of());
        }
    }

    @Test
    void noSePuedeControlarUnaPlagaInactivaNiRepetirlaEnLaMismaAtencion() throws Exception {
        long pulgon = plaga("Pulgón");
        repo.jdbc().update("UPDATE mod05_plaga SET activo=false WHERE id=:id", Map.of("id", pulgon));
        var conPlagaInactiva =
                values(
                        "contrato_id",
                        contrato,
                        "descripcion",
                        "Control fitosanitario",
                        "origen",
                        "Demanda",
                        "alcances",
                        List.of(values("area_verde_id", area)),
                        "plagas_controladas",
                        List.of(pulgon));
        send("POST", "atenciones/", conPlagaInactiva, "jefe", 400);

        repo.jdbc().update("UPDATE mod05_plaga SET activo=true", Map.of());
        var conPlagaRepetida =
                values(
                        "contrato_id",
                        contrato,
                        "descripcion",
                        "Control fitosanitario",
                        "origen",
                        "Demanda",
                        "alcances",
                        List.of(values("area_verde_id", area)),
                        "plagas_controladas",
                        List.of(pulgon, pulgon));
        send("POST", "atenciones/", conPlagaRepetida, "jefe", 400);
        assertEquals(0, count("atencion_plaga"));
    }

    // --- RF-FIT-005
    // ---------------------------------------------------------------------------------------------

    Map<String, Object> observacionNueva(Long areaVerdeId, Long ejemplarId, long plagaId) {
        return values(
                "area_verde_id",
                areaVerdeId,
                "ejemplar_id",
                ejemplarId,
                "plaga_id",
                plagaId,
                "descripcion",
                "Presencia visible en hojas jóvenes",
                "fecha_observacion",
                LocalDate.now(Mod05Application.LIMA).toString());
    }

    @Test
    void losTresRolesRegistranObservacionesEnUnEspacioOUnEjemplarYSeListan() throws Exception {
        long pulgon = plaga("Pulgón"), cochinilla = plaga("Cochinilla");
        var enEspacio =
                send(
                        "POST",
                        "observaciones-plagas/",
                        observacionNueva(area, null, pulgon),
                        "ingeniero",
                        201);
        assertEquals("Jardín A", enEspacio.get("area_verde_nombre").asText());
        assertTrue(enEspacio.get("ejemplar_codigo").isNull());
        assertEquals("Pulgón", enEspacio.get("plaga_nombre").asText());
        assertEquals("Nombre ingeniero", enEspacio.get("usuario_creacion_nombre").asText());

        var enEjemplar =
                send(
                        "POST",
                        "observaciones-plagas/",
                        observacionNueva(null, ejemplar, cochinilla),
                        "practicante",
                        201);
        assertEquals("E-01", enEjemplar.get("ejemplar_codigo").asText());
        assertTrue(enEjemplar.get("area_verde_nombre").isNull());

        var lista = send("GET", "observaciones-plagas/", null, "jefe", 200);
        assertEquals(2, lista.get("count").asInt());
        assertEquals(
                1,
                send("GET", "observaciones-plagas/?plaga_id=" + pulgon, null, "jefe", 200)
                        .get("count")
                        .asInt());
        assertEquals(
                1,
                send("GET", "observaciones-plagas/?area_verde_id=" + area, null, "jefe", 200)
                        .get("count")
                        .asInt());
        assertEquals(
                1,
                send("GET", "observaciones-plagas/?ejemplar_id=" + ejemplar, null, "jefe", 200)
                        .get("count")
                        .asInt());
        assertEquals(
                "Pulgón",
                send("GET", "observaciones-plagas/" + enEspacio.get("id").asLong() + "/", null, "practicante", 200)
                        .get("plaga_nombre")
                        .asText());
        assertTrue(
                repo.jdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM mod05_registroauditoria WHERE"
                                                + " entidad='ObservacionPlaga' AND accion='Alta'",
                                        Map.of(),
                                        Long.class)
                        == 2);
    }

    @Test
    void elAvisoDelAsistenteConsultaObservacionesPorEspacioOEjemplar() throws Exception {
        long pulgon = plaga("Pulgón");
        send("POST", "observaciones-plagas/", observacionNueva(area, null, pulgon), "jefe", 201);
        send("POST", "observaciones-plagas/", observacionNueva(null, ejemplar2, pulgon), "jefe", 201);

        var porAreaYEjemplar =
                send(
                        "GET",
                        "observaciones-plagas/por-alcance/?area_verde_id=" + area + "&ejemplar_id=" + ejemplar2,
                        null,
                        "practicante",
                        200); // un solo id en cada filtro: la coma se prueba abajo
        assertEquals(2, porAreaYEjemplar.size());

        var soloElOtroEjemplar =
                send("GET", "observaciones-plagas/por-alcance/?ejemplar_id=" + ejemplar, null, "practicante", 200);
        assertEquals(0, soloElOtroEjemplar.size());

        // Varios destinos del mismo tipo, separados por comas (varios espacios/ejemplares elegidos a la vez).
        var porDosEjemplares =
                send(
                        "GET",
                        "observaciones-plagas/por-alcance/?ejemplar_id=" + ejemplar + "," + ejemplar2,
                        null,
                        "practicante",
                        200);
        assertEquals(1, porDosEjemplares.size());

        var sinFiltro = send("GET", "observaciones-plagas/por-alcance/", null, "practicante", 200);
        assertEquals(0, sinFiltro.size());
    }

    @Test
    void sinRolOSinCredencialesNoRegistranNiConsultanObservaciones() throws Exception {
        var body = observacionNueva(area, null, plaga("Pulgón"));
        send("POST", "observaciones-plagas/", body, "sinrol", 403);
        send("POST", "observaciones-plagas/", body, null, 401);
        send("GET", "observaciones-plagas/", null, "sinrol", 403);
        send("GET", "observaciones-plagas/por-alcance/?area_verde_id=" + area, null, null, 401);
        assertEquals(0, count("observacion_plaga"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "sin_destino",
                "ambos_destinos",
                "sin_plaga",
                "sin_descripcion",
                "sin_fecha",
                "fecha_futura",
                "area_inexistente",
                "ejemplar_inexistente",
                "plaga_inexistente",
                "plaga_inactiva"
            })
    void observacionInvalidaNoCreaNada(String caso) throws Exception {
        var data = observacionNueva(area, null, plaga("Pulgón"));
        int esperado = 400;
        switch (caso) {
            case "sin_destino" -> data.put("area_verde_id", null);
            case "ambos_destinos" -> data.put("ejemplar_id", ejemplar);
            case "sin_plaga" -> data.remove("plaga_id");
            case "sin_descripcion" -> data.put("descripcion", "  ");
            case "sin_fecha" -> data.remove("fecha_observacion");
            case "fecha_futura" ->
                    data.put(
                            "fecha_observacion",
                            LocalDate.now(Mod05Application.LIMA).plusDays(1).toString());
            case "area_inexistente" -> {
                data.put("area_verde_id", 999999);
                esperado = 404;
            }
            case "ejemplar_inexistente" -> {
                data.put("area_verde_id", null);
                data.put("ejemplar_id", 999999);
                esperado = 404;
            }
            case "plaga_inexistente" -> {
                data.put("plaga_id", 999999);
                esperado = 404;
            }
            case "plaga_inactiva" -> {
                repo.jdbc()
                        .update(
                                "UPDATE mod05_plaga SET activo=false WHERE id=:id",
                                Map.of("id", plaga("Pulgón")));
                try {
                    send("POST", "observaciones-plagas/", data, "jefe", 400);
                } finally {
                    repo.jdbc().update("UPDATE mod05_plaga SET activo=true", Map.of());
                }
                assertEquals(0, count("observacion_plaga"));
                return;
            }
        }
        send("POST", "observaciones-plagas/", data, "jefe", esperado);
        assertEquals(0, count("observacion_plaga"));
    }
}
