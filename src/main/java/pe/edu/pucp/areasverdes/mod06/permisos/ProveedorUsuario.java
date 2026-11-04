package pe.edu.pucp.areasverdes.mod06.permisos;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/**
 * Obtiene el usuario de la petición: el de la sesión de MOD-01 que deja {@code SeguridadGestion}.
 * Se lee en cada petición, así que una desactivación, un vencimiento o un cambio de permisos valen
 * desde la siguiente. Las pruebas sustituyen este bean para simular un usuario con rol.
 */
@Component
public class ProveedorUsuario {

    public Usuario usuario(HttpServletRequest peticion) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioSesion u) {
            return new Usuario(true, u.id(), u.permisos());
        }
        return Usuario.ANONIMO;
    }
}
