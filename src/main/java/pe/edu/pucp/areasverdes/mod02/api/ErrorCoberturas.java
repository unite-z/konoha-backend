package pe.edu.pucp.areasverdes.mod02.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

public class ErrorCoberturas extends RuntimeException {

    private final int estado;
    private final String codigo;

    public ErrorCoberturas(int estado, String codigo, String detalle) {
        super(detalle);
        this.estado = estado;
        this.codigo = codigo;
    }

    @RestControllerAdvice(basePackageClasses = ErrorCoberturas.class)
    static class Manejador {
        @ExceptionHandler(ErrorCoberturas.class)
        ResponseEntity<Map<String, String>> dominio(ErrorCoberturas error) {
            return ResponseEntity.status(error.estado)
                    .body(Map.of("codigo", error.codigo, "detalle", error.getMessage()));
        }
    }
}
