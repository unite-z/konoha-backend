package pe.edu.pucp.areasverdes.mod05;

public class ApiError extends RuntimeException {
    final int status;
    final String code;
    public ApiError(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    public static ApiError invalid(String message) { return new ApiError(400, "ErrorDominio", message); }
    public static ApiError missing(String message) { return new ApiError(404, "NoEncontrado", message); }
    public static ApiError conflict(String message) { return new ApiError(409, "Conflicto", message); }
    public static ApiError storage() { return new ApiError(503, "AlmacenamientoNoDisponible", "No se pudo acceder al archivo. Reintenta la operación"); }
}
