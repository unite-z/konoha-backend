package pe.edu.pucp.areasverdes.mod05;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;

import com.fasterxml.jackson.databind.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

/** RF-FIT-001: plan fitosanitario estacional. */
@SpringBootTest
@AutoConfigureMockMvc
class PlanFitosanitarioIntegrationTest {
    static final String BASE = "/api/tercerizados/", PASSWORD = "prueba-integration-local";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
    }

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired Mod05Repository repo;
    @Autowired PasswordEncoder passwords;
    long proveedor, tipo, contrato, area, ejemplar;
    final int anio = LocalDate.now().getYear() + 1;

    @BeforeEach
    void setup() {
        // Exclusivamente base *_test: nunca se ejecuta contra la base de desarrollo.
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_usuario,mod05_proveedor,mod05_tiposervicio,mod05_areaverde"
                                + " RESTART IDENTITY CASCADE");
        long jefeId = -1;
        for (String name : List.of("jefe", "ingeniero")) {
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
            String role = name.equals("jefe") ? Usuario.JEFE : Usuario.ROLES.get(1);
            repo.jdbc()
                    .update(
                            "INSERT INTO mod05_usuario_rol(usuario_id,rol) VALUES (:id,:rol)",
                            Map.of("id", id, "rol", role));
            if (name.equals("jefe")) jefeId = id;
        }
        // mod03_duracion_servicio referencia core_usuario (AV-287): las cuentas de mod05_usuario
        // necesitan su copia, con el mismo id, igual que en ApiPlanificacion.
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        """
                        INSERT INTO core_usuario (id, nombre_usuario, correo, nombre, credencial_hash, rol_id)
                        SELECT u.id, 'mod05-' || u.usuario, 'mod05-' || u.usuario || '@prueba.pucp.edu.pe',
                            u.nombre, 'sin-clave', r.id
                        FROM mod05_usuario u
                        LEFT JOIN mod05_usuario_rol ur ON ur.usuario_id = u.id
                        JOIN core_rol r ON r.codigo = CASE ur.rol
                            WHEN 'Jefe de sección' THEN 'jefe-seccion'
                            WHEN 'Ingeniero de campo' THEN 'ingeniero-campo'
                            WHEN 'Practicante autorizado' THEN 'practicante'
                            ELSE 'capataz' END
                        """);
        tipo =
                repo.insert(
                        "tiposervicio",
                        values(
                                "nombre",
                                "Control fitosanitario",
                                "ejecutor",
                                "Tercerizado",
                                "es_riesgo",
                                false,
                                "activo",
                                true));
        repo.jdbc()
                .getJdbcTemplate()
                .execute("UPDATE mod05_tiposervicio SET es_periodico=true WHERE id=" + tipo);
        repo.jdbc()
                .update(
                        "INSERT INTO mod03_duracion_servicio(tipo_servicio_id,"
                            + " duracion_maxima_dias, hora_inicio, hora_fin, usuario_creacion_id,"
                            + " usuario_modificacion_id) VALUES (:tipo, 1, '06:00', '18:00', :jefe,"
                            + " :jefe)",
                        Map.of("tipo", tipo, "jefe", jefeId));
        proveedor =
                repo.insert(
                        "proveedor",
                        values(
                                "razon_social",
                                "Fumigaciones del Campus S.A.",
                                "ruc",
                                null,
                                "activo",
                                true));
        contrato =
                repo.insert(
                        "contrato",
                        values(
                                "numero",
                                "CT-FIT-TEST",
                                "proveedor_id",
                                proveedor,
                                "tipo_servicio_id",
                                tipo,
                                "vigencia_inicio",
                                LocalDate.of(anio - 1, 1, 1),
                                "vigencia_fin",
                                LocalDate.of(anio, 12, 31)));
        area = repo.insert("areaverde", values("nombre", "Jardín Central", "activo", true));
        ejemplar = repo.insert("ejemplar", values("codigo", "E-01", "area_verde_id", area));
    }

    long plaga(String nombre) {
        return repo.jdbc()
                .queryForObject(
                        "SELECT id FROM mod05_plaga WHERE nombre=:n",
                        Map.of("n", nombre),
                        Long.class);
    }

    JsonNode send(String path, Object body, String user, int expected) throws Exception {
        var request = post(BASE + path);
        if (user != null) request = request.with(httpBasic(user, PASSWORD));
        if (body != null)
            request =
                    request.contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsBytes(body));
        var result = http.perform(request).andExpect(status().is(expected)).andReturn();
        var bytes = result.getResponse().getContentAsByteArray();
        return bytes.length == 0 ? null : json.readTree(bytes);
    }

    Map<String, Object> aplicacion(String estacion, LocalDate fecha) {
        return values(
                "estacion",
                estacion,
                "fecha_inicio",
                fecha.toString(),
                "fecha_fin",
                fecha.toString(),
                "alcances",
                List.of(values("area_verde_id", area)));
    }

    List<Map<String, Object>> plan4Estaciones() {
        return List.of(
                aplicacion("Verano", LocalDate.of(anio, 1, 15)),
                aplicacion("Otoño", LocalDate.of(anio, 4, 15)),
                aplicacion("Invierno", LocalDate.of(anio, 7, 15)),
                aplicacion("Primavera", LocalDate.of(anio, 10, 15)));
    }

    @Test
    void elJefeProgramaElPlanCompletoYGeneraLasCuatroAtenciones() throws Exception {
        var data = values("contrato_id", contrato, "aplicaciones", plan4Estaciones());
        var creadas = send("plan-fitosanitario/", data, "jefe", 201);
        assertEquals(4, creadas.size());
        for (var atencion : creadas) {
            assertEquals("ProgramacionEstacional", atencion.get("origen").asText());
            assertEquals("Requerimiento registrado", atencion.get("estado").asText());
        }
        assertEquals(
                4,
                repo.jdbc()
                        .getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_programacion_servicio WHERE"
                                        + " tipo_servicio_id="
                                        + tipo,
                                Integer.class));
    }

    @Test
    void soloElJefeProgramaElPlan() throws Exception {
        var data = values("contrato_id", contrato, "aplicaciones", plan4Estaciones());
        send("plan-fitosanitario/", data, "ingeniero", 403);
        send("plan-fitosanitario/", data, null, 401);
    }

    @Test
    void sinLasCuatroEstacionesExigeMotivo() throws Exception {
        var incompleto =
                List.of(
                        aplicacion("Verano", LocalDate.of(anio, 1, 15)),
                        aplicacion("Otoño", LocalDate.of(anio, 4, 15)));
        var sinMotivo = values("contrato_id", contrato, "aplicaciones", incompleto);
        send("plan-fitosanitario/", sinMotivo, "jefe", 400);
        var conMotivo =
                values(
                        "contrato_id",
                        contrato,
                        "aplicaciones",
                        incompleto,
                        "motivo",
                        "Invierno y primavera se cubren con mantenimiento interno este año");
        var creadas = send("plan-fitosanitario/", conMotivo, "jefe", 201);
        assertEquals(2, creadas.size());
    }

    @Test
    void elContratoDebeSerDeControlFitosanitario() throws Exception {
        long otroTipo =
                repo.insert(
                        "tiposervicio",
                        values(
                                "nombre",
                                "Poda de altura a demanda",
                                "ejecutor",
                                "Tercerizado",
                                "es_riesgo",
                                true,
                                "activo",
                                true));
        long otroContrato =
                repo.insert(
                        "contrato",
                        values(
                                "numero",
                                "CT-PODA-TEST",
                                "proveedor_id",
                                proveedor,
                                "tipo_servicio_id",
                                otroTipo,
                                "vigencia_inicio",
                                LocalDate.of(anio - 1, 1, 1),
                                "vigencia_fin",
                                LocalDate.of(anio, 12, 31)));
        var data = values("contrato_id", otroContrato, "aplicaciones", plan4Estaciones());
        send("plan-fitosanitario/", data, "jefe", 400);
    }

    @Test
    void noRepiteUnaEstacionEnElMismoPlan() throws Exception {
        var repetido =
                List.of(
                        aplicacion("Verano", LocalDate.of(anio, 1, 15)),
                        aplicacion("Verano", LocalDate.of(anio, 1, 20)));
        var data = values("contrato_id", contrato, "aplicaciones", repetido);
        send("plan-fitosanitario/", data, "jefe", 400);
    }

    // --- RF-FIT-006
    // ---------------------------------------------------------------------------------------------

    long marcarEspecial(int adicionales) throws Exception {
        var marcado =
                send(
                        "atenciones-especiales/",
                        values(
                                "ejemplar_id",
                                ejemplar,
                                "plaga_id",
                                plaga("Pulgón"),
                                "aplicaciones_adicionales",
                                adicionales),
                        "jefe",
                        201);
        return marcado.get("id").asLong();
    }

    Map<String, Object> aplicacionAdicional(LocalDate fecha) {
        return values(
                "contrato_id",
                contrato,
                "fecha_inicio",
                fecha.toString(),
                "fecha_fin",
                fecha.toString());
    }

    @Test
    void elJefeProgramaUnaAplicacionAdicionalParaElEjemplarMarcado() throws Exception {
        long especial = marcarEspecial(2);
        var actualizado =
                send(
                        "atenciones-especiales/" + especial + "/aplicaciones/",
                        aplicacionAdicional(LocalDate.of(anio, 3, 1)),
                        "jefe",
                        201);
        assertEquals(1, actualizado.get("aplicaciones_realizadas").asInt());
        assertEquals(2, actualizado.get("aplicaciones_adicionales").asInt());

        var detalle = sendGet("atenciones-especiales/" + especial + "/", "jefe", 200);
        assertEquals(1, detalle.get("aplicaciones").size());
        long atencionId = detalle.get("aplicaciones").get(0).get("id").asLong();
        assertEquals(
                "Requerimiento registrado",
                detalle.get("aplicaciones").get(0).get("estado").asText());

        var atencion = sendGet("atenciones/" + atencionId + "/", "jefe", 200);
        assertEquals(especial, atencion.get("atencion_especial_id").asLong());
        assertEquals("Demanda", atencion.get("origen").asText());

        var historial =
                get("/api/v1/inventario/ejemplares/" + ejemplar + "/historial-intervenciones")
                        .queryParam("fuente", "fitosanitario")
                        .with(httpBasic("jefe", PASSWORD));
        http.perform(historial).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        http.perform(
                        patch(BASE + "atenciones/" + atencionId + "/orden-compra/")
                                .with(httpBasic("jefe", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"numero_orden_compra\":\"OC-FIT-TEST\"}"))
                .andExpect(status().isOk());
        send(
                "atenciones/" + atencionId + "/inicio/",
                values("fecha_hora_inicio", "2026-11-06T08:00:00-05:00"),
                "jefe",
                200);
        send(
                "atenciones/" + atencionId + "/fin/",
                values("fecha_hora_fin", "2026-11-06T10:00:00-05:00"),
                "jefe",
                200);
        http.perform(
                        patch("/api/v1/inventario/ejemplares/" + ejemplar + "/situacion")
                                .with(httpBasic("jefe", PASSWORD))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"situacion":"retirado","fecha":"2026-11-06","motivo":"Daño irreversible"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacion").value("retirado"));
        http.perform(historial)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(atencionId))
                .andExpect(jsonPath("$[0].fecha").value("2026-11-06"))
                .andExpect(jsonPath("$[0].origen").value("Servicios tercerizados"));
    }

    JsonNode sendGet(String path, String user, int expected) throws Exception {
        var request = get(BASE + path);
        if (user != null) request = request.with(httpBasic(user, PASSWORD));
        var result = http.perform(request).andExpect(status().is(expected)).andReturn();
        var bytes = result.getResponse().getContentAsByteArray();
        return bytes.length == 0 ? null : json.readTree(bytes);
    }

    @Test
    void noSeProgramaMasAplicacionesAdicionalesQueLasFijadasAlMarcar() throws Exception {
        long especial = marcarEspecial(1);
        send(
                "atenciones-especiales/" + especial + "/aplicaciones/",
                aplicacionAdicional(LocalDate.of(anio, 3, 1)),
                "jefe",
                201);
        send(
                "atenciones-especiales/" + especial + "/aplicaciones/",
                aplicacionAdicional(LocalDate.of(anio, 6, 1)),
                "jefe",
                400);
    }

    @Test
    void soloElJefeProgramaAplicacionesAdicionales() throws Exception {
        long especial = marcarEspecial(2);
        send(
                "atenciones-especiales/" + especial + "/aplicaciones/",
                aplicacionAdicional(LocalDate.of(anio, 3, 1)),
                "ingeniero",
                403);
        send(
                "atenciones-especiales/" + especial + "/aplicaciones/",
                aplicacionAdicional(LocalDate.of(anio, 3, 1)),
                null,
                401);
    }

    @Test
    void elContratoDeLaAplicacionAdicionalDebeSerDeControlFitosanitario() throws Exception {
        long especial = marcarEspecial(2);
        long otroTipo =
                repo.insert(
                        "tiposervicio",
                        values(
                                "nombre",
                                "Poda de altura a demanda",
                                "ejecutor",
                                "Tercerizado",
                                "es_riesgo",
                                true,
                                "activo",
                                true));
        long otroContrato =
                repo.insert(
                        "contrato",
                        values(
                                "numero",
                                "CT-PODA-FIT-TEST",
                                "proveedor_id",
                                proveedor,
                                "tipo_servicio_id",
                                otroTipo,
                                "vigencia_inicio",
                                LocalDate.of(anio - 1, 1, 1),
                                "vigencia_fin",
                                LocalDate.of(anio, 12, 31)));
        var data =
                values(
                        "contrato_id",
                        otroContrato,
                        "fecha_inicio",
                        LocalDate.of(anio, 3, 1).toString(),
                        "fecha_fin",
                        LocalDate.of(anio, 3, 1).toString());
        send("atenciones-especiales/" + especial + "/aplicaciones/", data, "jefe", 400);
    }

    // --- RF-INV-011: cancelar el seguimiento
    // ------------------------------------------------------

    @Test
    void cualquierRolDelModuloCancelaElSeguimientoYQuedaComoHistorico() throws Exception {
        long especial = marcarEspecial(2);
        var cancelado =
                send(
                        "atenciones-especiales/" + especial + "/cancelar/",
                        values("motivo", "Marcado por error"),
                        "ingeniero",
                        200);
        assertTrue(cancelado.get("cancelado").asBoolean());
        assertEquals("Cancelado", cancelado.get("estado").asText());
        assertEquals("Marcado por error", cancelado.get("motivo_cancelacion").asText());
        assertNotNull(cancelado.get("usuario_cancelacion_id"));
        assertNotNull(cancelado.get("fecha_cancelacion"));

        var detalle = sendGet("atenciones-especiales/" + especial + "/", "jefe", 200);
        assertEquals("Cancelado", detalle.get("estado").asText());
    }

    /** AV-154 (RF-INV-011): retirar la marca exige su motivo; sin él, sigue en seguimiento. */
    @Test
    void elMotivoDeCancelacionEsObligatorio() throws Exception {
        long especial = marcarEspecial(1);
        send("atenciones-especiales/" + especial + "/cancelar/", values("motivo", ""), "jefe", 400);
        var detalle = sendGet("atenciones-especiales/" + especial + "/", "jefe", 200);
        assertFalse(detalle.get("cancelado").asBoolean());
    }

    @Test
    void noSeCancelaUnSeguimientoYaCancelado() throws Exception {
        long especial = marcarEspecial(1);
        send(
                "atenciones-especiales/" + especial + "/cancelar/",
                values("motivo", "Plaga controlada"),
                "jefe",
                200);
        send(
                "atenciones-especiales/" + especial + "/cancelar/",
                values("motivo", "Plaga controlada"),
                "jefe",
                409);
    }

    @Test
    void noSeProgramaUnaAplicacionAdicionalParaUnSeguimientoCancelado() throws Exception {
        long especial = marcarEspecial(2);
        send(
                "atenciones-especiales/" + especial + "/cancelar/",
                values("motivo", "Plaga controlada"),
                "jefe",
                200);
        send(
                "atenciones-especiales/" + especial + "/aplicaciones/",
                aplicacionAdicional(LocalDate.of(anio, 3, 1)),
                "jefe",
                400);
    }

    @Test
    void sinSesionNoSeCancelaElSeguimiento() throws Exception {
        long especial = marcarEspecial(1);
        send("atenciones-especiales/" + especial + "/cancelar/", values("motivo", ""), null, 401);
    }
}
