package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampo;

/**
 * Panel, indicador 1 (CU-MOD06-001, paso 4): actividades del personal propio por estado y tipo.
 *
 * <p>TODO (cuando se fusione MOD-04): el cálculo real (número de actividades por estado y tipo) se
 * hará cuando {@link FuenteOperacionCampo} tenga consultas. Hasta entonces responde «Sin datos» con
 * su motivo.
 */
@Component
public class IndicadorActividadesPorEstado implements IndicadorPanel {

    public static final String CLAVE = "actividades-por-estado";

    private final FuenteOperacionCampo operacion;
    private final Clock reloj;

    public IndicadorActividadesPorEstado(FuenteOperacionCampo operacion, Clock reloj) {
        this.operacion = operacion;
        this.reloj = reloj;
    }

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public Indicador definicion() {
        return DefinicionesIndicador.ACTIVIDADES_POR_ESTADO;
    }

    @Override
    public ResultadoIndicador calcular(ConsultaPanel consulta) {
        return ResultadoIndicador.sinDatos(this, motivo(), List.of());
    }

    @Override
    public DetalleIndicador detalle(
            ConsultaPanel consulta, String page, Paginacion.EnlacePagina enlace) {
        return Detalles.sinDatos(this, consulta, reloj, page, motivo(), List.of());
    }

    private String motivo() {
        Disponibilidad d = operacion.disponibilidad();
        return d.disponible() ? PanelService.MENSAJE_SIN_DATOS : d.motivo();
    }
}
