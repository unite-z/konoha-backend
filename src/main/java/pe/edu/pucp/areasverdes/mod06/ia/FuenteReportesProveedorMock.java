package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;

/**
 * {@link FuenteReportesProveedor} sobre la {@link FuenteDatos} del MOD-06: el comportamiento anterior de RF-IA-001,
 * movido sin cambios ({@code IA_FUENTE_REPORTES=mock} y pruebas existentes).
 */
public class FuenteReportesProveedorMock implements FuenteReportesProveedor {

    private final FuenteDatos fuente;

    public FuenteReportesProveedorMock(FuenteDatos fuente) {
        this.fuente = fuente;
    }

    @Override
    public Map<String, Object> obtenerReporte(Object reporteId) {
        return fuente.obtener("ReporteProveedor", reporteId);
    }

    @Override
    public Map<String, Object> obtenerAtencion(Object atencionId) {
        return fuente.obtener("AtencionTercerizada", atencionId);
    }

    @Override
    public List<Map<String, Object>> archivosAsociados(Object reporteId, String categoria) {
        return fuente.listar("ArchivoAsociado",
            "entidad", ExtraccionReportesService.ENTIDAD_REPORTE, "registro_id", reporteId, "categoria", categoria);
    }

    @Override
    public Map<String, Object> obtenerArchivo(Object archivoId) {
        return fuente.obtener("Archivo", archivoId);
    }

    @Override
    public byte[] leerContenidoArchivo(Map<String, Object> archivo) {
        return fuente.leerContenidoArchivo(archivo);
    }
}
