package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod05.Usuario;

class AccesoGestionTest {

    static Usuario con(boolean activo, String... roles) {
        return new Usuario(1, "u", "U", "", activo, List.of(roles));
    }

    @Test
    void losTresRolesPuedenConsultar() {
        for (String rol : Usuario.ROLES) {
            assertDoesNotThrow(() -> AccesoGestion.consultar(con(true, rol)));
        }
    }

    @Test
    void sinRolNoPuedeConsultar() {
        var e = assertThrows(ErrorGestion.class, () -> AccesoGestion.consultar(con(true)));
        assertEquals(403, e.estado());
        assertEquals("sin-permiso", e.codigo());
    }

    @Test
    void inactivoNoPuedeConsultarAunqueSeaJefe() {
        var e =
                assertThrows(
                        ErrorGestion.class,
                        () -> AccesoGestion.consultar(con(false, Usuario.JEFE)));
        assertEquals(403, e.estado());
        assertEquals("sin-permiso", e.codigo());
    }

    @Test
    void sinUsuarioNoPuedeConsultar() {
        var e = assertThrows(ErrorGestion.class, () -> AccesoGestion.consultar(null));
        assertEquals(403, e.estado());
        assertEquals("sin-permiso", e.codigo());
    }
}
