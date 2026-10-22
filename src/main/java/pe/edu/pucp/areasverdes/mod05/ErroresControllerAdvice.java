package pe.edu.pucp.areasverdes.mod05;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;

@RestControllerAdvice
public class ErroresControllerAdvice {
    private static final Logger log = LoggerFactory.getLogger(ErroresControllerAdvice.class);
    @ExceptionHandler(ApiError.class)
    ResponseEntity<?> domain(ApiError e) { return ResponseEntity.status(e.status).body(Map.of("codigo", e.code, "detalle", e.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        Map<String,List<String>> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.computeIfAbsent(f.getField(), k -> new ArrayList<>()).add(f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<?> malformed(Exception e) { return domain(ApiError.invalid("Datos incompletos o formato inválido; revisa los campos de la solicitud")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> large(Exception e) { return domain(ApiError.invalid("El archivo o la carga supera el tamaño permitido")); }
    @ExceptionHandler({DataIntegrityViolationException.class, CannotAcquireLockException.class})
    ResponseEntity<?> conflict(Exception e) {
        log.warn("Conflicto de persistencia: {}", e.getClass().getSimpleName());
        return domain(ApiError.conflict("La operación entra en conflicto con los datos actuales. Actualiza e inténtalo nuevamente"));
    }
}
