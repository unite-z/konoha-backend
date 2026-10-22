package pe.edu.pucp.areasverdes.mod06.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;

/**
 * Entradas de la API del MOD-06 ({@code serializers.py}, parte de entrada), con componentes {@code snake_case}.
 *
 * <p>La validación la hacen {@code SerializadoresEntrada} con los mensajes del Django; estos {@code record} reciben
 * los datos ya validados y documentan el contrato en OpenAPI.
 */
public final class Entradas {

    private Entradas() {}

    /** Parámetros de RF-REP-002. */
    public record CumplimientoCorteQuery(
        @Schema(description = "Fecha de inicio del periodo (AAAA-MM-DD), incluida.", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate desde,
        @Schema(description = "Fecha de fin del periodo (AAAA-MM-DD), incluida.", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate hasta) {}

    /** Parámetros de RF-REP-004. */
    public record DistribucionPersonalQuery(LocalDate desde, LocalDate hasta,
        @Schema(description = "Cuadrilla (Cuadrilla.id).", minimum = "1") Long cuadrilla_id,
        @Schema(description = "Clase de actividad (ClaseActividad.id).", minimum = "1") Long clase_actividad_id,
        @Schema(description = "Tipo de actividad (TipoActividad.id).", minimum = "1") Long tipo_actividad_id,
        @Schema(description = "Espacio (AreaVerde.id).", minimum = "1") Long area_verde_id) {}

    /** Parámetros de RF-REP-006. */
    public record AtencionesEspacioSectorQuery(LocalDate desde, LocalDate hasta,
        @Schema(description = "Sector (SectorMantenimiento.id) o 'sin_sector' para el grupo sin sector.",
            oneOf = {Long.class, String.class}) Object sector_id,
        @Schema(description = "Modalidad de ejecución (enumerado Ejecutor).", allowableValues = {"PersonalPropio", "Tercerizado"}) String modalidad,
        @Schema(description = "Espacio (AreaVerde.id).", minimum = "1") Long area_verde_id,
        @Schema(description = "asc: menos atendidos primero (para priorizar); desc (por defecto): más atendidos primero.",
            allowableValues = {"asc", "desc"}) String orden) {}

    /** Filtros de la lista de propuestas de IA. */
    public record ExtraccionesQuery(
        @Schema(description = "Estado de la sugerencia (EstadoSugerencia).", allowableValues = {"Propuesta", "Validada", "Rechazada"}) String estado,
        @Schema(description = "Reporte del proveedor (ReporteProveedor.id).", minimum = "1") Long reporte_proveedor_id) {}

    /** Cuerpo de {@code POST /ia/extracciones/}. */
    public record SolicitudExtraccion(
        @Schema(description = "ReporteProveedor cuyo informe adjunto se analizará.", minimum = "1", type = "integer",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Object reporte_proveedor_id) {}

    /** Decisión sobre un campo propuesto (CU-MOD05-025 1a). */
    public record DecisionCampo(
        @Schema(description = "aceptar: se usa el valor propuesto; corregir: se usa valor_final; descartar: no se incorpora.",
            allowableValues = {"aceptar", "corregir", "descartar"}, requiredMode = Schema.RequiredMode.REQUIRED) String accion,
        @Schema(description = "Obligatorio si la acción es corregir; debe ser del tipo del campo.", nullable = true) Object valor_final) {}

    /** Cuerpo de {@code POST /ia/extracciones/{id}/validar/}. */
    public record Validacion(
        @Schema(description = "Una entrada por cada campo de la propuesta: {\"campo_<id>\": {accion, valor_final}}.",
            requiredMode = Schema.RequiredMode.REQUIRED) Map<String, DecisionCampo> decisiones) {}

    /** Cuerpo de {@code POST /ia/extracciones/{id}/rechazar/}. */
    public record Rechazo(@Schema(description = "Motivo del rechazo (opcional).", maxLength = 1000, nullable = true) String motivo) {}
}
