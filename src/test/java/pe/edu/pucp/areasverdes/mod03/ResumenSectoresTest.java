package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ResumenSectoresTest {

    @Test
    void porcentajeConUnDecimal() {
        assertEquals(new BigDecimal("33.3"), ResumenSectores.porcentaje(1, 3));
        assertEquals(new BigDecimal("100.0"), ResumenSectores.porcentaje(2, 2));
    }

    @Test
    void sinProgramadoEsCero() {
        assertEquals(new BigDecimal("0.0"), ResumenSectores.porcentaje(0, 0));
    }
}
