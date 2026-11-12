package pe.edu.pucp.areasverdes.mod01.servicios;

import java.util.Map;

/**
 * Error de dominio de MOD-01. Se responde como {@code {codigo, detalle}}, con {@code codigo}
 * estable en kebab-case, igual que MOD-03 y MOD-05 mientras se decide ProblemDetail (ADR 0004).
 * {@code datos} añade campos al cuerpo cuando la interfaz los necesita (por ejemplo, la cuenta que
 * se puede reactivar).
 */
public class ErrorCore extends RuntimeException {

    /** Mensaje de CU-MOD01-006 2a: no revela qué dato ni qué regla falla. */
    public static final String SIN_PERMISO = "No tiene permiso para esta acción";

    private final int estado;
    private final String codigo;
    private final Map<String, Object> datos;

    public ErrorCore(int estado, String codigo, String detalle) {
        this(estado, codigo, detalle, Map.of());
    }

    public ErrorCore(int estado, String codigo, String detalle, Map<String, Object> datos) {
        super(detalle);
        this.estado = estado;
        this.codigo = codigo;
        this.datos = Map.copyOf(datos);
    }

    public int estado() {
        return estado;
    }

    public String codigo() {
        return codigo;
    }

    public Map<String, Object> datos() {
        return datos;
    }

    public static ErrorCore invalido(String codigo, String detalle) {
        return new ErrorCore(400, codigo, detalle);
    }

    public static ErrorCore noAutenticado(String detalle) {
        return new ErrorCore(401, "no-autenticado", detalle);
    }

    public static ErrorCore sinPermiso() {
        return new ErrorCore(403, "sin-permiso", SIN_PERMISO);
    }

    public static ErrorCore noEncontrado(String detalle) {
        return new ErrorCore(404, "no-encontrado", detalle);
    }

    public static ErrorCore conflicto(String codigo, String detalle) {
        return new ErrorCore(409, codigo, detalle);
    }
}
