package pe.edu.pucp.areasverdes.mod06.permisos;

import org.springframework.stereotype.Component;

/**
 * Permisos de los reportes y la extracción IA del MOD-06, con los códigos de MOD-01 ({@code
 * docs/modulos/mod01.md}): el rol los concede y un cambio vale desde la siguiente petición.
 *
 * <pre>
 * | Función                                              | Permiso                 | Hoy lo tienen                 |
 * |------------------------------------------------------|-------------------------|-------------------------------|
 * | Reportes (RF-REP-002/004/006)                        | REP.INDICADOR.CONSULTAR | Jefe, Ingeniero y Practicante |
 * | Solicitar y consultar análisis de IA (RF-IA-001)     | IA.PROPUESTA.PROPONER   | Jefe, Ingeniero y Practicante |
 * | Validar o rechazar datos de la IA y ver sus métricas | IA.PROPUESTA.VALIDAR    | Jefe                          |
 * </pre>
 *
 * <p>Sin sesión, la cadena {@code SeguridadGestion} ya ha respondido 401 antes de llegar aquí.
 */
@Component
public class Permisos {

    public static final String CONSULTAR_REPORTES = "REP.INDICADOR.CONSULTAR";
    public static final String PROPONER_IA = "IA.PROPUESTA.PROPONER";
    public static final String VALIDAR_IA = "IA.PROPUESTA.VALIDAR";

    public static final String MENSAJE_ROL_SIN_ACCESO = "Tu rol no tiene acceso a esta función";

    /** Id del usuario autenticado, o {@code null} si es anónimo. */
    public static Object idDeUsuario(Usuario usuario) {
        return usuario != null && usuario.autenticado() ? usuario.id() : null;
    }

    /** Un usuario anónimo nunca tiene permiso. */
    public boolean tienePermiso(Usuario usuario, String permiso) {
        return usuario != null && usuario.autenticado() && usuario.permisos().contains(permiso);
    }
}
