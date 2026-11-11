package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod06.apoyo.ClienteIAFalso;

/**
 * RF-IA-005 y RF-IA-004 contra PostgreSQL con {@link ClienteIAFalso}. Escenario propio: un área de
 * inventario con una incidencia Registrada (MOD-03) y, en MOD-04, una actividad sin tiempo y otra
 * del mismo tipo ya ejecutada (antecedente: 2 operarios, 3 horas).
 */
@Import(PriorizacionInconsistenciasApiTest.IaFalsa.class)
class ClasificacionEstimacionApiTest extends ApiGestion {

    @Autowired PriorizacionInconsistenciasApiTest.FabricaFalsa fabrica;

    long incidencia;

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
        var sql = jdbc.getJdbcTemplate();
        sql.execute(
                "TRUNCATE mod06_sugerencia_ia, mod03_incidencia, mod04_personal RESTART IDENTITY"
                        + " CASCADE");
        sql.execute("DELETE FROM mod02_area_verde WHERE nombre = 'Jardín Central'");
        sql.execute(
                """
                INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2, usuario_creacion_id, usuario_modificacion_id)
                VALUES ('Jardín Central', 'Campus', 'Centro', 500, 1, 1)\
                """);
        sql.execute(
                "INSERT INTO mod03_tipo_incidencia (nombre, sencilla) VALUES ('Rama caída"
                        + " (prueba)', true) ON CONFLICT DO NOTHING");
        incidencia =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod03_incidencia (codigo, tipo_incidencia_id, area_verde_id, descripcion, canal, estado,
                            reportante_nombre, registrada_por_id, fecha_registro)
                        SELECT 'INC-PRUEBA-1', t.id, a.id, 'Rama caída sobre la vereda', 'Web', 'Registrada', 'Vecino', 1, now()
                        FROM mod03_tipo_incidencia t, mod02_area_verde a
                        WHERE t.nombre = 'Rama caída (prueba)' AND a.nombre = 'Jardín Central'
                        RETURNING id\
                        """,
                        Map.of(),
                        Long.class);
        sql.execute(
                """
                INSERT INTO mod04_tipo_actividad (id, nombre, clase_id, activo) VALUES (106, 'Riego 06', 6, true)
                ON CONFLICT (id) DO NOTHING;
                INSERT INTO mod04_personal (id, nombres, apellidos, tipo_personal) VALUES
                    (1, 'Rosa', 'Quispe', 'Capataz'), (2, 'Luis', 'Ramos', 'Operario'), (3, 'Ana', 'Torres', 'Operario');
                INSERT INTO mod04_cuadrilla (id, nombre, capataz_id, usuario_creacion_id, usuario_modificacion_id)
                    VALUES (1, 'Cuadrilla Norte', 1, 1, 1);
                INSERT INTO mod04_parte_diario (id, cuadrilla_id, capataz_id, fecha, usuario_creacion_id)
                    VALUES (1, 1, 1, '2026-10-05', 1);
                INSERT INTO mod04_actividad (id, parte_id, tipo_actividad_id, area_verde_id, fecha_hora_actividad,
                    tiempo_minutos, usuario_creacion_id) VALUES
                    (1, 1, 106, 1, '2026-10-05 08:00-05', 180, 1),
                    (2, 1, 106, 1, '2026-10-06 08:00-05', NULL, 1);
                INSERT INTO mod04_participacion_actividad (actividad_id, operario_id) VALUES (1, 2), (1, 3);
                """);
        fabrica.cliente = null;
    }

    ResultActions publicar(String usuario, String ruta, String cuerpo) throws Exception {
        return http.perform(
                post("/api/v1/ia/" + ruta)
                        .cookie(sesion(usuario))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    static long id(ResultActions r) throws Exception {
        return Long.parseLong(
                r.andReturn()
                        .getResponse()
                        .getContentAsString()
                        .replaceAll("(?s)^\\{\"id\":(\\d+).*", "$1"));
    }

    // --- RF-IA-005
    // --------------------------------------------------------------------------------

    @Test
    void laIncidenciaRegistradaSeClasificaYPasaAClasificadaEnMod03() throws Exception {
        http.perform(
                        get("/api/v1/ia/clasificaciones-incidencia/pendientes/")
                                .cookie(sesion("ingeniero")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].codigo").value("INC-PRUEBA-1"));

        fabrica.cliente =
                new ClienteIAFalso(
                        Map.of(
                                "sugerencia",
                                Map.of(
                                        "tipo_evento",
                                        "Incidencia",
                                        "categoria",
                                        "Poda",
                                        "severidad",
                                        "Media",
                                        "via_atencion",
                                        "PersonalPropio",
                                        "es_sencilla",
                                        true),
                                "motivos",
                                Map.of("severidad", "Bloquea la vereda"),
                                "explicacion",
                                "Prueba",
                                "limitaciones",
                                List.of()));
        long id =
                id(
                        publicar(
                                        "ingeniero",
                                        "clasificaciones-incidencia/",
                                        "{\"incidencia_id\": " + incidencia + "}")
                                .andExpect(status().isCreated())
                                .andExpect(
                                        jsonPath("$.contenido.sugerencia.severidad").value("Media"))
                                .andExpect(jsonPath("$.registro_id").value(incidencia)));

        publicar(
                        "jefe",
                        "clasificaciones-incidencia/" + id + "/validar/",
                        "{\"por_campo\": {\"severidad\": {\"accion\": \"corregir\","
                                + " \"valor_final\": \"Alta\"}}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Validada"))
                .andExpect(
                        jsonPath("$.contenido.decision.valores_registrados.severidad")
                                .value("Alta"));
        Map<String, Object> fila =
                jdbc.queryForMap(
                        "SELECT estado, severidad, impacto, via FROM mod03_incidencia WHERE id ="
                                + " :id",
                        Map.of("id", incidencia));
        assertEquals(
                Map.of(
                        "estado",
                        "Clasificada",
                        "severidad",
                        "Alta",
                        "impacto",
                        "Alto",
                        "via",
                        "PersonalPropio"),
                fila);
    }

    @Test
    void sinCamposObligatoriosNoSeTocaLaIncidencia() throws Exception {
        fabrica.cliente = new ClienteIAFalso(Map.of("sugerencia", Map.of("severidad", "Baja")));
        long id =
                id(
                        publicar(
                                "ingeniero",
                                "clasificaciones-incidencia/",
                                "{\"incidencia_id\": " + incidencia + "}"));
        publicar("jefe", "clasificaciones-incidencia/" + id + "/validar/", "{\"por_campo\": {}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("clasificacion-incompleta"))
                .andExpect(jsonPath("$.errores.tipo_evento[0]").value("Obligatorio"));
        assertEquals(
                "Registrada",
                jdbc.queryForObject(
                        "SELECT estado FROM mod03_incidencia WHERE id = :id",
                        Map.of("id", incidencia),
                        String.class));
        http.perform(
                        get("/api/v1/ia/clasificaciones-incidencia/" + id + "/")
                                .cookie(sesion("jefe")))
                .andExpect(jsonPath("$.estado").value("Propuesta"));
    }

    @Test
    void laClasificacionManualNoLlamaALaIa() throws Exception {
        publicar(
                        "jefe",
                        "clasificaciones-incidencia/manual/",
                        "{\"incidencia_id\": "
                                + incidencia
                                + ", \"valores\": {\"tipo_evento\": \"Reclamo\", \"severidad\":"
                                + " \"Baja\", \"via_atencion\": \"Tercerizado\"}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("Validada"))
                .andExpect(jsonPath("$.contenido.sugerencia").doesNotExist());
        assertEquals(
                "Tercerizada",
                jdbc.queryForObject(
                        "SELECT via FROM mod03_incidencia WHERE id = :id",
                        Map.of("id", incidencia),
                        String.class));
    }

    // --- RF-IA-004
    // --------------------------------------------------------------------------------

    @Test
    void laEstimacionUsaAntecedentesYCalculaLaDiferencia() throws Exception {
        http.perform(
                        get("/api/v1/ia/estimaciones/actividades-pendientes/")
                                .cookie(sesion("practicante")))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].codigo").value("ACT-2"));

        fabrica.cliente =
                new ClienteIAFalso(
                        Map.of(
                                "estimacion",
                                Map.of(
                                        "operarios_min",
                                        2,
                                        "operarios_max",
                                        3,
                                        "operarios_sugeridos",
                                        2,
                                        "duracion",
                                        3,
                                        "unidad_duracion",
                                        "horas",
                                        "insumos",
                                        List.of(
                                                Map.of(
                                                        "nombre",
                                                        "Agua",
                                                        "cantidad",
                                                        200,
                                                        "unidad",
                                                        "L"))),
                                "variables",
                                List.of(),
                                "explicacion",
                                "Prueba",
                                "limitaciones",
                                List.of()));
        long id =
                id(
                        publicar("ingeniero", "estimaciones/", "{\"actividad_id\": 2}")
                                .andExpect(status().isCreated())
                                .andExpect(
                                        jsonPath("$.contenido.antecedentes[0].operarios").value(2))
                                .andExpect(
                                        jsonPath("$.contenido.antecedentes[0].duracion_horas")
                                                .value(3.0)));

        publicar(
                        "jefe",
                        "estimaciones/" + id + "/validar/",
                        """
                        {"acciones": {"operarios": "corregir", "duracion": "corregir", "insumos": "corregir"},
                         "confirmado": {"operarios": 3, "duracion": 1, "unidad_duracion": "jornadas",
                                        "insumos": [{"nombre": "agua", "cantidad": 150, "unidad": "L"},
                                                    {"nombre": "Abono", "cantidad": 2, "unidad": "kg"}]}}\
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.decision.diferencia.operarios").value(1))
                // 1 jornada = 8 h → 8 − 3 = 5 horas.
                .andExpect(jsonPath("$.contenido.decision.diferencia.duracion").value(5.0))
                .andExpect(jsonPath("$.contenido.decision.diferencia.insumos.length()").value(2));
    }

    @Test
    void laEstimacionManualSeGuardaValidada() throws Exception {
        publicar(
                        "ingeniero",
                        "estimaciones/manual/",
                        "{\"actividad_id\": 2, \"confirmado\": {\"operarios\": 2, \"duracion\": 4,"
                                + " \"unidad_duracion\": \"horas\", \"insumos\": []}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("Validada"))
                .andExpect(jsonPath("$.contenido.estimacion").doesNotExist());
    }
}
