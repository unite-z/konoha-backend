package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.OffsetDateTime;
import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Panel de indicadores de gestión (RF-REP-001 · HU-MOD06-001 · CU-MOD06-001), con los campos
 * comunes de los reportes ({@code ReporteBase} del frontend).
 */
public record PanelIndicadores(
        RangoFechas periodo,
        FiltrosPanel filtrosAplicados,
        OffsetDateTime generadoEn,
        boolean sinDatos,
        String mensajeSinDatos,
        List<ResultadoIndicador> indicadores,
        List<String> notas) {}
