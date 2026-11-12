package pe.edu.pucp.areasverdes.mod06.gestion;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;

/**
 * Error de dominio de los reportes de gestión del MOD-06 (RF-REP-001, RF-REP-005, RF-REP-007). Se
 * responde como {@code {codigo, detalle}}, con {@code codigo} estable en kebab-case, igual que
 * MOD-03 ({@code ErrorPlanificacion}) mientras se decide ProblemDetail (ADR 0004, propuesto). Si
 * hay errores por campo, se añade {@code errores: {campo: [mensajes]}}, que el frontend muestra
 * junto a cada campo ({@code interpretarError} en {@code services/api/cliente.ts}).
 */
public class ErrorGestion extends RuntimeException {

    private final int estado;
    private final String codigo;
    private final Map<String, List<String>> errores;

    public ErrorGestion(int estado, String codigo, String detalle) {
        this(estado, codigo, detalle, Map.of());
    }

    public ErrorGestion(
            int estado, String codigo, String detalle, Map<String, List<String>> errores) {
        super(detalle);
        this.estado = estado;
        this.codigo = codigo;
        this.errores = Map.copyOf(errores);
    }

    int estado() {
        return estado;
    }

    String codigo() {
        return codigo;
    }

    Map<String, List<String>> errores() {
        return errores;
    }

    /** 403: autenticado, pero sin permiso (mismo texto que MOD-03). */
    public static ErrorGestion sinPermiso() {
        return new ErrorGestion(
                403, "sin-permiso", "Tu usuario no tiene permisos para esta operación");
    }

    /** 400 de validación con un mensaje para un campo (el nombre del campo, en snake_case). */
    public static ErrorGestion validacion(String campo, String mensaje) {
        return new ErrorGestion(
                400, "validacion", "Revisa los datos", Map.of(campo, List.of(mensaje)));
    }

    /**
     * Solo atiende a los controladores de {@code mod06.gestion}, antes que el manejador global de
     * MOD-05.
     */
    @RestControllerAdvice(basePackageClasses = ErrorGestion.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static class Manejador {

        @ExceptionHandler(ErrorGestion.class)
        ResponseEntity<Map<String, Object>> dominio(ErrorGestion e) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("codigo", e.codigo);
            cuerpo.put("detalle", e.getMessage());
            if (!e.errores.isEmpty()) {
                cuerpo.put("errores", e.errores);
            }
            return ResponseEntity.status(e.estado).body(cuerpo);
        }

        /**
         * Falta un parámetro obligatorio de la consulta ({@code desde}, {@code hasta}, {@code
         * mes}).
         */
        @ExceptionHandler(MissingServletRequestParameterException.class)
        ResponseEntity<Map<String, Object>> falta(MissingServletRequestParameterException e) {
            return dominio(validacion(e.getParameterName(), mensajeFalta(e.getParameterName())));
        }

        /** Un parámetro no tiene el tipo esperado (fecha, número o booleano). */
        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        ResponseEntity<Map<String, Object>> tipo(MethodArgumentTypeMismatchException e) {
            return dominio(validacion(e.getName(), mensajeTipo(e.getRequiredType())));
        }
    }

    static String mensajeFalta(String parametro) {
        return switch (parametro) {
            case "desde" -> Periodo.MENSAJE_FALTA_DESDE;
            case "hasta" -> Periodo.MENSAJE_FALTA_HASTA;
            case "mes" -> "Indica el mes del reporte (mes), con el formato AAAA-MM";
            default -> "Indica el parámetro " + parametro;
        };
    }

    static String mensajeTipo(Class<?> tipo) {
        if (tipo == LocalDate.class) {
            return Periodo.MENSAJE_FORMATO_FECHA;
        }
        if (tipo == Long.class || tipo == long.class) {
            return "Usa un número entero";
        }
        if (tipo == Boolean.class || tipo == boolean.class) {
            return "Usa true o false";
        }
        return "Revisa el formato de este valor";
    }
}
