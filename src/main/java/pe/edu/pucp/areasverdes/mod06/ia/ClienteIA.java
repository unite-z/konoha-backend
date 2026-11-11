package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.List;
import java.util.Map;

/**
 * Cliente del dominio para el componente de IA desacoplado (ADR-001, figura 1): envía un PDF y
 * recibe un JSON con el esquema pedido. Implementación real: {@link ClienteGemini}; en las pruebas,
 * un doble.
 */
public interface ClienteIA {

    /** Proveedor de IA (se conserva en {@code SugerenciaIA.contenido.ia.proveedor}). */
    String proveedor();

    /** Nombre del modelo configurado (se conserva en {@code SugerenciaIA.contenido.ia.modelo}). */
    String modelo();

    /**
     * Envía el PDF con la instrucción del sistema y pide salida JSON con {@code esquema}. Cualquier
     * fallo → {@code ServicioIANoDisponible}.
     */
    Map<String, Object> extraerJson(byte[] pdf, Map<String, Object> esquema, String instruccion);

    /** Una imagen enviada a la IA (JPG, PNG o WEBP). */
    record Imagen(byte[] datos, String tipoMime) {}

    /**
     * Envía {@code datos} (texto o JSON ya preparado por el backend) y, si las hay, imágenes, con
     * la instrucción del sistema; pide salida JSON con {@code esquema}. Lo usan las funciones de IA
     * de S12 (RF-IA-002 a RF-IA-006 y RN-IA-002). Cualquier fallo → {@code ServicioIANoDisponible}.
     */
    Map<String, Object> generarJson(
            String datos, List<Imagen> imagenes, Map<String, Object> esquema, String instruccion);
}
