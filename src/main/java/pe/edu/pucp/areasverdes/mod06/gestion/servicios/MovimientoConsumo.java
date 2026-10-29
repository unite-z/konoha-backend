package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.LocalDate;

/**
 * Un movimiento de consumo en el detalle del indicador «Consumo de materiales e insumos». Es {@code
 * MovimientoMaterial} sin el tipo de movimiento (interno) y con la cantidad en texto decimal.
 */
public record MovimientoConsumo(
        long id,
        long actividadId,
        LocalDate fecha,
        Long areaVerdeId,
        String espacio,
        String tipoMaterial,
        String categoria,
        String especie,
        String cantidad,
        String unidad) {}
