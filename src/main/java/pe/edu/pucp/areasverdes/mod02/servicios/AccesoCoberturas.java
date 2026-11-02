package pe.edu.pucp.areasverdes.mod02.servicios;

import java.util.List;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

public final class AccesoCoberturas {

    private AccesoCoberturas() {}

    public static void consultar(Usuario usuario) {
        if (usuario == null
                || !usuario.activo()
                || usuario.roles().stream()
                        .noneMatch(
                                rol ->
                                        List.of(
                                                        Usuario.JEFE,
                                                        "Ingeniero de campo",
                                                        "Practicante autorizado",
                                                        "Capataz")
                                                .contains(rol))) {
            throw new ErrorCoberturas(
                    403, "sin-permiso", "Tu usuario no tiene permisos para esta operación");
        }
    }

    public static void gestionar(Usuario usuario) {
        consultar(usuario);
        if (!usuario.roles().contains(Usuario.JEFE)) {
            throw new ErrorCoberturas(
                    403, "sin-permiso", "Solo el jefe de sección puede gestionar el catálogo");
        }
    }

    public static void registrar(Usuario usuario) {
        consultar(usuario);
        if (!usuario.roles().contains(Usuario.JEFE)
                && !usuario.roles().contains("Ingeniero de campo")
                && !usuario.roles().contains("Practicante autorizado")) {
            throw new ErrorCoberturas(
                    403,
                    "sin-permiso",
                    "Solo el jefe de sección o el ingeniero de campo puede registrar áreas");
        }
    }
}
