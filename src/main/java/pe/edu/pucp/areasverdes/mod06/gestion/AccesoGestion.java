package pe.edu.pucp.areasverdes.mod06.gestion;

import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/**
 * Permisos de los reportes de gestión del MOD-06. El usuario es el de la sesión de MOD-01 y el
 * permiso es {@link #CONSULTAR} ({@code docs/modulos/mod01.md}): hoy lo tienen Jefe de sección,
 * Ingeniero de campo y Practicante; Capataz y Operario, no (matriz de las HU v3, 2.1). Un cambio de
 * rol o de permisos en MOD-01 vale desde la siguiente petición.
 *
 * <p>Los cinco indicadores del panel tienen el mismo permiso (HU-MOD06-001, criterio 4). Si un
 * indicador necesitara otro, se filtra en {@code PanelService}, no en el controlador.
 */
public final class AccesoGestion {

    /** Permiso de MOD-01 «Consultar el panel de indicadores y reportes». */
    public static final String CONSULTAR = "REP.INDICADOR.CONSULTAR";

    private AccesoGestion() {}

    /** Consulta de solo lectura: el usuario de la sesión necesita {@link #CONSULTAR}. */
    public static void consultar(UsuarioSesion usuario) {
        if (usuario == null || !usuario.tiene(CONSULTAR)) {
            throw ErrorGestion.sinPermiso();
        }
    }
}
