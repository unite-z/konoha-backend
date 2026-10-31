package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.MovimientoMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.TipoMovimiento;

/**
 * Agrupación de los movimientos de material (RF-REP-005 · CU-MOD05-029), en funciones puras sin
 * Spring. La reutiliza la tarjeta «Consumo de materiales e insumos» del panel (RF-REP-001).
 *
 * <ul>
 *   <li>Las cantidades se suman con {@link BigDecimal}; la unidad forma parte de la clave, así que
 *       nunca se suman unidades distintas.
 *   <li>Sin espacio (flujo 2a): {@code areaVerdeId} nulo y «Sin espacio», siempre al final.
 *   <li>Orden estable con las reglas del español (las tildes ordenan bien): espacio (u origen y
 *       destino), tipo de material y unidad.
 * </ul>
 */
public final class AgrupadorMaterial {

    /** Texto del CU-MOD05-029, flujo 2a. */
    public static final String SIN_ESPACIO = "Sin espacio";

    private AgrupadorMaterial() {}

    /**
     * Total por espacio, tipo de material y unidad de un tipo de movimiento (consumido o
     * propagado).
     */
    public static List<TotalMaterial> porEspacio(
            List<MovimientoMaterial> movimientos, TipoMovimiento tipo) {
        Map<List<?>, Acumulado> grupos =
                agrupar(
                        movimientos,
                        tipo,
                        m -> Arrays.asList(m.areaVerdeId(), m.tipoMaterial(), m.unidad()));
        Collator textos = textos();
        return grupos.values().stream()
                .map(
                        a ->
                                new TotalMaterial(
                                        a.primero.areaVerdeId(),
                                        nombre(a.primero.areaVerdeId(), a.primero.espacio()),
                                        a.primero.tipoMaterial(),
                                        a.primero.categoria(),
                                        a.primero.unidad(),
                                        texto(a.suma),
                                        a.movimientos))
                .sorted(
                        Comparator.comparing((TotalMaterial t) -> t.areaVerdeId() == null)
                                .thenComparing(TotalMaterial::espacio, textos)
                                .thenComparing(t -> vacio(t.tipoMaterial()), textos)
                                .thenComparing(t -> vacio(t.unidad()), textos))
                .toList();
    }

    /** Total por origen, destino, tipo de material y unidad de los traslados. */
    public static List<TotalTraslado> traslados(List<MovimientoMaterial> movimientos) {
        Map<List<?>, Acumulado> grupos =
                agrupar(
                        movimientos,
                        TipoMovimiento.TRASLADO,
                        m ->
                                Arrays.asList(
                                        m.origenAreaId(),
                                        m.destinoAreaId(),
                                        m.tipoMaterial(),
                                        m.unidad()));
        Collator textos = textos();
        return grupos.values().stream()
                .map(
                        a ->
                                new TotalTraslado(
                                        a.primero.origenAreaId(),
                                        nombre(a.primero.origenAreaId(), a.primero.origen()),
                                        a.primero.destinoAreaId(),
                                        nombre(a.primero.destinoAreaId(), a.primero.destino()),
                                        a.primero.tipoMaterial(),
                                        a.primero.categoria(),
                                        a.primero.unidad(),
                                        texto(a.suma),
                                        a.movimientos))
                .sorted(
                        Comparator.comparing((TotalTraslado t) -> t.origenAreaId() == null)
                                .thenComparing(TotalTraslado::origen, textos)
                                .thenComparing(t -> t.destinoAreaId() == null)
                                .thenComparing(TotalTraslado::destino, textos)
                                .thenComparing(t -> vacio(t.tipoMaterial()), textos)
                                .thenComparing(t -> vacio(t.unidad()), textos))
                .toList();
    }

    /**
     * Total por tipo de material y unidad (sin espacio) de un tipo de movimiento: para la
     * comparación y el panel.
     */
    public static List<TotalMes> porMaterial(
            List<MovimientoMaterial> movimientos, TipoMovimiento tipo) {
        Map<List<?>, Acumulado> grupos =
                agrupar(movimientos, tipo, m -> Arrays.asList(m.tipoMaterial(), m.unidad()));
        Collator textos = textos();
        return grupos.values().stream()
                .map(a -> new TotalMes(a.primero.tipoMaterial(), a.primero.unidad(), texto(a.suma)))
                .sorted(
                        Comparator.comparing((TotalMes t) -> vacio(t.tipoMaterial()), textos)
                                .thenComparing(t -> vacio(t.unidad()), textos))
                .toList();
    }

    // ---------------------------------------------------------------------------------------------

    /** Suma y cuenta de un grupo; {@code primero} da los textos del grupo. */
    private static final class Acumulado {
        private final MovimientoMaterial primero;
        private BigDecimal suma = BigDecimal.ZERO;
        private int movimientos;

        private Acumulado(MovimientoMaterial primero) {
            this.primero = primero;
        }
    }

    private static Map<List<?>, Acumulado> agrupar(
            List<MovimientoMaterial> movimientos,
            TipoMovimiento tipo,
            Function<MovimientoMaterial, List<?>> claveDe) {
        Map<List<?>, Acumulado> grupos = new LinkedHashMap<>();
        for (MovimientoMaterial m : movimientos) {
            if (m.tipoMovimiento() != tipo) {
                continue;
            }
            Acumulado a = grupos.computeIfAbsent(claveDe.apply(m), k -> new Acumulado(m));
            a.suma = a.suma.add(m.cantidad());
            a.movimientos++;
        }
        return grupos;
    }

    private static String nombre(Long id, String nombre) {
        return id == null ? SIN_ESPACIO : vacio(nombre);
    }

    private static String vacio(String texto) {
        return texto == null ? "" : texto;
    }

    private static String texto(BigDecimal cantidad) {
        return cantidad.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Un {@link Collator} por llamada: no es seguro compartirlo entre hilos. */
    private static Collator textos() {
        return Collator.getInstance(Locale.forLanguageTag("es-ES"));
    }
}
