package pe.edu.pucp.areasverdes.comun;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deja {@code /api/health} abierto sin credenciales para el healthcheck de Docker y el smoke test
 * del despliegue. Va antes que las cadenas de los módulos. Solo responde {@code {"status":"UP"}},
 * sin detalles.
 */
@Configuration
public class SeguridadSalud {

    @Bean
    @Order(0)
    SecurityFilterChain cadenaSeguridadSalud(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/health", "/api/health/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable());
        return http.build();
    }
}
