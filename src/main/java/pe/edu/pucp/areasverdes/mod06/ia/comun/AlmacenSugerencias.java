package pe.edu.pucp.areasverdes.mod06.ia.comun;

import java.util.List;
import java.util.Map;

/**
 * Persistencia de las SugerenciaIA (Modelo BD 4.6.3), común a todas las funciones de IA de MOD-06.
 *
 * <p>Los registros son mapas con las claves del serializador {@code SugerenciaIA}: {@code id},
 * {@code tipo}, {@code entidad}, {@code registro_id}, {@code archivo_id}, {@code contenido} (mapa),
 * {@code estado}, {@code validador_id}, {@code fecha_validacion}, {@code fecha_creacion}, {@code
 * usuario_creacion_id}, {@code fecha_modificacion} y {@code usuario_modificacion_id}. Los instantes
 * van como texto ISO-8601 con desfase. La implementación JDBC añade {@code usuario_creacion_nombre}
 * y {@code validador_nombre}.
 *
 * <p>Toda alta y toda revisión dejan su fila en la auditoría común (RF-T-008) dentro de la misma
 * transacción.
 */
public interface AlmacenSugerencias {

    /** Guarda una sugerencia nueva en {@code Propuesta} y la devuelve tal como quedó guardada. */
    Map<String, Object> crear(Map<String, Object> datos);

    /** La sugerencia con ese id, o {@code null}. */
    Map<String, Object> obtener(long id);

    /**
     * Como {@link #obtener}, pero bloquea la fila hasta el final de la transacción: dos revisiones
     * simultáneas de la misma propuesta no pueden pisarse (la segunda ve el estado ya cambiado y
     * recibe 409).
     */
    Map<String, Object> obtenerParaRevisar(long id);

    /**
     * Sugerencias de un tipo, de la más reciente a la más antigua. Los filtros {@code null} no se
     * aplican; {@code entidad} y {@code registroId} se aplican juntos.
     */
    List<Map<String, Object>> listar(String tipo, String estado, String entidad, Long registroId);

    /**
     * Guarda la revisión ({@code estado}, {@code validador_id}, {@code fecha_validacion}, {@code
     * contenido}, {@code fecha_modificacion}, {@code usuario_modificacion_id}) y la devuelve
     * actualizada. {@code motivo} va a la auditoría (motivo de rechazo, si lo hay).
     */
    Map<String, Object> guardarRevision(long id, Map<String, Object> cambios, String motivo);
}
