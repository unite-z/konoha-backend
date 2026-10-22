package pe.edu.pucp.areasverdes.mod03;

import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Permisos de MOD-03 por rol. Consultar: los tres roles. Configurar y programar: jefe de sección.
 */
final class Acceso {

    private Acceso() {}

    static void consultar(Usuario usuario) {
        if (usuario == null || !usuario.activo() || usuario.rol() == null) {
            throw ErrorPlanificacion.sinPermiso();
        }
    }

    static void gestionar(Usuario usuario) {
        consultar(usuario);
        if (!usuario.roles().contains(Usuario.JEFE)) {
            throw ErrorPlanificacion.sinPermiso();
        }
    }
}
