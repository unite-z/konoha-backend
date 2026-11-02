package pe.edu.pucp.areasverdes.mod02.datos;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.Asignacion;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.Persona;

/** Consultas de persistencia de sectores y cuentas de capataz. */
@Repository
public class CatalogosMod02Repository {

    private final NamedParameterJdbcTemplate jdbc;

    public CatalogosMod02Repository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Persona> capatacesActivos() {
        return jdbc.query(
                "SELECT u.id, u.nombre FROM core_usuario u "
                        + "JOIN core_rol r ON r.id = u.rol_id "
                        + "WHERE u.activo AND r.codigo = 'capataz' "
                        + "ORDER BY lower(u.nombre), u.id",
                Map.of(),
                new DataClassRowMapper<>(Persona.class));
    }

    public boolean esCapatazActivo(long usuarioId) {
        return Boolean.TRUE.equals(
                jdbc.query(
                        "SELECT EXISTS (SELECT 1 FROM core_usuario u "
                                + "JOIN core_rol r ON r.id = u.rol_id "
                                + "WHERE u.id = :id AND u.activo AND r.codigo = 'capataz')",
                        Map.of("id", usuarioId),
                        rs -> rs.next() && rs.getBoolean(1)));
    }

    public boolean existeSector(long sectorId) {
        return Boolean.TRUE.equals(
                jdbc.query(
                        "SELECT EXISTS (SELECT 1 FROM mod02_sector WHERE id = :id)",
                        Map.of("id", sectorId),
                        rs -> rs.next() && rs.getBoolean(1)));
    }

    public List<Asignacion> historialCapataz(long sectorId) {
        return jdbc.query(
                "SELECT h.id, h.capataz_id, u.nombre capataz_nombre, h.fecha_inicio::text"
                        + " fecha_inicio, h.fecha_fin::text fecha_fin FROM"
                        + " mod02_historial_capataz_sector h JOIN core_usuario u ON u.id ="
                        + " h.capataz_id WHERE h.sector_id = :id ORDER BY h.fecha_inicio DESC, h.id"
                        + " DESC",
                Map.of("id", sectorId),
                new DataClassRowMapper<>(Asignacion.class));
    }
}
