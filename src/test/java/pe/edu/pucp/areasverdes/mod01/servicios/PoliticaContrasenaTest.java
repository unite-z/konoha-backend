package pe.edu.pucp.areasverdes.mod01.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** CU-MOD01-002 y HU-MOD01-003, «Contraseña débil» (supuesto ACC-S6). */
class PoliticaContrasenaTest {

    @Test
    void seisCaracteresSinNumerosIncumplenDosReglas() {
        assertEquals(
                List.of("Debe tener al menos 8 caracteres", "Debe incluir al menos un número"),
                PoliticaContrasena.incumplidas("abcdef"));
    }

    @Test
    void ochoCaracteresConLetrasYNumerosCumplen() {
        assertTrue(PoliticaContrasena.incumplidas("abcdefg1").isEmpty());
        assertEquals(
                List.of("Debe incluir al menos una letra"),
                PoliticaContrasena.incumplidas("12345678"));
    }

    @Test
    void lasTemporalesCumplenLaPoliticaYNoSeRepiten() {
        for (int i = 0; i < 200; i++) {
            String temporal = PoliticaContrasena.temporal();
            assertEquals(12, temporal.length());
            assertTrue(PoliticaContrasena.incumplidas(temporal).isEmpty(), temporal);
        }
        assertNotEquals(PoliticaContrasena.temporal(), PoliticaContrasena.temporal());
    }
}
