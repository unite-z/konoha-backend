package pe.edu.pucp.areasverdes.mod01.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.pucp.areasverdes.mod01.servicios.AutenticacionService;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * La sesión de MOD-01 para las cadenas de seguridad de otros módulos (AV-234). MOD-03 y MOD-05 la
 * aceptan además de HTTP Basic, así que la aplicación web entra solo por MOD-01 y no guarda ninguna
 * credencial. Es un puente hasta la consolidación de usuarios: el principal sigue siendo el {@link
 * Usuario} de MOD-05, con el mismo {@code id} (la copia de {@code V6} lo conserva) y el rol actual
 * de MOD-01, de modo que un cambio de rol o una desactivación valen también ahí.
 */
@Component
public class SesionCompartida {

    /**
     * Sin sesión HTTP, Spring trata cada petición autenticada como un inicio de sesión nuevo y su
     * estrategia CSRF borraba la cookie {@code XSRF-TOKEN} en cada respuesta: las escrituras
     * fallaban una sí y otra no. El token no se regenera; la sesión de MOD-01 ya rota con cada
     * inicio.
     */
    public static final SessionAuthenticationStrategy SIN_REGENERAR_CSRF =
            new NullAuthenticatedSessionStrategy();

    private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** Nombre del rol de MOD-05 que corresponde a cada rol de MOD-01. El capataz no tiene. */
    static final Map<String, String> ROL_MOD05 =
            Map.of(
                    "jefe-seccion", Usuario.JEFE,
                    "ingeniero-campo", "Ingeniero de campo",
                    "practicante", "Practicante autorizado");

    private final AutenticacionService autenticacion;
    private final ObjectMapper json;

    public SesionCompartida(AutenticacionService autenticacion, ObjectMapper json) {
        this.autenticacion = autenticacion;
        this.json = json;
    }

    /**
     * Filtro que autentica la petición con la cookie de MOD-01 y el principal que da {@code mapa}.
     */
    public OncePerRequestFilter filtro(Function<UsuarioSesion, Object> mapa) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
                    throws ServletException, IOException {
                var usuario = autenticacion.validar(token(peticion));
                if (usuario.isPresent()) {
                    UsuarioSesion u = usuario.get();
                    if (u.debeCambiarContrasena()) {
                        SeguridadMod01.escribir(
                                respuesta,
                                json,
                                403,
                                "cambio-contrasena-obligatorio",
                                "Define una contraseña nueva antes de continuar");
                        return;
                    }
                    Object principal = mapa.apply(u);
                    var autoridades =
                            principal instanceof Usuario m
                                    ? m.getAuthorities()
                                    : List.<GrantedAuthority>of();
                    SecurityContextHolder.getContext()
                            .setAuthentication(
                                    UsernamePasswordAuthenticationToken.authenticated(
                                            principal, null, autoridades));
                }
                cadena.doFilter(peticion, respuesta);
            }
        };
    }

    /** El usuario de MOD-05 equivalente: mismo id, rol de MOD-01. */
    public static Usuario comoUsuarioMod05(UsuarioSesion u) {
        String rol = ROL_MOD05.get(u.rol());
        return new Usuario(
                u.id(),
                u.nombreUsuario(),
                u.nombre(),
                "",
                true,
                rol == null ? List.of() : List.of(rol));
    }

    /**
     * Escrituras que exigen el token CSRF: las que llegan con la cookie de sesión. Las de HTTP
     * Basic (pruebas y smoke test del despliegue) no lo necesitan: el navegador no envía esa
     * cabecera solo.
     */
    public static boolean exigeCsrf(HttpServletRequest peticion) {
        return !METODOS_SEGUROS.contains(peticion.getMethod()) && token(peticion) != null;
    }

    /** El mismo repositorio de tokens que MOD-01: cookie {@code XSRF-TOKEN} en la raíz. */
    public static CsrfTokenRepository tokensCsrf() {
        var tokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokens.setCookiePath("/");
        return tokens;
    }

    static String token(HttpServletRequest peticion) {
        if (peticion.getCookies() == null) {
            return null;
        }
        for (Cookie c : peticion.getCookies()) {
            if (SeguridadMod01.COOKIE_SESION.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
