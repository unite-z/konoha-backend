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
 * <p>La modalidad de toda atención tercerizada es {@code Tercerizado}. Las actividades del personal
 * propio (MOD-04) todavía no están en el sistema, así que con {@code PersonalPropio} no hay nada
 * que contar. El sector (HU v2: agrupador principal) tampoco está: se agrupa por espacio.
 */
@Component
public class IndicadorAtencionesPorEspacio implements IndicadorPanel {

    public static final String CLAVE = "atenciones-por-espacio";
    public static final String SIN_ESPACIO = AgrupadorMaterial.SIN_ESPACIO;
    public static final String NOTA_SIN_SECTOR =
            "El sector de los espacios todavía no está disponible en el sistema: las atenciones se"
                    + " agrupan por espacio";
    public static final String NOTA_SOLO_TERCERIZADAS =
            "Por ahora solo se cuentan las atenciones tercerizadas: las actividades del personal"
                    + " propio todavía no están disponibles en el sistema";

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
        List<ConteoEspacio> conteos = atenciones.contarPorEspacio(consulta.comoFiltroAtenciones());
        long total = conteos.stream().mapToLong(ConteoEspacio::total).sum();
        if (total == 0) {
            return ResultadoIndicador.sinDatos(
                    this, AtencionesEspacioSectorService.MENSAJE_SIN_DATOS, notas);
        }
        Collator textos = Collator.getInstance(Locale.forLanguageTag("es-ES"));
        List<ConteoEspacio> ordenados = new ArrayList<>(conteos);
        ordenados.sort(
                Comparator.comparing((ConteoEspacio c) -> c.areaVerdeId() == null)
                        .thenComparing(ConteoEspacio::total, Comparator.reverseOrder())
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

    // TODO (cuando se fusione MOD-04 y FuenteOperacionCampo tenga consultas): contar aquí las
    // actividades del personal
    // propio con las mismas reglas de RF-REP-006.
    private String motivoPersonalPropio() {
        Disponibilidad d = operacion.disponibilidad();
        return d.disponible() ? AtencionesEspacioSectorService.MENSAJE_SIN_DATOS : d.motivo();
    }

    private List<String> notas(ConsultaPanel consulta) {
        boolean soloTercerizado =
                AtencionesEspacioSectorService.TERCERIZADO.equals(consulta.filtros().modalidad());
        if (!soloTercerizado && !operacion.disponibilidad().disponible()) {
            return List.of(NOTA_SIN_SECTOR, NOTA_SOLO_TERCERIZADAS);
        }
        return List.of(NOTA_SIN_SECTOR);
    }

    private static String nombre(ConteoEspacio c) {
        if (c.areaVerdeId() == null) {
            return SIN_ESPACIO;
        }
        return c.espacio() == null ? "" : c.espacio();
    }
}
