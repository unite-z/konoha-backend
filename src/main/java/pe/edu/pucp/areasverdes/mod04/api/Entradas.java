package pe.edu.pucp.areasverdes.mod04.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class Entradas {
    private Entradas() {}

    public record Personal(
            @NotBlank @Size(max = 100) String nombres,
            @NotBlank @Size(max = 150) String apellidos,
            @NotBlank @Pattern(regexp = "Capataz|Operario") String tipoPersonal,
            @Positive Long usuarioId) {}

    /** PUT reemplaza la composición completa, no aplica cambios parciales. */
    public record Cuadrilla(
            @NotBlank @Size(max = 150) String nombre,
            @NotNull @Positive Long capatazId,
            @NotNull Boolean activa,
            @NotNull @Size(max = 200) List<@NotNull @Positive Long> operarios) {}

    public record Encargo(
            @NotNull @Positive Long cuadrillaId,
            @NotNull @Positive Long tipoActividadId,
            @NotNull @Positive Long areaVerdeId,
            @NotBlank @Size(max = 1000) String descripcion,
            @NotBlank @Pattern(regexp = "Semanal|Quincenal") String periodicidad,
            @NotNull LocalDate fechaReunion,
            @NotNull LocalDate fechaInicio,
            @NotNull LocalDate fechaFin,
            @NotNull LocalDate plazoPrevisto) {}

    public record Participantes(
            @NotNull @Size(max = 200) List<@NotNull @Positive Long> operarios) {}

    public record Parte(@NotNull @Positive Long cuadrillaId, @NotNull LocalDate fecha) {}

    public record Actividad(
            @NotNull @Positive Long parteId,
            @NotNull @Positive Long tipoActividadId,
            @NotNull @Positive Long areaVerdeId,
            @NotNull OffsetDateTime fechaHoraActividad,
            @Positive Long encargoId,
            @Size(max = 1000) String observaciones) {}
}
