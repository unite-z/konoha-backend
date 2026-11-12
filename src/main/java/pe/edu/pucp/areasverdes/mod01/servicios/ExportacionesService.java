package pe.edu.pucp.areasverdes.mod01.servicios;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportación de listados a Excel (RF-T-011, HU-MOD01-013): actividades, atenciones, incidencias y
 * ejemplares. Solo lee, y cada tipo exige el permiso de consulta de su entidad. Las columnas son
 * fijas y no incluyen datos personales ni importes (RNF-008). Si la tabla del módulo dueño aún no
 * está en la base, responde 409: no se inventan datos.
 *
 * <p>El SQL es constante; la petición solo elige la clave de {@link #LISTADOS}.
 */
@Service
public class ExportacionesService {

    /** Por encima de este número de filas, Excel deja de ser una lista manejable y se rechaza. */
    static final int MAXIMO_FILAS = 50_000;

    public record Archivo(String nombre, byte[] contenido) {}

    /** Columna de la consulta, por su alias, con la cabecera que se escribe en el archivo. */
    record Columna(String alias, String cabecera) {}

    record Listado(String tabla, String permiso, String hoja, List<Columna> columnas, String sql) {}

    static final Map<String, Listado> LISTADOS =
            Map.of(
                    "actividades",
                    new Listado(
                            "mod04_actividad",
                            "CAM.PARTE.CONSULTAR",
                            "Actividades",
                            List.of(
                                    new Columna("id", "Id"),
                                    new Columna("fecha", "Fecha y hora"),
                                    new Columna("tipo", "Tipo de actividad"),
                                    new Columna("area", "Área verde"),
                                    new Columna("cuadrilla", "Cuadrilla"),
                                    new Columna("estado", "Estado del parte"),
                                    new Columna("estado_actividad", "Estado de la actividad"),
                                    new Columna("observaciones", "Observaciones")),
                            """
                            SELECT a.id,
                                to_char(a.fecha_hora_actividad AT TIME ZONE 'America/Lima',
                                    'YYYY-MM-DD HH24:MI') fecha,
                                t.nombre tipo, v.nombre area, c.nombre cuadrilla, p.estado,
                                a.estado_actividad,
                                a.observaciones, p.cuadrilla_id
                            FROM mod04_actividad a
                            JOIN mod04_parte_diario p ON p.id = a.parte_id
                            JOIN mod04_tipo_actividad t ON t.id = a.tipo_actividad_id
                            JOIN mod05_areaverde v ON v.id = a.area_verde_id
                            JOIN mod04_cuadrilla c ON c.id = p.cuadrilla_id
                            ORDER BY a.fecha_hora_actividad DESC, a.id DESC
                            """),
                    "atenciones",
                    new Listado(
                            "mod05_atenciontercerizada",
                            "SER.ATENCION.REGISTRAR",
                            "Atenciones",
                            List.of(
                                    new Columna("codigo", "Código"),
                                    new Columna("clase", "Clase"),
                                    new Columna("servicio", "Tipo de servicio"),
                                    new Columna("origen", "Origen"),
                                    new Columna("estado", "Estado"),
                                    new Columna("inicio", "Inicio"),
                                    new Columna("fin", "Fin"),
                                    new Columna("descripcion", "Descripción")),
                            """
                            SELECT a.codigo, a.clase, s.nombre servicio, a.origen, a.estado,
                                to_char(a.fecha_hora_inicio AT TIME ZONE 'America/Lima',
                                    'YYYY-MM-DD HH24:MI') inicio,
                                to_char(a.fecha_hora_fin AT TIME ZONE 'America/Lima',
                                    'YYYY-MM-DD HH24:MI') fin,
                                a.descripcion
                            FROM mod05_atenciontercerizada a
                            JOIN mod05_tiposervicio s ON s.id = a.tipo_servicio_id
                            ORDER BY a.fecha_creacion DESC, a.id DESC
                            """),
                    "incidencias",
                    new Listado(
                            "mod03_incidencia",
                            "PLA.INCIDENCIA.REGISTRAR",
                            "Incidencias",
                            List.of(
                                    new Columna("codigo", "Código"),
                                    new Columna("tipo", "Tipo"),
                                    new Columna("espacio", "Espacio"),
                                    new Columna("estado", "Estado"),
                                    new Columna("severidad", "Severidad"),
                                    new Columna("canal", "Canal"),
                                    new Columna("registro", "Registro"),
                                    new Columna("cierre", "Cierre")),
                            // Sin el reportante ni su contacto: son datos personales (RNF-008).
                            """
                            SELECT i.codigo, t.nombre tipo, a.nombre espacio, i.estado, i.severidad,
                                i.canal,
                                to_char(i.fecha_registro AT TIME ZONE 'America/Lima',
                                    'YYYY-MM-DD HH24:MI') registro,
                                to_char(i.fecha_cierre AT TIME ZONE 'America/Lima',
                                    'YYYY-MM-DD HH24:MI') cierre
                            FROM mod03_incidencia i
                            JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                            JOIN mod02_area_verde a ON a.id = i.area_verde_id
                            ORDER BY i.fecha_registro DESC, i.id DESC
                            """),
                    "ejemplares",
                    new Listado(
                            "mod02_ejemplar",
                            "INV.INVENTARIO.CONSULTAR",
                            "Ejemplares",
                            List.of(
                                    new Columna("codigo", "Código"),
                                    new Columna("codigo_catastro", "Código de catastro"),
                                    new Columna("referencia_catastro", "Referencia de catastro"),
                                    new Columna("especie", "Especie"),
                                    new Columna("tipo_vegetacion", "Tipo de vegetación"),
                                    new Columna("area", "Área verde"),
                                    new Columna("ubicacion", "Ubicación"),
                                    new Columna("cantidad", "Cantidad")),
                            """
                            SELECT e.codigo, e.codigo_catastro, e.referencia_catastro,
                                coalesce(s.nombre_comun, s.nombre_cientifico) especie,
                                e.tipo_vegetacion, coalesce(v.nombre, historica.nombre) area, e.ubicacion, e.cantidad
                            FROM mod02_ejemplar e
                            LEFT JOIN mod02_especie_vegetal s ON s.id = e.especie_id
                            LEFT JOIN mod02_area_verde v ON v.id = e.area_verde_inventario_id
                            LEFT JOIN mod05_areaverde historica ON historica.id = e.area_verde_id
                            ORDER BY e.codigo
                            """));

    private final NamedParameterJdbcTemplate jdbc;
    private final Autorizacion autorizacion;

    public ExportacionesService(NamedParameterJdbcTemplate jdbc, Autorizacion autorizacion) {
        this.jdbc = jdbc;
        this.autorizacion = autorizacion;
    }

    @Transactional(readOnly = true)
    public Archivo exportar(String tipo, UsuarioSesion usuario) {
        Listado listado = LISTADOS.get(tipo);
        if (listado == null) {
            throw ErrorCore.invalido(
                    "tipo-desconocido",
                    "El tipo debe ser actividades, atenciones, incidencias o ejemplares");
        }
        autorizacion.exigir(usuario, listado.permiso());
        boolean disponible =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT to_regclass(:t) IS NOT NULL",
                                Map.of("t", listado.tabla()),
                                Boolean.class));
        if (!disponible) {
            throw ErrorCore.conflicto(
                    "entidad-no-disponible", "El módulo de " + tipo + " aún no está disponible");
        }
        return new Archivo(tipo + ".xlsx", libro(listado, usuario));
    }

    private byte[] libro(Listado listado, UsuarioSesion usuario) {
        var salida = new ByteArrayOutputStream();
        try (Workbook libro = new Workbook(salida, "Areas Verdes", "1.0")) {
            Worksheet hoja = libro.newWorksheet(listado.hoja());
            List<Columna> columnas = listado.columnas();
            for (int c = 0; c < columnas.size(); c++) {
                hoja.value(0, c, columnas.get(c).cabecera());
                hoja.style(0, c).bold().set();
            }
            String consulta = listado.sql();
            Map<String, ?> parametros = Map.of();
            if ("CAM.PARTE.CONSULTAR".equals(listado.permiso())) {
                var alcance = autorizacion.alcanceCuadrillas(usuario);
                if (alcance.isPresent()) {
                    consulta =
                            "SELECT * FROM ("
                                    + consulta
                                    + ") actividades WHERE cuadrilla_id IN (:cuadrillas)";
                    // NamedParameterJdbcTemplate no admite una colección vacía en IN.
                    parametros =
                            Map.of(
                                    "cuadrillas",
                                    alcance.get().isEmpty() ? List.of(-1L) : alcance.get());
                }
            }
            jdbc.query(
                    consulta, parametros, (ResultSet fila) -> escribirFila(hoja, fila, columnas));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }

    /** Cada celda se escribe como valor, nunca como fórmula, aunque el texto empiece por «=». */
    private static void escribirFila(Worksheet hoja, ResultSet fila, List<Columna> columnas)
            throws SQLException {
        int numero = fila.getRow();
        if (numero > MAXIMO_FILAS) {
            throw ErrorCore.conflicto(
                    "volumen-excedido", "El listado supera las " + MAXIMO_FILAS + " filas");
        }
        for (int c = 0; c < columnas.size(); c++) {
            Object valor = fila.getObject(columnas.get(c).alias());
            if (valor instanceof Number n) {
                hoja.value(numero, c, n);
            } else if (valor != null) {
                hoja.value(numero, c, valor.toString());
            }
        }
    }
}
