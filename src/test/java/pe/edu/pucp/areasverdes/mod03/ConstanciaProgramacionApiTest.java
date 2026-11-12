package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** HU-MOD03-004: aviso con constancia y confirmación expresa ante una prohibición. */
class ConstanciaProgramacionApiTest extends ApiPlanificacion {

    static final LocalDate HOY = LocalDate.now(LIMA);

    @BeforeEach
    void escenario() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
    }

    /** Programa dos días desde HOY + desde, con el resto del cuerpo JSON dado. */
    ResultActions programar(int servicio, int desde, String resto) throws Exception {
        return http.perform(
                post("/api/v1/planificacion/programaciones/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_servicio_id\": %d, \"fecha_inicio_prevista\": \"%s\","
                                                .formatted(servicio, HOY.plusDays(desde))
                                        + " \"fecha_fin_prevista\": \"%s\"%s}"
                                                .formatted(HOY.plusDays(desde + 1), resto)));
    }

    @Test
    void laProhibicionBloqueaAunqueSeJustifiqueYSeConfirme() throws Exception {
        int antes = programaciones();
        programar(1, 1, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("fecha-prohibida"));
        programar(
                        1,
                        1,
                        ", \"justificacion\": \"Única ventana del proveedor\","
                                + " \"confirmar_prohibicion\": true")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("fecha-prohibida"));
        assertEquals(antes, programaciones());
    }

    int programaciones() {
        return jdbc.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM mod03_programacion_servicio", Integer.class);
    }

    @Test
    void unAvisoSeGuardaConMotivoComoConstancia() throws Exception {
        jdbc.getJdbcTemplate()
                .update(
                        "INSERT INTO mod03_restriccion_calendario (tipo_restriccion_id,"
                            + " fecha_inicio, fecha_fin, usuario_creacion_id,"
                            + " usuario_modificacion_id) SELECT id, ?, ?, 1, 1 FROM"
                            + " mod03_tipo_restriccion WHERE nombre = 'Actividad extracurricular'",
                        HOY.plusDays(5),
                        HOY.plusDays(5));
        programar(1, 5, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("restricciones-sin-justificar"));
        programar(1, 5, ", \"justificacion\": \"Única ventana del proveedor\"")
                .andExpect(status().isCreated());
        assertEquals(
                "Única ventana del proveedor",
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT justificacion FROM mod03_confirmacion_restriccion",
                                String.class));
    }

    void restriccion(String tipo, int desde) {
        jdbc.getJdbcTemplate()
                .update(
                        "INSERT INTO mod03_restriccion_calendario (tipo_restriccion_id,"
                                + " fecha_inicio, fecha_fin, usuario_creacion_id,"
                                + " usuario_modificacion_id) SELECT id, ?, ?, 1, 1 FROM"
                                + " mod03_tipo_restriccion WHERE nombre = ?",
                        HOY.plusDays(desde),
                        HOY.plusDays(desde),
                        tipo);
    }

    @Test
    void enUnFeriadoSeProgramaSinConflictoYElEventoMasivoEsAviso() throws Exception {
        restriccion("Feriado", 10);
        restriccion("Paro", 10);
        restriccion("Cierre del campus", 10);
        programar(1, 10, "").andExpect(status().isCreated());
        restriccion("Evento masivo", 20);
        programar(1, 20, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("restricciones-sin-justificar"));
        programar(1, 20, ", \"justificacion\": \"Única ventana del proveedor\"")
                .andExpect(status().isCreated());
    }

    @Test
    void unServicioSilenciosoNoNecesitaConstancia() throws Exception {
        programar(2, 1, "").andExpect(status().isCreated());
    }
}
