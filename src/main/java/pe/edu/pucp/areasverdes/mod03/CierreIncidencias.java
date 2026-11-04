package pe.edu.pucp.areasverdes.mod03;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revisión de la atención de una incidencia (AV-279, RF-INC-005, HU-024 y CU-024): el jefe la
 * cierra, con evidencia obligatoria, o la devuelve a En atención con observaciones. El aviso al
 * reportante lo añade AV-281; la ficha ya dice a quién se avisa.
 */
@Service
public class CierreIncidencias {

    public record Revision(
            @Size(max = 300, message = "Máximo 300 caracteres") String observaciones) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final Incidencias incidencias;
    private final Avisos avisos;

    public CierreIncidencias(
            NamedParameterJdbcTemplate jdbc, Incidencias incidencias, Avisos avisos) {
        this.jdbc = jdbc;
        this.incidencias = incidencias;
        this.avisos = avisos;
    }

    @Transactional
    public void cerrar(long id, Revision r, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        exigirAtendida(id, "cerrar");
        if (incidencias.evidencias(id).isEmpty()) {
            throw new ErrorPlanificacion(
                    409, "sin-evidencia", "No se puede cerrar sin evidencia de la atención");
        }
        jdbc.update(
                "UPDATE mod03_incidencia SET estado = 'Cerrada', fecha_cierre = now() WHERE id ="
                        + " :id",
                Map.of("id", id));
        transicion(id, "Cerrada", usuario, texto(r));
        // AV-281: quien la reportó, si es usuario del sistema, recibe el aviso del cierre.
        Map<String, Object> i =
                jdbc.queryForMap(
                        "SELECT codigo, reportante_usuario_id FROM mod03_incidencia WHERE id = :id",
                        Map.of("id", id));
        if (i.get("reportante_usuario_id") != null) {
            avisos.aUsuario(
                    ((Number) i.get("reportante_usuario_id")).longValue(),
                    "%s cerrada: gracias por reportarla".formatted(i.get("codigo")),
                    "incidencia",
                    id);
        }
    }

    @Transactional
    public void devolver(long id, Revision r, UsuarioPlanificacion usuario) {
        Acceso.gestionarIncidencias(usuario);
        String observaciones = texto(r);
        if (observaciones == null) {
            throw new ErrorPlanificacion(
                    400, "observaciones-requeridas", "Escribe qué falta por atender");
        }
        exigirAtendida(id, "devolver");
        jdbc.update(
                "UPDATE mod03_incidencia SET estado = 'EnAtencion' WHERE id = :id",
                Map.of("id", id));
        transicion(id, "EnAtencion", usuario, "devuelta: " + observaciones);
    }

    private void exigirAtendida(long id, String accion) {
        List<String> estado =
                jdbc.queryForList(
                        "SELECT estado FROM mod03_incidencia WHERE id = :id FOR UPDATE",
                        Map.of("id", id),
                        String.class);
        if (estado.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "incidencia-no-encontrada", "No existe esa incidencia");
        }
        if (!"Atendida".equals(estado.getFirst())) {
            throw new ErrorPlanificacion(
                    409,
                    "transicion-invalida",
                    "Solo se puede " + accion + " una incidencia Atendida");
        }
    }

    private static String texto(Revision r) {
        return r == null || r.observaciones() == null || r.observaciones().isBlank()
                ? null
                : r.observaciones().trim();
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
