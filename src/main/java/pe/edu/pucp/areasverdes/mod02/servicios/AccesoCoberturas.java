package pe.edu.pucp.areasverdes.mod02.servicios;

import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Permisos de MOD-02 (AV-382): códigos de MOD-01 de la sesión, nunca el nombre del rol. */
public final class AccesoCoberturas {

    static final String CONSULTAR = "INV.INVENTARIO.CONSULTAR";
    static final String REGISTRAR = "INV.INVENTARIO.REGISTRAR";
    static final String GESTIONAR_CATALOGO = "CORE.CATALOGO.GESTIONAR";

    private AccesoCoberturas() {}

    static void exigir(Usuario usuario, String permiso, String detalle) {
        if (usuario == null || !usuario.puede(permiso)) {
            throw new ErrorCoberturas(403, "sin-permiso", detalle);
        }
    }

    public static void consultar(Usuario usuario) {
        exigir(usuario, CONSULTAR, "Tu usuario no tiene permisos para esta operación");
    }

    public static void gestionar(Usuario usuario) {
        consultar(usuario);
        exigir(usuario, GESTIONAR_CATALOGO, "Tu rol no puede gestionar el catálogo");
    }

    public static void registrar(Usuario usuario) {
        consultar(usuario);
        exigir(usuario, REGISTRAR, "Tu rol no puede registrar áreas verdes");
    }
}
