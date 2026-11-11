package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Lecturas de MOD-05 (y de las especies de MOD-02) para RF-REP-008, en solo lectura y con SQL
 * parametrizado. Una atención es del periodo por su inicio o, si aún no empezó, por su fecha de
 * creación, en America/Lima (como el panel).
 */
@Repository
public class ServiciosTercerizadosRepository {

    /**
     * Una atención del periodo con lo que el reporte necesita de su alcance, informe y decisión.
     */
    public record AtencionServicio(
            long id,
            String codigo,
            String clase,
            String estado,
            boolean esRiesgo,
            OffsetDateTime fechaHoraInicio,
            OffsetDateTime fechaHoraFin,
            String espacio,
            String ejemplarCodigo,
            BigDecimal metrado,
            String unidad,
            Integer ultimaRevision,
            Integer revisionDecidida,
            String resultadoDecidido,
            int documentos,
            boolean conEvidencia) {}

    public record Atendido(long atencionId, String tipo, String nombre, Long ejemplarId) {}

    public record Contrato(
            long id, String numero, LocalDate vigenciaInicio, LocalDate vigenciaFin) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ServiciosTercerizadosRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> nombreTipoServicio(long id) {
        return jdbc
                .queryForList(
                        "SELECT nombre FROM mod05_tiposervicio WHERE id = :id",
                        Map.of("id", id),
                        String.class)
                .stream()
                .findFirst();
    }

    public Optional<String> razonSocial(long proveedorId) {
        return jdbc
                .queryForList(
                        "SELECT razon_social FROM mod05_proveedor WHERE id = :id",
                        Map.of("id", proveedorId),
                        String.class)
                .stream()
                .findFirst();
    }

    public Optional<Contrato> contrato(long contratoId) {
        return jdbc
                .query(
                        "SELECT id, numero, vigencia_inicio, vigencia_fin FROM mod05_contrato"
                                + " WHERE id = :id",
                        Map.of("id", contratoId),
                        new DataClassRowMapper<>(Contrato.class))
                .stream()
                .findFirst();
    }

    /**
     * Atenciones del servicio en el periodo, de la más antigua a la más reciente, hasta {@code
     * limite}.
     */
    public List<AtencionServicio> atenciones(
            RangoFechas periodo,
            long tipoServicioId,
            Long proveedorId,
            Long contratoId,
            int limite) {
        return jdbc.query(
                """
                SELECT a.id, a.codigo, a.clase, a.estado, a.es_riesgo, a.fecha_hora_inicio, a.fecha_hora_fin,
                       (SELECT string_agg(DISTINCT v.nombre, ', ') FROM mod05_alcanceatencion al
                          JOIN mod05_areaverde v ON v.id = al.area_verde_id WHERE al.atencion_id = a.id) AS espacio,
                       (SELECT min(e.codigo) FROM mod05_alcanceatencion al
                          JOIN mod02_ejemplar e ON e.id = al.ejemplar_id WHERE al.atencion_id = a.id) AS ejemplar_codigo,
                       (SELECT sum(al.metrado) FROM mod05_alcanceatencion al WHERE al.atencion_id = a.id) AS metrado,
                       (SELECT CASE WHEN count(DISTINCT u.simbolo) = 1 THEN min(u.simbolo) END
                          FROM mod05_alcanceatencion al JOIN mod05_unidadmedida u ON u.id = al.unidad_medida_id
                          WHERE al.atencion_id = a.id) AS unidad,
                       (SELECT max(r.revision) FROM mod05_reporteproveedor r WHERE r.atencion_id = a.id)
                           AS ultima_revision,
                       d.revision AS revision_decidida, d.resultado AS resultado_decidido,
                       (SELECT count(*) FROM mod05_archivoasociado x
                          JOIN mod05_reporteproveedor r ON r.id = x.reporte_id WHERE r.atencion_id = a.id)::int
                           AS documentos,
                       EXISTS (SELECT 1 FROM mod05_archivoasociado x
                          JOIN mod05_reporteproveedor r ON r.id = x.reporte_id
                          WHERE r.atencion_id = a.id AND x.categoria = 'EvidenciaEspacio') AS con_evidencia
                FROM mod05_atenciontercerizada a
                LEFT JOIN mod05_contrato c ON c.id = a.contrato_id
                LEFT JOIN LATERAL (
                    SELECT r.revision, k.resultado FROM mod05_conformidad k
                    JOIN mod05_reporteproveedor r ON r.id = k.reporte_id
                    WHERE k.atencion_id = a.id ORDER BY r.revision DESC, k.id DESC LIMIT 1) d ON true
                WHERE a.tipo_servicio_id = :tipo
                  AND (CAST(:proveedor AS bigint) IS NULL OR c.proveedor_id = :proveedor)
                  AND (CAST(:contrato AS bigint) IS NULL OR a.contrato_id = :contrato)
                  AND (COALESCE(a.fecha_hora_inicio, a.fecha_creacion) AT TIME ZONE 'America/Lima')::date
                      BETWEEN :desde AND :hasta
                ORDER BY COALESCE(a.fecha_hora_inicio, a.fecha_creacion), a.id
                LIMIT :limite
                """,
                new MapSqlParameterSource()
                        .addValue("tipo", tipoServicioId)
                        .addValue("proveedor", proveedorId)
                        .addValue("contrato", contratoId)
                        .addValue("desde", periodo.desde())
                        .addValue("hasta", periodo.hasta())
                        .addValue("limite", limite),
                new DataClassRowMapper<>(AtencionServicio.class));
    }

    /** Plagas (MOD-05) y especies de los ejemplares del alcance (MOD-02) de esas atenciones. */
    public List<Atendido> atendidos(Collection<Long> atencionIds) {
        if (atencionIds.isEmpty()) return List.of();
        return jdbc.query(
                """
                SELECT ap.atencion_id, 'Plaga' AS tipo, p.nombre, NULL::bigint AS ejemplar_id
                FROM mod05_atencion_plaga ap JOIN mod05_plaga p ON p.id = ap.plaga_id
                WHERE ap.atencion_id IN (:ids)
                UNION ALL
                SELECT al.atencion_id, 'Especie', COALESCE(s.nombre_comun, s.nombre_cientifico), e.id
                FROM mod05_alcanceatencion al
                JOIN mod02_ejemplar e ON e.id = al.ejemplar_id
                JOIN mod02_especie_vegetal s ON s.id = e.especie_id
                WHERE al.atencion_id IN (:ids)
                """,
                Map.of("ids", atencionIds),
                new DataClassRowMapper<>(Atendido.class));
    }
}
