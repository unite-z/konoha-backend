package pe.edu.pucp.areasverdes.mod02.servicios;

import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Permisos de poda por rol, equivalentes a {@code INV.PODA.PLANIFICAR} de {@code V9}. */
public final class AccesoPoda {

    private AccesoPoda() {}

    public static void planificar(Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        if (!usuario.roles().contains(Usuario.JEFE)) {
            throw new ErrorCoberturas(
                    403, "sin-permiso", "Solo el jefe de sección puede planificar la campaña");
        }
    }
}
