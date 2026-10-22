package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod05.Usuario;

class AccesoTest {

    static Usuario con(boolean activo, String... roles) {
        return new Usuario(1, "u", "U", "", activo, List.of(roles));
    }

    @Test
    void losTresRolesPuedenConsultar() {
        for (String rol : Usuario.ROLES) {
            assertDoesNotThrow(() -> Acceso.consultar(con(true, rol)));
        }
    }

    @Test
    void sinRolOInactivoNoPuedeConsultar() {
        var sinRol = assertThrows(ErrorPlanificacion.class, () -> Acceso.consultar(con(true)));
        assertEquals(403, sinRol.estado());
        assertEquals("sin-permiso", sinRol.codigo());
        assertThrows(ErrorPlanificacion.class, () -> Acceso.consultar(con(false, Usuario.JEFE)));
    }

    @Test
    void soloElJefeGestiona() {
        assertDoesNotThrow(() -> Acceso.gestionar(con(true, Usuario.JEFE)));
        assertThrows(
                ErrorPlanificacion.class, () -> Acceso.gestionar(con(true, "Ingeniero de campo")));
        assertThrows(
                ErrorPlanificacion.class,
                () -> Acceso.gestionar(con(true, "Practicante autorizado")));
    }
}
