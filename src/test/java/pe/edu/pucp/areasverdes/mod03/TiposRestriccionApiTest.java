package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;

class TiposRestriccionApiTest extends ApiPlanificacion {
    @Autowired ObjectMapper json;
    @Autowired CatalogoTiposRestriccion catalogo;
    @Autowired pe.edu.pucp.areasverdes.mod02.servicios.CampanasPodaService campanas;
    @MockitoSpyBean RegistradorAuditoria auditoria;
    final List<Long> creados = new ArrayList<>();
    List<Map<String, Object>> originales;

    @BeforeEach
    void escenario() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
        originales =
                jdbc.getJdbcTemplate()
                        .queryForList(
                                "SELECT * FROM mod03_tipo_restriccion WHERE nombre IN"
                                        + " ('Paro','Cierre del campus')");
    }

    @AfterEach
    void limpiar() {
        for (long id : creados) {
            jdbc.getJdbcTemplate()
                    .update(
                            "DELETE FROM mod03_restriccion_calendario WHERE tipo_restriccion_id=?",
                            id);
            jdbc.getJdbcTemplate().update("DELETE FROM mod03_tipo_restriccion WHERE id=?", id);
        }
        for (var fila : originales)
            jdbc.update(
                    "UPDATE mod03_tipo_restriccion SET"
                        + " nombre=:nombre,efecto=:efecto,activo=:activo,aplica_servicios_ruidosos=:aplica_servicios_ruidosos"
                        + " WHERE id=:id",
                    fila);
    }

    ResultActions postTipo(String usuario, String body) throws Exception {
        return http.perform(
                post("/api/v1/planificacion/tipos-restriccion/")
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    ResultActions patchTipo(String usuario, long id, String body) throws Exception {
        return http.perform(
                patch("/api/v1/planificacion/tipos-restriccion/" + id + "/")
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    long crear() throws Exception {
        var respuesta =
                postTipo(
                                "jefe",
                                json.writeValueAsString(
                                        Map.of(
                                                "nombre",
                                                "Demo " + UUID.randomUUID(),
                                                "efecto",
                                                "Advertencia",
                                                "aplica_servicios_ruidosos",
                                                true)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse();
        long id = json.readTree(respuesta.getContentAsString()).get("id").asLong();
        creados.add(id);
        return id;
    }

    int auditorias(long id) {
        return jdbc.getJdbcTemplate()
                .queryForObject(
                        "SELECT count(*) FROM core_registro_auditoria WHERE"
                                + " entidad='mod03_tipo_restriccion' AND registro_id=?",
                        Integer.class,
                        id);
    }

    @Test
    void creaConsultaDesactivaConservaHistoriaYReactiva() throws Exception {
        long id = crear();
        http.perform(
                        get("/api/v1/planificacion/tipos-restriccion/")
                                .with(httpBasic("ingeniero", CLAVE)))
                .andExpect(
                        jsonPath("$[?(@.id == " + id + ")].activo")
                                .value(org.hamcrest.Matchers.contains(true)));
        jdbc.getJdbcTemplate()
                .update(
                        "INSERT INTO"
                            + " mod03_restriccion_calendario(tipo_restriccion_id,fecha_inicio,fecha_fin,usuario_creacion_id,usuario_modificacion_id)"
                            + " VALUES (?,'2027-01-11','2027-01-11',1,1)",
                        id);
        patchTipo("jefe", id, "{\"activo\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        http.perform(get("/api/v1/planificacion/tipos-restriccion/").with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        http.perform(
                        get("/api/v1/planificacion/tipos-restriccion/?incluir_inactivos=true")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(
                        jsonPath("$[?(@.id == " + id + ")].activo")
                                .value(org.hamcrest.Matchers.contains(false)));
        http.perform(
                        post("/api/v1/planificacion/restricciones/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"tipo_restriccion_id\":"
                                                + id
                                                + ",\"fecha_inicio\":\"2027-01-12\",\"fecha_fin\":\"2027-01-12\"}"))
                .andExpect(status().isBadRequest());
        http.perform(
                        get("/api/v1/planificacion/restricciones/?desde=2027-01-01&hasta=2027-01-31")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(
                        jsonPath("$[?(@.tipo_restriccion_id == " + id + ")].efecto")
                                .value(org.hamcrest.Matchers.contains("Advertencia")));
        patchTipo("jefe", id, "{\"activo\":true,\"aplica_servicios_ruidosos\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.aplica_servicios_ruidosos").value(false));
        assertEquals(3, auditorias(id));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"nombre\":\"   \"}",
                "{\"nombre\":\"Demo\",\"efecto\":\"AVISO\",\"aplica_servicios_ruidosos\":true}",
                "{\"nombre\":\"Demo\",\"efecto\":\"Advertencia\",\"aplica_servicios_ruidosos\":\"true\"}",
                "{\"nombre\":\"Demo\",\"efecto\":\"Advertencia\",\"aplica_servicios_ruidosos\":null}",
                "[]",
                "null",
                "{"
            })
    void rechazaIncompletosOMalformados(String body) throws Exception {
        int antes =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_tipo_restriccion", Integer.class);
        postTipo("jefe", body).andExpect(status().isBadRequest());
        assertEquals(
                antes,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_tipo_restriccion", Integer.class));
    }

    @Test
    void nombresDuplicadosNoDependenDeMayusculasNiEspaciosInclusoInactivos() throws Exception {
        long id = crear();
        patchTipo("jefe", id, "{\"nombre\":\"Ceremonia de graduación\",\"activo\":false}")
                .andExpect(status().isOk());
        postTipo(
                        "jefe",
                        "{\"nombre\":\"  CEREMONIA   DE GRADUACIÓN "
                            + " \",\"efecto\":\"Favorable\",\"aplica_servicios_ruidosos\":false}")
                .andExpect(status().isConflict());
    }

    @Test
    void protegidoRechazaCambiosMixtosSinDatosNiAuditoriaParciales() throws Exception {
        long id =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT id FROM mod03_tipo_restriccion WHERE nombre='Cierre del"
                                        + " campus'",
                                Long.class);
        int antes = auditorias(id);
        patchTipo("jefe", id, "{\"nombre\":\"Otro\",\"efecto\":\"Prohibicion\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("tipo-protegido"));
        patchTipo("jefe", id, "{\"activo\":false}").andExpect(status().isConflict());
        assertEquals(antes, auditorias(id));
        assertEquals(
                "Cierre del campus",
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT nombre FROM mod03_tipo_restriccion WHERE id=?",
                                String.class,
                                id));
        patchTipo("jefe", id, "{\"efecto\":\"Advertencia\"}").andExpect(status().isOk());
        jdbc.getJdbcTemplate()
                .update(
                        "INSERT INTO"
                            + " mod03_restriccion_calendario(tipo_restriccion_id,fecha_inicio,fecha_fin,usuario_creacion_id,usuario_modificacion_id)"
                            + " VALUES (?,'2027-12-01','2027-12-31',1,1)",
                        id);
        var ventana =
                campanas.ventanaCierre(
                        2027,
                        new pe.edu.pucp.areasverdes.mod05.Usuario(
                                1,
                                "jefe",
                                "Jefe",
                                "",
                                true,
                                List.of(),
                                Set.of("INV.INVENTARIO.CONSULTAR", "INV.PODA.PLANIFICAR")));
        assertEquals(java.time.LocalDate.of(2027, 12, 1), ventana.fechaInicio());
        // La búsqueda exacta que usa MOD-02 mantiene el mismo cierre, aunque cambie su efecto.
        assertEquals(
                1,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_tipo_restriccion WHERE nombre='Cierre"
                                        + " del campus' AND activo",
                                Integer.class));
    }

    @Test
    void sinPermisoNoCreaNiModifica() throws Exception {
        long id = crear();
        postTipo(
                        "ingeniero",
                        "{\"nombre\":\"No"
                            + " permitido\",\"efecto\":\"Advertencia\",\"aplica_servicios_ruidosos\":false}")
                .andExpect(status().isForbidden());
        patchTipo("ingeniero", id, "{\"activo\":false}").andExpect(status().isForbidden());
    }

    @Test
    void cambiosAProhibicionIdentificanRecurrenciasYRespetanRuidososSinCancelar() throws Exception {
        long id = crear();
        http.perform(
                        post("/api/v1/planificacion/restricciones/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"tipo_restriccion_id\":"
                                                + id
                                                + ",\"fecha_inicio\":\"2027-01-11\",\"fecha_fin\":\"2027-01-11\",\"recurrencia\":{\"dias_semana\":[1],\"hasta\":\"2027-01-25\"}}"))
                .andExpect(status().isCreated());
        jdbc.getJdbcTemplate()
                .update(
                        "UPDATE mod03_programacion_servicio SET"
                            + " estado='Programada',fecha_inicio_prevista='2027-01-18',fecha_fin_prevista='2027-01-20'");
        patchTipo("jefe", id, "{\"efecto\":\"Prohibicion\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciclos_afectados.length()").value(1))
                .andExpect(jsonPath("$.ciclos_afectados[0].id").value(1));
        assertEquals(
                2,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_programacion_servicio WHERE"
                                        + " estado='Programada'",
                                Integer.class));
        patchTipo("jefe", id, "{\"aplica_servicios_ruidosos\":false}")
                .andExpect(jsonPath("$.ciclos_afectados.length()").value(2));
        http.perform(
                        patch("/api/v1/planificacion/programaciones/1/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fecha_inicio_prevista\":\"2027-01-18\",\"fecha_fin_prevista\":\"2027-01-20\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("fecha-prohibida"));
        assertTrue(
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT jsonb_exists(valores_nuevos, 'efecto') FROM"
                                        + " core_registro_auditoria WHERE"
                                        + " entidad='mod03_tipo_restriccion' AND registro_id=? AND"
                                        + " accion='Modificacion' ORDER BY id LIMIT 1",
                                Boolean.class,
                                id));
    }

    @Test
    void falloDeAuditoriaRevierteElAlta() throws Exception {
        String nombre = "Rollback " + UUID.randomUUID();
        doThrow(new IllegalStateException("Auditoría no disponible"))
                .when(auditoria)
                .registrarAlta(eq("mod03_tipo_restriccion"), anyLong(), anyMap(), anyLong());
        assertThrows(
                IllegalStateException.class,
                () ->
                        catalogo.crear(
                                json.readTree(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "nombre",
                                                        nombre,
                                                        "efecto",
                                                        "Advertencia",
                                                        "aplica_servicios_ruidosos",
                                                        false))),
                                new UsuarioPlanificacion(
                                        1, "Jefe", Set.of("PLA.PLANIFICACION.GESTIONAR"))));
        assertEquals(
                0,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_tipo_restriccion WHERE nombre=?",
                                Integer.class,
                                nombre));
    }

    @Test
    void rechazaIdentificadorYNulosEnPatchSinCambiarNiAuditar() throws Exception {
        long id = crear();
        int antes = auditorias(id);
        patchTipo("jefe", id, "{\"id\":999,\"efecto\":\"Favorable\"}")
                .andExpect(status().isBadRequest());
        patchTipo("jefe", id, "{\"efecto\":null}").andExpect(status().isBadRequest());
        patchTipo("jefe", id, "{\"activo\":\"false\"}").andExpect(status().isBadRequest());
        assertEquals(antes, auditorias(id));
        assertEquals(
                "Advertencia",
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT efecto FROM mod03_tipo_restriccion WHERE id=?",
                                String.class,
                                id));
    }

    @Test
    void dosAltasConcurrentesDelMismoNombreNoSeDuplican() throws Exception {
        String body =
                json.writeValueAsString(
                        Map.of(
                                "nombre",
                                "Concurrente " + UUID.randomUUID(),
                                "efecto",
                                "Advertencia",
                                "aplica_servicios_ruidosos",
                                false));
        try (var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var salida = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<org.springframework.mock.web.MockHttpServletResponse>
                    alta =
                            () -> {
                                salida.await();
                                return postTipo("jefe", body).andReturn().getResponse();
                            };
            var f1 = ejecutor.submit(alta);
            var f2 = ejecutor.submit(alta);
            salida.countDown();
            var uno = f1.get(30, java.util.concurrent.TimeUnit.SECONDS);
            var dos = f2.get(30, java.util.concurrent.TimeUnit.SECONDS);
            for (var r : List.of(uno, dos))
                if (r.getStatus() == 201)
                    creados.add(json.readTree(r.getContentAsString()).get("id").asLong());
            assertEquals(
                    List.of(201, 409),
                    java.util.stream.Stream.of(uno.getStatus(), dos.getStatus()).sorted().toList());
        }
    }
}
