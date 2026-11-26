package pe.edu.pucp.areasverdes.mod03;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avance del ciclo por sector y servicio (AV-433, RD-15): lo programado y lo ejecutado en corte,
 * riego y poda. El reporte {@code /reportes/programado-ejecutado} no lo da: agrupa por tipo de
 * servicio y por programación, no por sector.
 *
 * <p>La unidad es un área verde asignada a un ciclo ({@code mod03_programacion_area} o la ruta de
 * corte). Está ejecutada si su jardín de la ruta está {@code Cortado} o, en los ciclos sin ruta
 * para esa área, si el ciclo está {@code Completada}. Los ciclos cancelados no cuentan. El sector
 * es el vigente del área.
 */
@Service
public class ResumenSectores {

    static final List<String> SERVICIOS = List.of("corte", "riego", "poda");

    public record Servicio(
            String servicio,
            int programado,
            int ejecutado,
            BigDecimal porcentaje,
            OffsetDateTime actualizadoEn) {}

    public record Sector(long sectorId, String sector, List<Servicio> servicios) {}

    public record Resumen(
            Long cicloId,
            LocalDate desde,
            LocalDate hasta,
            OffsetDateTime generadoEn,
            List<Sector> sectores) {}

    private record Acumulado(int programado, int ejecutado, OffsetDateTime actualizadoEn) {}

    private static final String SECTORES =
            "SELECT id, nombre FROM mod02_sector WHERE activo ORDER BY lower(nombre), id";

    private static final String AVANCE =
            """
            WITH asignadas AS (
                SELECT programacion_id, area_verde_id FROM mod03_programacion_area
                UNION
                SELECT programacion_id, area_verde_id FROM mod03_ruta_corte
            ), unidades AS (
                SELECT h.sector_id,
                    CASE WHEN t.nombre ILIKE '%corte%' THEN 'corte'
                         WHEN t.nombre ILIKE '%riego%' THEN 'riego'
                         WHEN t.nombre ILIKE '%poda%' THEN 'poda' END servicio,
                    (r.estado = 'Cortado'
                        OR (r.id IS NULL AND p.estado = 'Completada')) ejecutada,
                    greatest(p.fecha_modificacion, r.marcado_en) actualizado_en
                FROM asignadas s
                JOIN mod03_programacion_servicio p ON p.id = s.programacion_id
                JOIN mod05_tiposervicio t ON t.id = p.tipo_servicio_id
                JOIN mod02_historial_area_sector h ON h.area_verde_id = s.area_verde_id
                    AND h.fecha_fin IS NULL
                LEFT JOIN mod03_ruta_corte r ON r.programacion_id = s.programacion_id
                    AND r.area_verde_id = s.area_verde_id
                WHERE p.estado <> 'Cancelada'
                  AND (CAST(:ciclo AS bigint) IS NULL OR p.id = :ciclo)
                  AND (CAST(:desde AS date) IS NULL OR p.fecha_fin_prevista >= :desde)
                  AND (CAST(:hasta AS date) IS NULL OR p.fecha_inicio_prevista <= :hasta)
            )
            SELECT sector_id, servicio, count(*) programado,
                   count(*) FILTER (WHERE ejecutada) ejecutado,
                   max(actualizado_en) actualizado_en
            FROM unidades WHERE servicio IS NOT NULL GROUP BY sector_id, servicio
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock reloj;

    public ResumenSectores(NamedParameterJdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Resumen resumir(Long cicloId, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ErrorPlanificacion(
                    400, "periodo-invalido", "«hasta» no puede ser anterior a «desde»");
        }
        if (cicloId != null
                && Boolean.FALSE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS (SELECT 1 FROM mod03_programacion_servicio WHERE id"
                                        + " = :id)",
                                Map.of("id", cicloId),
                                Boolean.class))) {
            throw new ErrorPlanificacion(404, "ciclo-no-encontrado", "El ciclo no existe");
        }
        var filtros = new MapSqlParameterSource();
        filtros.addValue("ciclo", cicloId);
        filtros.addValue("desde", desde);
        filtros.addValue("hasta", hasta);
        Map<String, Acumulado> acumulados = new HashMap<>();
        jdbc.query(
                AVANCE,
                filtros,
                rs -> {
                    acumulados.put(
                            rs.getLong("sector_id") + "/" + rs.getString("servicio"),
                            new Acumulado(
                                    rs.getInt("programado"),
                                    rs.getInt("ejecutado"),
                                    rs.getObject("actualizado_en", OffsetDateTime.class)));
                });
        List<Sector> sectores = new ArrayList<>();
        jdbc.query(
                SECTORES,
                rs -> {
                    long id = rs.getLong("id");
                    List<Servicio> servicios = new ArrayList<>();
                    for (String servicio : SERVICIOS) {
                        var a = acumulados.get(id + "/" + servicio);
                        servicios.add(
                                a == null
                                        ? new Servicio(servicio, 0, 0, BigDecimal.ZERO, null)
                                        : new Servicio(
                                                servicio,
                                                a.programado(),
                                                a.ejecutado(),
                                                porcentaje(a.ejecutado(), a.programado()),
                                                a.actualizadoEn()));
                    }
                    sectores.add(new Sector(id, rs.getString("nombre"), servicios));
                });
        return new Resumen(cicloId, desde, hasta, OffsetDateTime.now(reloj), sectores);
    }

    static BigDecimal porcentaje(int ejecutado, int programado) {
        if (programado == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(ejecutado * 100L)
                .divide(BigDecimal.valueOf(programado), 1, RoundingMode.HALF_UP);
    }
}
