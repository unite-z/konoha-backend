package pe.edu.pucp.areasverdes.mod06.apoyo;

import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.d;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.l;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Datos de prueba de RF-IA-001 (fixture {@code datos_ia} y {@code PROPUESTA_COMPLETA} de {@code test_extraccion_reportes.py}). */
public final class DatosIa {

    private DatosIa() {}

    public static final byte[] PDF = "%PDF-1.4 informe de prueba".getBytes(StandardCharsets.US_ASCII);
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public static Map<String, Object> propuestaCompleta() {
        return d("campo_1", 1250.5,
            "campo_2", l(d("producto", "Producto de ejemplo", "dosis", "1 L/ha")),
            "campo_3", l(d("plaga", "Plaga de ejemplo")),
            "campo_4", l(d("espacio", "Biblioteca Central", "evidencia", "foto-1")),
            "campo_5", "Recorrido por los jardines del sector 2");
    }

    public static Map<String, List<Map<String, Object>>> datosIa() {
        Map<String, List<Map<String, Object>>> datos = new LinkedHashMap<>();
        datos.put("TipoServicio", l(d("id", 1, "nombre", "Corte de césped", "ejecutor", "Tercerizado"),
            d("id", 2, "nombre", "Control fitosanitario", "ejecutor", "Tercerizado")));
        datos.put("AtencionTercerizada", l(d("id", 4, "tipo_servicio_id", 2, "estado", "Reporte adjunto"),
            d("id", 5, "tipo_servicio_id", 1, "estado", "Reporte adjunto"), d("id", 6, "tipo_servicio_id", 2, "estado", "Observado")));
        datos.put("ReporteProveedor", l(d("id", 1, "atencion_id", 4, "revision", 1), d("id", 2, "atencion_id", 5, "revision", 1),
            d("id", 3, "atencion_id", 6, "revision", 1), d("id", 4, "atencion_id", 4, "revision", 2)));
        datos.put("FormatoReporteServicio", l(d("id", 1, "tipo_servicio_id", 2, "version", 1, "vigente", true),
            d("id", 2, "tipo_servicio_id", 2, "version", 0, "vigente", false)));
        List<String[]> campos = List.of(new String[] {"Metrado", "Decimal"}, new String[] {"Productos aplicados", "JSON"},
            new String[] {"Plagas atendidas", "JSON"}, new String[] {"Evidencias por espacio", "JSON"},
            new String[] {"Ruta recorrida", "Texto largo"});
        List<Map<String, Object>> cfr = l();
        for (int i = 0; i < campos.size(); i++) {
            cfr.add(d("id", i + 1, "formato_id", 1, "nombre_campo", campos.get(i)[0], "tipo_dato", campos.get(i)[1], "obligatorio", true));
        }
        datos.put("CampoFormatoReporte", cfr);
        datos.put("Archivo", l(
            d("id", 1, "nombre_original", "informe.pdf", "tipo_mime", "application/pdf", "ruta_almacenamiento", "archivos/informe.pdf"),
            d("id", 2, "nombre_original", "corte.pdf", "tipo_mime", "application/pdf", "ruta_almacenamiento", "archivos/corte.pdf"),
            d("id", 3, "nombre_original", "informe.docx", "tipo_mime", DOCX, "ruta_almacenamiento", "archivos/informe.docx")));
        datos.put("ArchivoAsociado", l(
            d("id", 1, "archivo_id", 1, "entidad", "ReporteProveedor", "registro_id", 1, "categoria", "ReporteProveedor"),
            d("id", 2, "archivo_id", 2, "entidad", "ReporteProveedor", "registro_id", 2, "categoria", "ReporteProveedor"),
            d("id", 3, "archivo_id", 3, "entidad", "ReporteProveedor", "registro_id", 4, "categoria", "ReporteProveedor")));
        return datos;
    }

    /** {@code fuente_ia}: datos y el PDF del informe 1. */
    public static FuenteDatosMemoria fuente() {
        return new FuenteDatosMemoria(datosIa(), Map.of("archivos/informe.pdf", PDF));
    }
}
