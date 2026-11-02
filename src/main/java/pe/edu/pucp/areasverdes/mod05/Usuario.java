package pe.edu.pucp.areasverdes.mod05;

import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record Usuario(
        long id,
        String usuario,
        String nombre,
        String passwordHash,
        boolean activo,
        List<String> roles)
        implements UserDetails {
    public static final String JEFE = "Jefe de sección";
    public static final String CAPATAZ = "Capataz";
    public static final List<String> ROLES =
            List.of(JEFE, "Ingeniero de campo", "Practicante autorizado", CAPATAZ);

    public String rol() {
        return ROLES.stream().filter(roles::contains).findFirst().orElse(null);
    }

    public void exigir(boolean jefe) {
        if (!activo || rol() == null || (jefe && !roles.contains(JEFE)))
            throw new ApiError(
                    403, "SinPermiso", "Tu usuario no tiene permisos para esta operación");
    }

    @Override
    public String getUsername() {
        return usuario;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
