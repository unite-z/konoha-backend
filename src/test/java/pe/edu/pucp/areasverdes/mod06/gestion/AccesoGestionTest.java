package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

class AccesoGestionTest {

    static UsuarioSesion con(String rol, String... permisos) {
        return new UsuarioSesion(1, "u", "U", rol, rol, Set.of(permisos), false, 1);
    }

    @Test
    void conElPermisoDeMod01PuedeConsultar() {
        assertDoesNotThrow(
                () -> AccesoGestion.consultar(con("ingeniero-campo", AccesoGestion.CONSULTAR)));
    }

    @Test
    void sinElPermisoNoPuedeConsultarAunqueSeaJefe() {
        var e =
                assertThrows(
                        ErrorGestion.class,
                        () -> AccesoGestion.consultar(con(UsuarioSesion.JEFE, "OTRO.PERMISO")));
        assertEquals(403, e.estado());
        assertEquals("sin-permiso", e.codigo());
    }

    @Test
    void elCapatazSinPermisoNoPuedeConsultar() {
        var e =
                assertThrows(
                        ErrorGestion.class,
                        () -> AccesoGestion.consultar(con(UsuarioSesion.CAPATAZ)));
        assertEquals(403, e.estado());
    }

    @Test
    void sinUsuarioNoPuedeConsultar() {
        var e = assertThrows(ErrorGestion.class, () -> AccesoGestion.consultar(null));
        assertEquals(403, e.estado());
        assertEquals("sin-permiso", e.codigo());
    }
}
