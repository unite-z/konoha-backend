package pe.edu.pucp.areasverdes.mod06.gestion;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Periodo de consulta {@code [desde, hasta]}, ambos incluidos, en fechas locales de America/Lima
 * (CU-MOD06-001: «el actor selecciona un rango de fechas»). Se serializa como {@code {desde,
 * hasta}}, igual que {@code periodo} en los demás reportes. La validación con mensajes al usuario
 * está en {@code ParametrosGestion}; aquí, un rango invertido es un error de programación.
 */
public record RangoFechas(LocalDate desde, LocalDate hasta) {

    public RangoFechas {
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("hasta < desde");
        }
    }

    /** {@code true} si la fecha está en el periodo (extremos incluidos); una fecha nula, no. */
    public boolean contiene(LocalDate fecha) {
        return fecha != null && !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }

    /** Del primer al último día del mes. */
    public static RangoFechas delMes(YearMonth mes) {
        return new RangoFechas(mes.atDay(1), mes.atEndOfMonth());
    }
}
