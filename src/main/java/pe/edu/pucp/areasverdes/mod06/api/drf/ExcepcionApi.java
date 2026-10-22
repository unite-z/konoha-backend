package pe.edu.pucp.areasverdes.mod06.api.drf;

/**
 * {@code APIException} de DRF con cuerpo {@code {"detail": "<mensaje>"}} (403, 404, 405, 406, 415 y errores de
 * lectura del cuerpo). Los mensajes son los de DRF en español ({@code LANGUAGE_CODE = "es"}).
 */
public class ExcepcionApi extends RuntimeException {

    public static final String NO_ENCONTRADO = "No encontrado.";
    public static final String NO_ACEPTABLE = "No se ha podido satisfacer la solicitud de cabecera de Accept.";
    public static final String PAGINA_INVALIDA = "Página inválida.";

    private final int estado;
    private final String detalle;

    public ExcepcionApi(int estado, String detalle) {
        super(detalle, null, false, false);
        this.estado = estado;
        this.detalle = detalle;
    }

    public int estado() {
        return estado;
    }

    public String detalle() {
        return detalle;
    }

    public static ExcepcionApi metodoNoPermitido(String metodo) {
        return new ExcepcionApi(405, "Método \"" + metodo + "\" no permitido.");
    }

    public static ExcepcionApi tipoNoSoportado(String tipo) {
        return new ExcepcionApi(415, "Tipo de medio \"" + tipo + "\" incompatible en la solicitud.");
    }

    public static ExcepcionApi errorLectura(String detalle) {
        return new ExcepcionApi(400, detalle);
    }
}
