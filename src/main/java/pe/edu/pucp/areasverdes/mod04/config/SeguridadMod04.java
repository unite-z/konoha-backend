package pe.edu.pucp.areasverdes.mod04.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Protege MOD-04 antes de la cadena abierta de MOD-06; los roles se validan en el servicio. */
@Configuration
public class SeguridadMod04 {
    @Bean
    @Order(0)
    SecurityFilterChain cadenaSeguridadMod04(HttpSecurity http, ObjectMapper json)
            throws Exception {
        http.securityMatcher("/api/v1/operacion-campo", "/api/v1/operacion-campo/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .csrf(c -> c.disable())
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
}
