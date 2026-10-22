package pe.edu.pucp.areasverdes.mod06.comun.errores;

/**
 * {@code DATA_SOURCE=db} aún no implementado o error al leer los datos (503, CU-MOD06-001 E1: permite reintentar).
 */
public class FuenteDatosNoDisponible extends ErrorDominio {

    public FuenteDatosNoDisponible(String mensaje) {
        super(mensaje);
    }
}
