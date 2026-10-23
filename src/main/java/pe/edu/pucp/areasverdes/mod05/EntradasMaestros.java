package pe.edu.pucp.areasverdes.mod05;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entradas de RF-SER-001 (proveedores), RF-SER-002 (contratos) y RF-INV-011 (atención especial).
 */
public final class EntradasMaestros {
    private EntradasMaestros() {}

    public static final String VINCULO = "SinVinculo|Principal|Subsidiaria|Vinculada";

    public record Proveedor(
            @NotBlank(message = "Indica la razón social")
                    @Size(max = 200, message = "La razón social admite hasta 200 caracteres")
                    String razon_social,
            @NotBlank(message = "Indica el RUC")
                    @Pattern(regexp = "\\d{11}", message = "El RUC tiene 11 dígitos")
                    String ruc,
            @Size(max = 200, message = "El contacto admite hasta 200 caracteres") String contacto,
            @Pattern(regexp = VINCULO, message = "Tipo de vínculo no válido") String tipo_vinculo,
            @Positive Long proveedor_relacionado_id) {}

    public record Contrato(
            @NotNull(message = "Selecciona un proveedor") @Positive Long proveedor_id,
            @NotNull(message = "Selecciona el tipo de servicio") @Positive Long tipo_servicio_id,
            @NotBlank(message = "Indica el área o metrado estimado")
                    @Size(max = 120, message = "Admite hasta 120 caracteres")
                    String area_metrado_estimado,
            @NotNull(message = "Indica el monto fijo")
                    @DecimalMin(value = "0", message = "El monto no puede ser negativo")
                    @Digits(
                            integer = 10,
                            fraction = 2,
                            message = "El monto admite hasta dos decimales")
                    BigDecimal monto_fijo,
            @NotBlank(message = "Indica la frecuencia pactada")
                    @Size(max = 120, message = "Admite hasta 120 caracteres")
                    String frecuencia_pactada,
            @NotNull(message = "Indica el inicio de la vigencia") LocalDate vigencia_inicio,
            @NotNull(message = "Indica el fin de la vigencia") LocalDate vigencia_fin,
            String condiciones) {}

    public record AtencionEspecial(
            @NotNull(message = "Selecciona un ejemplar") @Positive Long ejemplar_id,
            @NotNull(message = "Selecciona la plaga recurrente") @Positive Long plaga_id,
            @NotNull(message = "Indica las aplicaciones adicionales")
                    @Min(value = 1, message = "Son una o dos aplicaciones")
                    @Max(value = 2, message = "Son una o dos aplicaciones")
                    Integer aplicaciones_adicionales) {}
}
