package pe.edu.pucp.areasverdes.mod03;

import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod05.AtencionService;
import pe.edu.pucp.areasverdes.mod05.Entradas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Derivar una incidencia clasificada a un proveedor (AV-278, RF-INC-004, HU-023 y CU-023): crea su
 * atención tercerizada en MOD-05 con {@link AtencionService}, de origen {@code Demanda}, con el
 * contrato vigente elegido y el ejemplar o el espacio de la incidencia como alcance.
 *
 * <p>Lo que MOD-05 aún no admite:
 *
 * <ul>
 *   <li>la atención extraordinaria sin contrato (CU-MOD05-008): sin contrato responde 409 {@code
 *       sin-contrato};
 *   <li>la conformidad: la incidencia pasa a Atendida («Sistema») cuando la atención llega a
 *       «Reporte adjunto», el informe del proveedor ({@link #sincronizar()}), y ese informe vale
 *       como evidencia para cerrarla;
 *   <li>la poda del ejemplar de MOD-02 (RF-POD-005): {@code crear_poda} se acepta y no hace nada.
 * </ul>
 *
 * <p>Las condiciones de MOD-05 se comprueban antes de llamarlo, porque un error dentro de su
 * transacción desharía también la derivación. El espacio de MOD-02 se cruza con el de MOD-05 por el
 * nombre (deuda de V1), como en {@link RequerimientoCorte}; el ejemplar tiene el mismo id en los
 * dos.
 */
@Service
public class DerivacionTercerizada {

    /** Contrato de un proveedor con el servicio que cubre (el código es su número). */
    public record Contrato(long id, String codigo, boolean vigente, List<String> servicios) {}

    public record Proveedor(long id, String razonSocial, List<Contrato> contratos) {}

    /** Cuerpo de {@code POST incidencias/{id}/requerimiento/}. */
    public record NuevoRequerimiento(
            @NotNull(message = "Indica el proveedor") Long proveedorId,
            Long contratoId,
            String servicio,
            @NotNull(message = "Indica el tipo de atención")
                    @Pattern(
                            regexp = "Programada|Extraordinaria",
                            message = "El tipo de atención es Programada o Extraordinaria")
                    String tipoAtencion,
            Boolean crearPoda) {}

    /** Un paso del avance en MOD-05: Hecho (con su fecha), EnCurso o Pendiente. */
    public record Paso(String nombre, String estado, LocalDate fecha) {}

    /** El requerimiento de la incidencia y su avance en MOD-05. */
    public record Requerimiento(
            long id, String codigo, String proveedor, String tipoAtencion, List<Paso> pasos) {}

    static final String REPORTE = "Reporte adjunto";

    private final NamedParameterJdbcTemplate jdbc;
    private final AtencionService atenciones;
    private final Clock reloj;

    public DerivacionTercerizada(
            NamedParameterJdbcTemplate jdbc, AtencionService atenciones, Clock reloj) {
        this.jdbc = jdbc;
        this.atenciones = atenciones;
        this.reloj = reloj;
    }

    /** Proveedores activos con sus contratos de servicios tercerizados, vigentes o no. */
    public List<Proveedor> proveedores(UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        Map<Long, Proveedor> proveedores = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT v.id, v.razon_social, c.id contrato_id, c.numero,
                    (:hoy BETWEEN c.vigencia_inicio AND c.vigencia_fin AND t.activo) vigente,
                    t.nombre servicio
                FROM mod05_proveedor v
                LEFT JOIN mod05_contrato c ON c.proveedor_id = v.id
                LEFT JOIN mod05_tiposervicio t ON t.id = c.tipo_servicio_id
                WHERE v.activo AND (c.id IS NULL OR t.ejecutor = 'Tercerizado')
                ORDER BY v.razon_social, v.id, c.vigencia_fin DESC, c.id
                """,
                Map.of("hoy", LocalDate.now(reloj)),
                rs -> {
                    String razonSocial = rs.getString("razon_social");
                    Proveedor p =
                            proveedores.computeIfAbsent(
                                    rs.getLong("id"),
                                    id -> new Proveedor(id, razonSocial, new ArrayList<>()));
                    long contrato = rs.getLong("contrato_id");
                    if (!rs.wasNull()) {
                        p.contratos()
                                .add(
                                        new Contrato(
                                                contrato,
                                                rs.getString("numero"),
                                                rs.getBoolean("vigente"),
                                                List.of(rs.getString("servicio"))));
                    }
                });
        return List.copyOf(proveedores.values());
    }

    @Transactional
    public void derivar(long id, NuevoRequerimiento r, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        if (!usuario.tiene(RequerimientoCorte.REGISTRAR_ATENCION)) {
            throw new ErrorPlanificacion(
                    403, "sin-permiso", "Tu usuario no puede registrar requerimientos en MOD-05");
        }
        List<Map<String, Object>> filas =
                jdbc.queryForList(
                        """
                        SELECT i.codigo, i.estado, i.descripcion, i.ejemplar_id, t.nombre tipo,
                            a.nombre espacio
                        FROM mod03_incidencia i
                        JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                        JOIN mod02_area_verde a ON a.id = i.area_verde_id
                        WHERE i.id = :id FOR UPDATE OF i
                        """,
                        Map.of("id", id));
        if (filas.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "incidencia-no-encontrada", "No existe esa incidencia");
        }
        var incidencia = filas.getFirst();
        if (!"Clasificada".equals(incidencia.get("estado"))) {
            throw new ErrorPlanificacion(
                    409,
                    "transicion-invalida",
                    "No se puede derivar una incidencia " + incidencia.get("estado"));
        }
        if (r.contratoId() == null) {
            throw new ErrorPlanificacion(
                    409,
                    "sin-contrato",
                    "Servicios tercerizados aún no registra atenciones extraordinarias sin"
                            + " contrato: elige un proveedor con contrato vigente");
        }
        List<String> servicio = servicioDelContrato(r);
        if (servicio.isEmpty()) {
            throw new ErrorPlanificacion(
                    409,
                    "contrato-no-vigente",
                    "El contrato no es de ese proveedor o no está vigente hoy");
        }
        if (r.servicio() != null
                && !r.servicio().isBlank()
                && !r.servicio().equals(servicio.getFirst())) {
            throw new ErrorPlanificacion(
                    400, "servicio-invalido", "El contrato no cubre " + r.servicio());
        }
        Entradas.Alcance alcance = alcance(incidencia);
        String descripcion =
                "Incidencia %s · %s en %s%s. Atención %s (derivada desde MOD-03)."
                        .formatted(
                                incidencia.get("codigo"),
                                incidencia.get("tipo"),
                                incidencia.get("espacio"),
                                ((String) incidencia.get("descripcion")).isBlank()
                                        ? ""
                                        : ": " + incidencia.get("descripcion"),
                                r.tipoAtencion().toLowerCase());
        var atencion =
                atenciones.crear(
                        new Entradas.Crear(
                                r.contratoId(),
                                descripcion,
                                "Demanda",
                                null,
                                null,
                                null,
                                List.of(alcance)),
                        new Usuario(
                                usuario.id(),
                                "",
                                usuario.nombre(),
                                "",
                                true,
                                List.of(Usuario.JEFE)));
        jdbc.update(
                """
                INSERT INTO mod03_requerimiento_incidencia (incidencia_id, atencion_id,
                    proveedor_id, tipo_atencion, usuario_id)
                VALUES (:id, :atencion, :proveedor, :tipo, :usuario)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("atencion", atencion.get("id"))
                        .addValue("proveedor", r.proveedorId())
                        .addValue("tipo", r.tipoAtencion())
                        .addValue("usuario", usuario.id()));
        jdbc.update(
                "UPDATE mod03_incidencia SET estado = 'DerivadaTercerizada', via = 'Tercerizada'"
                        + " WHERE id = :id",
                Map.of("id", id));
        jdbc.update(
                "INSERT INTO mod03_historial_incidencia (incidencia_id, estado, usuario_id,"
                        + " detalle) VALUES (:id, 'DerivadaTercerizada', :usuario, :detalle)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("usuario", usuario.id())
                        .addValue(
                                "detalle",
                                "requerimiento "
                                        + corto((String) atencion.get("codigo"))
                                        + " a "
                                        + atencion.get("proveedor_nombre")));
    }

    /** El servicio del contrato si es del proveedor y está vigente hoy, como exige MOD-05. */
    private List<String> servicioDelContrato(NuevoRequerimiento r) {
        return jdbc.queryForList(
                """
                SELECT t.nombre FROM mod05_contrato c
                JOIN mod05_proveedor v ON v.id = c.proveedor_id AND v.activo
                JOIN mod05_tiposervicio t ON t.id = c.tipo_servicio_id
                    AND t.activo AND t.ejecutor = 'Tercerizado'
                WHERE c.id = :contrato AND c.proveedor_id = :proveedor
                    AND :hoy BETWEEN c.vigencia_inicio AND c.vigencia_fin
                """,
                new MapSqlParameterSource()
                        .addValue("contrato", r.contratoId())
                        .addValue("proveedor", r.proveedorId())
                        .addValue("hoy", LocalDate.now(reloj)),
                String.class);
    }

    /**
     * El ejemplar (mismo id en MOD-02 y MOD-05) o, sin él, el espacio de MOD-05 del mismo nombre.
     */
    private Entradas.Alcance alcance(Map<String, Object> incidencia) {
        if (incidencia.get("ejemplar_id") != null) {
            return new Entradas.Alcance(
                    null, ((Number) incidencia.get("ejemplar_id")).longValue(), null, null);
        }
        List<Long> espacio =
                jdbc.queryForList(
                        "SELECT id FROM mod05_areaverde WHERE activo AND lower(nombre) ="
                                + " lower(:nombre) ORDER BY id LIMIT 1",
                        Map.of("nombre", incidencia.get("espacio")),
                        Long.class);
        if (espacio.isEmpty()) {
            throw new ErrorPlanificacion(
                    409,
                    "espacio-sin-catalogo",
                    "El espacio de la incidencia no está en el catálogo de Servicios tercerizados");
        }
        return new Entradas.Alcance(espacio.getFirst(), null, null, null);
    }

    /**
     * Pasa a Atendida («Sistema») las incidencias derivadas cuya última atención ya tiene el
     * informe del proveedor («Reporte adjunto»): MOD-05 aún no registra la conformidad.
     */
    @Transactional
    public void sincronizar() {
        jdbc.update(
                """
                WITH ultimas AS (
                    SELECT DISTINCT ON (incidencia_id) incidencia_id, atencion_id
                    FROM mod03_requerimiento_incidencia
                    ORDER BY incidencia_id, fecha_hora DESC, id DESC
                ), atendidas AS (
                    UPDATE mod03_incidencia i SET estado = 'Atendida'
                    FROM ultimas u JOIN mod05_atenciontercerizada a ON a.id = u.atencion_id
                    WHERE i.id = u.incidencia_id AND i.estado = 'DerivadaTercerizada'
                        AND a.estado = :reporte
                    RETURNING i.id
                )
                INSERT INTO mod03_historial_incidencia (incidencia_id, estado, usuario_id, detalle)
                SELECT id, 'Atendida', NULL, 'informe del proveedor en Servicios tercerizados'
                FROM atendidas
                """,
                Map.of("reporte", REPORTE));
    }

    /** {@code true} si la última atención de la incidencia tiene el informe del proveedor. */
    boolean conInforme(long incidencia) {
        return REPORTE.equals(ultima(incidencia).map(a -> a.get("estado")).orElse(null));
    }

    /** El último requerimiento de la incidencia con su avance, o {@code null} si no tiene. */
    Requerimiento requerimiento(long incidencia) {
        return ultima(incidencia)
                .map(
                        a ->
                                new Requerimiento(
                                        ((Number) a.get("id")).longValue(),
                                        (String) a.get("codigo"),
                                        (String) a.get("proveedor"),
                                        (String) a.get("tipo_atencion"),
                                        pasos(a)))
                .orElse(null);
    }

    private java.util.Optional<Map<String, Object>> ultima(long incidencia) {
        return jdbc
                .queryForList(
                        """
                        SELECT a.id, a.codigo, a.estado, a.fecha_creacion, a.fecha_orden_compra,
                            v.razon_social proveedor, r.tipo_atencion,
                            (SELECT min(h.fecha_hora) FROM mod05_historialestado h
                             WHERE h.atencion_id = a.id AND h.estado_nuevo = :reporte) fecha_informe
                        FROM mod03_requerimiento_incidencia r
                        JOIN mod05_atenciontercerizada a ON a.id = r.atencion_id
                        JOIN mod05_proveedor v ON v.id = r.proveedor_id
                        WHERE r.incidencia_id = :id
                        ORDER BY r.fecha_hora DESC, r.id DESC LIMIT 1
                        """,
                        Map.of("id", incidencia, "reporte", REPORTE))
                .stream()
                .findFirst();
    }

    /**
     * Requerimiento, orden de compra, informe del proveedor y conformidad (que MOD-05 aún no
     * registra). El primero sin hacer está en curso.
     */
    private static List<Paso> pasos(Map<String, Object> a) {
        String[] nombres = {
            "Requerimiento", "Orden de compra", "Informe del proveedor", "Conformidad"
        };
        LocalDate[] fechas = {
            dia(a.get("fecha_creacion")),
            dia(a.get("fecha_orden_compra")),
            REPORTE.equals(a.get("estado")) ? dia(a.get("fecha_informe")) : null,
            null
        };
        List<Paso> pasos = new ArrayList<>();
        boolean enCurso = false;
        for (int i = 0; i < nombres.length; i++) {
            String estado;
            if (fechas[i] != null) {
                estado = "Hecho";
            } else if (!enCurso) {
                estado = "EnCurso";
                enCurso = true;
            } else {
                estado = "Pendiente";
            }
            pasos.add(new Paso(nombres[i], estado, fechas[i]));
        }
        return pasos;
    }

    private static LocalDate dia(Object valor) {
        if (valor instanceof OffsetDateTime instante) {
            return instante.atZoneSameInstant(LIMA).toLocalDate();
        }
        if (valor instanceof java.sql.Timestamp t) {
            return t.toInstant().atZone(LIMA).toLocalDate();
        }
        return null;
    }

    /** Las once primeras letras del código de MOD-05, como las muestra su interfaz. */
    static String corto(String codigo) {
        return codigo.length() > 11 ? codigo.substring(0, 11) : codigo;
    }
}
