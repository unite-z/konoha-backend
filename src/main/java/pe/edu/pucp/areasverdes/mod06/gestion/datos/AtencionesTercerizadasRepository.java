package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * Atenciones tercerizadas de MOD-05 para los reportes de gestión (RF-REP-001 y RF-REP-007).
 *
 * <p><b>Solo lectura: nunca escribe.</b> Únicamente {@code SELECT} sobre {@code mod05_*}, como
 * {@code mod06.ia.FuenteReportesProveedorMod05}. SQL siempre parametrizado: el único texto variable
 * son las constantes de esta clase.
 *
 * <p>Reglas de RF-REP-006 ({@link AtencionesEspacioSectorService}), reutilizadas tal cual:
 *
 * <ul>
 *   <li>cuenta una atención si su estado está en {@link
 *       AtencionesEspacioSectorService#ESTADOS_ATENCION_EJECUTADA};
 *   <li>su fecha es la fecha local de inicio o, si no la tiene, de fin; sin ninguna, no cuenta;
 *   <li>su espacio es el área del alcance o el área del ejemplar del alcance;
 *   <li>cuenta una vez por par (atención, espacio); sin alcance, un único par sin espacio.
 * </ul>
 */
@Repository
public class AtencionesTercerizadasRepository {

    /** Pares (atención realizada, espacio) del periodo, con los filtros opcionales. */
    static final String PARES_REALIZADOS =
            """
            WITH pares AS (
                SELECT DISTINCT
                       a.id AS atencion_id,
                       a.codigo,
                       ts.nombre AS tipo_servicio,
                       a.estado,
                       (coalesce(a.fecha_hora_inicio, a.fecha_hora_fin)
                           AT TIME ZONE 'America/Lima')::date AS fecha,
                       coalesce(al.area_verde_id, e.area_verde_id) AS area_verde_id
                FROM mod05_atenciontercerizada a
                JOIN mod05_tiposervicio ts ON ts.id = a.tipo_servicio_id
                LEFT JOIN mod05_alcanceatencion al ON al.atencion_id = a.id
                LEFT JOIN mod05_ejemplar e ON e.id = al.ejemplar_id
                WHERE a.estado IN (:ejecutados)
                  AND coalesce(a.fecha_hora_inicio, a.fecha_hora_fin) IS NOT NULL
                  AND (coalesce(a.fecha_hora_inicio, a.fecha_hora_fin)
                      AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                  AND (CAST(:tipoServicio AS bigint) IS NULL OR a.tipo_servicio_id = :tipoServicio)
                  AND (CAST(:estado AS varchar) IS NULL OR a.estado = :estado)
            )
            SELECT p.atencion_id, p.codigo, p.tipo_servicio, p.estado, p.fecha, p.area_verde_id,
                   av.nombre AS espacio
            FROM pares p
            LEFT JOIN mod05_areaverde av ON av.id = p.area_verde_id
            WHERE (CAST(:area AS bigint) IS NULL OR p.area_verde_id = :area)
            """;

    /**
     * Todas las atenciones del periodo, en cualquier estado.
     *
     * <p>SUPUESTO (pendiente de confirmar): una atención pertenece al periodo por la fecha local de
     * inicio o, si aún no ha empezado, por la de creación (pregunta 5 de la fase 0). La
     * documentación no define fórmula ni meta (pendiente 6 de los mockups); revisar con la
     * contraparte.
     */
    static final String ATENCIONES_DEL_PERIODO =
            """
            FROM mod05_atenciontercerizada a
            JOIN mod05_tiposervicio ts ON ts.id = a.tipo_servicio_id
            JOIN mod05_contrato c ON c.id = a.contrato_id
            JOIN mod05_proveedor pr ON pr.id = c.proveedor_id
            -- SUPUESTO (pendiente de confirmar): fecha de inicio o, si no la tiene, de creación.
            WHERE (coalesce(a.fecha_hora_inicio, a.fecha_creacion)
                  AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
              AND (CAST(:tipoServicio AS bigint) IS NULL OR a.tipo_servicio_id = :tipoServicio)
              AND (CAST(:estado AS varchar) IS NULL OR a.estado = :estado)
              AND (CAST(:area AS bigint) IS NULL OR EXISTS (
                    SELECT 1 FROM mod05_alcanceatencion al
                    LEFT JOIN mod05_ejemplar e ON e.id = al.ejemplar_id
                    WHERE al.atencion_id = a.id
                      AND coalesce(al.area_verde_id, e.area_verde_id) = :area))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AtencionesTercerizadasRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --- Atenciones por espacio (pares realizados) -----------------------------------------------

    /** Total de pares (atención, espacio) por espacio. */
    public List<ConteoEspacio> contarPorEspacio(FiltroAtenciones filtro) {
        return jdbc.query(
                "SELECT t.area_verde_id, t.espacio, count(*) AS total FROM ("
                        + PARES_REALIZADOS
                        + ") t GROUP BY t.area_verde_id, t.espacio",
                parametros(filtro),
                new DataClassRowMapper<>(ConteoEspacio.class));
    }

    public long contarPares(FiltroAtenciones filtro) {
        return contar("SELECT count(*) FROM (" + PARES_REALIZADOS + ") t", parametros(filtro));
    }

    /** Página del detalle: de la fecha más reciente a la más antigua. */
    public List<ParAtencionEspacio> pares(
            FiltroAtenciones filtro, int limite, long desplazamiento) {
        return jdbc.query(
                PARES_REALIZADOS
                        + " ORDER BY p.fecha DESC, p.codigo, av.nombre NULLS LAST, p.area_verde_id"
                        + " LIMIT :limite OFFSET :desplazamiento",
                pagina(parametros(filtro), limite, desplazamiento),
                new DataClassRowMapper<>(ParAtencionEspacio.class));
    }

    // --- Cumplimiento de servicios tercerizados (todas las del periodo)
    // ----------------------------

    public List<ConteoEstado> contarPorEstado(FiltroAtenciones filtro) {
        return jdbc.query(
                "SELECT a.estado, count(*) AS total "
                        + ATENCIONES_DEL_PERIODO
                        + " GROUP BY a.estado",
                parametros(filtro),
                new DataClassRowMapper<>(ConteoEstado.class));
    }

    public long contarAtenciones(FiltroAtenciones filtro) {
        return contar("SELECT count(*) " + ATENCIONES_DEL_PERIODO, parametros(filtro));
    }

    /** Página del detalle: de la fecha más reciente a la más antigua. */
    public List<AtencionDelPeriodo> atenciones(
            FiltroAtenciones filtro, int limite, long desplazamiento) {
        return jdbc.query(
                """
                SELECT a.id AS atencion_id, a.codigo, ts.nombre AS tipo_servicio, a.estado,
                       (coalesce(a.fecha_hora_inicio, a.fecha_creacion)
                           AT TIME ZONE 'America/Lima')::date AS fecha,
                       pr.razon_social AS proveedor
                """
                        + ATENCIONES_DEL_PERIODO
                        + " ORDER BY fecha DESC, a.codigo, a.id"
                        + " LIMIT :limite OFFSET :desplazamiento",
                pagina(parametros(filtro), limite, desplazamiento),
                new DataClassRowMapper<>(AtencionDelPeriodo.class));
    }

    // --- Mapa de atenciones (RF-REP-007)
    // ----------------------------------------------------------

    /**
     * Atenciones realizadas del periodo, una fila por destino del alcance (espacio o ejemplar); sin
     * alcance, una fila con ambos nulos. Sin {@code DISTINCT} por espacio: cada ejemplar es un
     * marcador propio en el mapa. {@code esRiesgo} nulo = sin filtro.
     */
    public List<AtencionRealizada> atencionesRealizadas(FiltroAtenciones filtro, Boolean esRiesgo) {
        return jdbc.query(
                """
                SELECT a.id AS atencion_id, a.codigo, ts.id AS tipo_servicio_id,
                       ts.nombre AS tipo_servicio, a.estado, a.es_riesgo,
                       (coalesce(a.fecha_hora_inicio, a.fecha_hora_fin)
                           AT TIME ZONE 'America/Lima')::date AS fecha,
                       pr.razon_social AS proveedor,
                       coalesce(al.area_verde_id, e.area_verde_id) AS area_verde_id,
                       av.nombre AS espacio,
                       al.ejemplar_id,
                       e.codigo AS ejemplar_codigo,
                       (SELECT count(*) FROM mod05_archivoasociado aa
                          JOIN mod05_reporteproveedor r ON r.id = aa.reporte_id
                         WHERE r.atencion_id = a.id) AS archivos_asociados
                FROM mod05_atenciontercerizada a
                JOIN mod05_tiposervicio ts ON ts.id = a.tipo_servicio_id
                JOIN mod05_contrato c ON c.id = a.contrato_id
                JOIN mod05_proveedor pr ON pr.id = c.proveedor_id
                LEFT JOIN mod05_alcanceatencion al ON al.atencion_id = a.id
                LEFT JOIN mod05_ejemplar e ON e.id = al.ejemplar_id
                LEFT JOIN mod05_areaverde av ON av.id = coalesce(al.area_verde_id, e.area_verde_id)
                WHERE a.estado IN (:ejecutados)
                  AND coalesce(a.fecha_hora_inicio, a.fecha_hora_fin) IS NOT NULL
                  AND (coalesce(a.fecha_hora_inicio, a.fecha_hora_fin)
                      AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                  AND (CAST(:tipoServicio AS bigint) IS NULL OR a.tipo_servicio_id = :tipoServicio)
                  AND (CAST(:estado AS varchar) IS NULL OR a.estado = :estado)
                  AND (CAST(:area AS bigint) IS NULL
                      OR coalesce(al.area_verde_id, e.area_verde_id) = :area)
                  AND (CAST(:riesgo AS boolean) IS NULL OR a.es_riesgo = :riesgo)
                ORDER BY fecha DESC, a.codigo, av.nombre NULLS LAST, e.codigo NULLS FIRST
                """,
                parametros(filtro).addValue("riesgo", esRiesgo),
                new DataClassRowMapper<>(AtencionRealizada.class));
    }

    // ---------------------------------------------------------------------------------------------

    private long contar(String sql, MapSqlParameterSource parametros) {
        Long total = jdbc.queryForObject(sql, parametros, Long.class);
        return total == null ? 0 : total;
    }

    private static MapSqlParameterSource parametros(FiltroAtenciones filtro) {
        return new MapSqlParameterSource()
                .addValue("ejecutados", AtencionesEspacioSectorService.ESTADOS_ATENCION_EJECUTADA)
                .addValue("desde", filtro.periodo().desde())
                .addValue("hasta", filtro.periodo().hasta())
                .addValue("area", filtro.areaVerdeId())
                .addValue("tipoServicio", filtro.tipoServicioId())
                .addValue("estado", filtro.estado());
    }

    private static MapSqlParameterSource pagina(
            MapSqlParameterSource parametros, int limite, long desplazamiento) {
        return parametros.addValue("limite", limite).addValue("desplazamiento", desplazamiento);
    }
}
