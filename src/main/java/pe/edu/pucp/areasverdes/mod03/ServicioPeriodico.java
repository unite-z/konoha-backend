package pe.edu.pucp.areasverdes.mod03;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** Servicio periódico con su frecuencia por estación (RF-PLA-001) y su duración (RF-PLA-002). */
public record ServicioPeriodico(
        long id,
        String nombre,
        boolean esRuidoso,
        List<Frecuencia> frecuencias,
        Duracion duracion) {

    public record Frecuencia(
            String estacion,
            int frecuenciaTeoricaDias,
            Integer frecuenciaPracticaDias,
            Integer frecuenciaMaximaDias,
            OffsetDateTime fechaModificacion,
            String modificadoPor) {}

    public record Duracion(
            int duracionMaximaDias,
            Integer diasLimpieza,
            LocalTime horaInicio,
            LocalTime horaFin,
            OffsetDateTime fechaModificacion,
            String modificadoPor) {}
}
