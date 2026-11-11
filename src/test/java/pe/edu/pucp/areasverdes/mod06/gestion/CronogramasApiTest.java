package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
 * RF-IA-003 contra PostgreSQL con {@link ClienteIAFalso}. Escenario: {@code escenario-gestion.sql}
 * con Control fitosanitario (tipo 2) periódico, duración máxima de 3 días, frecuencia de 30 días,
 * sin programaciones, contrato vigente con el proveedor 1, y una prohibición el 03/11/2026.
 */
@Import(PriorizacionInconsistenciasApiTest.IaFalsa.class)
class CronogramasApiTest extends ApiGestion {

    @Autowired PriorizacionInconsistenciasApiTest.FabricaFalsa fabrica;

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
        var sql = jdbc.getJdbcTemplate();
        sql.execute("TRUNCATE mod06_sugerencia_ia RESTART IDENTITY");
        sql.execute(
                """
                UPDATE mod05_tiposervicio SET es_periodico = (id = 2);
                INSERT INTO mod03_duracion_servicio (tipo_servicio_id, duracion_maxima_dias, dias_limpieza,
                    usuario_creacion_id, usuario_modificacion_id) VALUES (2, 3, 0, 1, 1);
                INSERT INTO mod03_frecuencia_servicio (tipo_servicio_id, estacion, frecuencia_teorica_dias,
                    frecuencia_practica_dias, frecuencia_maxima_dias, usuario_creacion_id, usuario_modificacion_id)
                    VALUES (2, 'Primavera', 30, 30, 40, 1, 1);
                INSERT INTO mod03_tipo_restriccion (nombre, efecto, aplica_servicios_ruidosos, activo) VALUES ('Feriado de prueba', 'Prohibicion', false, true)
                    ON CONFLICT DO NOTHING;
                DELETE FROM mod03_restriccion_calendario WHERE descripcion = 'Feriado de prueba';
                INSERT INTO mod03_restriccion_calendario (tipo_restriccion_id, fecha_inicio, fecha_fin, descripcion, aplica_servicios_ruidosos,
                    activo, usuario_creacion_id, usuario_modificacion_id)
                    SELECT id, '2026-11-03', '2026-11-03', 'Feriado de prueba', false, true, 1, 1
                    FROM mod03_tipo_restriccion WHERE nombre = 'Feriado de prueba';
                """);
        fabrica.cliente =
                new ClienteIAFalso(
                        Map.of(
                                "propuestas",
                                List.of(
                                        Map.of(
                                                "clave",
                                                "servicio-2",
                                                "fecha_inicio",
                                                "2026-11-02",
                                                "fecha_fin",
                                                "2026-11-04",
                                                "motivo",
                                                "Frecuencia vencida")),
                                "criterios",
                                List.of("Frecuencia"),
                                "explicacion",
                                "Prueba",
                                "limitaciones",
                                List.of()));
    }

    ResultActions publicar(String usuario, String ruta, String cuerpo) throws Exception {
        return http.perform(
                post("/api/v1/ia/cronogramas/" + ruta)
                        .cookie(sesion(usuario))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private long solicitar() throws Exception {
        String r =
                publicar("ingeniero", "", "{\"desde\": \"2026-11-01\", \"hasta\": \"2026-11-30\"}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.contenido.entrada.actividades_pendientes").value(1))
                        .andExpect(jsonPath("$.contenido.propuestas[0].clave").value("servicio-2"))
                        .andExpect(
                                jsonPath("$.contenido.propuestas[0].fecha_inicio")
                                        .value("2026-11-02"))
                        .andExpect(
                                jsonPath("$.contenido.propuestas[0].recursos.proveedor_id")
                                        .value(1))
                        .andExpect(
                                jsonPath("$.contenido.propuestas[0].coincidencias[0].tipo")
                                        .value("Prohibicion"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return Long.parseLong(r.replaceAll("(?s)^\\{\"id\":(\\d+).*", "$1"));
    }

    private int programadas() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mod03_programacion_servicio WHERE tipo_servicio_id = 2"
                        + " AND fecha_inicio_prevista >= '2026-11-01'",
                Map.of(),
                Integer.class);
    }

    @Test
    void sobreUnaProhibicionNoSeProgramaYCorregidaSeCreaEnMod03() throws Exception {
        long id = solicitar();
        publicar(
                        "jefe",
                        id + "/validar/",
                        "{\"por_propuesta\": {\"servicio-2\": {\"accion\": \"aceptar\"}}}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("restriccion-sin-confirmar"))
                .andExpect(jsonPath("$.errores['servicio-2']").exists());
        assertEquals(0, programadas());

        publicar(
                        "jefe",
                        id + "/validar/",
                        "{\"por_propuesta\": {\"servicio-2\": {\"accion\": \"aceptar\","
                                + " \"confirma_restriccion\": true}}}")
                // MOD-03 no programa sobre una prohibición: nada se crea y sigue en Propuesta.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("programacion-rechazada"));
        assertEquals(0, programadas());

        publicar(
                        "jefe",
                        id + "/validar/",
                        "{\"por_propuesta\": {\"servicio-2\": {\"accion\": \"corregir\","
                                + " \"valores_finales\": {\"fecha_inicio\": \"2026-11-04\","
                                + " \"fecha_fin\": \"2026-11-06\", \"proveedor_id\": 1}}}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Validada"))
                .andExpect(
                        jsonPath("$.contenido.decision.programaciones_creadas.length()").value(1));
        assertEquals(1, programadas());
    }

    @Test
    void faltaDecidirUnaPropuestaEs400() throws Exception {
        long id = solicitar();
        publicar("jefe", id + "/validar/", "{\"por_propuesta\": {}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("decisiones-incompletas"));
    }

    @Test
    void descartarlaTodaNoProgramaNada() throws Exception {
        long id = solicitar();
        publicar("jefe", id + "/rechazar/", "{\"motivo\": \"Esperamos al nuevo contrato\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Rechazada"));
        assertEquals(0, programadas());
    }
}
