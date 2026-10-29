package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.AtencionesTercerizadasRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ConteoEstado;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FiltroAtenciones;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * Panel, indicador 5 (CU-MOD06-001, paso 4): cumplimiento de servicios tercerizados.
 *
 * <p>SUPUESTO (pendiente de confirmar; la documentación no define fórmula ni meta, pendiente 6 de
 * los mockups; revisar con la contraparte): porcentaje de atenciones del periodo que están
 * ejecutadas ({@link AtencionesEspacioSectorService#ESTADOS_ATENCION_EJECUTADA}) sobre el total de
 * atenciones del periodo, con el desglose por estado. Ver {@link
 * DefinicionesIndicador#CUMPLIMIENTO_TERCERIZADOS}.
 */
@Component
public class IndicadorCumplimientoTercerizados implements IndicadorPanel {

    public static final String CLAVE = "cumplimiento-tercerizados";
    public static final String MENSAJE_SIN_DATOS =
            "No hay atenciones tercerizadas en el periodo elegido";
    public static final String MENSAJE_NO_APLICA_PERSONAL_PROPIO =
            "El cumplimiento de servicios tercerizados no aplica al personal propio";
    public static final String NOTA_CONFORMIDAD =
            "La conformidad de las atenciones (observada o conforme) todavía no se registra en el"
                    + " sistema: el cumplimiento se mide como atenciones ejecutadas sobre"
                    + " registradas";
    public static final String NOTA_FECHA_REFERENCIA =
            "Una atención pertenece al periodo por su fecha de inicio o, si aún no ha empezado, por"
                    + " su fecha de registro";

    private static final List<String> NOTAS = List.of(NOTA_CONFORMIDAD, NOTA_FECHA_REFERENCIA);
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final AtencionesTercerizadasRepository atenciones;
    private final Clock reloj;

    public IndicadorCumplimientoTercerizados(
            AtencionesTercerizadasRepository atenciones, Clock reloj) {
        this.atenciones = atenciones;
        this.reloj = reloj;
    }

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public Indicador definicion() {
        return DefinicionesIndicador.CUMPLIMIENTO_TERCERIZADOS;
    }

    @Override
    public ResultadoIndicador calcular(ConsultaPanel consulta) {
        if (soloPersonalPropio(consulta)) {
            return ResultadoIndicador.sinDatos(this, MENSAJE_NO_APLICA_PERSONAL_PROPIO, NOTAS);
        }
        List<ConteoEstado> conteos = atenciones.contarPorEstado(consulta.comoFiltroAtenciones());
        long total = conteos.stream().mapToLong(ConteoEstado::total).sum();
        if (total == 0) {
            return ResultadoIndicador.sinDatos(this, MENSAJE_SIN_DATOS, NOTAS);
        }
        long ejecutadas =
                conteos.stream()
                        .filter(
                                c ->
                                        AtencionesEspacioSectorService.ESTADOS_ATENCION_EJECUTADA
                                                .contains(c.estado()))
                        .mapToLong(ConteoEstado::total)
                        .sum();
        String valor =
                BigDecimal.valueOf(ejecutadas)
                        .multiply(CIEN)
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                        .toPlainString();
        List<ConteoEstado> ordenados = new ArrayList<>(conteos);
        ordenados.sort(
                Comparator.comparing(IndicadorCumplimientoTercerizados::posicion)
                        .thenComparing(ConteoEstado::estado));
        List<ElementoDesglose> desglose =
                ordenados.stream()
                        .map(c -> new ElementoDesglose(c.estado(), String.valueOf(c.total())))
                        .toList();
        return new ResultadoIndicador(CLAVE, definicion(), valor, false, null, desglose, NOTAS);
    }

    @Override
    public DetalleIndicador detalle(
            ConsultaPanel consulta, String page, Paginacion.EnlacePagina enlace) {
        if (soloPersonalPropio(consulta)) {
            return Detalles.sinDatos(
                    this, consulta, reloj, page, MENSAJE_NO_APLICA_PERSONAL_PROPIO, NOTAS);
        }
        FiltroAtenciones filtro = consulta.comoFiltroAtenciones();
        return Detalles.pagina(
                this,
                consulta,
                reloj,
                atenciones.contarAtenciones(filtro),
                page,
                enlace,
                desplazamiento ->
                        atenciones.atenciones(filtro, Paginacion.TAMANO_PAGINA, desplazamiento),
                MENSAJE_SIN_DATOS,
                NOTAS);
    }

    private static boolean soloPersonalPropio(ConsultaPanel consulta) {
        return AtencionesEspacioSectorService.PERSONAL_PROPIO.equals(
                consulta.filtros().modalidad());
    }

    /**
     * Orden del ciclo de MOD-05 ({@link PanelService#ESTADOS}); un estado desconocido, al final.
     */
    private static int posicion(ConteoEstado c) {
        int i = PanelService.ESTADOS.indexOf(c.estado());
        return i < 0 ? Integer.MAX_VALUE : i;
    }
}
