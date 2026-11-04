package pe.edu.pucp.areasverdes.mod06.permisos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/** El usuario de MOD-06 es el de la sesión de MOD-01: su id se registra y sus permisos deciden. */
class ProveedorUsuarioTest {

    private final ProveedorUsuario proveedor = new ProveedorUsuario();
    private final Permisos permisos = new Permisos();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void conSesionDevuelveElIdYLosPermisosDeMod01() {
        var principal =
                new UsuarioSesion(
                        7L,
                        "ingeniero",
                        "Ingeniera",
                        "ingeniero-campo",
                        "Ingeniero de campo",
                        Set.of(Permisos.PROPONER_IA),
                        false,
                        1L);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                principal, null, List.of()));

        Usuario usuario = proveedor.usuario(new MockHttpServletRequest());

        assertEquals(7L, Permisos.idDeUsuario(usuario));
        assertTrue(permisos.tienePermiso(usuario, Permisos.PROPONER_IA));
        assertFalse(permisos.tienePermiso(usuario, Permisos.VALIDAR_IA));
    }

    @Test
    void sinSesionEsAnonimoYNoTienePermisos() {
        Usuario usuario = proveedor.usuario(new MockHttpServletRequest());

        assertEquals(Usuario.ANONIMO, usuario);
        assertFalse(permisos.tienePermiso(usuario, Permisos.CONSULTAR_REPORTES));
    }
}
