package pe.edu.pucp.areasverdes.mod03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Aviso proactivo de plazo de las incidencias sencillas (AV-280, RN-019). */
class AvisosPlazoIncidenciasApiTest extends ApiPlanificacion {

    static final String AVISOS = "/api/v1/planificacion/avisos/";

    @Autowired AvisosPlazoIncidencias avisosPlazo;

    @BeforeEach
    void espacio() {
        var sql = jdbc.getJdbcTemplate();
        sql.update(
                "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                        + " usuario_creacion_id, usuario_modificacion_id) VALUES ('Jardín de la"
                        + " Biblioteca', 'Biblioteca', 'Campus', 120, 1, 1)");
    }

    /** Inserta una incidencia sencilla abierta hace {@code horas}; devuelve su id. */
    long incidencia(int horas, String via, String estado) {
        return jdbc.getJdbcTemplate()
                .queryForObject(
                        """
                        INSERT INTO mod03_incidencia (codigo, tipo_incidencia_id, area_verde_id,
                            canal, descripcion, estado, via, registrada_por_id, fecha_registro)
                        SELECT 'INC-T' || (SELECT count(*) + 1 FROM mod03_incidencia), t.id,
                            (SELECT id FROM mod02_area_verde LIMIT 1), 'Telefono', 'Rama caída',
                            ?, ?, 1, now() - make_interval(hours => ?)
                        FROM mod03_tipo_incidencia t WHERE t.nombre = 'Rama caída' RETURNING id
                        """,
                        Long.class,
                        estado,
                        via,
                        horas);
    }

    long avisosDePlazo() {
        return jdbc.getJdbcTemplate()
                .queryForObject(
                        "SELECT count(*) FROM mod03_aviso WHERE texto LIKE '%sin atenderse%'",
                        Long.class);
    }

    @Test
    void avisaAlSuperarElObjetivoYAlVencerSinDuplicar() throws Exception {
        long id = incidencia(10, "PersonalPropio", "Registrada");
        avisosPlazo.avisar();
        assertThat(avisosDePlazo()).isZero();

        jdbc.getJdbcTemplate()
                .update("UPDATE mod03_incidencia SET fecha_registro = now() - interval '31 hours'");
        avisosPlazo.avisar();
        avisosPlazo.avisar();
        assertThat(avisosDePlazo()).isEqualTo(1);
        http.perform(get(AVISOS).with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$[0].texto").value(containsString("superó el objetivo")))
                .andExpect(jsonPath("$[0].origen_id").value(id));

        jdbc.getJdbcTemplate()
                .update("UPDATE mod03_incidencia SET fecha_registro = now() - interval '49 hours'");
        avisosPlazo.avisar();
        avisosPlazo.avisar();
        assertThat(avisosDePlazo()).isEqualTo(2);
        http.perform(get(AVISOS).with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$[0].texto").value(containsString("venció su plazo")));
    }

    @Test
    void noAvisaDeLaTercerizadaNiDeLaCerrada() {
        incidencia(50, "Tercerizada", "DerivadaTercerizada");
        incidencia(50, "PersonalPropio", "Cerrada");
        assertThat(avisosPlazo.avisar()).isZero();
        assertThat(avisosDePlazo()).isZero();
    }
}
