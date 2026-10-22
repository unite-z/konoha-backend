package pe.edu.pucp.areasverdes.mod06.ia;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod05.ApiError;
import pe.edu.pucp.areasverdes.mod05.ArchivoStorage;
import pe.edu.pucp.areasverdes.mod06.comun.errores.RecursoNoEncontrado;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatosMock;

/**
 * {@link FuenteReportesProveedor} sobre la base y el almacenamiento del MOD-05 ({@code IA_FUENTE_REPORTES=mod05}).
 *
 * <p><b>Solo lectura</b>: únicamente {@code SELECT} sobre {@code mod05_*} y {@link ArchivoStorage#leer}. Nunca escribe.
 *
 * <p>Correspondencia con {@code mock-data/} (esquema de {@code V1__mod05.sql}):
 * <ul>
 *   <li>{@code ReporteProveedor} ← {@code mod05_reporteproveedor} ({@code id}, {@code atencion_id}, ...).</li>
 *   <li>{@code AtencionTercerizada} ← {@code mod05_atenciontercerizada} ({@code id}, {@code tipo_servicio_id}, ...).</li>
 *   <li>{@code ArchivoAsociado} ({@code entidad}, {@code registro_id}, {@code categoria=ReporteProveedor}) ←
 *   {@code mod05_archivoasociado.reporte_id} con {@code categoria='InformeFinal'}. La traducción de la categoría se
 *   hace aquí, no en el servicio.</li>
 *   <li>{@code Archivo} ← {@code mod05_archivo}; los bytes se leen con {@link ArchivoStorage#leer}, que impide salir
 *   de {@code MOD05_ARCHIVOS_DIR}.</li>
 * </ul>
 */
@Component
public class FuenteReportesProveedorMod05 implements FuenteReportesProveedor {

    private static final Logger log = LoggerFactory.getLogger(FuenteReportesProveedorMod05.class);

    /** Categoría de {@code mock-data/} → categoría del MOD-05 ({@code Entradas.CATEGORIA}). */
    private static final Map<String, String> CATEGORIA_MOD05 =
        Map.of(ExtraccionReportesService.CATEGORIA_ARCHIVO_REPORTE, "InformeFinal");

    private final NamedParameterJdbcTemplate jdbc;
    private final ArchivoStorage almacenamiento;

    public FuenteReportesProveedorMod05(NamedParameterJdbcTemplate jdbc, ArchivoStorage almacenamiento) {
        this.jdbc = jdbc;
        this.almacenamiento = almacenamiento;
    }

    @Override
    public Map<String, Object> obtenerReporte(Object reporteId) {
        return uno("""
            SELECT id, atencion_id, revision, fecha_recepcion, resumen
            FROM mod05_reporteproveedor WHERE id = :id""", reporteId);
    }

    @Override
    public Map<String, Object> obtenerAtencion(Object atencionId) {
        return uno("""
            SELECT id, codigo, estado, tipo_servicio_id, contrato_id
            FROM mod05_atenciontercerizada WHERE id = :id""", atencionId);
    }

    @Override
    public List<Map<String, Object>> archivosAsociados(Object reporteId, String categoria) {
        Long id = comoId(reporteId);
        String categoriaMod05 = CATEGORIA_MOD05.get(categoria);
        if (id == null || categoriaMod05 == null) return List.of();
        List<Map<String, Object>> filas = jdbc.queryForList("""
            SELECT id, archivo_id, reporte_id, descripcion
            FROM mod05_archivoasociado WHERE reporte_id = :id AND categoria = :categoria""",
            Map.of("id", id, "categoria", categoriaMod05));
        List<Map<String, Object>> asociaciones = new ArrayList<>();
        for (Map<String, Object> fila : filas) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", normalizar(fila.get("id")));
            a.put("archivo_id", normalizar(fila.get("archivo_id")));
            a.put("entidad", ExtraccionReportesService.ENTIDAD_REPORTE);
            a.put("registro_id", normalizar(fila.get("reporte_id")));
            a.put("categoria", categoria);
            a.put("descripcion", fila.get("descripcion"));
            asociaciones.add(a);
        }
        return asociaciones;
    }

    @Override
    public Map<String, Object> obtenerArchivo(Object archivoId) {
        return uno("""
            SELECT id, nombre_original, tipo_mime, tamano_bytes, ruta_almacenamiento, hash_contenido
            FROM mod05_archivo WHERE id = :id""", archivoId);
    }

    /**
     * Lee los bytes con {@link ArchivoStorage#leer}. Archivo inexistente, ruta fuera del directorio base o error de
     * lectura → {@link RecursoNoEncontrado} con el mismo mensaje que {@code FuenteDatosMock}.
     */
    @Override
    public byte[] leerContenidoArchivo(Map<String, Object> archivo) {
        Object ruta = archivo.get("ruta_almacenamiento");
        if (!(ruta instanceof String clave) || clave.isBlank()) throw noEncontrado(archivo);
        try {
            return almacenamiento.leer(clave).getContentAsByteArray();
        } catch (ApiError | InvalidPathException | IOException e) {
            throw noEncontrado(archivo);
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static RecursoNoEncontrado noEncontrado(Map<String, Object> archivo) {
        log.warn("No se pudo leer del almacenamiento del MOD-05 el archivo {}", archivo.get("id"));
        return new RecursoNoEncontrado(FuenteDatosMock.MENSAJE_ARCHIVO_NO_ENCONTRADO);
    }

    private Map<String, Object> uno(String sql, Object id) {
        Long valor = comoId(id);
        if (valor == null) return null;
        List<Map<String, Object>> filas = jdbc.queryForList(sql, Map.of("id", valor));
        if (filas.isEmpty()) return null;
        Map<String, Object> registro = new LinkedHashMap<>();
        filas.getFirst().forEach((k, v) -> registro.put(k, normalizar(v)));
        return registro;
    }

    /** Id entero positivo, o {@code null} si no lo es (equivale a «no existe»). */
    private static Long comoId(Object id) {
        if (id instanceof Boolean) return null;
        if (id instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && d > 0 ? n.longValue() : null;
        }
        if (id instanceof String s) {
            try {
                long l = Long.parseLong(s.strip());
                return l > 0 ? l : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Mismos tipos que los JSON de {@code mock-data/}: enteros como {@code Long}, fechas como texto ISO. */
    private static Object normalizar(Object valor) {
        if (valor instanceof Integer || valor instanceof Short) return ((Number) valor).longValue();
        if (valor instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (valor instanceof java.sql.Timestamp t) return t.toInstant().toString();
        if (valor instanceof TemporalAccessor t) return t.toString();
        return valor;
    }
}
