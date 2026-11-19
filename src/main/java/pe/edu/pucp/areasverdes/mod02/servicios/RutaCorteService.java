package pe.edu.pucp.areasverdes.mod02.servicios;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod02.api.RutaCorteController.Jardin;
import pe.edu.pucp.areasverdes.mod02.api.RutaCorteController.NuevoRetiro;
import pe.edu.pucp.areasverdes.mod02.api.RutaCorteController.Reincorporacion;
import pe.edu.pucp.areasverdes.mod02.api.RutaCorteController.Retiro;
import pe.edu.pucp.areasverdes.mod02.api.RutaCorteController.Ruta;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Ruta de jardines del corte de césped (AV-129, RF-INV-004, HU-MOD02-009). La ruta son los jardines
 * de los sectores ordinarios (no «a demanda») menos los retirados en la fecha: la misma que MOD-03
 * propone y copia al programar un ciclo. Retirar y reincorporar nunca borran: queda el historial.
 */
@Service
public class RutaCorteService {

    private static final String ENTIDAD = "mod02_ruta_corte_retiro";

    private static final String RETIROS =
            """
            SELECT r.id, r.area_verde_id, a.nombre area_verde_nombre, r.desde, r.motivo,
                u.nombre autor, r.fecha_registro, r.reincorporado_desde, r.motivo_reincorporacion,
                ur.nombre autor_reincorporacion
            FROM mod02_ruta_corte_retiro r
            JOIN mod02_area_verde a ON a.id = r.area_verde_id
            JOIN core_usuario u ON u.id = r.usuario_id
            LEFT JOIN core_usuario ur ON ur.id = r.usuario_reincorporacion_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;
    private final Clock reloj;

    public RutaCorteService(
            NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria, Clock reloj) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public Ruta ruta(LocalDate fecha, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        LocalDate dia = fecha == null ? LocalDate.now(reloj) : fecha;
        Map<Long, Retiro> abiertos =
                jdbc.query(RETIROS + " WHERE r.reincorporado_desde IS NULL", this::retiro).stream()
                        .collect(Collectors.toMap(Retiro::areaVerdeId, Function.identity()));
        List<Jardin> jardines =
                jdbc.query(
                        """
                        SELECT a.id, a.nombre, s.nombre sector, s.atencion_a_demanda,
                            a.superficie_m2,
                            NOT s.atencion_a_demanda AND NOT EXISTS (
                                SELECT 1 FROM mod02_ruta_corte_retiro r
                                WHERE r.area_verde_id = a.id AND r.desde <= :fecha
                                  AND (r.reincorporado_desde IS NULL
                                       OR r.reincorporado_desde > :fecha)) en_ruta
                        FROM mod02_sector s
                        JOIN mod02_historial_area_sector h ON h.sector_id = s.id
                            AND h.fecha_fin IS NULL
                        JOIN mod02_area_verde a ON a.id = h.area_verde_id AND a.activo
                        WHERE s.activo
                        ORDER BY s.nombre, a.nombre, a.id
                        """,
                        Map.of("fecha", dia),
                        (rs, n) ->
                                new Jardin(
                                        rs.getLong("id"),
                                        rs.getString("nombre"),
                                        rs.getString("sector"),
                                        rs.getBoolean("atencion_a_demanda"),
                                        rs.getBigDecimal("superficie_m2"),
                                        rs.getBoolean("en_ruta"),
                                        abiertos.get(rs.getLong("id"))));
        List<Jardin> enRuta = jardines.stream().filter(Jardin::enRuta).toList();
        BigDecimal superficie =
                enRuta.stream().map(Jardin::superficieM2).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Ruta(dia, enRuta.size(), superficie, jardines);
    }

    public List<Retiro> retiros(Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return jdbc.query(RETIROS + " ORDER BY r.fecha_registro DESC, r.id DESC", this::retiro);
    }

    @Transactional
    public Retiro retirar(NuevoRetiro entrada, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        String motivo = motivoObligatorio(entrada.motivo(), "Escribe el motivo del retiro");
        noAntesDeHoy(entrada.desde());
        List<Boolean> sector =
                jdbc.query(
                        """
                        SELECT s.atencion_a_demanda
                        FROM mod02_area_verde a
                        JOIN mod02_historial_area_sector h ON h.area_verde_id = a.id
                            AND h.fecha_fin IS NULL
                        JOIN mod02_sector s ON s.id = h.sector_id AND s.activo
                        WHERE a.id = :id AND a.activo
                        """,
                        Map.of("id", entrada.areaVerdeId()),
                        (rs, n) -> rs.getBoolean(1));
        if (sector.isEmpty()) {
            throw new ErrorCoberturas(
                    404, "jardin-no-encontrado", "No existe ese jardín activo con sector");
        }
        if (sector.getFirst()) {
            throw new ErrorCoberturas(
                    400,
                    "fuera-de-ruta",
                    "Ese jardín es de un sector a demanda: no está en la ruta ordinaria");
        }
        long id;
        try {
            id =
                    jdbc.queryForObject(
                            """
                            INSERT INTO mod02_ruta_corte_retiro (area_verde_id, desde, motivo,
                                usuario_id)
                            VALUES (:area, :desde, :motivo, :usuario) RETURNING id
                            """,
                            new MapSqlParameterSource()
                                    .addValue("area", entrada.areaVerdeId())
                                    .addValue("desde", entrada.desde())
                                    .addValue("motivo", motivo)
                                    .addValue("usuario", usuario.id()),
                            Long.class);
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "ya-retirado", "Ese jardín ya tiene un retiro abierto de la ruta");
        }
        Retiro creado = buscar(id);
        auditoria.registrarAlta(ENTIDAD, id, valores(creado), usuario.id());
        return creado;
    }

    @Transactional
    public Retiro reincorporar(long id, Reincorporacion entrada, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        String motivo =
                motivoObligatorio(entrada.motivo(), "Escribe el motivo de la reincorporación");
        Retiro antes =
                jdbc
                        .query(
                                RETIROS + " WHERE r.id = :id FOR UPDATE OF r",
                                Map.of("id", id),
                                this::retiro)
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ErrorCoberturas(
                                                404,
                                                "retiro-no-encontrado",
                                                "No existe ese retiro"));
        if (antes.reincorporadoDesde() != null) {
            throw new ErrorCoberturas(
                    409, "ya-reincorporado", "Ese jardín ya se reincorporó a la ruta");
        }
        noAntesDeHoy(entrada.desde());
        if (entrada.desde().isBefore(antes.desde())) {
            throw new ErrorCoberturas(
                    400,
                    "fecha-anterior-al-retiro",
                    "La reincorporación no puede ser anterior al retiro");
        }
        jdbc.update(
                """
                UPDATE mod02_ruta_corte_retiro
                SET reincorporado_desde = :desde, motivo_reincorporacion = :motivo,
                    usuario_reincorporacion_id = :usuario
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("desde", entrada.desde())
                        .addValue("motivo", motivo)
                        .addValue("usuario", usuario.id()));
        Retiro despues = buscar(id);
        auditoria.registrarCambio(
                ENTIDAD,
                id,
                "Modificacion",
                valores(antes),
                valores(despues),
                "Reincorporación a la ruta",
                usuario.id());
        return despues;
    }

    private Retiro buscar(long id) {
        return jdbc.queryForObject(RETIROS + " WHERE r.id = :id", Map.of("id", id), this::retiro);
    }

    private void noAntesDeHoy(LocalDate fecha) {
        if (fecha.isBefore(LocalDate.now(reloj))) {
            throw new ErrorCoberturas(
                    400,
                    "fecha-pasada",
                    "Se aplica desde hoy o una fecha posterior: los ciclos ya programados"
                            + " conservan su ruta");
        }
    }

    private static String motivoObligatorio(String motivo, String detalle) {
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.isEmpty()) throw new ErrorCoberturas(400, "motivo-obligatorio", detalle);
        return limpio;
    }

    private Retiro retiro(ResultSet rs, int fila) throws SQLException {
        Date reincorporado = rs.getDate("reincorporado_desde");
        return new Retiro(
                rs.getLong("id"),
                rs.getLong("area_verde_id"),
                rs.getString("area_verde_nombre"),
                rs.getDate("desde").toLocalDate(),
                rs.getString("motivo"),
                rs.getString("autor"),
                rs.getObject("fecha_registro", java.time.OffsetDateTime.class),
                reincorporado == null ? null : reincorporado.toLocalDate(),
                rs.getString("motivo_reincorporacion"),
                rs.getString("autor_reincorporacion"));
    }

    private static Map<String, Object> valores(Retiro r) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("area_verde_id", r.areaVerdeId());
        v.put("desde", r.desde());
        v.put("motivo", r.motivo());
        v.put("reincorporado_desde", r.reincorporadoDesde());
        v.put("motivo_reincorporacion", r.motivoReincorporacion());
        return v;
    }
}
