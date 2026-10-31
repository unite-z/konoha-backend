package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un {@code MovimientoMaterial} (Modelo BD v1, 4.4: «Consumo, propagación o traslado de material en
 * una actividad», RF-ACT-005), con los datos que el adaptador resuelve con joins: la fecha y el
 * espacio de la {@code Actividad}, el nombre y la categoría del {@code TipoMaterial}, la especie,
 * la unidad ({@code UnidadMedida.simbolo}) y los nombres de los espacios de origen y destino.
 *
 * @param fecha fecha local (America/Lima) de la actividad
 * @param areaVerdeId espacio de la actividad; {@code null} = sin espacio
 * @param espacio nombre del espacio; {@code null} si {@code areaVerdeId} es {@code null}
 * @param categoria {@code TipoMaterial.categoria} («Material vegetal» o «Insumo», según el
 *     adaptador)
 * @param cantidad Decimal(10,2)
 * @param unidad {@code UnidadMedida.simbolo}
 */
public record MovimientoMaterial(
        long id,
        long actividadId,
        LocalDate fecha,
        Long areaVerdeId,
        String espacio,
        long tipoMaterialId,
        String tipoMaterial,
        String categoria,
        Long especieId,
        String especie,
        TipoMovimiento tipoMovimiento,
        BigDecimal cantidad,
        String unidad,
        Long origenAreaId,
        String origen,
        Long destinoAreaId,
        String destino) {}
