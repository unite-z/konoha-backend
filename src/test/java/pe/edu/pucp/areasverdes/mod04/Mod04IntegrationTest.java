package pe.edu.pucp.areasverdes.mod04;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.values;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import pe.edu.pucp.areasverdes.mod01.config.PrecargaDevMod01;
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
                        "TRUNCATE mod05_usuario,core_usuario,mod04_tipo_actividad,mod05_areaverde"
                                + " RESTART IDENTITY CASCADE");
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
        PrecargaDevMod01.copiarCuentasMod05(cuentas.jdbc());
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
        assertEquals(
                1,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM core_registro_auditoria WHERE"
                                    + " entidad='Cuadrilla' AND accion='Alta' AND usuario_id=:u",
                                Map.of("u", jefe),
                                Integer.class));
        assertThrows(
                DataAccessException.class,
                () ->
                        repo.jdbc()
                                .getJdbcTemplate()
                                .execute("UPDATE core_registro_auditoria SET accion='Alterado'"));
    }

    @Test
    void precargaInicialIdempotenteNoSobrescribe() {
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_usuario,core_usuario,mod04_tipo_actividad,mod05_areaverde"
                                + " RESTART IDENTITY CASCADE");
        seed.cargar(Path.of("mock-data"), PASSWORD);
        long count =
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM mod04_personal", Map.of(), Long.class);
        assertTrue(count > 20);
        var hash = hashDemo();
        seed.cargar(Path.of("mock-data"), "otra-clave-no-se-cambia");
        assertEquals(hash, hashDemo());
        assertEquals(
                count,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM mod04_personal", Map.of(), Long.class));
    }

    String hashDemo() {
        return repo.jdbc()
                .queryForObject(
                        "SELECT credencial_hash FROM core_usuario WHERE nombre_usuario=:nombre",
                        Map.of("nombre", "mod04_jefe"),
                        String.class);
    }

    Cookie sesionCore(String nombre) throws Exception {
        return http.perform(
                        post("/api/v1/core/auth/login/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsBytes(
                                                Map.of(
                                                        "identificador",
                                                        nombre,
                                                        "contrasena",
                                                        PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("av_sesion");
    }

    @Test
    void unaCuentaSoloDeCorePuedeGestionarCampoConCookieYCsrf() throws Exception {
        repo.jdbc()
                .update(
                        """
                        INSERT INTO core_usuario(nombre_usuario,nombre,credencial_hash,rol_id)
                        SELECT 'solo-core','Jefe core',:hash,id FROM core_rol WHERE codigo='jefe-seccion'
                        """,
                        Map.of("hash", HASH));
        var sesion = sesionCore("solo-core");
        http.perform(get(BASE + "/me/").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("Jefe de sección"));
        http.perform(
                        post(BASE + "/cuadrillas/")
                                .cookie(sesion)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(squad(c1, o1))))
                .andExpect(status().isForbidden());
        http.perform(
                        post(BASE + "/cuadrillas/")
                                .cookie(sesion)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(squad(c1, o1))))
                .andExpect(status().isCreated());
    }

    @Test
    void laCookieDeCapatazConservaSuAlcanceYRevalidaElRolYLaCuenta() throws Exception {
        long ajena = cuadrilla(c2, o2);
        var sesion = sesionCore("capataz1");
        http.perform(get(BASE + "/me/").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("Capataz"))
                .andExpect(jsonPath("$.personal_id").value(c1));
        http.perform(get(BASE + "/cuadrillas/" + ajena).cookie(sesion))
                .andExpect(status().isForbidden());
        repo.jdbc()
                .update(
                        """
                        UPDATE core_usuario SET rol_id=(SELECT id FROM core_rol WHERE codigo='practicante')
                        WHERE nombre_usuario='capataz1'
                        """,
                        Map.of());
        http.perform(get(BASE + "/me/").cookie(sesion))
                .andExpect(jsonPath("$.rol").value("Practicante autorizado"));
        repo.jdbc()
                .update(
                        "UPDATE core_usuario SET activo=false WHERE nombre_usuario='capataz1'",
                        Map.of());
        http.perform(get(BASE + "/me/").cookie(sesion)).andExpect(status().isUnauthorized());
    }

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping rutas;
}
