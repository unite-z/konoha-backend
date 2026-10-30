package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteMovimientosMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.MovimientoMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.TipoMovimiento;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * Panel, indicador 4 (CU-MOD06-001, paso 4): consumo de materiales e insumos, con el puerto y la
 * agrupación de RF-REP-005 ({@link FuenteMovimientosMaterial} y {@link AgrupadorMaterial}). El
 * valor es el número de movimientos de consumo: las cantidades de unidades distintas no se suman.
 */
@Component
public class IndicadorConsumoMateriales implements IndicadorPanel {

    public static final String CLAVE = "consumo-materiales";

    // CONFIRMADO: CU-MOD05-028 (lo registra el capataz) y CU-MOD05-029 flujo 3a (productos del
    // proveedor en vista aparte): no aplica a servicios tercerizados.
    public static final String MENSAJE_NO_APLICA_TERCERIZADOS =
            "El material de las cuadrillas es de personal propio: no aplica a servicios"
                    + " tercerizados";

    private static final List<String> NOTAS = List.of(MaterialVegetalService.NOTA_UNIDADES);

    private final FuenteMovimientosMaterial fuente;
    private final Clock reloj;

    public IndicadorConsumoMateriales(FuenteMovimientosMaterial fuente, Clock reloj) {
        this.fuente = fuente;
        this.reloj = reloj;
    }

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public Indicador definicion() {
        return DefinicionesIndicador.CONSUMO_MATERIALES;
    }

    @Override
    public ResultadoIndicador calcular(ConsultaPanel consulta) {
        String motivo = motivoSinDatos(consulta);
        if (motivo != null) {
            return ResultadoIndicador.sinDatos(this, motivo, NOTAS);
        }
        List<MovimientoMaterial> consumos = consumos(consulta);
        if (consumos.isEmpty()) {
            return ResultadoIndicador.sinDatos(this, PanelService.MENSAJE_SIN_DATOS, NOTAS);
        }
        List<ElementoDesglose> desglose =
                AgrupadorMaterial.porMaterial(consumos, TipoMovimiento.CONSUMO).stream()
                        .map(
                                t ->
                                        new ElementoDesglose(
                                                t.tipoMaterial() + " (" + t.unidad() + ")",
                                                t.cantidad()))
                        .toList();
        return new ResultadoIndicador(
                CLAVE, definicion(), String.valueOf(consumos.size()), false, null, desglose, NOTAS);
    }

    @Override
    public DetalleIndicador detalle(
            ConsultaPanel consulta, String page, Paginacion.EnlacePagina enlace) {
        String motivo = motivoSinDatos(consulta);
        if (motivo != null) {
            return Detalles.sinDatos(this, consulta, reloj, page, motivo, NOTAS);
        }
        List<MovimientoConsumo> registros =
                consumos(consulta).stream()
                        .sorted(
                                Comparator.comparing(MovimientoMaterial::fecha)
                                        .reversed()
                                        .thenComparing(MovimientoMaterial::id))
                        .map(IndicadorConsumoMateriales::comoDetalle)
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
                NOTAS);
    }

    /** Motivo de «Sin datos» antes de leer, o {@code null} si se puede calcular. */
    private String motivoSinDatos(ConsultaPanel consulta) {
        Disponibilidad d = fuente.disponibilidad();
        if (!d.disponible()) {
            return d.motivo();
        }
        if (AtencionesEspacioSectorService.TERCERIZADO.equals(consulta.filtros().modalidad())) {
            return MENSAJE_NO_APLICA_TERCERIZADOS;
        }
        return null;
    }

    private List<MovimientoMaterial> consumos(ConsultaPanel consulta) {
        return fuente.movimientos(consulta.periodo(), consulta.filtros().areaVerdeId()).stream()
                .filter(m -> m.tipoMovimiento() == TipoMovimiento.CONSUMO)
                .toList();
    }

    private static MovimientoConsumo comoDetalle(MovimientoMaterial m) {
        return new MovimientoConsumo(
                m.id(),
                m.actividadId(),
                m.fecha(),
                m.areaVerdeId(),
                m.areaVerdeId() == null ? AgrupadorMaterial.SIN_ESPACIO : m.espacio(),
                m.tipoMaterial(),
                m.categoria(),
                m.especie(),
                m.cantidad().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                m.unidad());
    }
}
