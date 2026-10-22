package pe.edu.pucp.areasverdes.mod06.comun.errores;

/**
 * Falta un dato configurable necesario, por ejemplo el formato estándar vigente (422).
 */
public class ConfiguracionFaltante extends ErrorDominio {

    public ConfiguracionFaltante(String mensaje) {
        super(mensaje);
    }
}
