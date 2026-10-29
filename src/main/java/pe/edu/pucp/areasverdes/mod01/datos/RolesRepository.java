package pe.edu.pucp.areasverdes.mod01.datos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Roles y permisos de MOD-01 ({@code core_rol}, {@code core_permiso}, {@code core_rol_permiso}).
 */
@Repository
public class RolesRepository {

    public record Rol(long id, String codigo, String nombre, String descripcion, boolean activo) {}

    public record Permiso(String codigo, String modulo, String descripcion, boolean esSensible) {}

    private final NamedParameterJdbcTemplate jdbc;

    public RolesRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Rol> listar() {
        return jdbc.query(
                "SELECT id, codigo, nombre, descripcion, activo FROM core_rol ORDER BY id",
                new DataClassRowMapper<>(Rol.class));
    }

    public Optional<Rol> porCodigo(String codigo) {
        return jdbc
                .query(
                        "SELECT id, codigo, nombre, descripcion, activo FROM core_rol"
                                + " WHERE codigo = :codigo",
                        Map.of("codigo", codigo),
                        new DataClassRowMapper<>(Rol.class))
                .stream()
                .findFirst();
    }

    public Set<String> permisos(long rolId) {
        return new TreeSet<>(
                jdbc.queryForList(
                        """
                        SELECT p.codigo FROM core_rol_permiso rp
                        JOIN core_permiso p ON p.id = rp.permiso_id
                        WHERE rp.rol_id = :rol
                        """,
                        Map.of("rol", rolId),
                        String.class));
    }

    public List<Permiso> catalogoPermisos() {
        return jdbc.query(
                "SELECT codigo, modulo, descripcion, es_sensible FROM core_permiso ORDER BY codigo",
                new DataClassRowMapper<>(Permiso.class));
    }

    /** Permisos de cada rol, por código de rol, para el resumen de CU-MOD01-005. */
    public Map<String, List<String>> permisosPorRol() {
        Map<String, List<String>> resultado = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT r.codigo rol, p.codigo permiso FROM core_rol r
                JOIN core_rol_permiso rp ON rp.rol_id = r.id
                JOIN core_permiso p ON p.id = rp.permiso_id
                ORDER BY r.id, p.codigo
                """,
                fila -> {
                    resultado
                            .computeIfAbsent(fila.getString("rol"), k -> new ArrayList<>())
                            .add(fila.getString("permiso"));
                });
        return resultado;
    }
}
