package pe.edu.pucp.areasverdes.mod02.servicios;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Reglas de la campaña de poda mayor que no dependen de la base (RF-POD-001, RN-018). */
public final class ReglasCampanaPoda {

    private ReglasCampanaPoda() {}

    public enum ClaseCampana {
        Anual,
        Extraordinaria
    }

    public enum EstadoCampana {
        Borrador,
        Planificada,
        EnEjecucion,
        Finalizada;

        public boolean editable() {
            return this == Borrador || this == Planificada;
        }
    }

    /**
     * Capacidad estimada de la campaña. Solo avisa: la lista puede superarla.
     *
     * @param dias días del periodo, contando el inicio y el fin
     * @param estimada ejemplares que caben con el objetivo diario
     * @param requeridoPorDia ejemplares al día necesarios para completar la lista
     * @param excedida la lista supera la capacidad estimada
     */
    public record Capacidad(
            long dias,
            long estimada,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal requeridoPorDia,
            boolean excedida) {}

    public static long dias(LocalDate inicio, LocalDate fin) {
        return ChronoUnit.DAYS.between(inicio, fin) + 1;
    }

    public static Capacidad capacidad(
            LocalDate inicio, LocalDate fin, int objetivoDiario, long totalEjemplares) {
        long dias = dias(inicio, fin);
        long estimada = dias * objetivoDiario;
        BigDecimal requerido =
                BigDecimal.valueOf(totalEjemplares)
                        .divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP);
        return new Capacidad(dias, estimada, requerido, totalEjemplares > estimada);
    }
}
