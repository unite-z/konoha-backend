package pe.edu.pucp.areasverdes.mod03;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod04.api.Entradas;
import pe.edu.pucp.areasverdes.mod04.servicios.OperacionCampoService;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Derivar una incidencia clasificada a un encargo directo de un capataz (AV-277, RF-INC-003, HU-022
 * y CU-022): crea el encargo de MOD-04 ({@code periodicidad} Directo, con el plazo y sin reunión ni
 * orden de servicio, RN-008) en la cuadrilla activa del capataz y guarda el vínculo en {@code
 * mod03_encargo_incidencia}.
 *
 * <p>Las condiciones de MOD-04 se comprueban antes de llamarlo, porque un error suyo desharía
 * también la derivación. El encargo apunta todavía a la copia de áreas de MOD-05: el espacio se
 * cruza por el nombre (deuda de V1, AV-287), como en {@link DerivacionTercerizada}.
 *
 * <p>El estado del último encargo manda en la incidencia ({@link #sincronizar()}, al consultarla):
 * En curso u Observado la deja En atención; Completado, Atendida («Sistema»); Cancelado, otra vez
 * Clasificada con el motivo del jefe, para derivarla de nuevo (el intento queda en el vínculo).
 */
@Service
public class DerivacionInterna {

    /** Cuerpo de {@code POST incidencias/{id}/encargo/}. */
    public record NuevoEncargo(
            @NotNull(message = "Indica el capataz") Long capatazId,
            String espacio,
            @NotBlank(message = "Indica la actividad") String actividad,
            @NotNull(message = "Indica el plazo") LocalDate plazo,
            List<String> incluye,
            Boolean avisarCapataz) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final OperacionCampoService campo;
    private final Avisos avisos;
    private final Clock reloj;

    public DerivacionInterna(
            NamedParameterJdbcTemplate jdbc,
            OperacionCampoService campo,
            Avisos avisos,
            Clock reloj) {
        this.jdbc = jdbc;
        this.campo = campo;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    /** El último encargo se canceló: hay que derivarla de nuevo (Figma 390-8582). */
    public record EncargoCancelado(String codigo, String motivo) {}

    /** «ENC-12»: MOD-04 no numera sus encargos. */
    static String codigo(long encargo) {
        return "ENC-" + encargo;
    }

    /** Actividades de campo activas que puede llevar el encargo. */
    public List<String> actividades(UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        return jdbc.queryForList(
                "SELECT DISTINCT nombre FROM mod04_tipo_actividad WHERE activo ORDER BY nombre",
                Map.of(),
                String.class);
    }

    @Transactional
    public void derivar(long id, NuevoEncargo e, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        sincronizar();
        if (!usuario.tiene(OperacionCampoService.GESTIONAR)) {
            throw new ErrorPlanificacion(
                    403, "sin-permiso", "Tu usuario no puede crear encargos en Operación de campo");
        }
        List<Map<String, Object>> filas =
                jdbc.queryForList(
                        """
                        SELECT i.codigo, i.estado, i.via, i.descripcion, t.nombre tipo,
                            a.nombre espacio, ej.codigo ejemplar
                        FROM mod03_incidencia i
                        JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                        JOIN mod02_area_verde a ON a.id = i.area_verde_id
                        LEFT JOIN mod02_ejemplar ej ON ej.id = i.ejemplar_id
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
        if ("Tercerizada".equals(incidencia.get("via"))) {
            throw new ErrorPlanificacion(
                    409, "incidencia-tercerizada", "Va por la vía tercerizada, no a un capataz");
        }
        String espacio = (String) incidencia.get("espacio");
        if (e.espacio() != null && !e.espacio().isBlank() && !e.espacio().equals(espacio)) {
            throw new ErrorPlanificacion(
                    400, "espacio-invalido", "El encargo va al espacio de la incidencia");
        }
        if (e.plazo().isBefore(LocalDate.now(reloj))) {
            throw new ErrorPlanificacion(
                    400, "plazo-pasado", "El plazo no puede ser anterior a hoy");
        }
        Map<String, Object> cuadrilla =
                unica(
                        """
                        SELECT c.id, p.nombres || ' ' || left(p.apellidos, 1) || '.' nombre, p.usuario_id
                        FROM mod04_cuadrilla c JOIN mod04_personal p ON p.id = c.capataz_id
                        WHERE c.capataz_id = :capataz AND c.activa AND p.activo
                            AND p.tipo_personal = 'Capataz'
                        ORDER BY c.id LIMIT 1
                        """,
                        Map.of("capataz", e.capatazId()),
                        400,
                        "capataz-invalido",
                        "No existe ese capataz con una cuadrilla activa");
        long actividad =
                (Long)
                        unica(
                                        "SELECT id FROM mod04_tipo_actividad WHERE activo AND"
                                            + " lower(nombre) = lower(:nombre) ORDER BY id LIMIT 1",
                                        Map.of("nombre", e.actividad().trim()),
                                        400,
                                        "actividad-invalida",
                                        "No existe esa actividad de campo")
                                .get("id");
        long area =
                (Long)
                        unica(
                                        "SELECT id FROM mod05_areaverde WHERE activo AND"
                                            + " lower(nombre) = lower(:nombre) ORDER BY id LIMIT 1",
                                        Map.of("nombre", espacio),
                                        409,
                                        "espacio-sin-equivalente",
                                        "Operación de campo aún no tiene el espacio " + espacio)
                                .get("id");
        var encargo =
                campo.guardarEncargo(
                        null,
                        new Entradas.Encargo(
                                (Long) cuadrilla.get("id"),
                                actividad,
                                area,
                                descripcion(incidencia, e.incluye()),
                                "Directo",
                                null,
                                null,
                                null,
                                e.plazo()),
                        new Usuario(
                                usuario.id(),
                                "",
                                usuario.nombre(),
                                "",
                                true,
                                List.of(),
                                usuario.permisos()));
        long encargoId = ((Number) encargo.get("id")).longValue();
        jdbc.update(
                "INSERT INTO mod03_encargo_incidencia (incidencia_id, encargo_id, usuario_id)"
                        + " VALUES (:id, :encargo, :usuario)",
                Map.of("id", id, "encargo", encargoId, "usuario", usuario.id()));
        jdbc.update(
                "UPDATE mod03_incidencia SET estado = 'DerivadaInterna', via = 'PersonalPropio',"
                        + " capataz_id = :capataz WHERE id = :id",
                Map.of("id", id, "capataz", e.capatazId()));
        jdbc.update(
                "INSERT INTO mod03_historial_incidencia (incidencia_id, estado, usuario_id,"
                        + " detalle) VALUES (:id, 'DerivadaInterna', :usuario, :detalle)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("usuario", usuario.id())
                        .addValue(
                                "detalle",
                                "encargo " + codigo(encargoId) + " a " + cuadrilla.get("nombre")));
        if (Boolean.TRUE.equals(e.avisarCapataz()) && cuadrilla.get("usuario_id") != null) {
            avisos.aUsuario(
                    ((Number) cuadrilla.get("usuario_id")).longValue(),
                    "%s: encargo %s en %s para el %s"
                            .formatted(
                                    incidencia.get("codigo"),
                                    codigo(encargoId),
                                    espacio,
                                    e.plazo()),
                    "incidencia",
                    id);
        }
    }

    /**
     * Lleva a cada incidencia derivada el estado de su último encargo. Idempotente: solo cambia las
     * que aún no lo reflejan, y deja la transición en el historial sin autor («Sistema»).
     */
    public void sincronizar() {
        jdbc.update(
                """
                WITH ultimos AS (
                    SELECT DISTINCT ON (v.incidencia_id) v.incidencia_id, v.encargo_id,
                        e.estado encargo_estado
                    FROM mod03_encargo_incidencia v JOIN mod04_encargo e ON e.id = v.encargo_id
                    ORDER BY v.incidencia_id, v.id DESC
                ), cambiadas AS (
                    UPDATE mod03_incidencia i SET estado = CASE u.encargo_estado
                            WHEN 'Completado' THEN 'Atendida'
                            WHEN 'Cancelado' THEN 'Clasificada'
                            ELSE 'EnAtencion' END
                    FROM ultimos u
                    WHERE i.id = u.incidencia_id AND (
                        (u.encargo_estado IN ('Completado', 'Cancelado')
                            AND i.estado IN ('DerivadaInterna', 'EnAtencion'))
                        OR (u.encargo_estado IN ('En curso', 'Observado')
                            AND i.estado = 'DerivadaInterna'))
                    RETURNING i.id, i.estado, u.encargo_id, u.encargo_estado
                )
                INSERT INTO mod03_historial_incidencia (incidencia_id, estado, usuario_id, detalle)
                SELECT c.id, c.estado, NULL, 'encargo ENC-' || c.encargo_id || CASE c.encargo_estado
                    WHEN 'Completado' THEN ' completado en Operación de campo'
                    WHEN 'Cancelado' THEN ' cancelado: ' || coalesce((
                        SELECT h.observaciones FROM mod04_encargo_cambio_estado h
                        WHERE h.encargo_id = c.encargo_id AND h.estado_nuevo = 'Cancelado'
                        ORDER BY h.id DESC LIMIT 1), 'sin motivo')
                    ELSE ' en curso' END
                FROM cambiadas c
                """,
                Map.of());
    }

    /** El código del último encargo de la incidencia, o {@code null} si no tiene. */
    String codigoEncargo(long incidencia) {
        return ultimo(incidencia)
                .map(f -> codigo(((Number) f.get("encargo_id")).longValue()))
                .orElse(null);
    }

    /**
     * El último encargo si está cancelado y la incidencia sigue Clasificada; si no, {@code null}.
     */
    EncargoCancelado encargoCancelado(long incidencia) {
        return ultimo(incidencia)
                .filter(f -> "Cancelado".equals(f.get("encargo_estado")))
                .filter(f -> "Clasificada".equals(f.get("incidencia_estado")))
                .map(
                        f ->
                                new EncargoCancelado(
                                        codigo(((Number) f.get("encargo_id")).longValue()),
                                        (String) f.get("motivo")))
                .orElse(null);
    }

    private Optional<Map<String, Object>> ultimo(long incidencia) {
        return jdbc
                .queryForList(
                        """
                        SELECT v.encargo_id, e.estado encargo_estado, i.estado incidencia_estado,
                            (SELECT h.observaciones FROM mod04_encargo_cambio_estado h
                                WHERE h.encargo_id = e.id AND h.estado_nuevo = 'Cancelado'
                                ORDER BY h.id DESC LIMIT 1) motivo
                        FROM mod03_encargo_incidencia v
                        JOIN mod04_encargo e ON e.id = v.encargo_id
                        JOIN mod03_incidencia i ON i.id = v.incidencia_id
                        WHERE v.incidencia_id = :id ORDER BY v.id DESC LIMIT 1
                        """,
                        Map.of("id", incidencia))
                .stream()
                .findFirst();
    }

    /**
     * Lo que lleva el encargo: la incidencia, su descripción y lo que se incluye (Figma 389-7502).
     */
    private static String descripcion(Map<String, Object> incidencia, List<String> incluye) {
        String texto =
                "Incidencia %s · %s en %s%s%s"
                        .formatted(
                                incidencia.get("codigo"),
                                incidencia.get("tipo"),
                                incidencia.get("espacio"),
                                incidencia.get("ejemplar") == null
                                        ? ""
                                        : " (ejemplar " + incidencia.get("ejemplar") + ")",
                                ((String) incidencia.get("descripcion")).isBlank()
                                        ? "."
                                        : ": " + incidencia.get("descripcion"));
        if (incluye != null && !incluye.isEmpty()) {
            texto += " Incluye: " + String.join(", ", incluye) + ".";
        }
        return texto.length() > 1000 ? texto.substring(0, 1000) : texto;
    }

    private Map<String, Object> unica(
            String sql, Map<String, Object> parametros, int estado, String codigo, String detalle) {
        List<Map<String, Object>> filas = jdbc.queryForList(sql, parametros);
        if (filas.isEmpty()) {
            throw new ErrorPlanificacion(estado, codigo, detalle);
        }
        return filas.getFirst();
    }
}
