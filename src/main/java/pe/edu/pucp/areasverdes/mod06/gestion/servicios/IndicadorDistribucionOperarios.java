package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampo;

/**
 * Panel, indicador 2 (CU-MOD06-001, paso 4): distribución de los operarios.
 *
 * <p>TODO (cuando se fusione MOD-04): el cálculo real (operarios que participan, por cuadrilla y
 * espacio) se hará cuando {@link FuenteOperacionCampo} tenga consultas. Hasta entonces responde
 * «Sin datos» con su motivo.
 *
 * <p>RF-REP-004 ya existe como reporte ({@code /api/v1/reportes/distribucion-personal/}), pero lee
 * {@code DATA_SOURCE} (datos simulados). Este indicador no lo reutiliza: el panel lee la base
 * local.
 */
@Component
public class IndicadorDistribucionOperarios implements IndicadorPanel {

    public static final String CLAVE = "distribucion-operarios";

    private final FuenteOperacionCampo operacion;
    private final Clock reloj;

    public IndicadorDistribucionOperarios(FuenteOperacionCampo operacion, Clock reloj) {
        this.operacion = operacion;
        this.reloj = reloj;
    }

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public Indicador definicion() {
        return DefinicionesIndicador.DISTRIBUCION_OPERARIOS;
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
