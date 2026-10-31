package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.OffsetDateTime;
import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Mapa de atenciones (RF-REP-007 · HU-MOD06-003 · CU-MOD06-003): GeoJSON y lista tabular, con los
 * campos comunes de los reportes. La lista va siempre, para la vista de lista (E1).
 *
 * @param totalesPorTipo panel de capas: totales por tipo de atención
 * @param sinCoordenadas flujo A1 y vista de lista (E1)
 */
public record MapaAtenciones(
        RangoFechas periodo,
        FiltrosMapa filtrosAplicados,
        OffsetDateTime generadoEn,
        boolean sinDatos,
        String mensajeSinDatos,
        Indicador indicador,
        List<TotalTipoAtencion> totalesPorTipo,
        ColeccionGeoJson mapa,
        List<AtencionMapa> sinCoordenadas,
        List<String> notas) {}
