package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.OffsetDateTime;
import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Reporte mensual de consumo, propagación y traslado de material vegetal e insumos (RF-REP-005 ·
 * HU-MOD05-029 · CU-MOD05-029). Empieza con los campos comunes de los reportes ({@code ReporteBase}
 * del frontend).
 *
 * @param periodo del primer al último día del mes
 * @param consumido pasos 2 y 3 del CU
 * @param propagado paso 3 del CU
 * @param trasladado HU-MOD05-029 v2
 * @param comparacion paso 4, del mes más antiguo al más reciente
 * @param productosProveedor flujo 3a: vista aparte de los productos aplicados por el proveedor
 */
public record ReporteMaterialVegetal(
        RangoFechas periodo,
        FiltrosMaterialVegetal filtrosAplicados,
        OffsetDateTime generadoEn,
        boolean sinDatos,
        String mensajeSinDatos,
        Indicador indicador,
        List<TotalMaterial> consumido,
        List<TotalMaterial> propagado,
        List<TotalTraslado> trasladado,
        List<ComparacionMes> comparacion,
        SeccionNoDisponible productosProveedor,
        List<String> notas) {}
