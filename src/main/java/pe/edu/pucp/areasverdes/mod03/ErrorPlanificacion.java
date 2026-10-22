package pe.edu.pucp.areasverdes.mod03;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Error de dominio de MOD-03. Se responde como {@code {codigo, detalle}}, con {@code codigo}
 * estable en kebab-case, como MOD-05 mientras se decide ProblemDetail (ADR 0004).
 */
public class ErrorPlanificacion extends RuntimeException {

    private final int estado;
    private final String codigo;

    public ErrorPlanificacion(int estado, String codigo, String detalle) {
        super(detalle);
        this.estado = estado;
        this.codigo = codigo;
    }

    int estado() {
        return estado;
    }

    String codigo() {
        return codigo;
    }

    static ErrorPlanificacion sinPermiso() {
        return new ErrorPlanificacion(
                403, "sin-permiso", "Tu usuario no tiene permisos para esta operación");
    }

    /** Solo atiende a los controladores de MOD-03, antes que el manejador global de MOD-05. */
    @RestControllerAdvice(basePackageClasses = ErrorPlanificacion.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class Manejador {

        @ExceptionHandler(ErrorPlanificacion.class)
        ResponseEntity<Map<String, Object>> dominio(ErrorPlanificacion e) {
            return ResponseEntity.status(e.estado)
                    .body(Map.of("codigo", e.codigo, "detalle", e.getMessage()));
        }
    }
}
