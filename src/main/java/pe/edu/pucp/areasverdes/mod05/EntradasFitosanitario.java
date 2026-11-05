package pe.edu.pucp.areasverdes.mod05;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/** Entradas de RF-FIT-001 (plan fitosanitario estacional). */
public final class EntradasFitosanitario {
    private EntradasFitosanitario() {}

    public static final String ESTACION = "Verano|Otoño|Invierno|Primavera";

    public record AplicacionEstacional(
            @NotBlank(message = "Indica la estación")
                    @Pattern(regexp = ESTACION, message = "Estación no válida")
                    String estacion,
            @NotNull(message = "Indica la fecha de inicio") LocalDate fecha_inicio,
            @NotNull(message = "Indica la fecha de fin") LocalDate fecha_fin,
            @NotEmpty(message = "Indica al menos un espacio o ejemplar")
                    List<Entradas.@NotNull @Valid Alcance> alcances) {}

    /**
     * Hasta cuatro aplicaciones, una por estación (RN-010). {@code motivo} hace falta si falta
     * alguna estación o si una fecha coincide con una restricción del calendario (RN-017); el
     * servicio lo reutiliza como justificación en ambos casos.
     */
    public record Plan(
            @NotNull(message = "Selecciona el contrato") @Positive Long contrato_id,
            @NotEmpty(message = "Indica al menos una aplicación")
                    @Size(max = 4, message = "Son hasta cuatro aplicaciones, una por estación")
                    List<@NotNull @Valid AplicacionEstacional> aplicaciones,
            @Size(max = 300, message = "Admite hasta 300 caracteres") String motivo) {}

    /** RF-FIT-005: observación de plaga en un espacio o ejemplar (uno de los dos, nunca ambos). */
    public record ObservacionPlaga(
            @Positive Long area_verde_id,
            @Positive Long ejemplar_id,
            @NotNull(message = "Selecciona la plaga observada") @Positive Long plaga_id,
            @NotBlank(message = "Describe la observación")
                    @Size(max = 500, message = "La observación admite hasta 500 caracteres")
                    String descripcion,
            @NotNull(message = "Indica la fecha de la observación") LocalDate fecha_observacion) {}

    /**
     * RF-FIT-006: una aplicación adicional del ejemplar marcado con atención especial (RF-INV-011).
     * {@code motivo} hace falta si la fecha coincide con una restricción del calendario (RN-017).
     */
    public record AplicacionAdicional(
            @NotNull(message = "Selecciona el contrato") @Positive Long contrato_id,
            @NotNull(message = "Indica la fecha de inicio") LocalDate fecha_inicio,
            @NotNull(message = "Indica la fecha de fin") LocalDate fecha_fin,
            @Size(max = 300, message = "Admite hasta 300 caracteres") String motivo) {}
}
