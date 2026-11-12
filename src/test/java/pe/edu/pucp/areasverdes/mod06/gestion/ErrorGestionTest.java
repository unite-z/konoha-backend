package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;

class ErrorGestionTest {

    @Test
    void losParametrosDelPeriodoQueFaltanUsanLosMensajesComunes() {
        assertEquals(Periodo.MENSAJE_FALTA_DESDE, ErrorGestion.mensajeFalta("desde"));
        assertEquals(Periodo.MENSAJE_FALTA_HASTA, ErrorGestion.mensajeFalta("hasta"));
    }

    @Test
    void elMensajeDeTipoDependeDelTipoEsperado() {
        assertEquals(Periodo.MENSAJE_FORMATO_FECHA, ErrorGestion.mensajeTipo(LocalDate.class));
        assertEquals("Usa un número entero", ErrorGestion.mensajeTipo(Long.class));
        assertEquals("Usa un número entero", ErrorGestion.mensajeTipo(long.class));
        assertEquals("Usa true o false", ErrorGestion.mensajeTipo(Boolean.class));
        assertEquals("Usa true o false", ErrorGestion.mensajeTipo(boolean.class));
    }

    @Test
    void validacionGuardaElMensajeEnSuCampo() {
        assertEquals(Map.of("x", List.of("m")), ErrorGestion.validacion("x", "m").errores());
    }

    @Test
    void sinErroresPorCampoElMapaEstaVacioYNoEsNulo() {
        var errores = new ErrorGestion(404, "c", "d").errores();
        assertNotNull(errores);
        assertTrue(errores.isEmpty());
    }
}
