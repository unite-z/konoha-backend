package pe.edu.pucp.areasverdes.mod01.servicios;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Contrato de autorización de CU-MOD01-006 para todos los módulos. Se comprueba en el servidor en
 * cada acción: ocultar opciones en la interfaz es usabilidad, no seguridad. Los códigos de permiso
 * están en {@code docs/modulos/mod01.md}.
 */
@Service
public class Autorizacion {

    public static final String VER_MONTOS = "SER.CONTRATO.VER_MONTOS";
    public static final String VER_DATOS_PERSONALES = "CORE.PERSONAL.VER_DATOS";

    private final RegistroAccesos accesos;
    private final ConsultaCampo campo;

    public Autorizacion(RegistroAccesos accesos, ConsultaCampo campo) {
        this.accesos = accesos;
        this.campo = campo;
    }

    /**
     * Exige el permiso. Si falta, registra el intento y responde 403 «No tiene permiso para esta
     * acción», sin revelar datos (CU-MOD01-006 2a). Vale igual para enlaces directos y llamadas a
     * la API.
     */
    public void exigir(UsuarioSesion usuario, String permiso) {
        if (usuario == null) {
            throw ErrorCore.noAutenticado("Inicia sesión para continuar");
        }
        if (!usuario.tiene(permiso)) {
            accesos.registrar(usuario.id(), null, "api", "SinPermiso", permiso, rutaActual());
            throw ErrorCore.sinPermiso();
        }
    }

    /** Montos de contratos y órdenes de compra: solo el jefe de sección (RNF-008). */
    public boolean puedeVerMontos(UsuarioSesion usuario) {
        return usuario != null && usuario.tiene(VER_MONTOS);
    }

    /** Datos personales de cuentas y personal: solo el jefe de sección (RNF-008). */
    public boolean puedeVerDatosPersonales(UsuarioSesion usuario) {
        return usuario != null && usuario.tiene(VER_DATOS_PERSONALES);
    }

    /**
     * Cuadrillas a las que se limita el usuario. Vacío significa sin límite de cuadrilla (roles de
     * oficina); para el capataz, solo las suyas (HU-MOD01-007, «Alcance de cuadrilla»).
     */
    public Optional<Set<Long>> alcanceCuadrillas(UsuarioSesion usuario) {
        if (usuario != null && UsuarioSesion.CAPATAZ.equals(usuario.rol())) {
            return Optional.of(campo.cuadrillasDelCapataz(usuario.id()));
        }
        return Optional.empty();
    }

    /** El capataz no puede registrar en la cuadrilla de otro (CU-MOD01-006 3b). */
    public void exigirCuadrilla(UsuarioSesion usuario, long cuadrillaId) {
        var alcance = alcanceCuadrillas(usuario);
        if (alcance.isPresent() && !alcance.get().contains(cuadrillaId)) {
            accesos.registrar(
                    usuario.id(),
                    null,
                    "api",
                    "SinPermiso",
                    "cuadrilla-ajena:" + cuadrillaId,
                    rutaActual());
            throw ErrorCore.sinPermiso();
        }
    }

    private static String rutaActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            HttpServletRequest peticion = a.getRequest();
            return peticion.getMethod() + " " + peticion.getRequestURI();
        }
        return null;
    }
}
