package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Alertas al jefe de sección, calculadas al consultar (solo dentro de la aplicación, ADR 0003):
 * frecuencia a punto de vencer (HU-MOD03-006, RF-PLA-008) y duración excedida (HU-MOD03-007,
 * RF-PLA-009).
 */
@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Planificación")
@SecurityRequirement(name = "basicAuth")
public class AlertasController {

    /** {@code dias}: días que faltan para el máximo (frecuencia) o días de exceso (duración). */
    public record Alerta(
            String tipo,
            long servicioId,
            String servicio,
            Long programacionId,
            int dias,
            String mensaje) {}

    private record Fila(long servicioId, String servicio, Long programacionId, int dias) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock reloj;
    private final int umbralDias;

    public AlertasController(
            NamedParameterJdbcTemplate jdbc,
            Clock reloj,
            @Value("${mod03.alertas.umbral-dias:7}") int umbralDias) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.umbralDias = umbralDias;
    }

    @Operation(summary = "Alertas de frecuencia a punto de vencer y de duración excedida")
    @GetMapping({"/alertas", "/alertas/"})
    public List<Alerta> alertas(@AuthenticationPrincipal Usuario usuario) {
        Acceso.consultar(usuario);
        LocalDate hoy = LocalDate.now(reloj);
        var datos = Map.of("hoy", hoy, "estacion", Programador.estacion(hoy), "umbral", umbralDias);
        List<Alerta> alertas = new ArrayList<>();
        for (Fila f : jdbc.query(FRECUENCIA, datos, new DataClassRowMapper<>(Fila.class))) {
            alertas.add(
                    new Alerta(
                            "FrecuenciaProxima",
                            f.servicioId(),
                            f.servicio(),
                            null,
                            f.dias(),
                            f.dias() >= 0
                                    ? "%s supera su frecuencia máxima en %d días y no está programado"
                                            .formatted(f.servicio(), f.dias())
                                    : "%s superó su frecuencia máxima hace %d días y no está programado"
                                            .formatted(f.servicio(), -f.dias())));
        }
        for (Fila f : jdbc.query(DURACION, datos, new DataClassRowMapper<>(Fila.class))) {
            alertas.add(
                    new Alerta(
                            "DuracionExcedida",
                            f.servicioId(),
                            f.servicio(),
                            f.programacionId(),
                            f.dias(),
                            "%s lleva %d días más de su duración máxima"
                                    .formatted(f.servicio(), f.dias())));
        }
        return alertas;
    }

    /** Días hasta la frecuencia máxima de la estación desde el último ciclo completado. */
    private static final String FRECUENCIA =
            """
            SELECT t.id servicio_id, t.nombre servicio, NULL::bigint programacion_id,
                (u.fin + f.frecuencia_maxima_dias - CAST(:hoy AS date)) dias
            FROM mod05_tiposervicio t
            JOIN mod03_frecuencia_servicio f ON f.tipo_servicio_id = t.id AND f.estacion = :estacion
            JOIN LATERAL (SELECT max(coalesce(p.fecha_fin_real, p.fecha_fin_prevista)) fin
                FROM mod03_programacion_servicio p
                WHERE p.tipo_servicio_id = t.id AND p.estado = 'Completada') u ON u.fin IS NOT NULL
            WHERE t.activo AND t.es_periodico AND f.frecuencia_maxima_dias IS NOT NULL
              AND u.fin + f.frecuencia_maxima_dias - CAST(:hoy AS date) <= :umbral
              AND NOT EXISTS (SELECT 1 FROM mod03_programacion_servicio p
                  WHERE p.tipo_servicio_id = t.id AND p.estado IN ('Programada', 'EnCurso'))
            ORDER BY dias, t.id
            """;

    /** Días de exceso de las programaciones en curso sobre su duración máxima con limpieza. */
    private static final String DURACION =
            """
            SELECT t.id servicio_id, t.nombre servicio, p.id programacion_id,
                (CAST(:hoy AS date) - p.fecha_inicio_real + 1)
                    - (d.duracion_maxima_dias + coalesce(d.dias_limpieza, 0)) dias
            FROM mod03_programacion_servicio p
            JOIN mod05_tiposervicio t ON t.id = p.tipo_servicio_id
            JOIN mod03_duracion_servicio d ON d.tipo_servicio_id = t.id
            WHERE p.estado = 'EnCurso' AND p.fecha_inicio_real IS NOT NULL
              AND CAST(:hoy AS date) - p.fecha_inicio_real + 1
                  > d.duracion_maxima_dias + coalesce(d.dias_limpieza, 0)
            ORDER BY dias DESC, p.id
            """;
}
