package pe.edu.pucp.areasverdes.mod05;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import pe.edu.pucp.areasverdes.mod01.config.SesionCompartida;

@Configuration
@SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
public class Seguridad {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetails(Mod05Repository repo) {
        return username ->
                repo.usuario(username)
                        .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }

    /**
     * La aplicación web entra con la sesión de MOD-01 (cookie y CSRF, AV-234). HTTP Basic se
     * mantiene para las pruebas y el smoke test del despliegue. Sin credenciales responde 401.
     */
    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper, SesionCompartida sesion)
            throws Exception {
        http.csrf(
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
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                "/api/tercerizados/docs",
                                                "/api/tercerizados/schema/**",
                                                "/swagger-ui/**",
                                                "/error")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .httpBasic(
                        b ->
                                b.authenticationEntryPoint(
                                        (req, res, e) -> {
                                            res.setStatus(401);
                                            res.setContentType("application/json;charset=UTF-8");
                                            mapper.writeValue(
                                                    res.getOutputStream(),
                                                    Map.of(
                                                            "detail",
                                                            "Credenciales inválidas o ausentes"));
                                        }))
                .exceptionHandling(
                        e ->
                                e.accessDeniedHandler(
                                        (req, res, ex) -> {
                                            res.setStatus(403);
                                            res.setContentType("application/json;charset=UTF-8");
                                            mapper.writeValue(
                                                    res.getOutputStream(),
                                                    Map.of(
                                                            "detail",
                                                            ex instanceof CsrfException
                                                                    ? "La sesión de la página"
                                                                            + " caducó. Recarga y"
                                                                            + " vuelve a intentarlo"
                                                                    : "No tiene permiso para esta"
                                                                            + " acción"));
                                        }));
        return http.build();
    }
}
