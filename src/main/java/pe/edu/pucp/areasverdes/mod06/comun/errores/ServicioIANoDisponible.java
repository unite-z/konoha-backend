package pe.edu.pucp.areasverdes.mod06.comun.errores;

/**
 * Falta la configuración de Gemini, o la llamada falló, agotó el tiempo o devolvió una respuesta inválida (503).
 *
 * <p>HU-MOD06-010: el error se informa y se permite continuar con el registro manual.
 */
public class ServicioIANoDisponible extends ErrorDominio {

    public ServicioIANoDisponible(String mensaje) {
        super(mensaje);
    }
}
