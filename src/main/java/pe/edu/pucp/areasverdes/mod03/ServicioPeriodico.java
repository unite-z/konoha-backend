package pe.edu.pucp.areasverdes.mod03;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Servicio periódico con su frecuencia por estación (RF-PLA-001) y su duración (RF-PLA-002). {@code
 * via}: quién lo hace ({@code Tercerizado} o {@code PersonalPropio}); {@code proveedor}: el del
 * contrato vigente si es tercerizado, o el del último contrato (AV-363).
 */
public record ServicioPeriodico(
        long id,
        String nombre,
        boolean esRuidoso,
        String via,
        String proveedor,
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
