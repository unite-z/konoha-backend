package pe.edu.pucp.areasverdes.mod03;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clasificar, descartar o pedir inspección de una incidencia (AV-276, RF-INC-002, HU-021 y CU-021).
 * Con un árbol de 5 m o más se sugiere el proveedor tercerizado (poda en altura, RN-005); elegir
 * otra vía que la sugerida exige motivo. Mientras MOD-02 no guarde la altura del ejemplar
 * (dasometría), la sugerida es personal propio.
 */
@Service
public class ClasificacionIncidencias {

    static final Set<String> SEVERIDADES = Set.of("Alta", "Media", "Baja");
    static final Set<String> IMPACTOS = Set.of("Alto", "Medio", "Bajo");
    static final Set<String> VIAS = Set.of("PersonalPropio", "Tercerizada");

    public record Clasificacion(
            @NotBlank(message = "Indica la severidad") String severidad,
            @NotBlank(message = "Indica el impacto") String impacto,
            @NotBlank(message = "Indica quién lo atiende") String via,
            @Size(max = 300, message = "Máximo 300 caracteres") String motivoCambioVia) {}

    public record Descarte(@Size(max = 300, message = "Máximo 300 caracteres") String motivo) {}

    public record SolicitudInspeccion(
            @NotNull(message = "Indica quién inspecciona") Long responsableId,
            @Size(max = 300, message = "Máximo 300 caracteres") String queVerificar) {}

    public record Inspector(long id, String nombre) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ClasificacionIncidencias(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ingenieros y practicantes activos: quienes pueden inspeccionar. */
    public List<Inspector> inspectores(UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        return jdbc.query(
                """
                SELECT u.id, u.nombre FROM core_usuario u JOIN core_rol r ON r.id = u.rol_id
                WHERE u.activo AND r.codigo IN ('ingeniero-campo', 'practicante')
                ORDER BY u.nombre
                """,
                (rs, n) -> new Inspector(rs.getLong("id"), rs.getString("nombre")));
    }

    @Transactional
    public void clasificar(long id, Clasificacion c, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        exigirEstado(id, Set.of("Registrada"), "clasificar");
        if (!SEVERIDADES.contains(c.severidad())
                || !IMPACTOS.contains(c.impacto())
                || !VIAS.contains(c.via())) {
            throw new ErrorPlanificacion(
                    400, "clasificacion-invalida", "Severidad, impacto o vía no válidos");
        }
        String sugerida = viaSugerida(id);
        boolean cambia = !sugerida.equals(c.via());
        String motivo = c.motivoCambioVia() == null ? "" : c.motivoCambioVia().trim();
        if (cambia && motivo.isEmpty()) {
            throw new ErrorPlanificacion(
                    400, "motivo-requerido", "Explica por qué cambias la vía sugerida");
        }
        jdbc.update(
                """
                UPDATE mod03_incidencia SET severidad = :severidad, impacto = :impacto, via = :via,
                    estado = 'Clasificada'
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("severidad", c.severidad())
                        .addValue("impacto", c.impacto())
                        .addValue("via", c.via()));
        transicion(id, "Clasificada", usuario, cambia ? "cambio de vía: " + motivo : null);
    }

    @Transactional
    public void descartar(long id, Descarte d, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        String motivo = d == null || d.motivo() == null ? "" : d.motivo().trim();
        if (motivo.isEmpty()) {
            throw new ErrorPlanificacion(400, "motivo-requerido", "Escribe el motivo del descarte");
        }
        exigirEstado(id, Set.of("Registrada", "Clasificada"), "descartar");
        jdbc.update(
                "UPDATE mod03_incidencia SET estado = 'Descartada', fecha_cierre = now() WHERE id ="
                        + " :id",
                Map.of("id", id));
        transicion(id, "Descartada", usuario, motivo);
    }

    @Transactional
    public void solicitarInspeccion(long id, SolicitudInspeccion s, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        exigirEstado(id, Set.of("Registrada"), "pedir inspección de");
        if (inspectores(usuario).stream().noneMatch(i -> i.id() == s.responsableId())) {
            throw new ErrorPlanificacion(
                    400,
                    "inspector-invalido",
                    "El responsable tiene que ser ingeniero o practicante");
        }
        jdbc.update(
                """
                INSERT INTO mod03_inspeccion_incidencia (incidencia_id, responsable_id, que_verificar,
                    solicitada_por_id)
                VALUES (:id, :responsable, :que, :usuario)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("responsable", s.responsableId())
                        .addValue(
                                "que",
                                s.queVerificar() == null || s.queVerificar().isBlank()
                                        ? null
                                        : s.queVerificar().trim())
                        .addValue("usuario", usuario.id()));
    }

    /**
     * Tercerizada si el ejemplar mide 5 m o más (RN-005); si no, o sin altura, personal propio.
     * MOD-02 aún no guarda la altura (dasometría), así que de momento siempre es personal propio.
     * Cuando la guarde, aquí se compara con 5 m.
     */
    String viaSugerida(long id) {
        return "PersonalPropio";
    }

    private void exigirEstado(long id, Set<String> permitidos, String accion) {
        List<String> estado =
                jdbc.queryForList(
                        "SELECT estado FROM mod03_incidencia WHERE id = :id FOR UPDATE",
                        Map.of("id", id),
                        String.class);
        if (estado.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "incidencia-no-encontrada", "No existe esa incidencia");
        }
        if (!permitidos.contains(estado.getFirst())) {
            throw new ErrorPlanificacion(
                    409,
                    "transicion-invalida",
                    "No se puede " + accion + " una incidencia " + estado.getFirst());
        }
    }

    private void transicion(long id, String estado, UsuarioPlanificacion usuario, String detalle) {
        jdbc.update(
                "INSERT INTO mod03_historial_incidencia (incidencia_id, estado, usuario_id,"
                        + " detalle) VALUES (:id, :estado, :usuario, :detalle)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("estado", estado)
                        .addValue("usuario", usuario.id())
                        .addValue("detalle", detalle));
    }
}
