package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Lecturas de MOD-03 y MOD-05 para RF-REP-010, en solo lectura y con SQL parametrizado.
 *
 * <p>El espacio del filtro es un id de {@code mod05_areaverde} (el catálogo de espacios del
 * frontend). Las programaciones apuntan a {@code mod02_area_verde}: se cruzan por nombre, como
 * MOD-03 ({@code InterrupcionesCorte}).
 */
@Repository
public class ProgramadoEjecutadoRepository {

    /** Una programación del periodo con su atención vinculada, si la tiene. */
    public record ProgramacionRealizada(
            long id,
            String codigo,
            long tipoServicioId,
            String tipoServicio,
            String ejecutor,
            Long proveedorId,
            String proveedor,
            String cuadrilla,
            String espacio,
            String ventanaMes,
            LocalDate fechaInicioPrevista,
            LocalDate fechaFinPrevista,
            LocalDate fechaInicioReal,
            LocalDate fechaFinReal,
            String estado,
            Long atencionId,
            String atencionCodigo,
            String atencionEstado,
            OffsetDateTime atencionInicio,
            OffsetDateTime atencionFin) {}

    /** Una atención del periodo que ninguna programación referencia. */
    public record AtencionSuelta(
            long id, String codigo, String estado, OffsetDateTime inicio, OffsetDateTime fin) {}

    private static final String ESPACIO_PROGRAMACION =
            """
            EXISTS (SELECT 1 FROM mod03_programacion_area pa
                    JOIN mod02_area_verde i ON i.id = pa.area_verde_id
                    JOIN mod05_areaverde m ON lower(btrim(m.nombre)) = lower(btrim(i.nombre))
                    WHERE pa.programacion_id = p.id AND m.id = :area)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProgramadoEjecutadoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Programaciones cuya ventana prevista se cruza con el periodo. */
    public List<ProgramacionRealizada> programaciones(
            RangoFechas periodo, Long tipoServicioId, Long areaVerdeId) {
        return jdbc.query(
                """
                SELECT p.id, p.codigo, p.tipo_servicio_id, t.nombre AS tipo_servicio, t.ejecutor,
                       p.proveedor_id, pr.razon_social AS proveedor, cu.nombre AS cuadrilla,
                       (SELECT string_agg(i.nombre, ', ' ORDER BY i.nombre) FROM mod03_programacion_area pa
                          JOIN mod02_area_verde i ON i.id = pa.area_verde_id WHERE pa.programacion_id = p.id)
                           AS espacio,
                       p.ventana_mes, p.fecha_inicio_prevista, p.fecha_fin_prevista, p.fecha_inicio_real,
                       p.fecha_fin_real, p.estado, a.id AS atencion_id, a.codigo AS atencion_codigo,
                       a.estado AS atencion_estado, a.fecha_hora_inicio AS atencion_inicio,
                       a.fecha_hora_fin AS atencion_fin
                FROM mod03_programacion_servicio p
                JOIN mod05_tiposervicio t ON t.id = p.tipo_servicio_id
                LEFT JOIN mod05_proveedor pr ON pr.id = p.proveedor_id
                LEFT JOIN mod04_cuadrilla cu ON cu.id = p.cuadrilla_id
                LEFT JOIN mod05_atenciontercerizada a ON a.id = p.atencion_id
                WHERE p.fecha_inicio_prevista <= :hasta AND p.fecha_fin_prevista >= :desde
                  AND (CAST(:tipo AS bigint) IS NULL OR p.tipo_servicio_id = :tipo)
                  AND (CAST(:area AS bigint) IS NULL OR %s)
                ORDER BY p.fecha_inicio_prevista, p.id
                """
                        .formatted(ESPACIO_PROGRAMACION),
                new MapSqlParameterSource()
                        .addValue("desde", periodo.desde())
                        .addValue("hasta", periodo.hasta())
                        .addValue("tipo", tipoServicioId)
                        .addValue("area", areaVerdeId),
                new DataClassRowMapper<>(ProgramacionRealizada.class));
    }

    /** Atenciones con inicio en el periodo que no están vinculadas a ninguna programación. */
    public List<AtencionSuelta> atencionesSinProgramacion(
            RangoFechas periodo, Long tipoServicioId, Long areaVerdeId) {
        return jdbc.query(
                """
                SELECT a.id, a.codigo, a.estado, a.fecha_hora_inicio AS inicio, a.fecha_hora_fin AS fin
                FROM mod05_atenciontercerizada a
                WHERE (a.fecha_hora_inicio AT TIME ZONE 'America/Lima')::date BETWEEN :desde AND :hasta
                  AND NOT EXISTS (SELECT 1 FROM mod03_programacion_servicio p WHERE p.atencion_id = a.id)
                  AND (CAST(:tipo AS bigint) IS NULL OR a.tipo_servicio_id = :tipo)
                  AND (CAST(:area AS bigint) IS NULL OR EXISTS (SELECT 1 FROM mod05_alcanceatencion al
                       WHERE al.atencion_id = a.id AND al.area_verde_id = :area))
                ORDER BY a.fecha_hora_inicio, a.id
                """,
                new MapSqlParameterSource()
                        .addValue("desde", periodo.desde())
                        .addValue("hasta", periodo.hasta())
                        .addValue("tipo", tipoServicioId)
                        .addValue("area", areaVerdeId),
                new DataClassRowMapper<>(AtencionSuelta.class));
    }
}
