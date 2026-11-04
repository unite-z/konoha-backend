package pe.edu.pucp.areasverdes.mod03;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plazo de las incidencias sencillas (AV-280, RF-INC-008, RN-019): el nivel se calcula en cada
 * consulta de la bandeja ({@code nivel_plazo}); aquí van las acciones de la alerta, anotar la causa
 * del retraso y reasignar el capataz responsable.
 */
@Service
public class PlazosIncidencias {

    public record CausaRetraso(@Size(max = 300, message = "Máximo 300 caracteres") String causa) {}

    public record Reasignacion(@NotNull(message = "Indica el capataz") Long capatazId) {}

    /** Capataz activo de MOD-04 con su cuadrilla. La carga del día llega con AV-277. */
    public record Capataz(long id, String nombre, String cuadrilla) {}

    private final NamedParameterJdbcTemplate jdbc;

    public PlazosIncidencias(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void anotarCausa(long id, CausaRetraso c, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        String causa = c == null || c.causa() == null ? "" : c.causa().trim();
        if (causa.isEmpty()) {
            throw new ErrorPlanificacion(400, "causa-requerida", "Escribe la causa del retraso");
        }
        exigirAbierta(id);
        jdbc.update(
                "INSERT INTO mod03_causa_retraso (incidencia_id, causa, usuario_id) VALUES (:id,"
                        + " :causa, :usuario)",
                Map.of("id", id, "causa", causa, "usuario", usuario.id()));
    }

    public List<Capataz> capataces(long id, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        return jdbc.query(
                """
                SELECT p.id, p.nombres || ' ' || left(p.apellidos, 1) || '.' nombre,
                    min(c.nombre) cuadrilla
                FROM mod04_personal p JOIN mod04_cuadrilla c ON c.capataz_id = p.id AND c.activa
                WHERE p.activo AND p.tipo_personal = 'Capataz'
                GROUP BY p.id, p.nombres, p.apellidos ORDER BY nombre
                """,
                (rs, n) ->
                        new Capataz(
                                rs.getLong("id"),
                                rs.getString("nombre"),
                                rs.getString("cuadrilla")));
    }

    @Transactional
    public void reasignar(long id, Reasignacion r, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        String via = exigirAbierta(id);
        if ("Tercerizada".equals(via)) {
            throw new ErrorPlanificacion(
                    409, "incidencia-tercerizada", "La atiende un proveedor, no un capataz");
        }
        if (capataces(id, usuario).stream().noneMatch(c -> c.id() == r.capatazId())) {
            throw new ErrorPlanificacion(400, "capataz-invalido", "No existe ese capataz activo");
        }
        jdbc.update(
                "UPDATE mod03_incidencia SET capataz_id = :capataz WHERE id = :id",
                Map.of("id", id, "capataz", r.capatazId()));
    }

    /** La vía de la incidencia si sigue abierta; 404 si no existe y 409 si ya se cerró. */
    private String exigirAbierta(long id) {
        List<Map<String, Object>> filas =
                jdbc.queryForList(
                        "SELECT estado, via FROM mod03_incidencia WHERE id = :id FOR UPDATE",
                        Map.of("id", id));
        if (filas.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "incidencia-no-encontrada", "No existe esa incidencia");
        }
        String estado = (String) filas.getFirst().get("estado");
        if (List.of("Cerrada", "Descartada").contains(estado)) {
            throw new ErrorPlanificacion(
                    409, "incidencia-cerrada", "Esa incidencia ya está cerrada");
        }
        return (String) filas.getFirst().get("via");
    }
}
