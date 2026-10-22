package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** V3: catálogo de tipos de restricción y restricciones de la base. */
class RestriccionesIntegrationTest extends ApiPlanificacion {

    void restriccion(String inicio, String fin, String horaInicio, String horaFin) {
        jdbc.getJdbcTemplate()
                .update(
                        "INSERT INTO mod03_restriccion_calendario(tipo_restriccion_id,"
                            + " fecha_inicio, fecha_fin, hora_inicio, hora_fin,"
                            + " usuario_creacion_id, usuario_modificacion_id) VALUES (1, ?::date,"
                            + " ?::date, ?::time, ?::time, 1, 1)",
                        inicio,
                        fin,
                        horaInicio,
                        horaFin);
    }

    @Test
    void elCatalogoDistingueProhibicionYAviso() {
        String efecto = "SELECT efecto FROM mod03_tipo_restriccion WHERE nombre = ?";
        var sql = jdbc.getJdbcTemplate();
        assertEquals("Prohibicion", sql.queryForObject(efecto, String.class, "Examen de admisión"));
        assertEquals(
                "Advertencia",
                sql.queryForObject(efecto, String.class, "Actividad extracurricular"));
    }

    @Test
    void rechazaPeriodosYHorariosInvertidos() {
        restriccion("2026-11-02", "2026-11-02", "08:00", "12:00");
        assertThrows(
                DataIntegrityViolationException.class,
                () -> restriccion("2026-11-03", "2026-11-02", null, null));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> restriccion("2026-11-02", "2026-11-02", "12:00", "08:00"));
    }
}
