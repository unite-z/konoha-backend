package pe.edu.pucp.areasverdes.mod02;

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

@Configuration
public class SeguridadMod02 {

    @Bean
    @Order(0)
    SecurityFilterChain cadenaSeguridadMod02(HttpSecurity http, ObjectMapper json)
            throws Exception {
        http.securityMatcher("/api/v1/inventario/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .csrf(c -> c.disable())
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
