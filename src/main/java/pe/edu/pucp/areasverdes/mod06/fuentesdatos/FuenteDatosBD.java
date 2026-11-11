package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.errores.FuenteDatosNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.DefinicionesIndicador;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Indicador;

/**
 * Fuente de datos real ({@code DATA_SOURCE=db}, el valor por defecto): lee las tablas de los
 * módulos dueños y devuelve cada entidad con los mismos atributos que los JSON de {@code
 * mock-data/}, para que los reportes RF-REP-002, RF-REP-004 y RF-REP-006 no cambien. Solo lectura y
 * SQL fijo, sin parámetros del usuario.
 *
 * <p>Correspondencias que no son directas (documentadas en el PR de AV-288/289/290):
 *
 * <ul>
 *   <li><b>CicloCorte</b>: no tiene tabla. Cada programación con ruta de corte ({@code
 *       mod03_ruta_corte}) es un ciclo. El fin de corte es el último jardín marcado cuando ya no
 *       queda ninguno pendiente; la limpieza sale de {@code mod03_limpieza_corte}. Estado:
 *       Programada → «Programado», EnCurso → «En corte» o «En limpieza», Completada → «Cerrado».
 *       Las canceladas no cuentan. RutaCorte es la ruta copiada de ese ciclo.
 *   <li><b>Área verde</b>: el espacio es {@code mod02_area_verde}, con su sector vigente ({@code
 *       mod02_historial_area_sector}). MOD-04 y MOD-05 aún apuntan a {@code mod05_areaverde}; se
 *       cruza por nombre, como MOD-03 ({@code InterrupcionesCorte}), y si hay ejemplar manda su
 *       área de inventario (V67). Sin correspondencia, el área queda nula y el evento va a «sin
 *       espacio».
 *   <li><b>Capataz del sector</b>: {@code mod02_historial_capataz_sector} guarda la cuenta de
 *       MOD-01; se traduce al Personal de MOD-04 vinculado a esa cuenta (V69).
 *   <li><b>AplicacionFitosanitaria</b> y <b>AplicacionObjetivo</b>: no existen como tablas; MOD-05
 *       registra las aplicaciones como atenciones con {@code atencion_especial_id}, que ya cuentan
 *       como AtencionTercerizada.
 *   <li><b>DefinicionIndicador</b>: catálogo en código ({@link DefinicionesIndicador}).
 * </ul>
 *
 * <p>Las escrituras de MOD-06 no pasan por aquí: SugerenciaIA y ReporteGenerado tienen su propio
 * repositorio.
 */
public class FuenteDatosBD implements FuenteDatos {

    public static final String MENSAJE_ENTIDAD_SIN_TABLA =
            "Esta información aún no está disponible en la base de datos";

    /** Área de inventario (MOD-02) de un área de MOD-05, por nombre. */
    private static final String AREA_INVENTARIO =
            """
            (SELECT min(i.id) FROM mod02_area_verde i JOIN mod05_areaverde m ON lower(btrim(m.nombre)) = lower(btrim(i.nombre))
             WHERE m.id = %s)
            """;

    private static final Map<String, String> CONSULTAS = new LinkedHashMap<>();

