package pe.edu.pucp.areasverdes.mod01.servicios;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Usuario autenticado de una petición, leído de la base en cada petición: un cambio de rol o una
 * desactivación valen desde la siguiente acción (HU-MOD01-006, CU-MOD01-006 2c).
 *
 * @param rol código estable del rol ({@code jefe-seccion}, {@code capataz}...)
 * @param permisos códigos {@code MODULO.RECURSO.ACCION} concedidos al rol
 */
public record UsuarioSesion(
        long id,
        String nombreUsuario,
        String nombre,
        String rol,
        String rolNombre,
        Set<String> permisos,
        boolean debeCambiarContrasena,
        long sesionId) {

    public static final String JEFE = "jefe-seccion";
    public static final String CAPATAZ = "capataz";

    public boolean tiene(String permiso) {
        return permisos.contains(permiso);
    }

    public boolean esJefe() {
        return JEFE.equals(rol);
    }

    /** Autoridades de Spring: un permiso por código, más {@code ROL_<codigo>}. */
    public Collection<? extends GrantedAuthority> autoridades() {
        var autoridades = new ArrayList<GrantedAuthority>();
        permisos.forEach(p -> autoridades.add(new SimpleGrantedAuthority(p)));
        autoridades.add(new SimpleGrantedAuthority("ROL_" + rol));
        return autoridades;
    }
}
