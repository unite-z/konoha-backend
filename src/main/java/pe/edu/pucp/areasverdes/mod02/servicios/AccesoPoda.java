package pe.edu.pucp.areasverdes.mod02.servicios;

import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Permisos de poda por rol, equivalentes a {@code INV.PODA.PLANIFICAR} de {@code V9}. */
public final class AccesoPoda {

    private AccesoPoda() {}

    /** Equivale a {@code INV.PODA.REGISTRAR} de {@code V9}: ingeniero de campo y practicante. */
    public static void registrar(Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        if (!usuario.roles().contains("Ingeniero de campo")
                && !usuario.roles().contains("Practicante autorizado")) {
            throw new ErrorCoberturas(
                    403, "sin-permiso", "Solo el ingeniero de campo puede registrar la poda");
        }
    }

    public static void planificar(Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        if (!usuario.roles().contains(Usuario.JEFE)) {
            throw new ErrorCoberturas(
                    403, "sin-permiso", "Solo el jefe de sección puede planificar la campaña");
        }
    }
}
