package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampo;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ParticipacionOperario;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * Panel, indicador 2 (CU-MOD06-001, paso 4): distribución de los operarios. El valor es el número
 * de operarios distintos que participan en las actividades del periodo; el desglose, cuántos
 * operarios distintos hay por cuadrilla y por espacio. Si {@link FuenteOperacionCampo} no está
 * disponible, responde «Sin datos» con su motivo.
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
        String motivo = motivoSinDatos(consulta);
        if (motivo != null) {
            return ResultadoIndicador.sinDatos(this, motivo, List.of());
        }
        List<ParticipacionOperario> participaciones = participaciones(consulta);
        if (participaciones.isEmpty()) {
            return ResultadoIndicador.sinDatos(this, PanelService.MENSAJE_SIN_DATOS, List.of());
        }
        long operarios =
                participaciones.stream().map(ParticipacionOperario::operarioId).distinct().count();
        List<ElementoDesglose> desglose = new ArrayList<>();
        desglose.addAll(operariosPor(participaciones, p -> "Cuadrilla: " + p.cuadrilla()));
        desglose.addAll(operariosPor(participaciones, p -> "Espacio: " + p.espacio()));
        return new ResultadoIndicador(
                CLAVE, definicion(), String.valueOf(operarios), false, null, desglose, List.of());
    }

    @Override
    public DetalleIndicador detalle(
            ConsultaPanel consulta, String page, Paginacion.EnlacePagina enlace) {
        String motivo = motivoSinDatos(consulta);
        if (motivo != null) {
            return Detalles.sinDatos(this, consulta, reloj, page, motivo, List.of());
        }
        List<ParticipacionOperario> registros =
                participaciones(consulta).stream()
                        .sorted(
                                Comparator.comparing(ParticipacionOperario::fecha)
                                        .reversed()
                                        .thenComparing(ParticipacionOperario::actividadId)
                                        .thenComparing(ParticipacionOperario::operarioId))
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
            return IndicadorActividadesPorEstado.MENSAJE_NO_APLICA_TERCERIZADOS;
        }
        return null;
    }

    private List<ParticipacionOperario> participaciones(ConsultaPanel consulta) {
        return operacion.participaciones(consulta.periodo(), consulta.filtros().areaVerdeId());
    }

    /** Operarios distintos por cada etiqueta, en orden alfabético. */
    private static List<ElementoDesglose> operariosPor(
            List<ParticipacionOperario> participaciones,
            Function<ParticipacionOperario, String> etiqueta) {
        Map<String, Set<Long>> grupos = new TreeMap<>();
        participaciones.forEach(
                p ->
                        grupos.computeIfAbsent(etiqueta.apply(p), k -> new HashSet<>())
                                .add(p.operarioId()));
        return grupos.entrySet().stream()
                .map(e -> new ElementoDesglose(e.getKey(), String.valueOf(e.getValue().size())))
                .toList();
    }
}
