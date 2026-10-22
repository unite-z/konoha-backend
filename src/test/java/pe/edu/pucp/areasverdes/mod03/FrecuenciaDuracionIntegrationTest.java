package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Restricciones de V2: la base rechaza configuraciones imposibles aunque el código falle. */
class FrecuenciaDuracionIntegrationTest extends ApiPlanificacion {

    @BeforeEach
    void servicio() {
        sql("TRUNCATE mod05_tiposervicio RESTART IDENTITY CASCADE");
        sql(
                "INSERT INTO mod05_tiposervicio(nombre, ejecutor, es_riesgo, activo, es_periodico,"
                    + " es_ruidoso) VALUES ('Corte de césped', 'Tercerizado', false, true, true,"
                    + " true)");
    }

    void sql(String sentencia) {
        jdbc.getJdbcTemplate().execute(sentencia);
    }

    void frecuencia(String estacion, int teorica, Integer practica, Integer maxima) {
        jdbc.getJdbcTemplate()
                .update(
                        "INSERT INTO mod03_frecuencia_servicio(tipo_servicio_id, estacion,"
                                + " frecuencia_teorica_dias, frecuencia_practica_dias,"
                                + " frecuencia_maxima_dias, usuario_creacion_id,"
                                + " usuario_modificacion_id) VALUES (1, ?, ?, ?, ?, 1, 1)",
                        estacion,
                        teorica,
                        practica,
                        maxima);
    }

    @Test
    void aceptaLaFrecuenciaDelCorteEnVerano() {
        assertDoesNotThrow(() -> frecuencia("Verano", 21, 30, 45));
    }

    @Test
    void rechazaUnaMaximaMenorQueLaHabitual() {
        assertThrows(DataIntegrityViolationException.class, () -> frecuencia("Verano", 21, 30, 25));
        assertThrows(
                DataIntegrityViolationException.class, () -> frecuencia("Otoño", 30, null, 20));
    }

    @Test
    void unaSolaFrecuenciaPorEstacionYEstacionesValidas() {
        frecuencia("Verano", 21, 30, 45);
        assertThrows(DataIntegrityViolationException.class, () -> frecuencia("Verano", 25, 30, 45));
        assertThrows(DataIntegrityViolationException.class, () -> frecuencia("Monzón", 25, 30, 45));
    }

    @Test
    void rechazaDuracionCeroYHorarioInvertido() {
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        sql(
                                "INSERT INTO mod03_duracion_servicio(tipo_servicio_id,"
                                        + " duracion_maxima_dias, usuario_creacion_id,"
                                        + " usuario_modificacion_id) VALUES (1, 0, 1, 1)"));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        sql(
                                "INSERT INTO mod03_duracion_servicio(tipo_servicio_id,"
                                        + " duracion_maxima_dias, hora_inicio, hora_fin,"
                                        + " usuario_creacion_id, usuario_modificacion_id)"
                                        + " VALUES (1, 1, '18:00', '06:00', 1, 1)"));
    }
}
