package pe.edu.pucp.areasverdes.mod03;

/**
 * Permisos de MOD-03 con los códigos de MOD-01 (AV-252, {@code docs/modulos/mod01.md}):
 *
 * <ul>
 *   <li>consultar: los cuatro roles, también el capataz (RF-PLA-007);
 *   <li>gestionar (configurar, restricciones, programar, interrupción y traslado): el jefe;
 *   <li>registrar la limpieza del ciclo de corte: jefe, ingeniero y practicante (RF-COR-006).
 * </ul>
 */
final class Acceso {

    static final String CONSULTAR = "PLA.PLANIFICACION.CONSULTAR";
    static final String GESTIONAR = "PLA.PLANIFICACION.GESTIONAR";
    static final String LIMPIEZA = "PLA.CORTE.LIMPIEZA";

    private Acceso() {}

    static void consultar(UsuarioPlanificacion usuario) {
        exigir(usuario, CONSULTAR);
    }

    static void gestionar(UsuarioPlanificacion usuario) {
        exigir(usuario, GESTIONAR);
    }

    /** Lo aplica el registro de la limpieza del ciclo de corte (AV-262). */
    static void registrarLimpieza(UsuarioPlanificacion usuario) {
        exigir(usuario, LIMPIEZA);
    }

    private static void exigir(UsuarioPlanificacion usuario, String permiso) {
        if (usuario == null || !usuario.tiene(permiso)) {
            throw ErrorPlanificacion.sinPermiso();
        }
    }
}
