package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.List;
import java.util.Map;

/**
 * Puerto de lectura de RF-IA-001: el reporte del proveedor, su atención, su informe y los bytes del informe.
 *
 * <p>Solo lectura. Devuelve registros con los mismos nombres de atributo que {@code mock-data/} (entidades
 * {@code ReporteProveedor}, {@code AtencionTercerizada}, {@code ArchivoAsociado} y {@code Archivo}), para que
 * {@link ExtraccionReportesService} no dependa de dónde se leen.
 *
 * <ul>
 *   <li>{@link FuenteReportesProveedorMod05}: base y almacenamiento del MOD-05 ({@code IA_FUENTE_REPORTES=mod05}).</li>
 *   <li>{@link FuenteReportesProveedorMock}: {@code FuenteDatos} del MOD-06, como antes ({@code IA_FUENTE_REPORTES=mock}).</li>
 * </ul>
 */
public interface FuenteReportesProveedor {

    /** {@code ReporteProveedor} por id, o {@code null} si no existe. */
    Map<String, Object> obtenerReporte(Object reporteId);

    /** {@code AtencionTercerizada} por id, o {@code null} si no existe. */
    Map<String, Object> obtenerAtencion(Object atencionId);

    /**
     * {@code ArchivoAsociado} del reporte con la categoría indicada (valor de {@code mock-data/}, por ejemplo
     * {@code ReporteProveedor}), con {@code entidad=ReporteProveedor} y {@code registro_id=reporteId}.
     */
    List<Map<String, Object>> archivosAsociados(Object reporteId, String categoria);

    /** {@code Archivo} (metadatos) por id, o {@code null} si no existe. */
    Map<String, Object> obtenerArchivo(Object archivoId);

    /** Bytes del archivo. {@code RecursoNoEncontrado} si no está en el almacenamiento. */
    byte[] leerContenidoArchivo(Map<String, Object> archivo);
}
