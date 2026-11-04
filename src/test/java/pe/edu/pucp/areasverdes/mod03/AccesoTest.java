package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AccesoTest {

    static UsuarioPlanificacion con(String... permisos) {
        return new UsuarioPlanificacion(1, "U", Set.of(permisos));
    }

    @Test
    void consultarExigeSuPermiso() {
        assertDoesNotThrow(() -> Acceso.consultar(con(Acceso.CONSULTAR)));
        var sinPermiso = assertThrows(ErrorPlanificacion.class, () -> Acceso.consultar(con()));
        assertEquals(403, sinPermiso.estado());
        assertEquals("sin-permiso", sinPermiso.codigo());
        assertThrows(ErrorPlanificacion.class, () -> Acceso.consultar(null));
    }

    @Test
    void consultarNoBastaParaGestionarNiParaLaLimpieza() {
        var lector = con(Acceso.CONSULTAR);
        assertThrows(ErrorPlanificacion.class, () -> Acceso.gestionar(lector));
        assertThrows(ErrorPlanificacion.class, () -> Acceso.registrarLimpieza(lector));
        assertDoesNotThrow(() -> Acceso.gestionar(con(Acceso.GESTIONAR)));
        assertDoesNotThrow(() -> Acceso.registrarLimpieza(con(Acceso.LIMPIEZA)));
    }
}
