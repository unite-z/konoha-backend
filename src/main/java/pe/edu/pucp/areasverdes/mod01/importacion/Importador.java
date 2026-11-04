package pe.edu.pucp.areasverdes.mod01.importacion;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reglas de un tipo de información importable (RF-T-009). {@code clasificar} no escribe nada: se
 * ejecuta al validar y otra vez al confirmar, porque los datos pueden haber cambiado entre medias.
 */
public interface Importador {

    String ALTA = "Alta";
    String ACTUALIZACION = "Actualización";
    String SIN_CAMBIOS = "Sin cambios";
    String REVISAR = "Revisar";
    String ERROR = "Error";

    /**
     * Resultado de una fila.
     *
     * @param codigo clave natural de la fila, para reconocerla en el informe
     * @param registroId registro existente que se actualizaría, o {@code null}
     */
    record Clasificacion(String codigo, String resultado, String detalle, Long registroId) {

        static Clasificacion error(String codigo, String detalle) {
            return new Clasificacion(codigo, ERROR, detalle, null);
        }
    }

    /** Clave de la API: {@code catastro}, {@code inventario}... */
    String tipo();

    /** Permiso de MOD-01 que exige importar este tipo. */
    String permiso();

    /** Cabeceras obligatorias, en minúsculas. */
    List<String> columnas();

    /**
     * Clasifica una fila.
     *
     * @param fila valores por cabecera en minúsculas, sin espacios sobrantes
     * @param vistos claves de las filas anteriores del mismo archivo, para detectar repetidos
     */
    Clasificacion clasificar(Map<String, String> fila, Set<String> vistos);

    /** Aplica una fila clasificada como alta o actualización y registra la auditoría. */
    void aplicar(Map<String, String> fila, Clasificacion clasificacion, long usuarioId);
}
