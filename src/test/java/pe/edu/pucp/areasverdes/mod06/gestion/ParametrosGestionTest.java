package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;
import pe.edu.pucp.areasverdes.mod06.gestion.api.ParametrosGestion;

class ParametrosGestionTest {

    static final LocalDate DIA = LocalDate.of(2026, 10, 15);
    static final List<String> MODALIDADES = List.of("PersonalPropio", "Tercerizado");

    @Test
    void unPeriodoDeUnSoloDiaVale() {
        assertEquals(new RangoFechas(DIA, DIA), ParametrosGestion.periodo(DIA, DIA));
    }

    @Test
    void unPeriodoInvertidoDaPeriodoInvalidoEnElCampoHasta() {
        var e =
                assertThrows(
                        ErrorGestion.class, () -> ParametrosGestion.periodo(DIA, DIA.minusDays(1)));
        assertEquals(400, e.estado());
        assertEquals("periodo-invalido", e.codigo());
        assertEquals(Map.of("hasta", List.of(Periodo.MENSAJE_ORDEN_FECHAS)), e.errores());
    }

    @Test
    void elMesSeLeeEnFormatoAnioMes() {
        assertEquals(YearMonth.of(2026, 10), ParametrosGestion.mes("2026-10"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-1", "26-10", "octubre", "", "2026-10-01"})
    void unMesMalEscritoDaValidacionEnElCampoMes(String texto) {
        var e = assertThrows(ErrorGestion.class, () -> ParametrosGestion.mes(texto));
        assertEquals(400, e.estado());
        assertEquals("validacion", e.codigo());
        assertEquals(List.of("Usa el formato AAAA-MM"), e.errores().get("mes"));
    }

    @Test
    void unoDeDejaPasarNuloYLosValoresPermitidos() {
        assertNull(ParametrosGestion.unoDe("modalidad", null, MODALIDADES));
        assertEquals(
                "Tercerizado", ParametrosGestion.unoDe("modalidad", "Tercerizado", MODALIDADES));
    }

    @Test
    void unoDeRechazaOtrosValoresListandoLosPermitidos() {
        var e =
                assertThrows(
                        ErrorGestion.class,
                        () -> ParametrosGestion.unoDe("modalidad", "Otro", MODALIDADES));
        assertEquals(400, e.estado());
        assertEquals("validacion", e.codigo());
        assertEquals(
                List.of("Usa uno de estos valores: PersonalPropio, Tercerizado"),
                e.errores().get("modalidad"));
    }
}
