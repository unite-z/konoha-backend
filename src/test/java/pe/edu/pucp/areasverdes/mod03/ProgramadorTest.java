package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static pe.edu.pucp.areasverdes.mod03.Programador.conflictos;
import static pe.edu.pucp.areasverdes.mod03.Programador.estacion;
import static pe.edu.pucp.areasverdes.mod03.Programador.inicioPropuesto;
import static pe.edu.pucp.areasverdes.mod03.Programador.ventanaLibre;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod03.Programador.Bloqueo;

class ProgramadorTest {

    static LocalDate d(String dia) {
        return LocalDate.parse(dia);
    }

    static Bloqueo bloqueo(String dia, String efecto, boolean soloRuidosos) {
        return new Bloqueo(1, d(dia), d(dia), efecto, soloRuidosos);
    }

    @Test
    void estacionesDelHemisferioSur() {
        assertEquals("Verano", estacion(d("2026-01-15")));
        assertEquals("Verano", estacion(d("2026-12-21")));
        assertEquals("Otoño", estacion(d("2026-03-21")));
        assertEquals("Invierno", estacion(d("2026-09-22")));
        assertEquals("Primavera", estacion(d("2026-09-23")));
    }

    @Test
    void proponeElFinDelUltimoCicloMasLaFrecuenciaSinIrAlPasado() {
        assertEquals(d("2026-02-10"), inicioPropuesto(d("2026-01-11"), 30, d("2026-01-20")));
        assertEquals(d("2026-03-01"), inicioPropuesto(d("2026-01-01"), 30, d("2026-03-01")));
        assertEquals(d("2026-03-01"), inicioPropuesto(null, 30, d("2026-03-01")));
    }

    @Test
    void lasFavorablesYLasDeRuidososNoBloqueanAUnServicioSilencioso() {
        var calendario =
                List.of(
                        bloqueo("2026-02-11", "Favorable", true),
                        bloqueo("2026-02-12", "Advertencia", true),
                        bloqueo("2026-02-13", "Prohibicion", false));
        assertEquals(2, conflictos(calendario, d("2026-02-10"), d("2026-02-13"), true).size());
        assertEquals(1, conflictos(calendario, d("2026-02-10"), d("2026-02-13"), false).size());
    }

    @Test
    void laVentanaLibreEsLaPrimeraSinRestricciones() {
        var eventoMasivo = List.of(bloqueo("2026-02-11", "Prohibicion", true));
        assertEquals(
                Optional.of(d("2026-02-12")),
                ventanaLibre(eventoMasivo, d("2026-02-10"), 2, true, 60));
        assertEquals(
                Optional.of(d("2026-02-10")),
                ventanaLibre(eventoMasivo, d("2026-02-10"), 2, false, 60));
        assertEquals(Optional.empty(), ventanaLibre(eventoMasivo, d("2026-02-10"), 2, true, 0));
    }

    @Test
    void cincoDiasDeCorteSuperanTresMasUnoDeLimpieza() {
        assertDoesNotThrow(() -> Programador.exigirDuracion(4, 3, 1));
        var error =
                assertThrows(ErrorPlanificacion.class, () -> Programador.exigirDuracion(5, 3, 1));
        assertEquals("duracion-excedida", error.codigo());
    }
}
