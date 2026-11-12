package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FiltroAtenciones;

/** Consulta del panel ya validada ({@code ParametrosGestion}). */
public record ConsultaPanel(RangoFechas periodo, FiltrosPanel filtros) {

    FiltroAtenciones comoFiltroAtenciones() {
        return new FiltroAtenciones(
                periodo, filtros.areaVerdeId(), filtros.tipoServicioId(), filtros.estado());
    }
}
