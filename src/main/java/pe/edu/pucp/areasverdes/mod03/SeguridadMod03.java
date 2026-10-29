package pe.edu.pucp.areasverdes.mod03;

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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import pe.edu.pucp.areasverdes.mod01.config.SesionCompartida;

/**
 * Rutas de MOD-03 ({@code /api/v1/planificacion/**}). La aplicación web entra con la sesión de
 * MOD-01 (cookie y CSRF, AV-234); HTTP Basic con los usuarios de MOD-05 se mantiene para las
 * pruebas. Tiene que ir antes que la cadena de MOD-06 (orden 1), que deja abierto el resto de
 * {@code /api/v1/**}; comparte el orden 0 con la de {@code /api/health}, que no se solapa. Sin
 * credenciales responde 401. Los permisos por rol los decide cada endpoint.
 */
@Configuration
public class SeguridadMod03 {

    @Bean
    @Order(0)
    SecurityFilterChain cadenaSeguridadMod03(
            HttpSecurity http, ObjectMapper json, SesionCompartida sesion) throws Exception {
        http.securityMatcher("/api/v1/planificacion", "/api/v1/planificacion/**")
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
                        sesion.filtro(SesionCompartida::comoUsuarioMod05),
                        BasicAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .httpBasic(
                        b -> b.authenticationEntryPoint((req, res, e) -> noAutenticado(res, json)));
        return http.build();
    }

    private static void noAutenticado(HttpServletResponse respuesta, ObjectMapper json)
            throws IOException {
        respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        respuesta.setContentType("application/json;charset=UTF-8");
        json.writeValue(
                respuesta.getOutputStream(),
                Map.of("codigo", "no-autenticado", "detalle", "Credenciales inválidas o ausentes"));
    }
}
