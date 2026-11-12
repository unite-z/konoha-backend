package pe.edu.pucp.areasverdes.mod06.permisos;

import java.util.Set;

/**
 * Usuario de la petición ({@code request.user} en el Django).
 *
 * @param autenticado {@code is_authenticated}
 * @param id id de {@code core_usuario}
 * @param permisos códigos de permiso que MOD-01 concede a su rol
 */
public record Usuario(boolean autenticado, Object id, Set<String> permisos) {

    /** Usuario sin sesión. */
    public static final Usuario ANONIMO = new Usuario(false, null, Set.of());
}
