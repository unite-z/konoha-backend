package pe.edu.pucp.areasverdes.mod01.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import pe.edu.pucp.areasverdes.mod01.servicios.ErrorCore;

/** Traduce los errores de los controladores de MOD-01, antes que el manejador global de MOD-05. */
@RestControllerAdvice(basePackageClasses = ManejadorErroresCore.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManejadorErroresCore {

    @ExceptionHandler(ErrorCore.class)
    ResponseEntity<Map<String, Object>> dominio(ErrorCore e) {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("codigo", e.codigo());
        cuerpo.put("detalle", e.getMessage());
        cuerpo.putAll(e.datos());
        return ResponseEntity.status(e.estado()).body(cuerpo);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, List<String>> errores = new LinkedHashMap<>();
        for (var campo : e.getFieldErrors()) {
            String nombre = campo.getField().replaceAll("([A-Z])", "_$1").toLowerCase();
            errores.computeIfAbsent(nombre, k -> new ArrayList<>()).add(campo.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "codigo",
                                "validacion",
                                "detalle",
                                "Revisa los datos",
                                "errores",
                                errores));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,
        MissingRequestHeaderException.class
    })
    ResponseEntity<Map<String, Object>> peticionInvalida(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("codigo", "peticion-invalida", "detalle", "La petición no es válida"));
    }
}
