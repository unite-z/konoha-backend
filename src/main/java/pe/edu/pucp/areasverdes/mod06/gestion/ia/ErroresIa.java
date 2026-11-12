package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;

/**
 * La IA no está configurada, falla o agota el tiempo: 503 {@code {codigo: ia-no-disponible,
 * detalle}}. El frontend ofrece entonces «Registrar manualmente» o «Reintentar». Los demás errores
 * de estas rutas son {@code ErrorGestion} y los traduce su manejador.
 */
@RestControllerAdvice(basePackageClasses = ErroresIa.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ErroresIa {

    @ExceptionHandler(ServicioIANoDisponible.class)
    ResponseEntity<Map<String, Object>> iaNoDisponible(ServicioIANoDisponible e) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", "ia-no-disponible");
        cuerpo.put("detalle", e.getMessage());
        return ResponseEntity.status(503).body(cuerpo);
    }
}
