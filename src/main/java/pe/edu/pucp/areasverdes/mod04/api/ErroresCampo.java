package pe.edu.pucp.areasverdes.mod04.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Mantiene las claves públicas snake_case al validar los DTO camelCase de MOD-04. */
@RestControllerAdvice(assignableTypes = OperacionCampoController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErroresCampo {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validar(MethodArgumentNotValidException error) {
        Map<String, List<String>> campos = new LinkedHashMap<>();
        var nombres = new PropertyNamingStrategies.SnakeCaseStrategy();
        error.getBindingResult()
                .getFieldErrors()
                .forEach(
                        campo ->
                                campos.computeIfAbsent(
                                                nombres.translate(campo.getField()),
                                                clave -> new ArrayList<>())
                                        .add(campo.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "codigo",
                                "datos-invalidos",
                                "detalle",
                                "Revisa los campos indicados",
                                "errores",
                                campos));
    }
}
