package pe.edu.pucp.areasverdes.mod06.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * El MOD-06 no usa Spring Security (sus permisos van en {@code permisos.Permisos}). Esta cadena se evalúa antes
 * que la del MOD-05 ({@code mod05.Seguridad}, HTTP Basic) y deja pasar sin cambios las rutas del MOD-06 y la
 * documentación, sin cabeceras ni sesión añadidas, para que respondan igual que antes de la fusión.
 */
@Configuration
public class SeguridadMod06 {

    @Bean
    @Order(1)
    SecurityFilterChain cadenaSeguridadMod06(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1", "/api/v1/**", "/api/schema", "/api/schema/**", "/api/docs", "/api/docs/**",
                "/api/swagger-ui", "/api/swagger-ui/**", "/swagger-ui.html", "/swagger-ui/**")
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .headers(h -> h.disable())
            .logout(l -> l.disable());
        return http.build();
    }
}