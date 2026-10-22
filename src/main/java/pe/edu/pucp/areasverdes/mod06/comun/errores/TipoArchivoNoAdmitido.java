package pe.edu.pucp.areasverdes.mod06.comun.errores;

/**
 * El archivo del informe no es PDF (415).
 */
public class TipoArchivoNoAdmitido extends ErrorDominio {

    public TipoArchivoNoAdmitido(String mensaje) {
        super(mensaje);
    }
}
