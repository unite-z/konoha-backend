package pe.edu.pucp.areasverdes.mod03;

import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertas de planificación guardadas con su estado (AV-255; solo dentro de la aplicación, ADR
 * 0003):
 *
 * <ul>
 *   <li>{@code FrecuenciaProxima}: el servicio llega a su frecuencia máxima sin ciclo programado
 *       (RF-PLA-008); {@code dias} es lo que falta, negativo si ya pasó;
 *   <li>{@code DuracionProxima}: al ciclo en curso le queda un día o menos de su duración máxima
 *       con limpieza (CU-007 3a), sin incumplimiento;
 *   <li>{@code DuracionExcedida}: el ciclo en curso la superó (RF-PLA-009); {@code dias} es el
 *       exceso.
 * </ul>
 *
 * <p>El proceso periódico ({@link #sincronizar()}, cada hora, y también al consultar) abre las que
 * aparecen, actualiza los días de las abiertas y da por atendidas las que ya no se cumplen.
 */
@Service
public class AlertasPlanificacion {

    /** Una alerta abierta. {@code proveedor}: «Personal propio» o la razón social. */
    public record Alerta(
            long id,
            String tipo,
            long servicioId,
            String servicio,
            Long programacionId,
            int dias,
            String mensaje,
            String proveedor,
            Integer maximoDias) {}

    public record AlertaAtendida(
            long id,
            String estado,
            String motivo,
            String atendidaPor,
            OffsetDateTime fechaAtencion) {}

    private record Calculada(
            String tipo,
            long servicioId,
            String servicio,
            Long programacionId,
            String referencia,
            int dias,
            String proveedor,
            Integer maximo) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock reloj;
    private final int umbralDias;

    public AlertasPlanificacion(
            NamedParameterJdbcTemplate jdbc,
            Clock reloj,
            @Value("${mod03.alertas.umbral-dias:7}") int umbralDias) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.umbralDias = umbralDias;
    }

    /** Proceso periódico: cada hora, en punto y cuarto (hora de Lima). */
    @Scheduled(cron = "0 15 * * * *", zone = "America/Lima")
    @Transactional
    public void sincronizar() {
        LocalDate hoy = LocalDate.now(reloj);
        var datos =
                Map.<String, Object>of(
                        "hoy", hoy, "estacion", Programador.estacion(hoy), "umbral", umbralDias);
        List<Calculada> calculadas = new ArrayList<>();
        calculadas.addAll(jdbc.query(FRECUENCIA, datos, new DataClassRowMapper<>(Calculada.class)));
        calculadas.addAll(jdbc.query(DURACION, datos, new DataClassRowMapper<>(Calculada.class)));
        List<Long> vigentes = new ArrayList<>(List.of(0L));
        for (Calculada c : calculadas) {
            vigentes.addAll(
                    jdbc.queryForList(
                            """
                            INSERT INTO mod03_alerta(tipo, tipo_servicio_id, programacion_id, referencia,
                                dias, mensaje)
                            VALUES (:tipo, :servicio, :programacion, :referencia, :dias, :mensaje)
                            ON CONFLICT (tipo, tipo_servicio_id, referencia)
                            DO UPDATE SET dias = EXCLUDED.dias, mensaje = EXCLUDED.mensaje
                            WHERE mod03_alerta.estado = 'Abierta'
                            RETURNING id
                            """,
                            new MapSqlParameterSource()
                                    .addValue("tipo", c.tipo())
                                    .addValue("servicio", c.servicioId())
                                    .addValue("programacion", c.programacionId())
                                    .addValue("referencia", c.referencia())
                                    .addValue("dias", c.dias())
                                    .addValue("mensaje", mensaje(c)),
                            Long.class));
        }
        // Lo que ya no se cumple (el ciclo terminó o se programó) queda atendido por el sistema.
        jdbc.update(
                """
                UPDATE mod03_alerta SET estado = 'Atendida', fecha_atencion = now()
                WHERE estado = 'Abierta' AND id NOT IN (:vigentes)
                """,
                Map.of("vigentes", vigentes));
    }

    /** Las alertas abiertas, recién sincronizadas: la frecuencia primero y luego la duración. */
    public List<Alerta> abiertas() {
        sincronizar();
        return jdbc.query(
                """
                SELECT a.id, a.tipo, a.tipo_servicio_id servicio_id, t.nombre servicio,
                    a.programacion_id, a.dias, a.mensaje, c.proveedor, c.maximo maximo_dias
                FROM mod03_alerta a JOIN mod05_tiposervicio t ON t.id = a.tipo_servicio_id
                LEFT JOIN LATERAL (%s) c ON true
                WHERE a.estado = 'Abierta'
                ORDER BY a.tipo <> 'FrecuenciaProxima', a.dias, a.id
                """
                        .formatted(PROVEEDOR_Y_MAXIMO),
                Map.of("estacion", Programador.estacion(LocalDate.now(reloj))),
                new DataClassRowMapper<>(Alerta.class));
    }

    /** CU-007 paso 6: el motivo o la acción de una alerta abierta, que queda atendida. */
    @Transactional
    public AlertaAtendida registrarMotivo(long id, String motivo, UsuarioPlanificacion usuario) {
        Acceso.gestionar(usuario);
        if (motivo == null || motivo.isBlank()) {
            throw new ErrorPlanificacion(400, "motivo-requerido", "Escribe el motivo o la acción");
        }
        int filas =
                jdbc.update(
                        """
                        UPDATE mod03_alerta SET estado = 'Atendida', motivo = :motivo,
                            atendida_por_id = :usuario, fecha_atencion = now()
                        WHERE id = :id AND estado = 'Abierta'
                        """,
                        Map.of("id", id, "motivo", motivo.trim(), "usuario", usuario.id()));
        if (filas == 0) {
            Integer existe =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM mod03_alerta WHERE id = :id",
                            Map.of("id", id),
                            Integer.class);
            throw existe == 0
                    ? new ErrorPlanificacion(404, "alerta-no-encontrada", "No existe esa alerta")
                    : new ErrorPlanificacion(409, "alerta-atendida", "Esa alerta ya está atendida");
        }
        return jdbc.queryForObject(
                """
                SELECT a.id, a.estado, a.motivo, u.nombre atendida_por, a.fecha_atencion
                FROM mod03_alerta a JOIN mod05_usuario u ON u.id = a.atendida_por_id
                WHERE a.id = :id
                """,
                Map.of("id", id),
                (rs, n) ->
                        new AlertaAtendida(
                                rs.getLong("id"),
                                rs.getString("estado"),
                                rs.getString("motivo"),
                                rs.getString("atendida_por"),
                                rs.getObject("fecha_atencion", OffsetDateTime.class)
                                        .atZoneSameInstant(LIMA)
                                        .toOffsetDateTime()));
    }

    /**
     * CU-006 paso 7: al programar un ciclo del servicio, su alerta de frecuencia queda atendida.
     */
    void atenderFrecuencia(long servicioId, UsuarioPlanificacion usuario) {
        jdbc.update(
                """
                UPDATE mod03_alerta SET estado = 'Atendida', motivo = 'Ciclo programado',
                    atendida_por_id = :usuario, fecha_atencion = now()
                WHERE tipo = 'FrecuenciaProxima' AND tipo_servicio_id = :servicio
                  AND estado = 'Abierta'
                """,
                Map.of("servicio", servicioId, "usuario", usuario.id()));
    }

    private static String mensaje(Calculada c) {
        return switch (c.tipo()) {
            case "FrecuenciaProxima" ->
                    c.dias() >= 0
                            ? "%s supera su frecuencia máxima en %d días y no está programado"
                                    .formatted(c.servicio(), c.dias())
                            : "%s superó su frecuencia máxima hace %d días y no está programado"
                                    .formatted(c.servicio(), -c.dias());
            case "DuracionProxima" ->
                    c.dias() == 0
                            ? "%s llega hoy a su duración máxima".formatted(c.servicio())
                            : "%s llega mañana a su duración máxima".formatted(c.servicio());
            default ->
                    "%s lleva %d días más de su duración máxima".formatted(c.servicio(), c.dias());
        };
    }

    /**
     * Proveedor y máximo de días de la alerta {@code a}: para la frecuencia, el proveedor del
     * último ciclo completado y la frecuencia máxima de la estación; para la duración, los del
     * ciclo.
     */
    private static final String PROVEEDOR_Y_MAXIMO =
            """
            SELECT coalesce(v.razon_social, 'Personal propio') proveedor,
                CASE WHEN a.tipo = 'FrecuenciaProxima' THEN f.frecuencia_maxima_dias
                    ELSE d.duracion_maxima_dias + coalesce(d.dias_limpieza, 0) END maximo
            FROM (SELECT 1) x
            LEFT JOIN mod03_programacion_servicio p ON p.id = coalesce(a.programacion_id,
                (SELECT q.id FROM mod03_programacion_servicio q WHERE q.tipo_servicio_id = a.tipo_servicio_id
                    AND q.estado = 'Completada'
                    ORDER BY coalesce(q.fecha_fin_real, q.fecha_fin_prevista) DESC, q.id DESC LIMIT 1))
            LEFT JOIN mod05_proveedor v ON v.id = p.proveedor_id
            LEFT JOIN mod03_frecuencia_servicio f ON f.tipo_servicio_id = a.tipo_servicio_id
                AND f.estacion = :estacion
            LEFT JOIN mod03_duracion_servicio d ON d.tipo_servicio_id = a.tipo_servicio_id
            """;

    /** Días hasta la frecuencia máxima de la estación desde el último ciclo completado. */
    private static final String FRECUENCIA =
            """
            SELECT 'FrecuenciaProxima' tipo, t.id servicio_id, t.nombre servicio,
                NULL::bigint programacion_id, CAST(u.fin AS varchar) referencia,
                (u.fin + f.frecuencia_maxima_dias - CAST(:hoy AS date)) dias,
                NULL proveedor, f.frecuencia_maxima_dias maximo
            FROM mod05_tiposervicio t
            JOIN mod03_frecuencia_servicio f ON f.tipo_servicio_id = t.id AND f.estacion = :estacion
            JOIN LATERAL (SELECT max(coalesce(p.fecha_fin_real, p.fecha_fin_prevista)) fin
                FROM mod03_programacion_servicio p
                WHERE p.tipo_servicio_id = t.id AND p.estado = 'Completada') u ON u.fin IS NOT NULL
            WHERE t.activo AND t.es_periodico AND f.frecuencia_maxima_dias IS NOT NULL
              AND u.fin + f.frecuencia_maxima_dias - CAST(:hoy AS date) <= :umbral
              AND NOT EXISTS (SELECT 1 FROM mod03_programacion_servicio p
                  WHERE p.tipo_servicio_id = t.id AND p.estado IN ('Programada', 'EnCurso'))
            """;

    /**
     * Ciclos en curso respecto a su duración máxima con limpieza: excedida (días de exceso) o a un
     * día o menos de alcanzarla (días que faltan).
     */
    private static final String DURACION =
            """
            SELECT CASE WHEN usados > total THEN 'DuracionExcedida' ELSE 'DuracionProxima' END tipo,
                servicio_id, servicio, programacion_id, CAST(programacion_id AS varchar) referencia,
                CASE WHEN usados > total THEN usados - total ELSE total - usados END dias,
                NULL proveedor, total maximo
            FROM (SELECT t.id servicio_id, t.nombre servicio, p.id programacion_id,
                    CAST(:hoy AS date) - p.fecha_inicio_real + 1 usados,
                    d.duracion_maxima_dias + coalesce(d.dias_limpieza, 0) total
                FROM mod03_programacion_servicio p
                JOIN mod05_tiposervicio t ON t.id = p.tipo_servicio_id
                JOIN mod03_duracion_servicio d ON d.tipo_servicio_id = t.id
                WHERE p.estado = 'EnCurso' AND p.fecha_inicio_real IS NOT NULL) c
            WHERE usados > total OR total - usados <= 1
            """;
}
