package pe.edu.pucp.areasverdes.mod06.comun.errores;

/**
 * Validar o rechazar una SugerenciaIA que no está en {@code Propuesta} (409, RI-32).
 */
public class TransicionNoPermitida extends ErrorDominio {

    public TransicionNoPermitida(String mensaje) {
        super(mensaje);
    }
}
