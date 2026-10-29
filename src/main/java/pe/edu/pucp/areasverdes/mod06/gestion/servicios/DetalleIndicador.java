package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.OffsetDateTime;
import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Detalle de los registros de un indicador con los mismos filtros del panel (HU-MOD06-001, criterio
 * 3): el sobre común de los reportes más la paginación {@code {count, next, previous, results}} de
 * {@code api.md} ({@code Paginado<T>} del frontend).
 */
public record DetalleIndicador(
        RangoFechas periodo,
        FiltrosPanel filtrosAplicados,
        OffsetDateTime generadoEn,
        boolean sinDatos,
        String mensajeSinDatos,
        Indicador indicador,
        long count,
        String next,
        String previous,
        List<?> results,
        List<String> notas) {}
