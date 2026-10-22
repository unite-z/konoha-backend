package pe.edu.pucp.areasverdes.mod06.config;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Documentación de la API ({@code SPECTACULAR_SETTINGS} y rutas {@code api/schema/} y {@code api/docs/} de
 * {@code config/urls.py}). springdoc publica el esquema en {@code /api/schema} y Swagger UI en {@code /api/docs};
 * las mismas rutas con barra final también funcionan.
 */
@Configuration
public class DocumentacionApi {

    @Bean
    OpenAPI openApiMod06() {
        return new OpenAPI().info(new Info()
            .title("API MOD-06 Reportes, indicadores e IA")
            .description("API del MOD-06: RF-REP-002, RF-REP-004, RF-REP-006 y RF-IA-001. Las consultas no modifican "
                + "registros operativos. Las propuestas de IA requieren validación humana (RN-IA-001)")
            .version("0.1.0"));
    }

    /** Rutas con barra final, como en el Django. */
    @Hidden
    @Controller
    static class RutasConBarra {

        @GetMapping("/api/schema/")
        String esquema() {
            return "forward:/api/schema";
        }

        @GetMapping("/api/docs/")
        String docs() {
            return "forward:/api/docs";
        }
    }
}
