package pe.edu.pucp.areasverdes.mod05;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class Entradas {
    private Entradas() {}
    public static final String ORIGEN = "Demanda|ProgramacionEstacional|SolicitudUnidad";
    public static final String CATEGORIA = "InformeFinal|Metrado|PlantasAtendidas|FichasDiarias|CharlasTecnicas|PlagasAtendidas|ProductosAplicados|EvidenciaEspacio|RutaRecorrida";
    public record Alcance(@Positive Long area_verde_id, @Positive Long ejemplar_id,
                          @DecimalMin("0") @Digits(integer=10, fraction=2) BigDecimal metrado, @Positive Long unidad_medida_id) {}
    public record Crear(@NotNull @Positive Long contrato_id, @NotBlank String descripcion,
                        @NotBlank @Pattern(regexp=ORIGEN) String origen, @Positive Long unidad_solicitante_id,
                        @Size(max=150) String unidad_provisional, Boolean es_riesgo,
                        @NotEmpty List<@NotNull @Valid Alcance> alcances) {}
    public record Orden(@NotBlank @Size(max=30) String numero_orden_compra, String motivo) {}
    public record Inicio(@NotNull OffsetDateTime fecha_hora_inicio) {}
    public record Fin(@NotNull OffsetDateTime fecha_hora_fin) {}
    public record Origen(@NotBlank @Pattern(regexp=ORIGEN) String origen, @Positive Long unidad_solicitante_id,
                         @Size(max=150) String unidad_provisional, @NotBlank String motivo) {}
    public record Acuerdo(@JsonProperty(required=true) Boolean acuerdo_unidad, LocalDate fecha_acuerdo_unidad,
                          String comentarios_acuerdo_unidad) {}
    public record Documento(@NotBlank @Pattern(regexp=CATEGORIA) String categoria,
                            @Positive Long alcance_id, @Size(max=200) String descripcion) {}
}
