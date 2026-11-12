package pe.edu.pucp.areasverdes.mod06.gestion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import pe.edu.pucp.areasverdes.mod01.config.SesionCompartida;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/**
 * Rutas del MOD-06: reportes (RF-REP-*) y extracción IA (RF-IA-001). Se entra solo con la sesión de
 * MOD-01 (cookie y CSRF, AV-234 y AV-327): el principal es el {@link UsuarioSesion} de MOD-01, sin
 * pasar por los usuarios de MOD-05, y los permisos los decide cada endpoint ({@code AccesoGestion}
 * o {@code Permisos}). Sin sesión responde 401.
 *
 * <p>Tiene que ir antes que la cadena de MOD-06 (orden 1), que solo deja abiertas la documentación
 * y la respuesta 404 de {@code /api/v1/**}; comparte el orden 0 con otras cadenas que no se
 * solapan.
 */
@Configuration
public class SeguridadGestion {

    @Bean
    @Order(0)
    SecurityFilterChain cadenaSeguridadGestion(
            HttpSecurity http, ObjectMapper json, SesionCompartida sesion) throws Exception {
        http.securityMatcher(
                        "/api/v1/reportes", "/api/v1/reportes/**", "/api/v1/ia", "/api/v1/ia/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .csrf(
                        c ->
                                c.csrfTokenRepository(SesionCompartida.tokensCsrf())
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .sessionAuthenticationStrategy(
                                                SesionCompartida.SIN_REGENERAR_CSRF)
                                        .requireCsrfProtectionMatcher(SesionCompartida::exigeCsrf))
                .addFilterBefore(
                        sesion.filtro(usuario -> usuario), AnonymousAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                        (req, res, ex) -> noAutenticado(res, json)));
        return http.build();
    }

    private static void noAutenticado(HttpServletResponse respuesta, ObjectMapper json)
            throws IOException {
        respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        respuesta.setContentType("application/json;charset=UTF-8");
        json.writeValue(
                respuesta.getOutputStream(),
                Map.of("codigo", "no-autenticado", "detalle", "Inicia sesión para continuar"));
    }
}
