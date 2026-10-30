package pe.edu.pucp.areasverdes.mod06.gestion.config;

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

/**
 * Rutas de los reportes de gestión del MOD-06 (RF-REP-001, RF-REP-005 y RF-REP-007): HTTP Basic con
 * los usuarios de MOD-05 hasta que exista la autenticación de MOD-01, como MOD-03. Tiene que ir
 * antes que la cadena de MOD-06 (orden 1), que deja abierto el resto de {@code /api/v1/**};
 * comparte el orden 0 con las de {@code /api/health} y {@code /api/v1/planificacion/**}, que no se
 * solapan. Sin credenciales responde 401. Los permisos por rol los decide cada endpoint ({@code
 * AccesoGestion}). No cambia nada de las demás rutas del MOD-06 (AGENTS.md: «Seguridad abierta: no
 * empeorar»).
 */
@Configuration
public class SeguridadGestion {

    @Bean
    @Order(0)
    SecurityFilterChain cadenaSeguridadGestion(HttpSecurity http, ObjectMapper json)
            throws Exception {
        http.securityMatcher(
                        "/api/v1/reportes/panel",
                        "/api/v1/reportes/panel/**",
                        "/api/v1/reportes/material-vegetal",
                        "/api/v1/reportes/material-vegetal/**",
                        "/api/v1/reportes/mapa-atenciones",
                        "/api/v1/reportes/mapa-atenciones/**")
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
