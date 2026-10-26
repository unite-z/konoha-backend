package pe.edu.pucp.areasverdes.mod02;

import pe.edu.pucp.areasverdes.mod05.Usuario;

final class AccesoCoberturas {

    private AccesoCoberturas() {}

    static void consultar(Usuario usuario) {
        if (usuario == null || !usuario.activo() || usuario.rol() == null) {
            throw new ErrorCoberturas(
                    403, "sin-permiso", "Tu usuario no tiene permisos para esta operación");
        }
    }

    static void gestionar(Usuario usuario) {
        consultar(usuario);
        if (!usuario.roles().contains(Usuario.JEFE)) {
            throw new ErrorCoberturas(
                    403, "sin-permiso", "Solo el jefe de sección puede gestionar el catálogo");
        }
    }

    static void registrar(Usuario usuario) {
        consultar(usuario);
        if (!usuario.roles().contains(Usuario.JEFE)
                && !usuario.roles().contains("Ingeniero de campo")) {
            throw new ErrorCoberturas(
                    403,
                    "sin-permiso",
                    "Solo el jefe de sección o el ingeniero de campo puede registrar áreas");
        }
    }
}
