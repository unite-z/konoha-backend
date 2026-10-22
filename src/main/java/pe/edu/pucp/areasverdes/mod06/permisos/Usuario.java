package pe.edu.pucp.areasverdes.mod06.permisos;

/**
 * Usuario de la petición ({@code request.user} en el Django).
 *
 * <p>Mientras el MOD-01 no publique la autenticación, todas las peticiones son anónimas ({@link #ANONIMO}).
 * TODO: ajustar al modelo de usuario del MOD-01 (Usuario.id, Usuario.rol_id → Rol.nombre).
 *
 * @param autenticado {@code is_authenticated}
 * @param id          {@code Usuario.id}
 * @param rol_id      {@code Usuario.rol_id}
 */
public record Usuario(boolean autenticado, Object id, Object rol_id) {

    /** Usuario anónimo (autenticación vacía). */
    public static final Usuario ANONIMO = new Usuario(false, null, null);
}
