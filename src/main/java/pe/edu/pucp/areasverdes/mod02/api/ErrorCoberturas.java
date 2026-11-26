package pe.edu.pucp.areasverdes.mod02.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

public class ErrorCoberturas extends RuntimeException {

    private final int estado;
    private final String codigo;
    private final transient Object impacto;

    public ErrorCoberturas(int estado, String codigo, String detalle) {
        this(estado, codigo, detalle, null);
    }

    /** Error que además lleva el impacto calculado, para que el cliente lo enseñe y confirme. */
    public ErrorCoberturas(int estado, String codigo, String detalle, Object impacto) {
        super(detalle);
        this.estado = estado;
        this.codigo = codigo;
        this.impacto = impacto;
    }

    @RestControllerAdvice(basePackageClasses = ErrorCoberturas.class)
    static class Manejador {
        @ExceptionHandler(ErrorCoberturas.class)
        ResponseEntity<Map<String, Object>> dominio(ErrorCoberturas error) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("codigo", error.codigo);
            cuerpo.put("detalle", error.getMessage());
            if (error.impacto != null) {
                cuerpo.put("impacto", error.impacto);
            }
            return ResponseEntity.status(error.estado).body(cuerpo);
        }
    }
}
