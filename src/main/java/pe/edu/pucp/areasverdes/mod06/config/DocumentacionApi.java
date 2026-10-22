package pe.edu.pucp.areasverdes.mod06.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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

        private final ObjectProvider<OpenApiWebMvcResource> esquemaOpenApi;

        RutasConBarra(ObjectProvider<OpenApiWebMvcResource> esquemaOpenApi) {
            this.esquemaOpenApi = esquemaOpenApi;
        }

        /**
         * Ruta de esquema que tenía el MOD-05 antes de la fusión; devuelve el mismo JSON que {@code /api/schema}.
         * Se genera aquí, sin forward, para que también funcione en MockMvc (que no ejecuta los forward).
         */
        @GetMapping(value = {"/api/tercerizados/schema", "/api/tercerizados/schema/"}, produces = MediaType.APPLICATION_JSON_VALUE)
        @ResponseBody
        byte[] esquemaMod05(HttpServletRequest peticion, Locale idioma) throws JsonProcessingException {
            // La URL de la petición como apiDocsUrl: así la URL del servidor del esquema es la misma que en /api/schema.
            return esquemaOpenApi.getObject().openapiJson(peticion, peticion.getRequestURI(), idioma);
        }

        /** Ruta de documentación que tenía el MOD-05 antes de la fusión; ahora muestra la documentación común. */
        @GetMapping({"/api/tercerizados/docs", "/api/tercerizados/docs/"})
        String docsMod05() {
            return "forward:/api/docs";
        }
    }
}
