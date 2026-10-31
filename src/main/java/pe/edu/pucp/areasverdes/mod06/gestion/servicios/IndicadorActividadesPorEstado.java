package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ActividadCampo;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampo;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * Panel, indicador 1 (CU-MOD06-001, paso 4): actividades del personal propio por estado y tipo. El
 * valor es el número de actividades del periodo; el desglose, cuántas hay por estado y por tipo.
 *
 * <p>SUPUESTO (pendiente de MOD-04): el estado es el del parte diario ({@code Abierto} o {@code
 * Cerrado}), porque {@code mod04_actividad} no tiene estado propio. Si {@link FuenteOperacionCampo}
 * no está disponible, responde «Sin datos» con su motivo.
 */
@Component
public class IndicadorActividadesPorEstado implements IndicadorPanel {

    public static final String CLAVE = "actividades-por-estado";

    public static final String MENSAJE_NO_APLICA_TERCERIZADOS =
            "Las actividades son del personal propio: no aplica a servicios tercerizados";

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
        String motivo = motivoSinDatos(consulta);
        if (motivo != null) {
            return ResultadoIndicador.sinDatos(this, motivo, List.of());
        }
        List<ActividadCampo> actividades = actividades(consulta);
        if (actividades.isEmpty()) {
            return ResultadoIndicador.sinDatos(this, PanelService.MENSAJE_SIN_DATOS, List.of());
        }
        List<ElementoDesglose> desglose = new ArrayList<>();
        desglose.addAll(contar(actividades, a -> "Estado: " + a.estado()));
        desglose.addAll(contar(actividades, a -> "Tipo: " + a.tipoActividad()));
        return new ResultadoIndicador(
                CLAVE,
                definicion(),
                String.valueOf(actividades.size()),
                false,
                null,
                desglose,
                List.of());
    }

    @Override
    public DetalleIndicador detalle(
            ConsultaPanel consulta, String page, Paginacion.EnlacePagina enlace) {
        String motivo = motivoSinDatos(consulta);
        if (motivo != null) {
            return Detalles.sinDatos(this, consulta, reloj, page, motivo, List.of());
        }
        List<ActividadCampo> registros =
                actividades(consulta).stream()
                        .sorted(
                                Comparator.comparing(ActividadCampo::fecha)
                                        .reversed()
                                        .thenComparing(ActividadCampo::id))
                        .toList();
        return Detalles.pagina(
                this,
                consulta,
                reloj,
                registros.size(),
                page,
                enlace,
                desplazamiento ->
                        registros.stream()
                                .skip(desplazamiento)
                                .limit(Paginacion.TAMANO_PAGINA)
                                .toList(),
                PanelService.MENSAJE_SIN_DATOS,
                List.of());
    }

    /** Motivo de «Sin datos» antes de leer, o {@code null} si se puede calcular. */
    private String motivoSinDatos(ConsultaPanel consulta) {
        Disponibilidad d = operacion.disponibilidad();
        if (!d.disponible()) {
            return d.motivo();
        }
        if (AtencionesEspacioSectorService.TERCERIZADO.equals(consulta.filtros().modalidad())) {
            return MENSAJE_NO_APLICA_TERCERIZADOS;
        }
        return null;
    }

    private List<ActividadCampo> actividades(ConsultaPanel consulta) {
        return operacion.actividades(consulta.periodo(), consulta.filtros().areaVerdeId());
    }

    /** Cuántas actividades hay por cada etiqueta, en orden alfabético. */
    static List<ElementoDesglose> contar(
            List<ActividadCampo> actividades, Function<ActividadCampo, String> etiqueta) {
        Map<String, Integer> conteo = new TreeMap<>();
        actividades.forEach(a -> conteo.merge(etiqueta.apply(a), 1, Integer::sum));
        return conteo.entrySet().stream()
                .map(e -> new ElementoDesglose(e.getKey(), String.valueOf(e.getValue())))
                .toList();
    }
}
