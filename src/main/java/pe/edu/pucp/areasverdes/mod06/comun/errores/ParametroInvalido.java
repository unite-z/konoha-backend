package pe.edu.pucp.areasverdes.mod06.comun.errores;

/**
 * Parámetro o cuerpo de la petición no válido (400).
 */
public class ParametroInvalido extends ErrorDominio {

    public ParametroInvalido(String mensaje) {
        super(mensaje);
    }
}
