package pe.edu.pucp.areasverdes.mod05;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.SecurityFilterChain;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@Configuration
@SecurityScheme(name="basicAuth", type=SecuritySchemeType.HTTP, scheme="basic")
public class Seguridad {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetails(Mod05Repository repo) {
        return username -> repo.usuario(username).orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        // Solo Authorization Basic explícito, sin cookies ni sesión. HTTPS fuera de localhost.
        http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/tercerizados/docs", "/api/tercerizados/schema/**", "/swagger-ui/**", "/error").permitAll()
                .anyRequest().authenticated())
            .httpBasic(b -> b.authenticationEntryPoint((req,res,e) -> {
                res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                mapper.writeValue(res.getOutputStream(), Map.of("detail", "Credenciales inválidas o ausentes"));
            }));
        return http.build();
    }
}
