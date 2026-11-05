package pe.edu.pucp.areasverdes.mod02.servicios;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rendimiento de una campaña en árboles por día (AV-310, RF-POD-003).
 *
 * <p>Se calcula solo con podas verificadas, es decir, con fotografía y sin anular. El numerador son
 * los ejemplares distintos, y cada uno cuenta una vez, en su primera fecha verificada. El
 * denominador son los días distintos con al menos una poda verificada, no los días del periodo. Sin
 * ejecución no hay promedio.
 */
public final class RendimientoCampana {

    /** Días trabajados de cada tramo de la evolución. */
    static final int DIAS_POR_TRAMO = 5;

    private RendimientoCampana() {}

    /** Una poda verificada de la campaña. */
    public record Registro(long ejemplarId, LocalDate fecha) {}

    /**
     * Tramo de días trabajados consecutivos.
     *
     * @param diaDesde primer día trabajado del tramo, desde 1
     * @param diaHasta último día trabajado del tramo
     * @param atendidos ejemplares contados por primera vez en el tramo
     */
    public record Tramo(
            int diaDesde,
            int diaHasta,
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            long atendidos,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal promedio) {}

    /**
     * Resultado del cálculo.
     *
     * @param verificados ejemplares distintos con poda verificada
     * @param diasConEjecucion días distintos con poda verificada
     * @param promedio ejemplares por día; nulo si no hay ejecución
     * @param umbralAlerta valor a partir del cual se destaca para revisión
     * @param destacado el promedio alcanza el umbral
     */
    public record Resultado(
            long verificados,
            long diasConEjecucion,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal promedio,
            int umbralAlerta,
            boolean destacado,
            List<Tramo> tramos) {}

    public static Resultado calcular(List<Registro> registros, int umbralAlerta) {
        Map<Long, LocalDate> primeraFecha = new TreeMap<>();
        Map<LocalDate, Long> atendidosPorDia = new TreeMap<>();
        for (Registro registro : registros) {
            primeraFecha.merge(
                    registro.ejemplarId(), registro.fecha(), (a, b) -> a.isBefore(b) ? a : b);
            atendidosPorDia.put(registro.fecha(), 0L);
        }
        primeraFecha.values().forEach(fecha -> atendidosPorDia.merge(fecha, 1L, Long::sum));

        long verificados = primeraFecha.size();
        long dias = atendidosPorDia.size();
        BigDecimal promedio = dias == 0 ? null : promedio(verificados, dias);
        boolean destacado =
                promedio != null && promedio.compareTo(BigDecimal.valueOf(umbralAlerta)) >= 0;
        return new Resultado(
                verificados, dias, promedio, umbralAlerta, destacado, tramos(atendidosPorDia));
    }

    private static List<Tramo> tramos(Map<LocalDate, Long> atendidosPorDia) {
        List<Map.Entry<LocalDate, Long>> dias = new ArrayList<>(atendidosPorDia.entrySet());
        List<Tramo> tramos = new ArrayList<>();
        for (int inicio = 0; inicio < dias.size(); inicio += DIAS_POR_TRAMO) {
            int fin = Math.min(inicio + DIAS_POR_TRAMO, dias.size());
            long atendidos =
                    dias.subList(inicio, fin).stream().mapToLong(Map.Entry::getValue).sum();
            tramos.add(
                    new Tramo(
                            inicio + 1,
                            fin,
                            dias.get(inicio).getKey(),
                            dias.get(fin - 1).getKey(),
                            atendidos,
                            promedio(atendidos, fin - inicio)));
        }
        return tramos;
    }

    private static BigDecimal promedio(long atendidos, long dias) {
        return BigDecimal.valueOf(atendidos)
                .divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP);
    }
}
