package pe.edu.pucp.areasverdes.mod06.permisos;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.SelectorFuenteDatos;

/**
 * Permisos por rol del MOD-06.
 *
 * <p>Fuente: matriz de permisos por rol (Historias de Usuario v3, sección 2.1) y catálogo de roles (Modelo de BD v1,
 * tabla 3).
 *
 * <pre>
 * | Función                                              | Jefe de sección | Ingeniero de campo | Practicante       | Capataz | Operario |
 * |------------------------------------------------------|-----------------|--------------------|-------------------|---------|----------|
 * | Panel de indicadores y reportes (RF-REP-002/004/006) | Consulta        | Consulta (ACC-S3)  | Consulta (ACC-S3) | —       | —        |
 * | Solicitar análisis de IA (RF-IA-001)                 | Sí              | Propone            | Propone           | —       | —        |
 * | Validar o rechazar datos propuestos por IA           | Valida          | —                  | —                 | —       | —        |
 * </pre>
 *
 * <p>Mientras el MOD-01 no esté integrado (autenticación vacía: el usuario es anónimo), {@link #tienePermiso}
 * permite el acceso y deja un aviso en el log una sola vez por proceso.
 */
@Component
public class Permisos {

    private static final Logger log = LoggerFactory.getLogger(Permisos.class);

    // --- Roles (Modelo de BD v1, tabla 3; nombres exactos) ----------------------

    public static final String ROL_JEFE_SECCION = "Jefe de sección";
    public static final String ROL_INGENIERO_CAMPO = "Ingeniero de campo";
    public static final String ROL_PRACTICANTE = "Practicante";
    public static final String ROL_CAPATAZ = "Capataz";
    public static final String ROL_OPERARIO = "Operario";

    public static final Set<String> ROLES_CONSULTA_REPORTES = Set.of(ROL_JEFE_SECCION, ROL_INGENIERO_CAMPO, ROL_PRACTICANTE);
    public static final Set<String> ROLES_SOLICITAR_IA = Set.of(ROL_JEFE_SECCION, ROL_INGENIERO_CAMPO, ROL_PRACTICANTE);
    public static final Set<String> ROLES_VALIDAR_IA = Set.of(ROL_JEFE_SECCION);

    public static final String MENSAJE_ROL_SIN_ACCESO = "Tu rol no tiene acceso a esta función";
    public static final String MENSAJE_SIN_AUTENTICACION =
        "Permisos de MOD-06 sin aplicar: falta la autenticación del MOD-01 (RF-T-001, RF-T-004)";

    private static boolean avisoEmitido = false;
    private static final ReentrantLock CANDADO_AVISO = new ReentrantLock();

    private final SelectorFuenteDatos selector;

    public Permisos(SelectorFuenteDatos selector) {
        this.selector = selector;
    }

    private static boolean estaAutenticado(Usuario usuario) {
        return usuario != null && usuario.autenticado();
    }

    /**
     * Nombre del rol del usuario, o {@code null} si es anónimo o no tiene rol.
     *
     * <p>TODO: ajustar al modelo de usuario del MOD-01 (Usuario.rol_id → Rol.nombre).
     * Hoy se asume un usuario con {@code rol_id} y se resuelve el nombre en la entidad Rol de la fuente de datos.
     */
    public Object rolDeUsuario(Usuario usuario) {
        if (!estaAutenticado(usuario)) return null;
        Object rolId = usuario.rol_id();
        if (rolId == null) return null;
        Map<String, Object> rol = selector.obtenerFuenteDatos().obtener("Rol", rolId);
        return Py.truthy(rol) ? rol.get("nombre") : null;
    }

    /**
     * Id del usuario autenticado, o {@code null} si es anónimo.
     *
     * <p>TODO: ajustar al modelo de usuario del MOD-01 (Usuario.id).
     */
    public static Object idDeUsuario(Usuario usuario) {
        if (!estaAutenticado(usuario)) return null;
        return usuario.id();
    }

    private static void avisarSinAutenticacion() {
        CANDADO_AVISO.lock();
        try {
            if (!avisoEmitido) {
                log.warn(MENSAJE_SIN_AUTENTICACION);
                avisoEmitido = true;
            }
        } finally {
            CANDADO_AVISO.unlock();
        }
    }

    /** Para las pruebas: vuelve a permitir el aviso único. */
    public static void reiniciarAviso() {
        CANDADO_AVISO.lock();
        try {
            avisoEmitido = false;
        } finally {
            CANDADO_AVISO.unlock();
        }
    }

    /**
     * Permiso por rol ({@code PermisoPorRol(roles).has_permission} en el Django).
     *
     * <ul>
     *   <li>Usuario anónimo (MOD-01 aún no integrado): permite y avisa una vez por proceso.
     *   TODO: cuando el MOD-01 publique la autenticación, rechazar a los usuarios anónimos.</li>
     *   <li>Usuario autenticado cuyo rol no está en el conjunto: {@code false} (403 «Tu rol no tiene acceso a esta
     *   función»).</li>
     * </ul>
     */
    public boolean tienePermiso(Usuario usuario, Set<String> roles) {
        if (!estaAutenticado(usuario)) {
            avisarSinAutenticacion();
            return true;
        }
        Object rol = rolDeUsuario(usuario);
        return rol instanceof String nombre && roles.contains(nombre);
    }
}
