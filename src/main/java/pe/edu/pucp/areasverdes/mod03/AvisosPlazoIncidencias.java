package pe.edu.pucp.areasverdes.mod03;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aviso proactivo de plazo de las incidencias sencillas (AV-280, RF-INC-008, RN-019; solo dentro de
 * la aplicación, ADR 0003). Cada hora, las sencillas de personal propio aún sin atender que pasan
 * de 24 h («SuperaObjetivo») o de 48 h («Vencida») generan un aviso a cada jefe de sección activo.
 * {@code mod03_aviso_plazo} guarda el nivel ya avisado de cada incidencia, de modo que repetir el
 * proceso no duplica nada. Mismo criterio de plazo que {@code nivel_plazo} de la bandeja.
 */
@Service
public class AvisosPlazoIncidencias {

    private final NamedParameterJdbcTemplate jdbc;
    private final Avisos avisos;

    public AvisosPlazoIncidencias(NamedParameterJdbcTemplate jdbc, Avisos avisos) {
        this.jdbc = jdbc;
        this.avisos = avisos;
    }

    /** Cada hora, en punto y media (hora de Lima). Devuelve cuántas incidencias avisó. */
    @Scheduled(cron = "0 30 * * * *", zone = "America/Lima")
    @Transactional
    public int avisar() {
        List<Map<String, Object>> pendientes =
                jdbc.queryForList(
                        """
                        SELECT i.id, i.codigo, a.nombre espacio, t.nombre tipo, c.nivel,
                            cp.nombres || ' ' || left(cp.apellidos, 1) || '.' capataz
                        FROM mod03_incidencia i
                        JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                        JOIN mod02_area_verde a ON a.id = i.area_verde_id
                        LEFT JOIN mod04_personal cp ON cp.id = i.capataz_id
                        CROSS JOIN LATERAL (SELECT CASE
                            WHEN now() - i.fecha_registro > interval '48 hours' THEN 'Vencida'
                            WHEN now() - i.fecha_registro > interval '24 hours' THEN 'SuperaObjetivo'
                            END nivel) c
                        WHERE t.sencilla AND coalesce(i.via, 'PersonalPropio') <> 'Tercerizada'
                          AND i.estado NOT IN ('Atendida', 'Cerrada', 'Descartada')
                          AND c.nivel IS NOT NULL
                        """,
                        Map.of());
        List<Long> jefes =
                jdbc.queryForList(
                        "SELECT u.id FROM core_usuario u JOIN core_rol r ON r.id = u.rol_id WHERE"
                                + " u.activo AND r.codigo = 'jefe-seccion'",
                        Map.of(),
                        Long.class);
        int avisadas = 0;
        for (Map<String, Object> p : pendientes) {
            long id = ((Number) p.get("id")).longValue();
            int nuevas =
                    jdbc.update(
                            "INSERT INTO mod03_aviso_plazo (incidencia_id, nivel) VALUES (:id,"
                                    + " :nivel) ON CONFLICT DO NOTHING",
                            Map.of("id", id, "nivel", p.get("nivel")));
            if (nuevas == 0) {
                continue;
            }
            String texto = texto(p);
            jefes.forEach(jefe -> avisos.aUsuario(jefe, texto, "incidencia", id));
            avisadas++;
        }
        return avisadas;
    }

    private static String texto(Map<String, Object> p) {
        String situacion =
                "Vencida".equals(p.get("nivel"))
                        ? "venció su plazo de 48 h"
                        : "superó el objetivo de 24 h";
        String capataz = p.get("capataz") == null ? "" : " · capataz " + p.get("capataz");
        return "%s %s sin atenderse: %s en %s%s"
                .formatted(p.get("codigo"), situacion, p.get("tipo"), p.get("espacio"), capataz);
    }
}
