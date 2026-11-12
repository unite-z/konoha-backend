package pe.edu.pucp.areasverdes.mod02.servicios;

import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Permisos de poda (AV-382): {@code INV.PODA.REGISTRAR} e {@code INV.PODA.PLANIFICAR}. */
public final class AccesoPoda {

    private AccesoPoda() {}

    public static void registrar(Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        AccesoCoberturas.exigir(usuario, "INV.PODA.REGISTRAR", "Tu rol no puede registrar la poda");
    }

    public static void planificar(Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        AccesoCoberturas.exigir(
                usuario, "INV.PODA.PLANIFICAR", "Tu rol no puede planificar la campaña de poda");
    }
}
