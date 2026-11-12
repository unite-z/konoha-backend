package pe.edu.pucp.areasverdes.mod03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.web.servlet.ResultActions;

/** AV-362: reprogramar una programación aún no iniciada, con las reglas del alta. */
class ReprogramarApiTest extends ApiPlanificacion {

    static final LocalDate HOY = LocalDate.now(LIMA);
    static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    long corte;

    @BeforeEach
    void escenario() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
        corte = programada(null, HOY.plusDays(10));
    }

    /** Un corte de césped (ruidoso) programado de {@code inicio} a tres días después. */
    long programada(Long proveedor, LocalDate inicio) {
        return jdbc.queryForObject(
                """
                INSERT INTO mod03_programacion_servicio (tipo_servicio_id, proveedor_id, estacion,
                    fecha_inicio_prevista, fecha_fin_prevista, ventana_mes, estado, creado_por_id,
                    usuario_creacion_id, usuario_modificacion_id)
                VALUES (1, :proveedor, 'Primavera', :inicio, :fin, :ventana, 'Programada', 1, 1, 1)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("proveedor", proveedor)
                        .addValue("inicio", inicio)
                        .addValue("fin", inicio.plusDays(3))
                        .addValue(
                                "ventana",
                                proveedor == null ? null : YearMonth.from(inicio).toString()),
                Long.class);
    }

    ResultActions reprogramar(
            String usuario, long id, LocalDate inicio, LocalDate fin, String resto)
            throws Exception {
        return http.perform(
                patch("/api/v1/planificacion/programaciones/{id}/", id)
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fecha_inicio_prevista\": \"%s\", \"fecha_fin_prevista\": \"%s\"%s}"
                                        .formatted(inicio, fin, resto)));
    }

    @Test
    void mueveLasFechasYLoDejaEnElHistorial() throws Exception {
        reprogramar("jefe", corte, HOY.plusDays(20), HOY.plusDays(23), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Programada"))
                .andExpect(jsonPath("$.fecha_inicio_prevista").value(HOY.plusDays(20).toString()))
                .andExpect(jsonPath("$.fecha_fin_prevista").value(HOY.plusDays(23).toString()))
                .andExpect(jsonPath("$.historial.length()").value(1))
                .andExpect(jsonPath("$.historial[0].estado").value("Programada"))
                .andExpect(jsonPath("$.historial[0].autor").value("jefe"))
                .andExpect(
                        jsonPath("$.historial[0].motivo")
                                .value(
                                        startsWith(
                                                "Reprogramada del "
                                                        + HOY.plusDays(20).format(DIA))));
    }

    @Test
    void aplicaLasMismasReglasQueElAlta() throws Exception {
        // El examen de admisión de mañana prohíbe los servicios ruidosos.
        reprogramar("jefe", corte, HOY.plusDays(1), HOY.plusDays(3), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("fecha-prohibida"));
        reprogramar("jefe", corte, HOY.plusDays(20), HOY.plusDays(26), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("duracion-excedida"));
        reprogramar("jefe", corte, HOY.plusDays(20), HOY.plusDays(19), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("periodo-invalido"));
        reprogramar("practicante", corte, HOY.plusDays(20), HOY.plusDays(23), "")
                .andExpect(status().isForbidden());
        reprogramar("jefe", 999999, HOY.plusDays(20), HOY.plusDays(23), "")
                .andExpect(status().isNotFound());
        // El ciclo completado del escenario ya no se puede mover.
        reprogramar("jefe", 1, HOY.plusDays(20), HOY.plusDays(23), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("transicion-invalida"));
    }

    @Test
    void unAvisoExigeJustificacionYQuedaComoConstancia() throws Exception {
        jdbc.update(
                """
                INSERT INTO mod03_restriccion_calendario (tipo_restriccion_id, fecha_inicio,
                    fecha_fin, usuario_creacion_id, usuario_modificacion_id)
                SELECT id, :dia, :dia, 1, 1 FROM mod03_tipo_restriccion
                WHERE nombre = 'Actividad extracurricular'
                """,
                Map.of("dia", HOY.plusDays(21)));
        reprogramar("jefe", corte, HOY.plusDays(20), HOY.plusDays(23), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("restricciones-sin-justificar"));
        reprogramar(
                        "jefe",
                        corte,
                        HOY.plusDays(20),
                        HOY.plusDays(23),
                        ", \"justificacion\": \"Solo en la zona norte\"")
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT justificacion FROM mod03_confirmacion_restriccion"
                                        + " WHERE programacion_id = :id",
                                Map.of("id", corte),
                                String.class))
                .isEqualTo("Solo en la zona norte");
    }

    @Test
    void conProveedorRespetaLaVentanaYLeAvisaDelTraslado() throws Exception {
        long proveedor =
                jdbc.queryForObject(
                        "INSERT INTO mod05_proveedor(razon_social, activo) VALUES ('Jardines"
                                + " Demo', true) RETURNING id",
                        Map.of(),
                        Long.class);
        LocalDate inicio = HOY.withDayOfMonth(1).plusMonths(1);
        long tercerizado = programada(proveedor, inicio);
        reprogramar("jefe", tercerizado, inicio.plusMonths(1), inicio.plusMonths(1).plusDays(3), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fuera-de-ventana"));
        reprogramar("jefe", tercerizado, inicio.plusDays(10), inicio.plusDays(13), "")
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT texto FROM mod03_aviso WHERE destinatario_proveedor_id ="
                                        + " :proveedor AND origen = 'programacion' AND origen_id ="
                                        + " :id",
                                Map.of("proveedor", proveedor, "id", tercerizado),
                                String.class))
                .startsWith("Servicio trasladado del ");
    }
}
