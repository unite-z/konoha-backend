package pe.edu.pucp.areasverdes.mod05;

import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal de MOD-05 (y de MOD-02 y MOD-04, que lo reutilizan). Desde AV-382 la autorización va
 * por {@code permisos}, los códigos de MOD-01 de la sesión ({@code core_rol_permiso}); {@code
 * roles} queda solo para mostrar y auditar el nombre del rol, nunca para decidir.
 */
public record Usuario(
        long id,
        String usuario,
        String nombre,
        String passwordHash,
        boolean activo,
        List<String> roles,
        Set<String> permisos)
        implements UserDetails {
    public static final String JEFE = "Jefe de sección";
    public static final String CAPATAZ = "Capataz";
    public static final List<String> ROLES =
            List.of(JEFE, "Ingeniero de campo", "Practicante autorizado");

    /** Ciclo de atención tercerizada: consultar, orden de compra, inicio, fin y reporte. */
    public static final String REGISTRAR_ATENCION = "SER.ATENCION.REGISTRAR";

    /**
     * Registrar atenciones y decidir sobre ellas: origen, acuerdo con la unidad, plan
     * fitosanitario.
     */
    public static final String GESTIONAR_ATENCION = "SER.ATENCION.GESTIONAR";

    public static final String REGISTRAR_CONFORMIDAD = "SER.CONFORMIDAD.REGISTRAR";
    public static final String VERIFICAR_SEGURIDAD = "SER.SEGURIDAD.VERIFICAR";
    public static final String CONSULTAR_CONTRATOS = "SER.CONTRATO.CONSULTAR";
    public static final String GESTIONAR_CONTRATOS = "SER.CONTRATO.GESTIONAR";
    public static final String VER_MONTOS = "SER.CONTRATO.VER_MONTOS";

    public Usuario {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
    }

    public String rol() {
        return ROLES.stream().filter(roles::contains).findFirst().orElse(null);
    }

    public boolean puede(String permiso) {
        return activo && permisos.contains(permiso);
    }

    public void exigir(String permiso) {
        if (!puede(permiso))
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
        return permisos.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
