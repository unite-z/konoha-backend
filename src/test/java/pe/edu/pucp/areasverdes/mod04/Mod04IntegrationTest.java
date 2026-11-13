package pe.edu.pucp.areasverdes.mod04;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.number;
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
import java.util.HashMap;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;
import pe.edu.pucp.areasverdes.mod01.config.PrecargaDevMod01;
import pe.edu.pucp.areasverdes.mod04.api.OperacionCampoController;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod04.servicios.IdempotenciaCampo;
import pe.edu.pucp.areasverdes.mod04.servicios.PrecargaCampoService;
import pe.edu.pucp.areasverdes.mod05.Mod05Repository;

@SpringBootTest
@AutoConfigureMockMvc
class Mod04IntegrationTest {
    static final String BASE = "/api/v1/operacion-campo", PASSWORD = "prueba-mod04-local";
    static final String HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    static final LocalDate HOY = LocalDate.of(2026, 10, 22);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
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
    @Autowired IdempotenciaCampo idempotencia;
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

    Cookie sesionCore(String usuario) throws Exception {
        var result =
                http.perform(
                                post("/api/v1/core/auth/login/")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsBytes(
                                                        values(
                                                                "identificador",
                                                                usuario,
                                                                "contrasena",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        return result.getResponse().getCookie("av_sesion");
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
        return actividad(squad, "capataz1");
    }

    long actividad(long squad, String usuario) throws Exception {
        long part =
                postJson("/partes", values("cuadrilla_id", squad, "fecha", HOY), usuario, 201)
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
                        usuario,
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
        postJson("/encargos", encargo(id, "Quincenal", 20), "jefe", 400);
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
    void losEncargosVencidosSeMarcanYSeFiltran() throws Exception {
        long equipo = cuadrilla(c1, o1);
        // Vencido: el plazo (HOY, en el pasado) ya pasó y sigue abierto.
        long vencido =
                postJson("/encargos/", encargo(equipo, "Semanal", 7), "jefe", 201)
                        .path("id")
                        .asLong();
        // Cancelado con el plazo pasado: ya no cuenta como vencido.
        long cancelado =
                postJson("/encargos/", encargo(equipo, "Semanal", 7), "jefe", 201)
                        .path("id")
                        .asLong();
        postJson(
                "/encargos/" + cancelado + "/estado/",
                values(
                        "estado_anterior",
                        "Por completar",
                        "estado",
                        "Cancelado",
                        "observaciones",
                        "Ya no hace falta"),
                "jefe",
                200);
        // Vigente: el plazo está lejos en el futuro.
        var futuro = encargo(equipo, "Semanal", 7);
        futuro.put("fecha_reunion", "2099-06-01");
        futuro.put("fecha_inicio", "2099-06-01");
        futuro.put("fecha_fin", "2099-06-07");
        futuro.put("plazo_previsto", "2099-06-07");
        long vigente = postJson("/encargos/", futuro, "jefe", 201).path("id").asLong();

        var jefe = httpBasic("jefe", PASSWORD);
        http.perform(get(BASE + "/encargos/" + vencido).with(jefe))
                .andExpect(jsonPath("$.vencido").value(true));
        http.perform(get(BASE + "/encargos/" + cancelado).with(jefe))
                .andExpect(jsonPath("$.vencido").value(false));
        http.perform(get(BASE + "/encargos/" + vigente).with(jefe))
                .andExpect(jsonPath("$.vencido").value(false));

        http.perform(get(BASE + "/encargos?vencidos=true").with(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(vencido))
                .andExpect(jsonPath("$.results[0].vencido").value(true));
        http.perform(get(BASE + "/encargos?vencidos=false").with(jefe))
                .andExpect(jsonPath("$.count").value(2));
        http.perform(get(BASE + "/encargos").with(jefe)).andExpect(jsonPath("$.count").value(3));
        http.perform(get(BASE + "/encargos?vencidos=quizas").with(jefe))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laJefaturaReprogramaUnEncargoVivoConJustificacionYQuedaEnElHistorial() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long id =
                postJson("/encargos/", encargo(equipo, "Semanal", 7), "jefe", 201)
                        .path("id")
                        .asLong();
        String ruta = "/encargos/" + id + "/reprogramacion/";
        var nueva =
                values(
                        "fecha_inicio",
                        HOY.plusDays(2).toString(),
                        "fecha_fin",
                        HOY.plusDays(8).toString(),
                        "plazo_previsto",
                        HOY.plusDays(8).toString(),
                        "justificacion",
                        "  Lluvias en la zona ");

        postJson(ruta, nueva, "capataz1", 403);
        postJson(ruta, nueva, "ingeniero", 403);

        var reprogramado = postJson(ruta, nueva, "jefe", 200);
        assertEquals(HOY.plusDays(2).toString(), reprogramado.path("fecha_inicio").asText());
        assertEquals(HOY.plusDays(8).toString(), reprogramado.path("fecha_fin").asText());
        assertEquals(HOY.plusDays(8).toString(), reprogramado.path("plazo_previsto").asText());
        var historial = reprogramado.path("reprogramaciones").get(0);
        assertEquals("Lluvias en la zona", historial.path("justificacion").asText());
        assertEquals(HOY.toString(), historial.path("fecha_inicio_anterior").asText());
        assertEquals(HOY.plusDays(2).toString(), historial.path("fecha_inicio_nueva").asText());
        assertEquals(HOY.toString(), historial.path("plazo_anterior").asText());
        assertEquals(HOY.plusDays(8).toString(), historial.path("plazo_nuevo").asText());
        assertTrue(historial.hasNonNull("autor"));
        http.perform(get(BASE + "/encargos/" + id).with(httpBasic("jefe", PASSWORD)))
                .andExpect(jsonPath("$.reprogramaciones.length()").value(1));
    }

    @Test
    void laReprogramacionAplicaLasReglasDelEncargoYNoToleraCambiosVacios() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long id =
                postJson("/encargos/", encargo(equipo, "Semanal", 7), "jefe", 201)
                        .path("id")
                        .asLong();
        String ruta = "/encargos/" + id + "/reprogramacion/";
        var igual =
                values(
                        "fecha_inicio",
                        HOY.toString(),
                        "fecha_fin",
                        HOY.plusDays(6).toString(),
                        "plazo_previsto",
                        HOY.toString(),
                        "justificacion",
                        "Sin cambios");
        postJson(ruta, igual, "jefe", 400);

        var sinJustificar = new HashMap<>(igual);
        sinJustificar.put("plazo_previsto", HOY.plusDays(1).toString());
        sinJustificar.put("justificacion", "   ");
        postJson(ruta, sinJustificar, "jefe", 400);

        var muyLargo = new HashMap<>(igual);
        muyLargo.put("fecha_fin", HOY.plusDays(7).toString());
        muyLargo.put("justificacion", "Más días");
        postJson(ruta, muyLargo, "jefe", 400);

        var plazoFuera = new HashMap<>(igual);
        plazoFuera.put("plazo_previsto", HOY.plusDays(9).toString());
        plazoFuera.put("justificacion", "Plazo fuera");
        postJson(ruta, plazoFuera, "jefe", 400);

        var sinPeriodo = values("plazo_previsto", HOY.plusDays(1).toString(), "justificacion", "x");
        postJson(ruta, sinPeriodo, "jefe", 400);
        postJson("/encargos/999999/reprogramacion/", igual, "jefe", 404);
    }

    @Test
    void noSeReprogramaUnEncargoCerradoNiDejaActividadesFueraDelNuevoPeriodo() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long id =
                postJson("/encargos/", encargo(equipo, "Semanal", 7), "jefe", 201)
                        .path("id")
                        .asLong();
        long parte =
                postJson("/partes", values("cuadrilla_id", equipo, "fecha", HOY), "capataz1", 201)
                        .path("id")
                        .asLong();
        postJson(
                "/actividades",
                values(
                        "parte_id",
                        parte,
                        "tipo_actividad_id",
                        tipo,
                        "area_verde_id",
                        area,
                        "fecha_hora_actividad",
                        "2026-10-22T08:00:00-05:00",
                        "encargo_id",
                        id),
                "capataz1",
                201);
        String ruta = "/encargos/" + id + "/reprogramacion/";
        // La actividad del día HOY quedaría fuera del periodo que empieza dos días después.
        var corrido =
                values(
                        "fecha_inicio",
                        HOY.plusDays(2).toString(),
                        "fecha_fin",
                        HOY.plusDays(8).toString(),
                        "plazo_previsto",
                        HOY.plusDays(8).toString(),
                        "justificacion",
                        "Mover");
        postJson(ruta, corrido, "jefe", 409);
        // Alargar el periodo manteniendo la actividad dentro sí se puede, aunque ya tenga
        // ejecución.
        var alargado = new HashMap<>(corrido);
        alargado.put("fecha_inicio", HOY.toString());
        alargado.put("fecha_fin", HOY.plusDays(6).toString());
        alargado.put("plazo_previsto", HOY.plusDays(3).toString());
        alargado.put("justificacion", "Alargar");
        postJson(ruta, alargado, "jefe", 200);

        postJson(
                "/encargos/" + id + "/estado/",
                values(
                        "estado_anterior",
                        "Por completar",
                        "estado",
                        "Cancelado",
                        "observaciones",
                        "Ya no hace falta"),
                "jefe",
                200);
        var otro = new HashMap<>(alargado);
        otro.put("plazo_previsto", HOY.plusDays(4).toString());
        postJson(ruta, otro, "jefe", 409);
    }

    @Test
    void elEncargoDirectoSoloCambiaElPlazo() throws Exception {
        long equipo = cuadrilla(c1, o1);
        var directo =
                values(
                        "cuadrilla_id",
                        equipo,
                        "tipo_actividad_id",
                        tipo,
                        "area_verde_id",
                        area,
                        "descripcion",
                        "Encargo directo",
                        "periodicidad",
                        "Directo",
                        "plazo_previsto",
                        LocalDate.now().plusDays(20).toString());
        long id = postJson("/encargos/", directo, "jefe", 201).path("id").asLong();
        String ruta = "/encargos/" + id + "/reprogramacion/";
        postJson(
                ruta,
                values(
                        "fecha_inicio",
                        HOY.toString(),
                        "plazo_previsto",
                        LocalDate.now().plusDays(30).toString(),
                        "justificacion",
                        "x"),
                "jefe",
                400);
        postJson(
                ruta,
                values("plazo_previsto", "2000-01-01", "justificacion", "Antes del inicio"),
                "jefe",
                400);
        var plazo = LocalDate.now().plusDays(30).toString();
        var reprogramado =
                postJson(
                        ruta,
                        values("plazo_previsto", plazo, "justificacion", "Más tiempo"),
                        "jefe",
                        200);
        assertEquals(plazo, reprogramado.path("plazo_previsto").asText());
        assertEquals(plazo, reprogramado.path("fecha_fin").asText());
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
    void oficinaConsultaRegistroRealPorCuadrillaFechaYHoraDeAlta() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long otraCuadrilla = cuadrilla(c2, o2);
        long actividad = actividad(equipo);
        String consulta = BASE + "/actividades/?cuadrilla_id=" + equipo + "&fecha=" + HOY;
        Cookie sesion = sesionCore("ingeniero");
        http.perform(get(consulta).cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(actividad))
                .andExpect(jsonPath("$.results[0].tipo_actividad_nombre").value("Mantenimiento"))
                .andExpect(
                        jsonPath("$.results[0].fecha_hora_actividad")
                                .value("2026-10-22T08:00:00-05:00"))
                .andExpect(
                        jsonPath("$.results[0].fecha_hora_registro")
                                .value("2026-10-22T13:00:00-05:00"));
        http.perform(get(BASE + "/actividades/?cuadrilla_id=" + otraCuadrilla).cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        http.perform(get(consulta + "3").cookie(sesion)).andExpect(status().isBadRequest());
        http.perform(get(BASE + "/actividades/?fecha=" + HOY.plusDays(1)).cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        http.perform(get(consulta).with(httpBasic("sinrol", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void lasActividadesSeFiltranPorEncargoParaLlegarDelEncargoASusTareas() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long encargo =
                postJson("/encargos/", encargo(equipo, "Semanal", 7), "jefe", 201)
                        .path("id")
                        .asLong();
        long parte =
                postJson("/partes", values("cuadrilla_id", equipo, "fecha", HOY), "capataz1", 201)
                        .path("id")
                        .asLong();
        var vinculada =
                values(
                        "parte_id",
                        parte,
                        "tipo_actividad_id",
                        tipo,
                        "area_verde_id",
                        area,
                        "fecha_hora_actividad",
                        "2026-10-22T08:00:00-05:00",
                        "encargo_id",
                        encargo);
        long deEncargo = postJson("/actividades", vinculada, "capataz1", 201).path("id").asLong();
        var sinEncargo = new HashMap<>(vinculada);
        sinEncargo.remove("encargo_id");
        postJson("/actividades", sinEncargo, "capataz1", 201);

        Cookie sesion = sesionCore("ingeniero");
        http.perform(get(BASE + "/actividades/?encargo_id=" + encargo).cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(deEncargo))
                .andExpect(jsonPath("$.results[0].encargo_id").value(encargo));
        http.perform(get(BASE + "/actividades/?encargo_id=" + (encargo + 1000)).cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        http.perform(get(BASE + "/actividades/?encargo_id=abc").cookie(sesion))
                .andExpect(status().isBadRequest());
        // Sin el filtro, las dos actividades de la cuadrilla.
        http.perform(get(BASE + "/actividades/?cuadrilla_id=" + equipo).cookie(sesion))
                .andExpect(jsonPath("$.count").value(2));
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
        String consulta = BASE + "/actividades/?cuadrilla_id=" + squad;
        http.perform(get(consulta).with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].area_verde_nombre").value("Jardín A"))
                .andExpect(jsonPath("$.results[0].fecha_hora_registro").isNotEmpty());
        http.perform(get(consulta).with(httpBasic("capataz2", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void unidadesDeMaterialIncluyenLaUnidadRamaDeAv309() throws Exception {
        http.perform(
                        get(BASE + "/catalogos/unidades-medida/")
                                .with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.simbolo == 'rama')].nombre").value("Rama"));
    }

    @Test
    void jefeVerificaActividadYSeConservanAutorFechaYAuditoria() throws Exception {
        long equipo = cuadrilla(c1, o1), actividad = actividad(equipo);
        var verificacion = values("observaciones", "Ejecución conforme; retirar ramas del sendero");

        putJson("/actividades/" + actividad + "/verificacion/", verificacion, "capataz1", 403);
        putJson("/actividades/" + actividad + "/verificacion/", verificacion, "ingeniero", 403);
        putJson("/actividades/" + actividad + "/verificacion/", verificacion, "jefe", 200);

        http.perform(get(BASE + "/actividades/" + actividad).with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observaciones").doesNotExist())
                .andExpect(
                        jsonPath("$.verificacion_observaciones")
                                .value(verificacion.get("observaciones")))
                .andExpect(jsonPath("$.verificado_por_usuario_id").value(jefe))
                .andExpect(jsonPath("$.verificado_por_nombre").value("jefe"))
                .andExpect(jsonPath("$.fecha_verificacion").isNotEmpty());
        assertEquals(
                1,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM core_registro_auditoria WHERE"
                                        + " entidad='Actividad' AND accion='Modificacion' AND"
                                        + " registro_id=:id AND usuario_id=:u",
                                Map.of("id", actividad, "u", jefe),
                                Integer.class));
    }

    @Test
    void jefeAnulaActividadConMotivoYConservaSusDatos() throws Exception {
        long equipo = cuadrilla(c1, o1), actividad = actividad(equipo);
        putJson(
                "/actividades/" + actividad + "/participantes/",
                values("operarios", List.of(o1)),
                "capataz1",
                200);
        var material = values("material_id", 1, "cantidad", 2, "unidad_medida_id", 1);
        postJson("/actividades/" + actividad + "/materiales/", material, "capataz1", 201);
        long archivo =
                repo.jdbc()
                        .queryForObject(
                                "INSERT INTO core_archivo (nombre_original,tipo_mime,tamano_bytes,"
                                        + " hash_contenido,clave) VALUES ('foto.png','image/png',1,"
                                        + " :hash,:clave) RETURNING id",
                                Map.of(
                                        "hash",
                                        "a".repeat(64),
                                        "clave",
                                        "prueba-av384-" + UUID.randomUUID()),
                                Long.class);
        repo.jdbc()
                .update(
                        "INSERT INTO core_adjunto (uuid_cliente,entidad,registro_id,archivo_id,"
                                + " hash_contenido,usuario_creacion_id) VALUES (:uuid,'actividad',"
                                + " :actividad,:archivo,:hash,:usuario)",
                        Map.of(
                                "uuid", UUID.randomUUID(),
                                "actividad", actividad,
                                "archivo", archivo,
                                "hash", "a".repeat(64),
                                "usuario", jefe));

        var anulacion = values("motivo", "  Registro duplicado en el parte  ");
        putJson("/actividades/" + actividad + "/anulacion/", anulacion, "capataz1", 403);
        putJson("/actividades/" + actividad + "/anulacion/", anulacion, "ingeniero", 403);
        putJson("/actividades/" + actividad + "/anulacion/", values("motivo", "   "), "jefe", 400);
        putJson("/actividades/" + actividad + "/anulacion/", anulacion, "jefe", 200);

        http.perform(get(BASE + "/actividades/" + actividad).with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado_actividad").value("Anulada"))
                .andExpect(jsonPath("$.anulacion_motivo").value("Registro duplicado en el parte"))
                .andExpect(jsonPath("$.anulada_por_usuario_id").value(jefe))
                .andExpect(jsonPath("$.anulada_por_nombre").value("jefe"))
                .andExpect(jsonPath("$.anulada_en").isNotEmpty());
        assertEquals(1, repo.participantes(actividad).get("cantidad_operarios"));
        assertEquals(
                1,
                repo.rows(
                                "SELECT id FROM mod04_material_actividad WHERE actividad_id=:id",
                                Map.of("id", actividad))
                        .size());
        assertEquals(
                1,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM core_adjunto WHERE entidad='actividad' AND"
                                        + " registro_id=:id",
                                Map.of("id", actividad),
                                Integer.class));
        var auditoria =
                repo.one(
                        "SELECT accion,motivo,valores_anteriores,valores_nuevos FROM"
                                + " core_registro_auditoria WHERE entidad='Actividad' AND"
                                + " registro_id=:id AND accion='Anulacion'",
                        Map.of("id", actividad));
        assertEquals("Registro duplicado en el parte", auditoria.get("motivo"));
        assertTrue(String.valueOf(auditoria.get("valores_anteriores")).contains("Registrada"));
        assertTrue(String.valueOf(auditoria.get("valores_nuevos")).contains("Anulada"));

        putJson(
                "/actividades/" + actividad + "/verificacion/",
                values("observaciones", "Conforme"),
                "jefe",
                409);
        putJson(
                "/actividades/" + actividad + "/participantes/",
                values("operarios", List.of()),
                "capataz1",
                409);
        postJson("/actividades/" + actividad + "/materiales/", material, "capataz1", 409);
        http.perform(
                        multipart("/api/v1/core/adjuntos/")
                                .file(
                                        new MockMultipartFile(
                                                "archivo",
                                                "nueva.png",
                                                "image/png",
                                                new byte[] {
                                                    (byte) 137, 80, 78, 71, 13, 10, 26, 10
                                                }))
                                .param("entidad", "actividad")
                                .param("registro_id", String.valueOf(actividad))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .cookie(sesionCore("capataz1"))
                                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("actividad-anulada"));

        putJson("/actividades/" + actividad + "/anulacion/", anulacion, "jefe", 200);
        putJson(
                "/actividades/" + actividad + "/anulacion/",
                values("motivo", "Otro motivo"),
                "jefe",
                409);
        assertEquals(
                1,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM core_registro_auditoria WHERE"
                                        + " entidad='Actividad' AND registro_id=:id AND"
                                        + " accion='Anulacion'",
                                Map.of("id", actividad),
                                Integer.class));
    }

    @Test
    void verificadaNoPuedeAnularseYLosFiltrosCuentanSoloRegistradas() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long registrada = actividad(equipo);
        long verificada =
                postJson(
                                "/actividades",
                                values(
                                        "parte_id",
                                        repo.get("actividad", registrada, false).get("parte_id"),
                                        "tipo_actividad_id",
                                        tipo,
                                        "area_verde_id",
                                        area,
                                        "fecha_hora_actividad",
                                        "2026-10-22T09:00:00-05:00"),
                                "capataz1",
                                201)
                        .path("id")
                        .asLong();
        putJson(
                "/actividades/" + verificada + "/verificacion/",
                values("observaciones", "Conforme"),
                "jefe",
                200);
        putJson(
                "/actividades/" + verificada + "/anulacion/",
                values("motivo", "No corresponde"),
                "jefe",
                409);

        http.perform(
                        get(BASE + "/actividades/")
                                .param("estado_actividad", "Registrada")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.pendientes_verificacion").value(1))
                .andExpect(jsonPath("$.results[0].id").value(registrada));
        http.perform(
                        get(BASE + "/actividades/")
                                .param("estado_actividad", "Verificada")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.pendientes_verificacion").value(1))
                .andExpect(jsonPath("$.results[0].id").value(verificada));
    }

    @Test
    void anulacionesSimultaneasNoDuplicanAuditoria() throws Exception {
        long equipo = cuadrilla(c1, o1), actividad = actividad(equipo);
        var inicio = new CountDownLatch(1);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> tareas = new ArrayList<>();
            for (int i = 0; i < 2; i++)
                tareas.add(
                        ejecutor.submit(
                                () -> {
                                    inicio.await();
                                    return http.perform(
                                                    put(BASE
                                                                    + "/actividades/"
                                                                    + actividad
                                                                    + "/anulacion")
                                                            .with(httpBasic("jefe", PASSWORD))
                                                            .contentType(MediaType.APPLICATION_JSON)
                                                            .content(
                                                                    "{\"motivo\":\"Registro"
                                                                            + " duplicado\"}"))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            inicio.countDown();
            for (var tarea : tareas) assertEquals(200, tarea.get(15, TimeUnit.SECONDS));
        }
        assertEquals(
                1,
                repo.jdbc()
                        .queryForObject(
                                "SELECT count(*) FROM core_registro_auditoria WHERE"
                                        + " entidad='Actividad' AND registro_id=:id AND"
                                        + " accion='Anulacion'",
                                Map.of("id", actividad),
                                Integer.class));
    }

    @Test
    void historialActividadCombinaMayusculasSinMezclarEntidadesRelacionadas() throws Exception {
        long equipo = cuadrilla(c1, o1), actividad = actividad(equipo);
        String primera = "Verificada en campo";
        String actualizada = "Verificada; retirar ramas del sendero";

        // Conserva una entrada histórica con entidad en minúsculas y dos eventos relacionados.
        repo.jdbc()
                .update(
                        """
                        INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id,
                            fecha_hora, valores_anteriores, valores_nuevos)
                        VALUES ('actividad', :id, 'Modificacion', :usuario, now() - interval '1 day',
                            '{}', '{"observaciones":"Evento histórico"}')
                        """,
                        Map.of("id", actividad, "usuario", jefe));
        for (String entidadRelacionada : List.of("ParticipacionActividad", "MaterialActividad")) {
            repo.jdbc()
                    .update(
                            """
                            INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id,
                                valores_anteriores, valores_nuevos)
                            VALUES (:entidad, :id, 'Modificacion', :usuario, '{}', '{}')
                            """,
                            Map.of(
                                    "entidad", entidadRelacionada,
                                    "id", actividad,
                                    "usuario", jefe));
        }

        putJson(
                "/actividades/" + actividad + "/verificacion/",
                values("observaciones", primera),
                "jefe",
                200);
        putJson(
                "/actividades/" + actividad + "/verificacion/",
                values("observaciones", actualizada),
                "jefe",
                200);

        var respuestaHistorial =
                http.perform(
                                get("/api/v1/core/historial/")
                                        .param("entidad", "ACTIVIDAD")
                                        .param("registro_id", String.valueOf(actividad))
                                        .cookie(sesionCore("jefe")))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode eventos = json.readTree(respuestaHistorial.getResponse().getContentAsByteArray());
        assertEquals(4, eventos.size());
        boolean verificacionInicial = false;
        boolean verificacionActualizada = false;
        boolean altaCapataz = false;
        boolean eventoHistorico = false;
        for (JsonNode evento : eventos) {
            JsonNode nuevos = evento.path("valores_nuevos");
            if (primera.equals(nuevos.path("verificacion_observaciones").asText())) {
                verificacionInicial = true;
                assertEquals("Modificacion", evento.path("accion").asText());
                assertEquals("jefe", evento.path("usuario").asText());
                assertTrue(evento.hasNonNull("fecha_hora"));
            }
            if (actualizada.equals(nuevos.path("verificacion_observaciones").asText())) {
                verificacionActualizada = true;
                assertEquals(
                        primera,
                        evento.path("valores_anteriores")
                                .path("verificacion_observaciones")
                                .asText());
                assertEquals("Modificacion", evento.path("accion").asText());
                assertEquals("jefe", evento.path("usuario").asText());
            }
            if ("Alta".equals(evento.path("accion").asText())) {
                altaCapataz = "capataz1".equals(evento.path("usuario").asText());
            }
            if ("Evento histórico".equals(nuevos.path("observaciones").asText())) {
                eventoHistorico = true;
            }
        }
        assertTrue(verificacionInicial);
        assertTrue(verificacionActualizada);
        assertTrue(altaCapataz);
        assertTrue(eventoHistorico);
    }

    @Test
    void catalogoJerarquicoConservaNombreHistoricoYBloqueaNuevasAltas() throws Exception {
        http.perform(get(BASE + "/catalogos/clases-actividad/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(9));
        var entrada = values("nombre", "Poda de formación", "clase_id", 4, "activo", true);
        long nuevo =
                postJson("/catalogos/tipos-actividad/", entrada, "jefe", 201).path("id").asLong();
        http.perform(
                        get(BASE + "/catalogos/tipos-actividad/?activo=true")
                                .with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[1].clase_id").value(4));
        postJson("/catalogos/tipos-actividad/", entrada, "jefe", 409);
        long equipo = cuadrilla(c1, o1);
        long parte =
                postJson("/partes/", values("cuadrilla_id", equipo, "fecha", HOY), "capataz1", 201)
                        .path("id")
                        .asLong();
        var actividad =
                values(
                        "parte_id",
                        parte,
                        "tipo_actividad_id",
                        nuevo,
                        "area_verde_id",
                        area,
                        "fecha_hora_actividad",
                        "2026-10-22T08:00:00-05:00");
        long anterior = postJson("/actividades/", actividad, "capataz1", 201).path("id").asLong();
        putJson(
                "/catalogos/tipos-actividad/" + nuevo + "/",
                values("nombre", "Poda renovada", "clase_id", 4, "activo", false),
                "jefe",
                200);
        http.perform(get(BASE + "/actividades/" + anterior).with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo_actividad_nombre").value("Poda de formación"));
        http.perform(get(BASE + "/actividades/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.results[0].tipo_actividad_nombre").value("Poda de formación"));
        postJson("/actividades/", actividad, "capataz1", 400);
        http.perform(
                        get(BASE + "/catalogos/tipos-actividad/?activo=true")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void registroDeActividadValidaClaseSolicitudYOrigenSinDuplicar() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long parte =
                postJson("/partes/", values("cuadrilla_id", equipo, "fecha", HOY), "capataz1", 201)
                        .path("id")
                        .asLong();
        var entrada =
                values(
                        "parte_id",
                        parte,
                        "tipo_actividad_id",
                        tipo,
                        "clase_actividad_id",
                        3,
                        "area_verde_id",
                        area,
                        "fecha_hora_actividad",
                        "2026-10-22T08:00:00-05:00",
                        "fecha_solicitud",
                        "2026-10-21");
        long actividad = postJson("/actividades/", entrada, "capataz1", 201).path("id").asLong();
        http.perform(get(BASE + "/actividades/" + actividad).with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clase_actividad_id").value(3))
                .andExpect(jsonPath("$.fecha_solicitud").value("2026-10-21"));
        entrada.put("clase_actividad_id", 4);
        postJson("/actividades/", entrada, "capataz1", 400);
        entrada.put("clase_actividad_id", 3);
        entrada.put("fecha_solicitud", "2026-10-23");
        postJson("/actividades/", entrada, "capataz1", 400);
        entrada.put("fecha_solicitud", "2026-10-21");
        entrada.put("incidencia_id", 999999L);
        postJson("/actividades/", entrada, "capataz1", 400);
        http.perform(get(BASE + "/actividades/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void medidasDeActividadSeConsultanYDatosInvalidosNoAlteranRegistro() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long parte =
                postJson("/partes/", values("cuadrilla_id", equipo, "fecha", HOY), "capataz1", 201)
                        .path("id")
                        .asLong();
        var entrada =
                values(
                        "parte_id",
                        parte,
                        "tipo_actividad_id",
                        tipo,
                        "area_verde_id",
                        area,
                        "fecha_hora_actividad",
                        "2026-10-22T08:00:00-05:00",
                        "superficie_m2",
                        12.5,
                        "cantidad_solicitada",
                        10,
                        "cantidad_ejecutada",
                        8,
                        "unidad_medida_id",
                        1,
                        "tiempo_minutos",
                        90);
        long actividad = postJson("/actividades/", entrada, "capataz1", 201).path("id").asLong();
        http.perform(get(BASE + "/actividades/" + actividad).with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superficie_m2").value(12.5))
                .andExpect(jsonPath("$.cantidad_ejecutada").value(8))
                .andExpect(jsonPath("$.unidad_medida_id").value(1))
                .andExpect(jsonPath("$.tiempo_minutos").value(90));
        entrada.put("tiempo_minutos", 1500);
        postJson("/actividades/", entrada, "capataz1", 400);
        entrada.put("tiempo_minutos", 90);
        entrada.put("unidad_medida_id", null);
        postJson("/actividades/", entrada, "capataz1", 400);
        http.perform(get(BASE + "/actividades/").with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void podaMenorVinculaEjemplarValidaAlturaYPermisos() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long parte =
                postJson("/partes/", values("cuadrilla_id", equipo, "fecha", HOY), "capataz1", 201)
                        .path("id")
                        .asLong();
        long poda =
                repo.insert(
                        "tipo_actividad",
                        values("nombre", "Poda menor", "activo", true, "clase_id", 4));
        long bajo =
                repo.jdbc()
                        .queryForObject(
                                "INSERT INTO mod02_ejemplar(codigo,area_verde_id,altura_total_m)"
                                        + " VALUES ('EJ-BAJO',:area,3.25) RETURNING id",
                                Map.of("area", area),
                                Long.class);
        long alto =
                repo.jdbc()
                        .queryForObject(
                                "INSERT INTO mod02_ejemplar(codigo,area_verde_id,altura_total_m)"
                                        + " VALUES ('EJ-ALTO',:area,5.00) RETURNING id",
                                Map.of("area", area),
                                Long.class);
        var entrada =
                values(
                        "parte_id",
                        parte,
                        "tipo_actividad_id",
                        poda,
                        "area_verde_id",
                        area,
                        "fecha_hora_actividad",
                        "2026-10-22T08:00:00-05:00",
                        "ejemplar_id",
                        bajo);

        long actividad = postJson("/actividades/", entrada, "capataz1", 201).path("id").asLong();
        http.perform(get(BASE + "/actividades/" + actividad).with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ejemplar_codigo").value("EJ-BAJO"))
                .andExpect(jsonPath("$.ejemplar_altura_m").value(3.25));
        http.perform(
                        get("/api/v1/inventario/ejemplares/" + bajo + "/historial-actividades")
                                .with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actividad_id").value(actividad))
                .andExpect(jsonPath("$[0].tipo_actividad").value("Poda menor"));

        String historial = "/api/v1/inventario/ejemplares/" + bajo + "/historial-intervenciones";
        http.perform(
                        patch("/api/v1/inventario/ejemplares/" + bajo + "/situacion")
                                .with(httpBasic("jefe", PASSWORD))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"situacion":"retirado","fecha":"2026-10-23","motivo":"Daño irreversible"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacion").value("retirado"));
        http.perform(
                        get(historial)
                                .queryParam("fuente", "podas")
                                .with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(actividad))
                .andExpect(jsonPath("$[0].tipo").value("Poda menor"))
                .andExpect(jsonPath("$[0].fecha").value("2026-10-22"))
                .andExpect(jsonPath("$[0].origen").value("Operación de campo"));
        http.perform(
                        get(historial)
                                .queryParam("fuente", "fitosanitario")
                                .with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isForbidden());

        entrada.put("ejemplar_id", alto);
        postJson("/actividades/", entrada, "capataz1", 409);
        entrada.put("ejemplar_id", null);
        entrada.put("pendiente_vinculacion", true);
        postJson("/actividades/", entrada, "capataz1", 201);
        postJson("/actividades/", entrada, "capataz2", 403);
    }

    @Test
    void materialYTrasladoSeAsocianALaActividadConPermisos() throws Exception {
        long equipo = cuadrilla(c1, o1);
        long actividad = actividad(equipo);
        var entrada =
                values(
                        "material_id",
                        1,
                        "cantidad",
                        4,
                        "unidad_medida_id",
                        1,
                        "origen",
                        "Vivero",
                        "destino",
                        "Jardín A");
        postJson("/actividades/" + actividad + "/materiales/", entrada, "capataz1", 201);
        http.perform(
                        get(BASE + "/actividades/" + actividad + "/materiales/")
                                .with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre_material").value("Esquejes"))
                .andExpect(jsonPath("$[0].origen").value("Vivero"))
                .andExpect(jsonPath("$[0].destino").value("Jardín A"));
        entrada.put("destino", null);
        postJson("/actividades/" + actividad + "/materiales/", entrada, "capataz1", 400);
        postJson("/actividades/" + actividad + "/materiales/", entrada, "capataz2", 403);
        http.perform(
                        get(BASE + "/actividades/" + actividad + "/materiales/")
                                .with(httpBasic("jefe", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
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

    @Test
    void soloJefeCancelaUnEncargoAbiertoConMotivo() throws Exception {
        long equipo = cuadrilla(c1, o1);
        var creado = postJson("/encargos/", encargo(equipo, "Semanal", 7), "jefe", 201);
        long id = creado.path("id").asLong();
        String ruta = "/encargos/" + id + "/estado/";
        var cancelar = values("estado_anterior", "Por completar", "estado", "Cancelado");
        postJson(ruta, cancelar, "jefe", 400);
        cancelar.put("observaciones", "Cierre temporal del espacio");
        postJson(ruta, cancelar, "capataz1", 403);
        var cancelado = postJson(ruta, cancelar, "jefe", 200);
        assertEquals("Cancelado", cancelado.path("estado").asText());
        var cambio = cancelado.path("historial_estados").get(0);
        assertEquals("Cierre temporal del espacio", cambio.path("observaciones").asText());
        assertTrue(cambio.hasNonNull("usuario_id"));
        assertTrue(cambio.hasNonNull("fecha"));
        postJson(
                ruta,
                values("estado_anterior", "Cancelado", "estado", "En curso"),
                "capataz1",
                409);
        postJson(ruta, cancelar, "jefe", 409);
    }

    @Test
    void encargoDirectoConservaHistorialYRestringeCambiosAlCapatazResponsable() throws Exception {
        long equipo = cuadrilla(c1, o1);
        var directo =
                values(
                        "cuadrilla_id",
                        equipo,
                        "tipo_actividad_id",
                        tipo,
                        "area_verde_id",
                        area,
                        "descripcion",
                        "Encargo directo",
                        "periodicidad",
                        "Directo",
                        "plazo_previsto",
                        HOY.plusDays(20).toString());
        var creado = postJson("/encargos/", directo, "jefe", 201);
        assertEquals("Directo", creado.path("periodicidad").asText());
        assertTrue(creado.path("fecha_reunion").isNull());
        assertEquals("Por completar", creado.path("estado").asText());
        var ruta = "/encargos/" + creado.path("id").asLong() + "/estado/";
        postJson(
                ruta,
                values("estado_anterior", "Por completar", "estado", "Completado"),
                "capataz1",
                409);
        var iniciar = values("estado_anterior", "Por completar", "estado", "En curso");
        postJson(ruta, iniciar, "capataz2", 403);
        postJson(ruta, iniciar, "jefe", 403);
        postJson(ruta, iniciar, "capataz1", 200);
        postJson(ruta, iniciar, "capataz1", 409);
        var observar = values("estado_anterior", "En curso", "estado", "Observado");
        postJson(ruta, observar, "capataz1", 400);
        observar.put("observaciones", "Falta material");
        postJson(ruta, observar, "capataz1", 200);
        postJson(
                ruta,
                values("estado_anterior", "Observado", "estado", "En curso"),
                "capataz1",
                200);
        var cerrado =
                postJson(
                        ruta,
                        values("estado_anterior", "En curso", "estado", "Completado"),
                        "capataz1",
                        200);
        assertEquals("Completado", cerrado.path("estado").asText());
        putJson("/encargos/" + creado.path("id").asLong(), directo, "jefe", 409);
        var historial = cerrado.path("historial_estados");
        assertEquals(4, historial.size());
        assertEquals("Falta material", historial.get(2).path("observaciones").asText());
        assertTrue(historial.get(2).hasNonNull("usuario_id"));
        assertTrue(historial.get(2).hasNonNull("fecha"));
    }

    @Test
    void cadaCapatazSoloConsultaSusEncargosConPlazo() throws Exception {
        long propia = cuadrilla(c1, o1), ajena = cuadrilla(c2, o2);
        long idPropio =
                postJson("/encargos", encargo(propia, "Semanal", 7), "jefe", 201)
                        .path("id")
                        .asLong();
        long idAjeno =
                postJson("/encargos", encargo(ajena, "Semanal", 7), "jefe", 201)
                        .path("id")
                        .asLong();
        http.perform(get(BASE + "/encargos/").with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(idPropio))
                .andExpect(jsonPath("$.results[0].plazo_previsto").value(HOY.toString()));
        http.perform(get(BASE + "/encargos/" + idAjeno).with(httpBasic("capataz1", PASSWORD)))
                .andExpect(status().isForbidden());
        postJson("/encargos", encargo(propia, "Semanal", 7), "capataz1", 403);
        assertTrue(repo.encargo(idPropio).containsKey("estado"));
        assertEquals("Por completar", repo.encargo(idPropio).get("estado"));
    }

    String hashDemo() {
        return repo.jdbc()
                .queryForObject(
                        "SELECT credencial_hash FROM core_usuario WHERE nombre_usuario=:nombre",
                        Map.of("nombre", "mod04_jefe"),
                        String.class);
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

    @Test
    void capatazTieneConsultaYRegistroSinPermisosDeJefatura() {
        List<String> permisos =
                repo.jdbc()
                        .queryForList(
                                """
                                SELECT p.codigo FROM core_permiso p
                                JOIN core_rol_permiso rp ON rp.permiso_id=p.id
                                JOIN core_rol r ON r.id=rp.rol_id
                                WHERE r.codigo='capataz' AND p.modulo='MOD-04'
                                ORDER BY p.codigo
                                """,
                                Map.of(),
                                String.class);

        assertEquals(
                List.of("CAM.CUADRILLA.CONSULTAR", "CAM.PARTE.CONSULTAR", "CAM.PARTE.REGISTRAR"),
                permisos);
    }

    @Test
    void cookieDeCapatazConsultaYRegistraSoloEnSuCuadrilla() throws Exception {
        long propia = cuadrilla(c1, o1), ajena = cuadrilla(c2, o2);
        long actividad = actividad(propia);
        long actividadAjena = actividad(ajena, "capataz2");
        postJson("/encargos", encargo(propia, "Semanal", 7), "jefe", 201);
        Cookie sesion = sesionCore("capataz1");

        for (String recurso : List.of("cuadrillas", "encargos", "partes", "actividades")) {
            http.perform(get(BASE + "/" + recurso + "/").cookie(sesion))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(1));
        }
        http.perform(get(BASE + "/personal/").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.results[?(@.id == " + c2 + ")]").isEmpty())
                .andExpect(jsonPath("$.results[?(@.id == " + o2 + ")]").isEmpty());
        http.perform(
                        put(BASE + "/actividades/" + actividad + "/participantes/")
                                .cookie(sesion)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(values("operarios", List.of(o1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad_operarios").value(1));
        http.perform(get(BASE + "/cuadrillas/" + ajena).cookie(sesion))
                .andExpect(status().isForbidden());
        http.perform(
                        get("/api/v1/core/adjuntos/")
                                .param("entidad", "actividad")
                                .param("registro_id", String.valueOf(actividad))
                                .cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        http.perform(
                        get("/api/v1/core/adjuntos/")
                                .param("entidad", "actividad")
                                .param("registro_id", String.valueOf(actividadAjena))
                                .cookie(sesion))
                .andExpect(status().isForbidden());
        http.perform(
                        post(BASE + "/cuadrillas/")
                                .cookie(sesion)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(squad(c1, o1))))
                .andExpect(status().isForbidden());
        http.perform(
                        put(BASE + "/actividades/" + actividad + "/verificacion/")
                                .cookie(sesion)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsBytes(
                                                values("observaciones", "No autorizado"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void capatazSinFichaObtieneListadosVaciosSinAccederADatosAjenos() throws Exception {
        cuadrilla(c1, o1);
        cuenta("capataz-sin-ficha", "Capataz");
        Cookie sesion = sesionCore("capataz-sin-ficha");

        for (String recurso :
                List.of("personal", "cuadrillas", "encargos", "partes", "actividades")) {
            http.perform(get(BASE + "/" + recurso + "/").cookie(sesion))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0));
        }
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
                        new Fila(
                                "catalogo",
                                "GET",
                                "/catalogos/clases-actividad",
                                null,
                                false,
                                false),
                        new Fila(
                                "catalogo",
                                "GET",
                                "/catalogos/unidades-medida",
                                null,
                                false,
                                false),
                        new Fila("catalogo", "GET", "/catalogos/materiales", null, false, false),
                        new Fila(
                                "crearTipo",
                                "POST",
                                "/catalogos/tipos-actividad",
                                values("nombre", "Tipo de matriz", "clase_id", 3, "activo", true),
                                true,
                                false),
                        new Fila(
                                "actualizarTipo",
                                "PUT",
                                "/catalogos/tipos-actividad/" + tipo,
                                values("nombre", "Mantenimiento", "clase_id", 3, "activo", true),
                                true,
                                false),
                        new Fila("catalogo", "GET", "/catalogos/areas-verdes", null, false, false),
                        new Fila("cuadrilla", "GET", "/cuadrillas/" + equipo, null, false, false),
                        new Fila("encargo", "GET", "/encargos/" + enc, null, false, false),
                        new Fila("actividad", "GET", "/actividades/" + act, null, false, false),
                        new Fila(
                                "materiales",
                                "GET",
                                "/actividades/" + act + "/materiales",
                                null,
                                false,
                                false),
                        new Fila(
                                "crearMaterial",
                                "POST",
                                "/actividades/" + act + "/materiales",
                                values("material_id", 1, "cantidad", 2, "unidad_medida_id", 1),
                                false,
                                true),
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
                                "reprogramarEncargo",
                                "POST",
                                "/encargos/" + enc + "/reprogramacion",
                                values(
                                        "fecha_inicio",
                                        HOY.toString(),
                                        "fecha_fin",
                                        HOY.plusDays(5).toString(),
                                        "plazo_previsto",
                                        HOY.plusDays(1).toString(),
                                        "justificacion",
                                        "Cambio de planificación"),
                                true,
                                false),
                        new Fila(
                                "cambiarEstadoEncargo",
                                "POST",
                                "/encargos/" + enc + "/estado",
                                values("estado_anterior", "Por completar", "estado", "En curso"),
                                false,
                                true),
                        new Fila(
                                "guardarParticipantes",
                                "PUT",
                                "/actividades/" + act + "/participantes",
                                values("operarios", List.of(o1)),
                                false,
                                true),
                        new Fila(
                                "verificarActividad",
                                "PUT",
                                "/actividades/" + act + "/verificacion",
                                values("observaciones", "Conforme en campo"),
                                true,
                                false),
                        new Fila(
                                "anularActividad",
                                "PUT",
                                "/actividades/" + act + "/anulacion",
                                values("motivo", "Registro inválido"),
                                true,
                                false));
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
                                && (!fila.nombre().equals("cambiarEstadoEncargo")
                                        || usuario.equals("capataz1"))
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

    @Test
    void materialReenviadoDesdeLaColaNoSeDuplicaYLaClaveDuraUnMes() throws Exception {
        long equipo = cuadrilla(c1, o1), act = actividad(equipo);
        var body = values("material_id", 1, "cantidad", 4, "unidad_medida_id", 1);
        String clave = UUID.randomUUID().toString();
        for (int intento = 0; intento < 2; intento++) {
            http.perform(
                            post(BASE + "/actividades/" + act + "/materiales/")
                                    .with(httpBasic("capataz1", PASSWORD))
                                    .header("Idempotency-Key", clave)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json.writeValueAsBytes(body)))
                    .andExpect(status().isCreated());
        }
        assertEquals(
                1,
                repo.rows(
                                "SELECT id FROM mod04_material_actividad WHERE actividad_id=:a",
                                Map.of("a", act))
                        .size());
        assertEquals(
                Boolean.TRUE,
                repo.jdbc()
                        .queryForObject(
                                "SELECT caduca > now() + interval '29 days' FROM"
                                        + " mod04_idempotencia WHERE clave=:c",
                                Map.of("c", UUID.fromString(clave)),
                                Boolean.class));
        body.put("cantidad", 5);
        http.perform(
                        post(BASE + "/actividades/" + act + "/materiales/")
                                .with(httpBasic("capataz1", PASSWORD))
                                .header("Idempotency-Key", clave)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("idempotency-key-reutilizada"));
    }

    @Test
    void registraOperariosSinCuentaYExigeCuentaParaElCapataz() throws Exception {
        var cuerpo =
                values("nombres", "Personal", "apellidos", "Nuevo", "tipo_personal", "Capataz");
        postJson("/personal/", cuerpo, "jefe", 400);
        cuerpo.put("tipo_personal", "Operario");
        var creado = postJson("/personal/", cuerpo, "jefe", 201);
        assertEquals("Operario", creado.path("tipo_personal").asText());
        assertTrue(creado.path("usuario_id").isNull());
    }

    @Test
    void asignaUnaCuentaCapatazSinCrearOtroUsuario() throws Exception {
        long usuario = cuenta("capataznuevo", "Capataz");
        var cuerpo =
                values(
                        "nombre",
                        "Cuadrilla vinculada",
                        "capataz_usuario_id",
                        usuario,
                        "activa",
                        true,
                        "operarios",
                        List.of(o1));
        var creada = postJson("/cuadrillas/", cuerpo, "jefe", 201);
        long id = creada.path("id").asLong();
        long ficha = creada.path("capataz_id").asLong();
        assertEquals("capataznuevo", creada.path("capataz_nombre").asText());
        assertEquals(
                ficha,
                putJson("/cuadrillas/" + id, cuerpo, "jefe", 200).path("capataz_id").asLong());
        http.perform(get(BASE + "/cuadrillas/" + id).cookie(sesionCore("capataznuevo")))
                .andExpect(status().isOk());
        cuerpo.put("capataz_usuario_id", jefe);
        postJson("/cuadrillas/", cuerpo, "jefe", 400);
    }

    @Test
    void limpiezaDeIdempotenciaConservaClavesVigentes() {
        UUID vencida = UUID.randomUUID();
        UUID vigente = UUID.randomUUID();
        repo.jdbc()
                .update(
                        """
                        INSERT INTO mod04_idempotencia (usuario_id, clave, hash, respuesta, caduca)
                        VALUES (:usuario, :vencida, 'hash', '{}'::jsonb, now() - interval '1 hour'),
                               (:usuario, :vigente, 'hash', '{}'::jsonb, now() + interval '1 hour')
                        """,
                        Map.of("usuario", jefe, "vencida", vencida, "vigente", vigente));

        idempotencia.limpiarCaducadas();

        assertEquals(
                List.of(vigente),
                repo.jdbc()
                        .queryForList(
                                "SELECT clave FROM mod04_idempotencia", Map.of(), UUID.class));
    }
}