    static {
        // --- MOD-03: planificación y corte
        CONSULTAS.put(
                "CicloCorte",
                """
                SELECT p.id, p.id AS programacion_id, p.id AS ruta_id, p.proveedor_id, p.atencion_id,
                       CASE p.estado WHEN 'Programada' THEN 'Programado' WHEN 'Completada' THEN 'Cerrado'
                            ELSE CASE WHEN l.inicio IS NULL THEN 'En corte' ELSE 'En limpieza' END END AS estado,
                       p.fecha_inicio_real,
                       CASE WHEN r.pendientes = 0 THEN (r.ultimo AT TIME ZONE 'America/Lima')::date END AS fecha_fin_corte,
                       l.inicio AS fecha_inicio_limpieza,
                       CASE WHEN p.estado = 'Completada' THEN l.fin END AS fecha_fin_limpieza
                FROM mod03_programacion_servicio p
                JOIN (SELECT programacion_id, count(*) FILTER (WHERE estado = 'Pendiente') AS pendientes,
                             max(marcado_en) AS ultimo
                      FROM mod03_ruta_corte GROUP BY programacion_id) r ON r.programacion_id = p.id
                LEFT JOIN (SELECT programacion_id, min(fecha) AS inicio, max(fecha) AS fin
                           FROM mod03_limpieza_corte GROUP BY programacion_id) l ON l.programacion_id = p.id
                WHERE p.estado <> 'Cancelada'
                """);
        CONSULTAS.put(
                "RutaCorte",
                """
                SELECT p.id, COALESCE('Ruta ' || p.codigo, 'Ruta del ciclo ' || p.id) AS nombre, true AS vigente
                FROM mod03_programacion_servicio p
                WHERE EXISTS (SELECT 1 FROM mod03_ruta_corte r WHERE r.programacion_id = p.id)
                """);
        CONSULTAS.put(
                "ProgramacionServicio",
                """
                SELECT id, codigo, tipo_servicio_id, proveedor_id, cuadrilla_id, estacion, fecha_inicio_prevista,
                       fecha_fin_prevista, fecha_inicio_real, fecha_fin_real, ventana_mes, estado, atencion_id
                FROM mod03_programacion_servicio
                """);
        CONSULTAS.put(
                "FrecuenciaServicio",
                """
                SELECT id, tipo_servicio_id, estacion, frecuencia_teorica_dias, frecuencia_practica_dias,
                       frecuencia_maxima_dias
                FROM mod03_frecuencia_servicio
                """);
        CONSULTAS.put(
                "DuracionServicio",
                """
                SELECT id, tipo_servicio_id, duracion_maxima_dias, dias_limpieza FROM mod03_duracion_servicio
                """);
        CONSULTAS.put(
                "TipoServicio",
                """
                SELECT id, nombre, ejecutor, es_periodico, es_ruidoso, es_riesgo, activo FROM mod05_tiposervicio
                """);

        // --- MOD-04: operación de campo
        CONSULTAS.put(
                "ParteDiario",
                "SELECT id, fecha, cuadrilla_id, capataz_id, estado FROM mod04_parte_diario");
        CONSULTAS.put("Cuadrilla", "SELECT id, nombre, capataz_id, activa FROM mod04_cuadrilla");
        CONSULTAS.put(
                "TipoActividad", "SELECT id, clase_id, nombre, activo FROM mod04_tipo_actividad");
        CONSULTAS.put("ClaseActividad", "SELECT id, nombre, activo FROM mod04_clase_actividad");
        // Sin documento ni teléfono (RNF-008): los reportes solo muestran el nombre.
        CONSULTAS.put(
                "Personal",
                "SELECT id, nombres, apellidos, tipo_personal, activo FROM mod04_personal");
        CONSULTAS.put(
                "AsignacionCuadrilla",
                """
                SELECT id, operario_id, cuadrilla_id, fecha_inicio, fecha_fin FROM mod04_asignacion_cuadrilla
                """);
        CONSULTAS.put(
                "Actividad",
                """
                SELECT a.id, a.parte_id, a.tipo_actividad_id,
                       COALESCE(e.area_verde_inventario_id, %s) AS area_verde_id,
                       a.ejemplar_id, a.fecha_hora_actividad, a.fecha_solicitud, a.incidencia_id, a.encargo_id,
                       a.cantidad_solicitada, a.cantidad_ejecutada, a.tiempo_minutos, a.superficie_m2
                FROM mod04_actividad a
                LEFT JOIN mod02_ejemplar e ON e.id = a.ejemplar_id
                """
                        .formatted(AREA_INVENTARIO.formatted("a.area_verde_id")));
        CONSULTAS.put(
                "ParticipacionActividad",
                "SELECT actividad_id, operario_id FROM mod04_participacion_actividad");

        // --- MOD-02: inventario
        CONSULTAS.put(
                "AreaVerde",
                """
                SELECT a.id, a.nombre, s.sector_id, a.tipo_uso_id, a.tipo_cobertura_id, a.cuartel_id, a.activo
                FROM mod02_area_verde a
                LEFT JOIN mod02_historial_area_sector s ON s.area_verde_id = a.id AND s.fecha_fin IS NULL
                """);
        CONSULTAS.put(
                "SectorMantenimiento",
                "SELECT id, nombre, atencion_a_demanda, activo FROM mod02_sector");
        CONSULTAS.put("Cuartel", "SELECT id, numero, nombre, estado FROM mod02_cuartel");
        CONSULTAS.put(
                "AsignacionSector",
                """
                SELECT h.id, h.sector_id, p.id AS capataz_id, (h.fecha_inicio AT TIME ZONE 'America/Lima')::date AS fecha_inicio,
                       (h.fecha_fin AT TIME ZONE 'America/Lima')::date AS fecha_fin
                FROM mod02_historial_capataz_sector h
                LEFT JOIN LATERAL (SELECT min(id) AS id FROM mod04_personal WHERE usuario_id = h.capataz_id) p ON true
                """);
        CONSULTAS.put(
                "Ejemplar",
                "SELECT id, codigo, area_verde_inventario_id AS area_verde_id FROM mod02_ejemplar");
        CONSULTAS.put(
                "IntervencionArbolado",
                """
                SELECT i.id, i.ejemplar_id, i.categoria, i.campana_id,
                       (SELECT min(a.id) FROM mod04_actividad a WHERE a.ejemplar_id = i.ejemplar_id
                           AND (a.fecha_hora_actividad AT TIME ZONE 'America/Lima')::date = i.fecha_ejecucion) AS actividad_id,
                       i.atencion_id, i.incidencia_id, i.tipo_intervencion_id, i.fecha_ejecucion,
                       i.cantidad_solicitada, i.cantidad_ejecutada, i.prioridad
                FROM mod02_intervencion_arbolado i
                WHERE i.activo
                """);

        // --- MOD-05: tercerizados
        CONSULTAS.put("Proveedor", "SELECT id, razon_social, ruc, activo FROM mod05_proveedor");
        CONSULTAS.put(
                "AtencionTercerizada",
                """
                SELECT id, codigo, contrato_id, tipo_servicio_id, clase, origen, fecha_hora_inicio, fecha_hora_fin,
                       estado, es_riesgo, atencion_especial_id
                FROM mod05_atenciontercerizada
                """);
        CONSULTAS.put(
                "AlcanceAtencion",
                """
                SELECT c.id, c.atencion_id, %s AS area_verde_id, c.ejemplar_id, c.metrado, c.unidad_medida_id
                FROM mod05_alcanceatencion c
                """
                        .formatted(AREA_INVENTARIO.formatted("c.area_verde_id")));
    }

