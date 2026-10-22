package pe.edu.pucp.areasverdes.mod06.comun.errores;

/**
 * Base de las excepciones de dominio del MOD-06. {@code mensaje} es el texto que verá el usuario.
 *
 * <p>Los servicios lanzan estas excepciones sin saber nada de HTTP. {@code api/ManejadorExcepciones} las traduce
 * al código HTTP correspondiente con el cuerpo {@code {"codigo": "<NombreExcepcion>", "detalle": "<mensaje>"}}.
 * Los mensajes siguen la Guía de Estilo Web (sección 7): dicen qué pasó y qué hacer.
 */
public class ErrorDominio extends RuntimeException {

    private final String mensaje;

    public ErrorDominio(String mensaje) {
        super(mensaje);
        this.mensaje = mensaje;
    }

    public String mensaje() {
        return mensaje;
    }
}
