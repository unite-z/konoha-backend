package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class RangoFechasTest {

    static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    @Test
    void contieneIncluyeLosExtremosYNoLaFechaNula() {
        var rango = new RangoFechas(DESDE, HASTA);
        assertTrue(rango.contiene(DESDE));
        assertTrue(rango.contiene(HASTA));
        assertFalse(rango.contiene(DESDE.minusDays(1)));
        assertFalse(rango.contiene(HASTA.plusDays(1)));
        assertFalse(rango.contiene(null));
    }

    @Test
    void delMesVaDelPrimerAlUltimoDia() {
        assertEquals(
                new RangoFechas(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
                RangoFechas.delMes(YearMonth.of(2026, 2)));
    }

    @Test
    void unRangoInvertidoEsUnErrorDeProgramacion() {
        assertThrows(IllegalArgumentException.class, () -> new RangoFechas(HASTA, DESDE));
    }
}
