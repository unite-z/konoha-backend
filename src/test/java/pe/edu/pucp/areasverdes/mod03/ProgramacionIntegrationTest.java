package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** V4: la base rechaza programaciones y confirmaciones incoherentes. */
class ProgramacionIntegrationTest extends ApiPlanificacion {

    @BeforeEach
    void servicio() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_tiposervicio RESTART IDENTITY CASCADE; INSERT INTO"
                                + " mod05_tiposervicio(nombre, ejecutor, es_riesgo, activo,"
                                + " es_periodico) VALUES ('Corte de césped', 'Tercerizado', false,"
                                + " true, true)");
    }

    void programar(String inicio, String fin, String estado, String ventana) {
        jdbc.getJdbcTemplate()
                .update(
                        "INSERT INTO mod03_programacion_servicio(tipo_servicio_id, estacion,"
                                + " fecha_inicio_prevista, fecha_fin_prevista, estado, ventana_mes,"
                                + " creado_por_id, usuario_creacion_id, usuario_modificacion_id)"
                                + " VALUES (1, 'Verano', ?::date, ?::date, ?, ?, 1, 1, 1)",
                        inicio,
                        fin,
                        estado,
                        ventana);
    }

    @Test
    void aceptaUnaProgramacionCoherente() {
        assertDoesNotThrow(() -> programar("2026-01-12", "2026-01-15", "Programada", "2026-01"));
    }

    @Test
    void rechazaFechasEstadosYVentanasInvalidos() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> programar("2026-01-15", "2026-01-12", "Programada", null));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> programar("2026-01-12", "2026-01-15", "Terminada", null));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> programar("2026-01-12", "2026-01-15", "Programada", "2026-13"));
    }

    @Test
    void laConfirmacionExigeJustificacion() {
        programar("2026-01-12", "2026-01-15", "Programada", null);
        jdbc.getJdbcTemplate()
                .execute(
                        "INSERT INTO mod03_restriccion_calendario(tipo_restriccion_id,"
                            + " fecha_inicio, fecha_fin, usuario_creacion_id,"
                            + " usuario_modificacion_id) VALUES (1, '2026-01-13', '2026-01-13', 1,"
                            + " 1)");
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.getJdbcTemplate()
                                .execute(
                                        "INSERT INTO"
                                            + " mod03_confirmacion_restriccion(programacion_id,"
                                            + " restriccion_id, usuario_id, justificacion) SELECT"
                                            + " p.id, r.id, 1, '  ' FROM"
                                            + " mod03_programacion_servicio p,"
                                            + " mod03_restriccion_calendario r"));
    }
}
