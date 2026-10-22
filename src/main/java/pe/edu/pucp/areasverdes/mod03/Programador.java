package pe.edu.pucp.areasverdes.mod03;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de programación sin base de datos (HU-MOD03-004 y HU-MOD03-010): estación del año, fecha
 * propuesta, restricciones que afectan a un periodo y ventana libre más próxima.
 */
final class Programador {

    /** Restricción del calendario vista desde la programación. */
    record Bloqueo(
            long id,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String efecto,
            boolean soloRuidosos) {

        static Bloqueo de(RestriccionesController.Restriccion r) {
            return new Bloqueo(
                    r.id(), r.fechaInicio(), r.fechaFin(), r.efecto(), r.aplicaServiciosRuidosos());
        }
    }

    private static final MonthDay OTONO = MonthDay.of(3, 21);
    private static final MonthDay INVIERNO = MonthDay.of(6, 21);
    private static final MonthDay PRIMAVERA = MonthDay.of(9, 23);
    private static final MonthDay VERANO = MonthDay.of(12, 21);

    private Programador() {}

    /** Estación del hemisferio sur (Lima) a la que pertenece el día. */
    static String estacion(LocalDate dia) {
        MonthDay md = MonthDay.from(dia);
        if (md.isBefore(OTONO) || !md.isBefore(VERANO)) {
            return "Verano";
        }
        if (md.isBefore(INVIERNO)) {
            return "Otoño";
        }
        return md.isBefore(PRIMAVERA) ? "Invierno" : "Primavera";
    }

    /** Fin del último ciclo más la frecuencia; nunca antes de hoy. Sin ciclo previo, hoy. */
    static LocalDate inicioPropuesto(LocalDate ultimoFin, int frecuenciaDias, LocalDate hoy) {
        if (ultimoFin == null) {
            return hoy;
        }
        LocalDate propuesto = ultimoFin.plusDays(frecuenciaDias);
        return propuesto.isBefore(hoy) ? hoy : propuesto;
    }

    /** Días que ocupa el periodo, contando ambos extremos. */
    static long dias(LocalDate inicio, LocalDate fin) {
        return ChronoUnit.DAYS.between(inicio, fin) + 1;
    }

    /**
     * Restricciones que afectan al servicio en el periodo. Las favorables no restringen, y las que
     * solo afectan a servicios ruidosos no cuentan para los que no lo son (RN-003).
     */
    static List<Bloqueo> conflictos(
            List<Bloqueo> calendario, LocalDate inicio, LocalDate fin, boolean ruidoso) {
        return calendario.stream()
                .filter(b -> !"Favorable".equals(b.efecto()))
                .filter(b -> ruidoso || !b.soloRuidosos())
                .filter(b -> !b.fechaInicio().isAfter(fin) && !b.fechaFin().isBefore(inicio))
                .toList();
    }

    /**
     * Primer inicio a partir de {@code desde} con un periodo de {@code dias} días sin conflictos.
     */
    static Optional<LocalDate> ventanaLibre(
            List<Bloqueo> calendario, LocalDate desde, long dias, boolean ruidoso, int horizonte) {
        for (int k = 0; k <= horizonte; k++) {
            LocalDate inicio = desde.plusDays(k);
            if (conflictos(calendario, inicio, inicio.plusDays(dias - 1), ruidoso).isEmpty()) {
                return Optional.of(inicio);
            }
        }
        return Optional.empty();
    }

    /** RN-002: la duración programada no supera la máxima del servicio más su limpieza. */
    static void exigirDuracion(long dias, int maxima, int limpieza) {
        if (dias > maxima + limpieza) {
            throw new ErrorPlanificacion(
                    400,
                    "duracion-excedida",
                    "La programación dura %d días y el máximo es %d de servicio más %d de limpieza"
                            .formatted(dias, maxima, limpieza));
        }
    }
}
