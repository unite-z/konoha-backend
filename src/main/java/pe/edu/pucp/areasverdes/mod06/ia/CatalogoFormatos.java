package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.List;
import java.util.Map;

/**
 * Formato estándar del informe del proveedor (RF-SER-009, entidades FormatoReporteServicio y
 * CampoFormatoReporte de MOD-05). MOD-06 solo lo lee para construir el esquema que se pide a la IA
 * (RF-IA-001).
 *
 * <p>Formato: {@code {id, tipo_servicio_id, version, vigente}}. Campo: {@code {id, formato_id,
 * nombre_campo, tipo_dato, obligatorio, subcampos}}, donde {@code subcampos} es {@code null} o una
 * lista de {@code {nombre, tipo_dato, descripcion}}.
 */
public interface CatalogoFormatos {

    /** El formato vigente de mayor versión del tipo de servicio, o {@code null} si no tiene. */
    Map<String, Object> formatoVigente(Object tipoServicioId);

    /** Los campos del formato, en su orden. */
    List<Map<String, Object>> campos(Object formatoId);
}
