package pe.edu.pucp.areasverdes.mod04.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
            @Schema(
                            description =
                                    "Identificador de la ficha de campo. Alternativo a"
                                            + " capataz_usuario_id.")
                    @Positive
                    Long capatazId,
            @Schema(description = "Identificador de una cuenta activa con rol Capataz.") @Positive
                    Long capatazUsuarioId,
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

    public record Verificacion(@NotBlank @Size(max = 1000) String observaciones) {}

    public record Anulacion(@NotBlank @Size(max = 1000) String motivo) {}

    public record Parte(@NotNull @Positive Long cuadrillaId, @NotNull LocalDate fecha) {}

    public record Actividad(
            @NotNull @Positive Long parteId,
            @NotNull @Positive Long tipoActividadId,
            @NotNull @Positive Long areaVerdeId,
            @NotNull OffsetDateTime fechaHoraActividad,
            @Positive Long encargoId,
            @Size(max = 1000) String observaciones,
            @Positive Long claseActividadId,
            LocalDate fechaSolicitud,
            @Positive Long incidenciaId,
            @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal superficieM2,
            @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal cantidadSolicitada,
            @PositiveOrZero @DecimalMax("9999999999.99") BigDecimal cantidadEjecutada,
            @Positive Long unidadMedidaId,
            @Positive @Max(1440) Integer tiempoMinutos,
            @Positive Long ejemplarId,
            Boolean pendienteVinculacion) {}

    public record TipoActividad(
            @NotBlank @Size(max = 150) String nombre,
            @NotNull @Positive Long claseId,
            @NotNull Boolean activo) {}

    public record MaterialActividad(
            @Positive Long materialId,
            @Size(max = 150) String descripcionOtro,
            @NotNull @Positive @DecimalMax("9999999999.99") BigDecimal cantidad,
            @NotNull @Positive Long unidadMedidaId,
            @Size(max = 150) String origen,
            @Size(max = 150) String destino) {}
}
