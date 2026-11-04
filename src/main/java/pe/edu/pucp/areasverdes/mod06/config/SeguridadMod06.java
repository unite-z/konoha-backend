package pe.edu.pucp.areasverdes.mod06.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deja pasar sin cambios la documentación y la respuesta 404 de las rutas desconocidas de {@code
 * /api/v1/**}, sin cabeceras ni sesión añadidas. Los reportes y la IA del MOD-06 los protege antes
 * {@code SeguridadGestion} (orden 0). {@code comun/PermisosGlobalesApiTest} falla si esta cadena
 * deja abierto un endpoint con datos.
 */
@Configuration
public class SeguridadMod06 {

    @Bean
    @Order(1)
    SecurityFilterChain cadenaSeguridadMod06(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/api/v1",
                        "/api/v1/**",
                        "/api/schema",
                        "/api/schema/**",
                        "/api/docs",
                        "/api/docs/**",
                        "/api/swagger-ui",
                        "/api/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable())
                .headers(h -> h.disable())
                .logout(l -> l.disable());
        return http.build();
    }
}
