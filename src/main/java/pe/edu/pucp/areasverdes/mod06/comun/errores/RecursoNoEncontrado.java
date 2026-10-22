package pe.edu.pucp.areasverdes.mod06.comun.errores;

/**
 * Identificador inexistente: reporte, archivo o sugerencia (404).
 */
public class RecursoNoEncontrado extends ErrorDominio {

    public RecursoNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