    /** Entidades que no tienen tabla porque sus datos ya llegan por otra (ver la cabecera). */
    private static final List<String> SIN_REGISTROS =
            List.of("AplicacionFitosanitaria", "AplicacionObjetivo");

    private final NamedParameterJdbcTemplate jdbc;

    public FuenteDatosBD(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Map<String, Object>> listar(String entidad, Map<String, Object> igualA) {
        FuenteDatos.validarEntidad(entidad);
        List<Map<String, Object>> registros;
        if (entidad.equals("DefinicionIndicador")) {
            registros = definiciones();
        } else if (SIN_REGISTROS.contains(entidad)) {
            registros = List.of();
        } else if (CONSULTAS.containsKey(entidad)) {
            registros = jdbc.query(CONSULTAS.get(entidad), Map.of(), FuenteDatosBD::fila);
        } else {
            throw new FuenteDatosNoDisponible(MENSAJE_ENTIDAD_SIN_TABLA + " (" + entidad + ")");
        }
        return FuenteDatos.filtrar(registros, igualA);
    }

    @Override
    public Map<String, Object> obtener(String entidad, Object id) {
        for (Map<String, Object> r : listar(entidad, Map.of())) {
            if (r.get("id") != null
                    && r.get("id").equals(id instanceof Number n ? n.longValue() : id)) return r;
        }
        return null;
    }

    @Override
    public Map<String, Object> crear(String entidad, Map<String, Object> datos) {
        throw new EscrituraNoPermitida(entidad);
    }

    @Override
    public Map<String, Object> actualizar(String entidad, Object id, Map<String, Object> cambios) {
        throw new EscrituraNoPermitida(entidad);
    }

    @Override
    public byte[] leerContenidoArchivo(Map<String, Object> archivo) {
        throw new FuenteDatosNoDisponible(MENSAJE_ENTIDAD_SIN_TABLA + " (Archivo)");
    }

    // ------------------------------------------------------------------ utilidades

    private static List<Map<String, Object>> definiciones() {
        List<Map<String, Object>> r = new ArrayList<>();
        long id = 0;
        for (Indicador i : DefinicionesIndicador.CATALOGO) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", ++id);
            d.put("codigo", i.codigo());
            d.put("nombre", i.nombre());
            d.put("descripcion_calculo", i.descripcionCalculo());
            d.put("meta", i.meta());
            d.put("unidad", i.unidad());
            d.put("activo", true);
            r.add(d);
        }
        return r;
    }

    /**
     * Una fila con los tipos que daría el JSON simulado: enteros como {@code Long}, decimales como
     * {@code Double}, días como {@code AAAA-MM-DD} e instantes como ISO-8601 en America/Lima.
     */
    static Map<String, Object> fila(ResultSet rs, int n) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> r = new LinkedHashMap<>();
        for (int c = 1; c <= meta.getColumnCount(); c++) {
            Object valor = rs.getObject(c);
            if (valor instanceof Integer i) valor = i.longValue();
            else if (valor instanceof Short s) valor = s.longValue();
            else if (valor instanceof BigDecimal d) valor = d.doubleValue();
            else if (valor instanceof Date d) valor = d.toLocalDate().toString();
            else if (valor instanceof Timestamp) {
                OffsetDateTime t = rs.getObject(c, OffsetDateTime.class);
                valor = Fechas.isoformat(t.atZoneSameInstant(Fechas.LIMA));
            }
            r.put(meta.getColumnLabel(c), valor);
        }
        return r;
    }
}
