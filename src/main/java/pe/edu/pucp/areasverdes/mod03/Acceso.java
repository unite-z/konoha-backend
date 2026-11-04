package pe.edu.pucp.areasverdes.mod03;

/**
 * Permisos de MOD-03 con los códigos de MOD-01 (AV-252, {@code docs/modulos/mod01.md}):
 *
 * <ul>
 *   <li>consultar: los cuatro roles, también el capataz (RF-PLA-007);
 *   <li>gestionar (configurar, restricciones, programar, interrupción y traslado): el jefe;
 *   <li>registrar la limpieza del ciclo de corte: jefe, ingeniero y practicante (RF-COR-006);
 *   <li>reportar incidencias: los cuatro roles; gestionarlas desde la oficina: el jefe.
 * </ul>
 */
final class Acceso {

    static final String CONSULTAR = "PLA.PLANIFICACION.CONSULTAR";
    static final String GESTIONAR = "PLA.PLANIFICACION.GESTIONAR";
    static final String LIMPIEZA = "PLA.CORTE.LIMPIEZA";
    static final String REGISTRAR_CORTE = "PLA.CORTE.REGISTRAR";
    static final String REGISTRAR_INCIDENCIA = "PLA.INCIDENCIA.REGISTRAR";
    static final String CLASIFICAR_INCIDENCIA = "PLA.INCIDENCIA.CLASIFICAR";

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

    /**
     * Marcar jardines de la ruta de corte: capataz, ingeniero y practicante (RF-COR-002, AV-260).
     */
    static void registrarCorte(UsuarioPlanificacion usuario) {
        exigir(usuario, REGISTRAR_CORTE);
    }

    /** Reportar y seguir las propias: los cuatro roles (HU-020, AV-274). */
    static void registrarIncidencia(UsuarioPlanificacion usuario) {
        exigir(usuario, REGISTRAR_INCIDENCIA);
    }

    /** La bandeja y la ficha de la oficina: el jefe, hasta los permisos propios de AV-281. */
    static void gestionarIncidencias(UsuarioPlanificacion usuario) {
        exigir(usuario, CLASIFICAR_INCIDENCIA);
    }

    private static void exigir(UsuarioPlanificacion usuario, String permiso) {
        if (usuario == null || !usuario.tiene(permiso)) {
            throw ErrorPlanificacion.sinPermiso();
        }
    }
}
