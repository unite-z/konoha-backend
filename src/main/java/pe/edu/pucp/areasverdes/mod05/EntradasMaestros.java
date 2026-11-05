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
    public static final String FRECUENCIA_UNIDAD = "Dia|Semana|Mes|Anio";
    static final String TELEFONO = "^$|^\\+?[0-9 ()\\-]{6,30}$";

    public record Proveedor(
            @NotBlank(message = "Indica la razón social")
                    @Size(max = 200, message = "La razón social admite hasta 200 caracteres")
                    String razon_social,
            @NotBlank(message = "Indica el RUC")
                    @Pattern(regexp = "\\d{11}", message = "El RUC tiene 11 dígitos")
                    String ruc,
            @Size(max = 120, message = "El nombre del contacto admite hasta 120 caracteres")
                    String contacto_nombre,
            @Pattern(
                            regexp = TELEFONO,
                            message = "El teléfono admite de 6 a 30 dígitos, espacios, +, - y ()")
                    String contacto_telefono,
            @Size(max = 254, message = "El correo admite hasta 254 caracteres")
                    @Email(message = "El correo no es válido")
                    String contacto_correo,
            @Pattern(regexp = VINCULO, message = "Tipo de vínculo no válido") String tipo_vinculo,
            @Positive Long proveedor_relacionado_id) {}

    public record EstadoProveedor(
            @NotNull(message = "Indica si el proveedor queda habilitado") Boolean activo) {}

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
            // Frecuencia: cantidad y período (4 veces al año) o, para clientes anteriores, el texto libre.
            // El servicio exige una de las dos formas (`MaestrosService.crearContrato`).
            @Size(max = 120, message = "Admite hasta 120 caracteres") String frecuencia_pactada,
            @Min(value = 1, message = "La frecuencia es de al menos una vez")
                    @Max(value = 365, message = "La frecuencia admite hasta 365 veces")
                    Integer frecuencia_cantidad,
            @Pattern(regexp = FRECUENCIA_UNIDAD, message = "Período de la frecuencia no válido")
                    String frecuencia_unidad,
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

    public record CancelarAtencionEspecial(
            @Size(max = 300, message = "El motivo admite hasta 300 caracteres") String motivo) {}

    public record ProductoAgronomico(
            @NotBlank(message = "Indica el nombre del producto")
                    @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
                    String nombre,
            @Size(max = 150, message = "El principio activo admite hasta 150 caracteres")
                    String principio_activo,
            @Size(max = 120, message = "La presentación admite hasta 120 caracteres")
                    String presentacion) {}

    public record EstadoProductoAgronomico(
            @NotNull(message = "Indica si el producto queda habilitado") Boolean activo) {}
}
