package pe.edu.pucp.areasverdes.mod06.gestion;

import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Permisos de los reportes de gestión del MOD-06 por rol. Matriz de permisos de las HU (v3, 2.1),
 * copiada en {@code mod06.permisos.Permisos}: «Panel de indicadores y reportes»: Jefe de sección,
 * Ingeniero de campo y Practicante consultan; Capataz y Operario, no. Con los usuarios de MOD-05
 * (hasta MOD-01) son los tres roles de {@link Usuario#ROLES}. RF-REP-007 v2 pide abrir el mapa a
 * todos los usuarios: hoy coincide con esos tres roles.
 *
 * <p>Los cinco indicadores del panel tienen el mismo permiso (HU-MOD06-001, criterio 4). Si un
 * indicador necesitara otro, se filtra en {@code PanelService}, no en el controlador.
 */
public final class AccesoGestion {

    private AccesoGestion() {}

    /** Consulta de solo lectura: cualquier usuario activo con uno de los tres roles. */
    public static void consultar(Usuario usuario) {
        if (usuario == null || !usuario.activo() || usuario.rol() == null) {
            throw ErrorGestion.sinPermiso();
        }
    }
}
