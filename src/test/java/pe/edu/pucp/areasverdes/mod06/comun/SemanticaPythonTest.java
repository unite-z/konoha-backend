package pe.edu.pucp.areasverdes.mod06.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pruebas adicionales del Java: la semántica de Python que el Django usaba de forma implícita (valores de
 * referencia obtenidos con CPython 3.14).
 */
class SemanticaPythonTest {

    @ParameterizedTest
    @CsvSource({"4.0,4.0", "6.5,6.5", "350.0,350.0", "0.1,0.1", "1e16,1e+16", "1234567890123456.0,1234567890123456.0",
        "0.0001,0.0001", "0.00001,1e-05", "12345678.0,12345678.0", "-2.5,-2.5", "1.5e-07,1.5e-07"})
    void reprDeFloat(double valor, String esperado) {
        assertEquals(esperado, Py.reprFloat(valor));
    }

    @Test
    void redondeoAlParComoPython() {
        assertEquals(2.67, Py.round(2.675, 2)); // 2.675 es 2.67499999... en binario
        assertEquals(0.3, Py.round(3.0 / 10, 4));
        assertEquals(0.5, Py.round(0.5, 1));
        assertEquals(2L, Py.roundEntero(2.5));
        assertEquals(4L, Py.roundEntero(3.5));
    }

    @Test
    void sumaCompensadaDePython312() {
        assertEquals(1.0, Py.sum(List.of(0.1, 0.2, 0.3, 0.4)));        // CPython 3.12+: 1.0 (antes 0.9999999999999999)
        assertEquals(6L, Py.sum(List.of(1L, 2L, 3L)));
        assertEquals(3.5, Py.sum(List.of(1L, 2.5)));
        assertEquals(2L, Py.sum(List.of(true, 1L)));
    }

    @Test
    void igualdadYClavesDePython() {
        assertTrue(Py.eq(1L, 1.0));
        assertTrue(Py.eq(true, 1L));
        assertFalse(Py.eq("1", 1L));
        assertEquals(Py.k(1.0), Py.k(1L));
        assertEquals(Py.k(true), Py.k(1L));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"1|1", "' 2'|2", "+2|2", "1_0|10", "007|7"})
    void intDeTexto(String texto, long esperado) {
        assertEquals(esperado, Py.parseInt(texto));
    }

    @Test
    void intDeTextoInvalido() {
        for (String t : List.of("", "1.5", "1__0", "_1", "abc", "1e3")) assertThrows(Py.ValueError.class, () -> Py.parseInt(t));
    }

    @Test
    void fechasIsoComoCPython() {
        assertEquals(LocalDate.of(2026, 1, 5), Fechas.fromIsoFecha("2026-01-05"));
        assertEquals(LocalDate.of(2026, 1, 5), Fechas.fromIsoFecha("20260105"));
        assertEquals(LocalDate.of(2026, 10, 20), Fechas.fromIsoFecha("2026-W43-2"));
        assertThrows(Py.ValueError.class, () -> Fechas.fromIsoFecha("2026-1-5"));
        assertEquals(LocalDate.of(2026, 10, 20), Fechas.fechaLocal(Fechas.fromIsoFechaHora("2026-10-21T02:00:00+00:00")));
        assertEquals(LocalDate.of(2026, 10, 20), Fechas.fechaLocal(Fechas.fromIsoFechaHora("2026-10-20T08:30:00-05:00")));
        assertEquals(LocalDate.of(2026, 10, 20), Fechas.fechaLocal(Fechas.fromIsoFechaHora("2026-10-20")));
        assertEquals(LocalDate.of(2026, 1, 1), Fechas.strptimeAaaaMmDd("2026-1-1"));
        assertNull(Fechas.strptimeAaaaMmDd("2026-02-30"));
        assertNull(Fechas.strptimeAaaaMmDd("20/10/2026"));
    }

    @Test
    void jsonComoCPython() {
        JsonPy.JsonDecodeError e = assertThrows(JsonPy.JsonDecodeError.class, () -> JsonPy.loads("\n\n  {\"a\": tru}"));
        assertEquals("Expecting value: line 3 column 9 (char 10)", e.getMessage());
        assertEquals("[\n  {\n    \"a\": 1.0,\n    \"b\": []\n  }\n]", JsonPy.dumpsArchivo(JsonPy.loads("[{\"a\": 1.0, \"b\": []}]")));
        assertEquals("{\"x\":\"ñ\\n\"}", JsonPy.dumpsRespuesta(JsonPy.loads("{\"x\": \"\\u00f1\\n\"}")));
    }
}
