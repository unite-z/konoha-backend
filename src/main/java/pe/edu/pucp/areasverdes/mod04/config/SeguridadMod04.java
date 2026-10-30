package pe.edu.pucp.areasverdes.mod04.config;

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
import pe.edu.pucp.areasverdes.mod04.servicios.OperacionCampoService;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Protege MOD-04 antes de la cadena abierta de MOD-06; los roles se validan en el servicio. */
@Configuration
public class SeguridadMod04 {
    @Bean
    @Order(0)
    SecurityFilterChain cadenaSeguridadMod04(
            HttpSecurity http, ObjectMapper json, SesionCompartida sesion) throws Exception {
        http.securityMatcher("/api/v1/operacion-campo", "/api/v1/operacion-campo/**")
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
                        sesion.filtro(SeguridadMod04::comoUsuarioCampo),
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
                                                            "Credenciales inválidas o ausentes"));
                                        }));
        return http.build();
    }

    /** El principal de campo conserva el id y el rol vigente de la sesión común. */
    static Usuario comoUsuarioCampo(UsuarioSesion usuario) {
        if (UsuarioSesion.CAPATAZ.equals(usuario.rol())) {
            return new Usuario(
                    usuario.id(),
                    usuario.nombreUsuario(),
                    usuario.nombre(),
                    "",
                    true,
                    List.of(OperacionCampoService.CAPATAZ));
        }
        return SesionCompartida.comoUsuarioMod05(usuario);
    }
}
