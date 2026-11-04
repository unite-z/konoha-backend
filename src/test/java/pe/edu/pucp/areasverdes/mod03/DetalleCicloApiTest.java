package pe.edu.pucp.areasverdes.mod03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** AV-364: código legible del ciclo y detalle de una programación. */
class DetalleCicloApiTest extends ApiPlanificacion {

    static final LocalDate HOY = LocalDate.now(LIMA);

    @BeforeEach
    void escenario() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
    }

    ResultActions programar(int servicio, LocalDate inicio, LocalDate fin) throws Exception {
        return http.perform(
                post("/api/v1/planificacion/programaciones/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_servicio_id\": %d, \"fecha_inicio_prevista\": \"%s\","
                                                .formatted(servicio, inicio)
                                        + " \"fecha_fin_prevista\": \"%s\"}".formatted(fin)));
    }

    @Test
    void lasInicialesDelServicioSinArticulosNiPreposiciones() {
        for (var caso :
                Map.of(
                                "Corte de césped", "CC",
                                "Control fitosanitario", "CF",
                                "Poda mayor programada", "PMP",
                                "Riego de los jardines y áreas", "RJA")
                        .entrySet()) {
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT mod03_prefijo_servicio(:nombre)",
                                    Map.of("nombre", caso.getKey()),
                                    String.class))
                    .isEqualTo(caso.getValue());
        }
    }

    @Test
    void cadaCicloRecibeUnCodigoCorrelativoPorServicioYAnio() throws Exception {
        LocalDate inicio = LocalDate.of(HOY.getYear() + 1, 3, 2);
        programar(1, inicio, inicio.plusDays(2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("CC-%d-01".formatted(inicio.getYear())));
        programar(1, inicio.plusDays(7), inicio.plusDays(9))
                .andExpect(jsonPath("$.codigo").value("CC-%d-02".formatted(inicio.getYear())));
        programar(2, inicio, inicio.plusDays(2))
                .andExpect(jsonPath("$.codigo").value("CF-%d-01".formatted(inicio.getYear())));
    }

    @Test
    void elDetalleTraeElCodigoElHistorialYLasRestriccionesQueLeAfectan() throws Exception {
        LocalDate inicio = LocalDate.of(HOY.getYear() + 1, 3, 2);
        long id =
                Long.parseLong(
                        programar(1, inicio, inicio.plusDays(2))
                                .andReturn()
                                .getResponse()
                                .getContentAsString()
                                .replaceAll("^\\{\"id\":(\\d+),.*$", "$1"));
        // Una actividad extracurricular (aviso para los ruidosos) en mitad del ciclo.
        jdbc.update(
                """
                INSERT INTO mod03_restriccion_calendario (tipo_restriccion_id, fecha_inicio,
                    fecha_fin, usuario_creacion_id, usuario_modificacion_id)
                SELECT id, :dia, :dia, 1, 1 FROM mod03_tipo_restriccion
                WHERE nombre IN ('Actividad extracurricular', 'Día sin actividad académica')
                """,
                Map.of("dia", inicio.plusDays(1)));
        http.perform(
                        get("/api/v1/planificacion/programaciones/{id}/", id)
                                .with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.codigo").value("CC-%d-01".formatted(inicio.getYear())))
                .andExpect(jsonPath("$.servicio").value("Corte de césped"))
                .andExpect(jsonPath("$.historial[0].estado").value("Programada"))
                .andExpect(jsonPath("$.restricciones.length()").value(1))
                .andExpect(jsonPath("$.restricciones[0].tipo").value("Actividad extracurricular"));
        http.perform(
                        get("/api/v1/planificacion/programaciones/{id}/", 999999)
                                .with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("programacion-no-encontrada"));
    }
}
