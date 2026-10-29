package pe.edu.pucp.areasverdes.mod01.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.pucp.areasverdes.mod01.servicios.AutenticacionService;
import pe.edu.pucp.areasverdes.mod01.servicios.ErrorCore;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistroAccesos;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/**
 * Rutas de MOD-01 ({@code /api/v1/core/**}). Sesión propia de MOD-01 (supuesto ACC-S1): token opaco
 * en una cookie {@code HttpOnly; SameSite=Strict}, revocable y revalidado en cada petición. Las
 * escrituras exigen el token CSRF de la cookie {@code XSRF-TOKEN} en la cabecera {@code
 * X-XSRF-TOKEN}. Sin sesión responde 401; sin permiso, 403. Solo el inicio de sesión es público.
 *
 * <p>Va antes que la cadena de MOD-06 (orden 1), que deja abierto el resto de {@code /api/v1/**}.
 */
@Configuration
@EnableScheduling
public class SeguridadMod01 {

    public static final String COOKIE_SESION = "av_sesion";
    public static final String RUTA_COOKIE = "/api";

    static final String[] LOGIN = {"/api/v1/core/auth/login", "/api/v1/core/auth/login/"};

    /** Lo único que puede hacer quien tiene que cambiar la contraseña temporal (HU-MOD01-003). */
    static final Set<String> PERMITIDO_CON_CONTRASENA_TEMPORAL =
            Set.of(
                    "/api/v1/core/auth/sesion",
                    "/api/v1/core/auth/contrasena",
                    "/api/v1/core/auth/logout");

    /** Administración de cuentas: solo el jefe, decidido antes de leer el cuerpo de la petición. */
    static final String[] ADMINISTRACION = {
        "/api/v1/core/usuarios", "/api/v1/core/usuarios/**",
        "/api/v1/core/roles", "/api/v1/core/roles/**",
        "/api/v1/core/accesos", "/api/v1/core/accesos/**"
    };

    @Bean
    @Order(0)
    SecurityFilterChain cadenaSeguridadMod01(
            HttpSecurity http,
            ObjectMapper json,
            AutenticacionService autenticacion,
            RegistroAccesos accesos)
            throws Exception {
        var tokensCsrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokensCsrf.setCookiePath("/");
        http.securityMatcher("/api/v1/core", "/api/v1/core/**")
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(HttpMethod.POST, LOGIN)
                                        .permitAll()
                                        .requestMatchers(ADMINISTRACION)
                                        .hasAuthority("CORE.USUARIO.GESTIONAR")
                                        .anyRequest()
                                        .authenticated())
                .csrf(
                        c ->
                                c.csrfTokenRepository(tokensCsrf)
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .ignoringRequestMatchers(LOGIN))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .addFilterBefore(
                        new FiltroSesion(autenticacion, json),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new FiltroCookieCsrf(), CsrfFilter.class)
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        escribir(
                                                                res,
                                                                json,
                                                                401,
                                                                "no-autenticado",
                                                                "Inicia sesión para continuar"))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        denegado(req, res, ex, json, accesos)));
        return http.build();
    }

    /**
     * 403 sin datos y, si falta el permiso (no el token CSRF), registro del intento (HU-MOD01-007).
     */
    static void denegado(
            HttpServletRequest peticion,
            HttpServletResponse respuesta,
            Exception causa,
            ObjectMapper json,
            RegistroAccesos accesos)
            throws IOException {
        if (causa instanceof CsrfException) {
            escribir(
                    respuesta,
                    json,
                    403,
                    "csrf-invalido",
                    "La sesión de la página caducó. Recarga y vuelve a intentarlo");
            return;
        }
        var autenticado = SecurityContextHolder.getContext().getAuthentication();
        if (autenticado != null && autenticado.getPrincipal() instanceof UsuarioSesion u) {
            accesos.registrar(
                    u.id(),
                    null,
                    "api",
                    "SinPermiso",
                    "CORE.USUARIO.GESTIONAR",
                    peticion.getMethod() + " " + peticion.getRequestURI());
        }
        escribir(respuesta, json, 403, "sin-permiso", ErrorCore.SIN_PERMISO);
    }

    static void escribir(
            HttpServletResponse respuesta,
            ObjectMapper json,
            int estado,
            String codigo,
            String detalle)
            throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json;charset=UTF-8");
        json.writeValue(respuesta.getOutputStream(), Map.of("codigo", codigo, "detalle", detalle));
    }

    /** Autentica la petición con la cookie de sesión, si la hay y sigue vigente. */
    static final class FiltroSesion extends OncePerRequestFilter {

        private final AutenticacionService autenticacion;
        private final ObjectMapper json;

        FiltroSesion(AutenticacionService autenticacion, ObjectMapper json) {
            this.autenticacion = autenticacion;
            this.json = json;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
                throws ServletException, IOException {
            var usuario = autenticacion.validar(token(peticion));
            if (usuario.isPresent()) {
                UsuarioSesion u = usuario.get();
                String ruta = peticion.getRequestURI().replaceAll("/+$", "");
                if (u.debeCambiarContrasena()
                        && !PERMITIDO_CON_CONTRASENA_TEMPORAL.contains(ruta)) {
                    escribir(
                            respuesta,
                            json,
                            403,
                            "cambio-contrasena-obligatorio",
                            "Define una contraseña nueva antes de continuar");
                    return;
                }
                var autenticado =
                        UsernamePasswordAuthenticationToken.authenticated(u, null, u.autoridades());
                SecurityContextHolder.getContext().setAuthentication(autenticado);
            }
            cadena.doFilter(peticion, respuesta);
        }

        static String token(HttpServletRequest peticion) {
            if (peticion.getCookies() == null) {
                return null;
            }
            for (Cookie c : peticion.getCookies()) {
                if (COOKIE_SESION.equals(c.getName())) {
                    return c.getValue();
                }
            }
            return null;
        }
    }

    /**
     * Spring genera el token CSRF de forma diferida; leerlo hace que la cookie {@code XSRF-TOKEN}
     * salga en la respuesta y la PWA pueda enviarlo en la siguiente escritura.
     */
    static final class FiltroCookieCsrf extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
                throws ServletException, IOException {
            if (peticion.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
                token.getToken();
            }
            cadena.doFilter(peticion, respuesta);
        }
    }
}
