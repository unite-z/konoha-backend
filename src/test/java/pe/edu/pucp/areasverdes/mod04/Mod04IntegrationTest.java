package pe.edu.pucp.areasverdes.mod04;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.number;
import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.values;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.pucp.areasverdes.mod04.api.OperacionCampoController;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod04.servicios.PrecargaCampoService;
import pe.edu.pucp.areasverdes.mod05.Mod05Repository;

@SpringBootTest
@AutoConfigureMockMvc
class Mod04IntegrationTest {
    static final String BASE = "/api/v1/operacion-campo", PASSWORD = "prueba-mod04-local";
    static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);
    static final LocalDate HOY = LocalDate.of(2026, 10, 22);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        String url =
                System.getenv()
                        .getOrDefault(
                                "MOD04_TEST_DB_URL",
                                "jdbc:postgresql://127.0.0.1:55432/mod04_spring_test");
        if (!url.endsWith("_test"))
            throw new IllegalStateException(
                    "Las pruebas requieren una base exclusiva terminada en _test");
        p.add("spring.datasource.url", () -> url);
        p.add(
                "spring.datasource.username",
                () ->
                        System.getenv()
                                .getOrDefault(
                                        "MOD04_TEST_DB_USER", System.getProperty("user.name")));
        p.add(
                "spring.datasource.password",
                () -> System.getenv().getOrDefault("MOD04_TEST_DB_PASSWORD", ""));
    }

    @TestConfiguration
    static class Tiempo {
        @Bean
        @Primary
        Clock relojPrueba() {
            return Clock.fixed(Instant.parse("2026-10-22T18:00:00Z"), ZoneId.of("America/Lima"));
        }
    }

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired Mod04Repository repo;
    @Autowired Mod05Repository cuentas;
    @Autowired PrecargaCampoService seed;
    long jefe, c1, c2, o1, o2, o3, tipo, area;

    @BeforeEach
    void datos() {
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_usuario,mod04_tipo_actividad,mod05_areaverde RESTART"
                                + " IDENTITY CASCADE");
        jefe = cuenta("jefe", "Jefe de sección");
        long u1 = cuenta("capataz1", "Capataz"), u2 = cuenta("capataz2", "Capataz");
        cuenta("ingeniero", "Ingeniero de campo");
        cuenta("sinrol", null);
        c1 = personal("Capataz 1", "Capataz", u1);
        c2 = personal("Capataz 2", "Capataz", u2);
        o1 = personal("Operario 1", "Operario", null);
        o2 = personal("Operario 2", "Operario", null);
        o3 = personal("Operario 3", "Operario", null);
        tipo = repo.insert("tipo_actividad", values("nombre", "Mantenimiento", "activo", true));
        area = repo.insert("areaverde", values("nombre", "Jardín A", "activo", true));
    }

    long cuenta(String name, String role) {
        long id =
                cuentas.insert(
                        "usuario",
                        values(
                                "usuario",
                                name,
                                "nombre",
                                name,
                                "password_hash",
                                HASH,
                                "activo",
                                true));
        if (role != null)
            cuentas.jdbc()
                    .update(
                            "INSERT INTO mod05_usuario_rol VALUES (:id,:r)",
                            Map.of("id", id, "r", role));
        return id;
    }

    long personal(String name, String type, Long user) {
        return repo.insert(
                "personal",
                values(
                        "nombres",
                        name,
                        "apellidos",
                        "Prueba",
                        "tipo_personal",
                        type,
                        "usuario_id",
                        user));
    }

    Map<String, Object> squad(long capataz, Long... people) {
        return values(
                "nombre",
                "Cuadrilla " + capataz,
                "capataz_id",
                capataz,
                "activa",
                true,
                "operarios",
                List.of(people));
    }

    JsonNode postJson(String path, Object body, String user, int expected) throws Exception {
        var result =
                http.perform(
                                post(BASE + path)
                                        .with(httpBasic(user, PASSWORD))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(body)))
                        .andExpect(status().is(expected))
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    JsonNode putJson(String path, Object body, String user, int expected) throws Exception {
        var result =
                http.perform(
                                put(BASE + path)
                                        .with(httpBasic(user, PASSWORD))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(body)))
                        .andExpect(status().is(expected))
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    long cuadrilla(long capataz, Long... people) throws Exception {
        return postJson("/cuadrillas/", squad(capataz, people), "jefe", 201).path("id").asLong();
    }

    Map<String, Object> encargo(long squad, String periodo, int days) {
        return values(
                "cuadrilla_id",
                squad,
                "tipo_actividad_id",
                tipo,
                "area_verde_id",
                area,
                "descripcion",
                "Mantener jardín",
                "periodicidad",
                periodo,
                "fecha_reunion",
                HOY,
                "fecha_inicio",
                HOY,
                "fecha_fin",
                HOY.plusDays(days - 1),
                "plazo_previsto",
                HOY);
    }

    long actividad(long squad) throws Exception {
        long part =
                postJson("/partes", values("cuadrilla_id", squad, "fecha", HOY), "capataz1", 201)
                        .path("id")
                        .asLong();
        return postJson(
                        "/actividades",
                        values(
                                "parte_id",
                                part,
                                "tipo_actividad_id",
                                tipo,
                                "area_verde_id",
                                area,
                                "fecha_hora_actividad",
                                "2026-10-22T08:00:00-05:00"),
                        "capataz1",
                        201)
                .path("id")
                .asLong();
    }

    @Test
    void cuadrillaCompuestaYReemplazoAtomico() throws Exception {
        long id = cuadrilla(c1, o1, o2);
        http.perform(get(BASE + "/cuadrillas/" + id).with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad_operarios").value(2));
        putJson("/cuadrillas/" + id, squad(c1, o2, o3), "jefe", 200);
        assertEquals(2, repo.cuadrilla(id).get("cantidad_operarios"));
        assertEquals(
                1,
                repo.rows(
                                "SELECT id FROM mod04_asignacion_cuadrilla WHERE operario_id=:id"
                                        + " AND fecha_fin IS NOT NULL",
                                Map.of("id", o1))
                        .size());
    }

    @Test
    void noPermiteDuplicadosCapatazComoOperarioNiPersonalInactivo() throws Exception {
        postJson("/cuadrillas", squad(c1, o1, o1), "jefe", 400);
        postJson("/cuadrillas", squad(c1, c1), "jefe", 400);
        repo.update("personal", o1, values("activo", false));
        postJson("/cuadrillas", squad(c1, o1), "jefe", 400);
        assertTrue(repo.rows("SELECT id FROM mod04_cuadrilla", Map.of()).isEmpty());
    }

    @Test
    void noDuplicaAsignacionEntreCuadrillasYRevierteCambios() throws Exception {
        cuadrilla(c1, o1);
        long second = cuadrilla(c2, o2);
        var change = squad(c2, o1);
        change.put("nombre", "Cambio rechazado");
        putJson("/cuadrillas/" + second, change, "jefe", 409);
        assertEquals("Cuadrilla " + c2, repo.cuadrilla(second).get("nombre"));
        assertEquals(
                o2,
                ((Number)
                                ((Map<?, ?>)
                                                ((List<?>) repo.cuadrilla(second).get("operarios"))
                                                        .getFirst())
                                        .get("id"))
                        .longValue());
    }

    @Test
    void periodosSemanalesYQuincenalesConLimitesIncluidos() throws Exception {
        long id = cuadrilla(c1, o1);
        postJson("/encargos/", encargo(id, "Semanal", 7), "jefe", 201);
        postJson("/encargos", encargo(id, "Quincenal", 14), "jefe", 201);
        postJson("/encargos", encargo(id, "Semanal", 8), "jefe", 400);
        postJson("/encargos", encargo(id, "Quincenal", 15), "jefe", 400);
        var bad = encargo(id, "Semanal", 7);
        bad.put("plazo_previsto", HOY.plusDays(7));
        postJson("/encargos", bad, "jefe", 400);
        bad = encargo(id, "Semanal", 7);
        bad.put("fecha_fin", HOY.minusDays(1));
        postJson("/encargos", bad, "jefe", 400);
    }

    @Test
    void participantesCalculaCantidadYReintentoNoDuplica() throws Exception {
        long act = actividad(cuadrilla(c1, o1, o2));
        var body = values("operarios", List.of(o1, o2));
        var first = putJson("/actividades/" + act + "/participantes/", body, "capataz1", 200);
        assertEquals(2, first.path("cantidad_operarios").asInt());
        putJson("/actividades/" + act + "/participantes", body, "capataz1", 200);
        assertEquals(2, repo.rows("SELECT * FROM mod04_participacion_actividad", Map.of()).size());
        assertEquals(
                0,
                putJson(
                                "/actividades/" + act + "/participantes",
                                values("operarios", List.of()),
                                "capataz1",
                                200)
                        .path("cantidad_operarios")
                        .asInt());
    }

    @Test
    void participantesInvalidosNoBorranLoRegistrado() throws Exception {
        long act = actividad(cuadrilla(c1, o1, o2));
        String path = "/actividades/" + act + "/participantes";
        putJson(path, values("operarios", List.of(o1)), "capataz1", 200);
        putJson(path, values("operarios", List.of(o1, o1)), "capataz1", 400);
        putJson(path, values("operarios", List.of(o3)), "capataz1", 400);
        putJson(path, values("operarios", List.of(c1)), "capataz1", 400);
        putJson(path, values("operarios", List.of(o1), "cantidad_operarios", 99), "capataz1", 400);
        assertEquals(1, repo.participantes(act).get("cantidad_operarios"));
    }

    @Test
    void permisosYVisibilidadPorCapataz() throws Exception {
        long first = cuadrilla(c1, o1), second = cuadrilla(c2, o2);
        http.perform(get(BASE + "/cuadrillas")).andExpect(status().isUnauthorized());
        postJson("/cuadrillas", squad(c1, o1), "capataz1", 403);
        http.perform(get(BASE + "/cuadrillas/" + second).with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isForbidden());
        http.perform(get(BASE + "/cuadrillas").with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(first));
        long act = actividad(first);
        putJson(
                "/actividades/" + act + "/participantes",
                values("operarios", List.of(o1)),
                "capataz2",
                403);
        putJson(
                "/actividades/" + act + "/participantes",
                values("operarios", List.of(o1)),
                "ingeniero",
                403);
        http.perform(get(BASE + "/personal").with(httpBasic("sinrol", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void parteCerradoYCuadrillaInactivaNoSeModifican() throws Exception {
        long squad = cuadrilla(c1, o1), act = actividad(squad);
        var inactive = squad(c1);
        inactive.put("activa", false);
        putJson("/cuadrillas/" + squad, inactive, "jefe", 409);
        long part = number(repo.get("actividad", act, false), "parte_id");
        repo.update("parte_diario", part, values("estado", "Cerrado"));
        putJson(
                "/actividades/" + act + "/participantes",
                values("operarios", List.of(o1)),
                "capataz1",
                409);
        putJson("/cuadrillas/" + squad, inactive, "jefe", 200);
        postJson("/encargos", encargo(squad, "Semanal", 7), "jefe", 409);
    }

    @Test
    void filtrosPaginacionValidacionYDocumentacion() throws Exception {
        long id = cuadrilla(c1, o1);
        postJson("/encargos", encargo(id, "Quincenal", 14), "jefe", 201);
        http.perform(
                        get(BASE + "/encargos?desde=2026-10-23&hasta=2026-10-24")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        http.perform(get(BASE + "/encargos?desde=mal").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isBadRequest());
        http.perform(get(BASE + "/cuadrillas?page=2").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isNotFound());
        http.perform(get(BASE + "/cuadrillas?activa=quizas").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isBadRequest());
        http.perform(get(BASE + "/cuadrillas/9999").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isNotFound());
        postJson(
                "/cuadrillas",
                values("nombre", " ", "capataz_id", c1, "activa", true, "operarios", List.of()),
                "jefe",
                400);
        http.perform(get("/api/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/operacion-campo/cuadrillas']").exists());
    }

    @Test
    void concurrenciaNoAsignaOperarioADosCuadrillas() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> tasks = new ArrayList<>();
            for (long capataz : List.of(c1, c2))
                tasks.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return http.perform(
                                                    post(BASE + "/cuadrillas")
                                                            .with(httpBasic("jefe", PASSWORD))
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(
                                                                    json.writeValueAsBytes(
                                                                            squad(capataz, o1))))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var task : tasks) statuses.add(task.get(15, TimeUnit.SECONDS));
            Collections.sort(statuses);
            assertEquals(List.of(201, 409), statuses);
        }
        assertEquals(
                1,
                repo.rows(
                                "SELECT id FROM mod04_asignacion_cuadrilla WHERE fecha_fin IS NULL",
                                Map.of())
                        .size());
    }

    @Test
    void auditoriaInmutable() throws Exception {
        cuadrilla(c1, o1);
        assertThrows(
                DataAccessException.class,
                () ->
                        repo.jdbc()
                                .getJdbcTemplate()
                                .execute("UPDATE mod04_registro_auditoria SET accion='Alterado'"));
    }

    @Test
    void opcionesYDetalleDeActividadRespetanPertenenciaYRol() throws Exception {
        long squad = cuadrilla(c1, o1, o2), act = actividad(squad);
        http.perform(get(BASE + "/actividades/" + act).with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuadrilla_nombre").value("Cuadrilla " + c1))
                .andExpect(jsonPath("$.tipo_actividad_nombre").value("Mantenimiento"));
        http.perform(
                        get(BASE + "/actividades/" + act + "/operarios-disponibles/")
                                .with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        http.perform(
                        get(BASE + "/actividades/" + act + "/operarios-disponibles/")
                                .with(httpBasic("capataz2", PASSWORD)))
                .andExpect(status().isForbidden());
        http.perform(
                        get(BASE + "/personal/?tipo_personal=Operario")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].cuadrilla_id").value(squad));
        http.perform(get(BASE + "/actividades/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].area_verde_nombre").value("Jardín A"));
    }

    @Test
    void precargaInicialIdempotenteNoSobrescribe() {
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_usuario,mod04_tipo_actividad,mod05_areaverde RESTART"
                                + " IDENTITY CASCADE");
        seed.cargar(Path.of("mock-data"), PASSWORD);
        long count =
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM mod04_personal", Map.of(), Long.class);
        assertTrue(count > 20);
        var hash = cuentas.usuario("mod04_jefe").orElseThrow().passwordHash();
        seed.cargar(Path.of("mock-data"), "otra-clave-no-se-cambia");
        assertEquals(hash, cuentas.usuario("mod04_jefe").orElseThrow().passwordHash());
        assertEquals(
                count,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM mod04_personal", Map.of(), Long.class));
    }

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping rutas;

    /**
     * Matriz por operación: todos consultan salvo sinrol; solo jefe gestiona; capataz escribe su
     * parte.
     */
    @Test
    void matrizDePermisosCubreCadaEndpoint() throws Exception {
        cuenta("practicante", "Practicante autorizado");
        long equipo = cuadrilla(c1, o1, o2), act = actividad(equipo);
        long enc =
                postJson("/encargos", encargo(equipo, "Semanal", 6), "jefe", 201)
                        .get("id")
                        .asLong();
        long parte = ((Number) repo.get("actividad", act, false).get("parte_id")).longValue();
        record Fila(
                String nombre,
                String verbo,
                String ruta,
                Object cuerpo,
                boolean jefe,
                boolean campo) {}
        var filas =
                List.of(
                        new Fila("me", "GET", "/me", null, false, false),
                        new Fila("listar", "GET", "/personal", null, false, false),
                        new Fila("listar", "GET", "/cuadrillas", null, false, false),
                        new Fila("listar", "GET", "/encargos", null, false, false),
                        new Fila("listar", "GET", "/partes", null, false, false),
                        new Fila("listar", "GET", "/actividades", null, false, false),
                        new Fila(
                                "catalogo",
                                "GET",
                                "/catalogos/tipos-actividad",
                                null,
                                false,
                                false),
                        new Fila("catalogo", "GET", "/catalogos/areas-verdes", null, false, false),
                        new Fila("cuadrilla", "GET", "/cuadrillas/" + equipo, null, false, false),
                        new Fila("encargo", "GET", "/encargos/" + enc, null, false, false),
                        new Fila("actividad", "GET", "/actividades/" + act, null, false, false),
                        new Fila(
                                "participantes",
                                "GET",
                                "/actividades/" + act + "/participantes",
                                null,
                                false,
                                false),
                        new Fila(
                                "disponibles",
                                "GET",
                                "/actividades/" + act + "/operarios-disponibles",
                                null,
                                false,
                                false),
                        new Fila(
                                "personal",
                                "POST",
                                "/personal",
                                values(
                                        "nombres",
                                        "Matriz",
                                        "apellidos",
                                        "Prueba",
                                        "tipo_personal",
                                        "Operario"),
                                true,
                                false),
                        new Fila(
                                "crearCuadrilla",
                                "POST",
                                "/cuadrillas",
                                squad(c2, o3),
                                true,
                                false),
                        new Fila(
                                "actualizarCuadrilla",
                                "PUT",
                                "/cuadrillas/" + equipo,
                                squad(c1, o1, o2),
                                true,
                                false),
                        new Fila(
                                "crearEncargo",
                                "POST",
                                "/encargos",
                                encargo(equipo, "Semanal", 6),
                                true,
                                false),
                        new Fila(
                                "actualizarEncargo",
                                "PUT",
                                "/encargos/" + enc,
                                encargo(equipo, "Semanal", 6),
                                true,
                                false),
                        new Fila(
                                "crearParte",
                                "POST",
                                "/partes",
                                values("cuadrilla_id", equipo, "fecha", HOY),
                                false,
                                true),
                        new Fila(
                                "crearActividad",
                                "POST",
                                "/actividades",
                                values(
                                        "parte_id",
                                        parte,
                                        "tipo_actividad_id",
                                        tipo,
                                        "area_verde_id",
                                        area,
                                        "fecha_hora_actividad",
                                        HOY + "T08:00:00-05:00"),
                                false,
                                true),
                        new Fila(
                                "guardarParticipantes",
                                "PUT",
                                "/actividades/" + act + "/participantes",
                                values("operarios", List.of(o1)),
                                false,
                                true));
        var registrados = new HashSet<String>();
        rutas.getHandlerMethods()
                .forEach(
                        (ruta, metodo) -> {
                            if (metodo.getBeanType().equals(OperacionCampoController.class))
                                registrados.add(metodo.getMethod().getName());
                        });
        assertEquals(registrados, new HashSet<>(filas.stream().map(Fila::nombre).toList()));
        for (var fila : filas)
            for (String usuario :
                    List.of("anonimo", "jefe", "ingeniero", "practicante", "capataz1", "sinrol")) {
                var solicitud =
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                                org.springframework.http.HttpMethod.valueOf(fila.verbo()),
                                BASE + fila.ruta());
                if (!usuario.equals("anonimo")) solicitud.with(httpBasic(usuario, PASSWORD));
                if (fila.cuerpo() != null)
                    solicitud
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(fila.cuerpo()));
                int estado = http.perform(solicitud).andReturn().getResponse().getStatus();
                boolean permitido =
                        !usuario.equals("sinrol")
                                && (!fila.jefe() || usuario.equals("jefe"))
                                && (!fila.campo()
                                        || usuario.equals("jefe")
                                        || usuario.equals("capataz1"));
                if (usuario.equals("anonimo")) assertEquals(401, estado, fila.nombre());
                else if (!permitido) assertEquals(403, estado, usuario + " " + fila.nombre());
                else
                    assertTrue(
                            (estado >= 200 && estado < 300) || estado == 409,
                            usuario + " " + fila.nombre() + " -> " + estado);
            }
    }

    @Test
    void idempotenciaNoDuplicaActividadYRechazaOtraCarga() throws Exception {
        long equipo = cuadrilla(c1, o1), act = actividad(equipo);
        long parte = ((Number) repo.get("actividad", act, false).get("parte_id")).longValue();
        var body =
                values(
                        "parte_id",
                        parte,
                        "tipo_actividad_id",
                        tipo,
                        "area_verde_id",
                        area,
                        "fecha_hora_actividad",
                        HOY + "T09:00:00-05:00");
        String clave = UUID.randomUUID().toString();
        var primera =
                http.perform(
                                post(BASE + "/actividades")
                                        .with(httpBasic("capataz1", PASSWORD))
                                        .header("Idempotency-Key", clave)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(body)))
                        .andExpect(status().isCreated())
                        .andReturn();
        var segunda =
                http.perform(
                                post(BASE + "/actividades")
                                        .with(httpBasic("capataz1", PASSWORD))
                                        .header("Idempotency-Key", clave)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsBytes(body)))
                        .andExpect(status().isCreated())
                        .andReturn();
        assertEquals(
                json.readTree(primera.getResponse().getContentAsByteArray()).get("id"),
                json.readTree(segunda.getResponse().getContentAsByteArray()).get("id"));
        assertEquals(2, repo.rows("SELECT id FROM mod04_actividad", Map.of()).size());
        body.put("observaciones", "Otro contenido");
        http.perform(
                        post(BASE + "/actividades")
                                .with(httpBasic("capataz1", PASSWORD))
                                .header("Idempotency-Key", clave)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("idempotency-key-reutilizada"));
    }
}
