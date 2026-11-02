package pe.edu.pucp.areasverdes.mod02.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Protege MOD-02 con la sesión de MOD-01; HTTP Basic queda para pruebas y smoke tests. */
@Configuration
public class SeguridadMod02 {

    @Bean
    @Order(0)
    SecurityFilterChain cadenaSeguridadMod02(
            HttpSecurity http, ObjectMapper json, SesionCompartida sesion) throws Exception {
        http.securityMatcher("/api/v1/inventario/**")
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
                        sesion.filtro(SeguridadMod02::comoUsuarioMod02),
                        BasicAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .httpBasic(
                        b ->
                                b.authenticationEntryPoint(
                                        (req, res, e) -> {
                                            res.setStatus(401);
                                            res.setContentType("application/json;charset=UTF-8");
                                            json.writeValue(
                                                    res.getOutputStream(),
                                                    Map.of(
                                                            "codigo",
                                                            "no-autenticado",
                                                            "detalle",
                                                            "Inicia sesión para continuar"));
                                        }));
        return http.build();
    }

    /** Adapta la sesión común al principal transitorio que esperan los servicios de MOD-02. */
    static Usuario comoUsuarioMod02(UsuarioSesion usuario) {
        String rol =
                switch (usuario.rol()) {
                    case "jefe-seccion" -> Usuario.JEFE;
                    case "ingeniero-campo" -> "Ingeniero de campo";
                    case "practicante" -> "Practicante autorizado";
                    case "capataz" -> "Capataz";
                    default -> null;
                };
        return new Usuario(
                usuario.id(),
                usuario.nombreUsuario(),
                usuario.nombre(),
                "",
                true,
                rol == null ? List.of() : List.of(rol));
    }
}
