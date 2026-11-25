package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.text.Collator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.AtencionesTercerizadasRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ConteoEspacio;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FiltroAtenciones;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampo;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * Panel, indicador 3 (CU-MOD06-001, paso 4): atenciones por espacio, con las reglas de RF-REP-006
 * (pares atención-espacio de las atenciones tercerizadas realizadas).
 *
 * <p>La modalidad de toda atención tercerizada es {@code Tercerizado}. Este indicador todavía no
 * cuenta las actividades del personal propio (MOD-04): con {@code PersonalPropio} lo dice en vez de
 * mostrar un cero, y remite al reporte «Atenciones por espacio y sector», que sí las cuenta y
 * agrupa por sector. Las atenciones sin espacio no cuentan: son un error de datos (AV-408).
 */
@Component
public class IndicadorAtencionesPorEspacio implements IndicadorPanel {

    public static final String CLAVE = "atenciones-por-espacio";
    public static final String NOTA_SIN_SECTOR =
            "El panel agrupa las atenciones por espacio; el reporte «Atenciones por espacio y"
                    + " sector» las agrupa por sector";
    public static final String NOTA_SOLO_TERCERIZADAS =
            "Este indicador solo cuenta las atenciones tercerizadas; las actividades del personal"
                    + " propio se cuentan en el reporte «Atenciones por espacio y sector»";
    public static final String MENSAJE_PERSONAL_PROPIO =
            "Este indicador todavía no cuenta las actividades del personal propio: consúltalas en"
                    + " el reporte «Atenciones por espacio y sector»";

    private final AtencionesTercerizadasRepository atenciones;
    private final FuenteOperacionCampo operacion;
    private final Clock reloj;

    public IndicadorAtencionesPorEspacio(
            AtencionesTercerizadasRepository atenciones,
            FuenteOperacionCampo operacion,
            Clock reloj) {
        this.atenciones = atenciones;
        this.operacion = operacion;
        this.reloj = reloj;
    }

    @Override
    public String clave() {
        return CLAVE;
    }

    @Override
    public Indicador definicion() {
        return DefinicionesIndicador.ATENCIONES_POR_ESPACIO;
    }

    @Override
    public ResultadoIndicador calcular(ConsultaPanel consulta) {
        List<String> notas = notas(consulta);
        if (soloPersonalPropio(consulta)) {
            return ResultadoIndicador.sinDatos(this, motivoPersonalPropio(), notas);
        }
        // Los pares ya excluyen las atenciones sin espacio (AV-408).
        List<ConteoEspacio> conteos = atenciones.contarPorEspacio(consulta.comoFiltroAtenciones());
        long total = conteos.stream().mapToLong(ConteoEspacio::total).sum();
        if (total == 0) {
            return ResultadoIndicador.sinDatos(
                    this, AtencionesEspacioSectorService.MENSAJE_SIN_DATOS, notas);
        }
        Collator textos = Collator.getInstance(Locale.forLanguageTag("es-ES"));
        List<ConteoEspacio> ordenados = new ArrayList<>(conteos);
        ordenados.sort(
                Comparator.comparing(ConteoEspacio::total, Comparator.<Long>reverseOrder())
                        .thenComparing(c -> nombre(c), textos));
        List<ElementoDesglose> desglose =
                ordenados.stream()
                        .map(c -> new ElementoDesglose(nombre(c), String.valueOf(c.total())))
                        .toList();
        return new ResultadoIndicador(
                CLAVE, definicion(), String.valueOf(total), false, null, desglose, notas);
    }

    @Override
    public DetalleIndicador detalle(
            ConsultaPanel consulta, String page, Paginacion.EnlacePagina enlace) {
        List<String> notas = notas(consulta);
        if (soloPersonalPropio(consulta)) {
            return Detalles.sinDatos(this, consulta, reloj, page, motivoPersonalPropio(), notas);
        }
        FiltroAtenciones filtro = consulta.comoFiltroAtenciones();
        return Detalles.pagina(
                this,
                consulta,
                reloj,
                atenciones.contarPares(filtro),
                page,
                enlace,
                desplazamiento ->
                        atenciones.pares(filtro, Paginacion.TAMANO_PAGINA, desplazamiento),
                AtencionesEspacioSectorService.MENSAJE_SIN_DATOS,
                notas);
    }

    private static boolean soloPersonalPropio(ConsultaPanel consulta) {
        return AtencionesEspacioSectorService.PERSONAL_PROPIO.equals(
                consulta.filtros().modalidad());
    }

    // TODO: contar aquí las actividades del personal propio (FuenteOperacionCampo) con las mismas
    // reglas de RF-REP-006, como ya hace el reporte «Atenciones por espacio y sector».
    private String motivoPersonalPropio() {
        Disponibilidad d = operacion.disponibilidad();
        return d.disponible() ? MENSAJE_PERSONAL_PROPIO : d.motivo();
    }

    private List<String> notas(ConsultaPanel consulta) {
        boolean soloTercerizado =
                AtencionesEspacioSectorService.TERCERIZADO.equals(consulta.filtros().modalidad());
        return soloTercerizado
                ? List.of(NOTA_SIN_SECTOR)
                : List.of(NOTA_SIN_SECTOR, NOTA_SOLO_TERCERIZADAS);
    }

    private static String nombre(ConteoEspacio c) {
        return c.espacio() == null ? "" : c.espacio();
    }
}
