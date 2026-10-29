package pe.edu.pucp.areasverdes.mod04.servicios;

import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.number;
import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.values;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class PrecargaCampoService {
    final Mod04Repository repo;
    final ObjectMapper json;
    final PasswordEncoder passwords;

    public PrecargaCampoService(
            Mod04Repository repo, ObjectMapper json, PasswordEncoder passwords) {
        this.repo = repo;
        this.json = json;
        this.passwords = passwords;
    }

    List<Map<String, Object>> leer(Path folder, String file) throws IOException {
        return json.readValue(folder.resolve(file).toFile(), new TypeReference<>() {});
    }

    long cuenta(String name, String role, String password) {
        String codigo = Usuario.JEFE.equals(role) ? "jefe-seccion" : "capataz";
        var existing =
                repo.rows(
                        """
                        SELECT u.id,u.activo,r.codigo FROM core_usuario u JOIN core_rol r ON r.id=u.rol_id
                        WHERE u.nombre_usuario=:nombre
                        """,
                        Map.of("nombre", name));
        if (!existing.isEmpty()) {
            var usuario = existing.getFirst();
            if (!Boolean.TRUE.equals(usuario.get("activo"))
                    || !codigo.equals(usuario.get("codigo")))
                throw new IllegalStateException("Conflicto de cuenta de demostración: " + name);
            return number(usuario, "id");
        }
        return repo.jdbc()
                .queryForObject(
                        """
                        INSERT INTO core_usuario(nombre_usuario,nombre,credencial_hash,rol_id)
                        SELECT :nombre,:nombre,:hash,r.id FROM core_rol r WHERE r.codigo=:rol
                        RETURNING id
                        """,
                        Map.of("nombre", name, "hash", passwords.encode(password), "rol", codigo),
                        Long.class);
    }

    @Transactional
    public void cargar(Path folder, String password) {
        if (password == null || password.length() < 12)
            throw new IllegalArgumentException(
                    "MOD04_DEMO_PASSWORD debe tener al menos 12 caracteres");
        repo.jdbc().getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(404005)");
        // Solo inicializa un módulo vacío. Nunca reemplaza datos ni composiciones ya registradas.
        if (repo.jdbc().queryForObject("SELECT count(*) FROM mod04_personal", Map.of(), Long.class)
                > 0) return;
        try {
            long jefe = cuenta("mod04_jefe", Usuario.JEFE, password);
            for (var p : leer(folder, "mod04/Personal.json")) {
                String tipo = (String) p.get("tipo_personal");
                if (!Set.of("Capataz", "Operario").contains(tipo)) continue;
                long id = number(p, "id");
                Long user =
                        tipo.equals("Capataz")
                                ? cuenta("mod04_capataz_" + id, "Capataz", password)
                                : null;
                repo.insert(
                        "personal",
                        values(
                                "id",
                                id,
                                "nombres",
                                p.get("nombres"),
                                "apellidos",
                                p.get("apellidos"),
                                "tipo_personal",
                                tipo,
                                "activo",
                                p.get("activo"),
                                "usuario_id",
                                user));
            }
            for (String[] fuente :
                    List.<String[]>of(
                            new String[] {"tipo_actividad", "mod04/TipoActividad.json"})) {
                for (var row : leer(folder, fuente[1]))
                    repo.insert(
                            fuente[0],
                            values(
                                    "id",
                                    row.get("id"),
                                    "nombre",
                                    row.get("nombre"),
                                    "activo",
                                    row.get("activo")));
            }
            for (var row : leer(folder, "mod04/Cuadrilla.json"))
                repo.insert(
                        "cuadrilla",
                        values(
                                "id",
                                row.get("id"),
                                "nombre",
                                row.get("nombre"),
                                "capataz_id",
                                row.get("capataz_id"),
                                "activa",
                                row.get("activa"),
                                "usuario_creacion_id",
                                jefe,
                                "usuario_modificacion_id",
                                jefe));
            for (var row : leer(folder, "mod04/AsignacionCuadrilla.json"))
                repo.insert(
                        "asignacion_cuadrilla",
                        values(
                                "id",
                                row.get("id"),
                                "cuadrilla_id",
                                row.get("cuadrilla_id"),
                                "operario_id",
                                row.get("operario_id"),
                                "fecha_inicio",
                                LocalDate.parse((String) row.get("fecha_inicio")),
                                "fecha_fin",
                                row.get("fecha_fin") == null
                                        ? null
                                        : LocalDate.parse((String) row.get("fecha_fin"))));
            for (String table :
                    List.of("personal", "tipo_actividad", "cuadrilla", "asignacion_cuadrilla"))
                repo.jdbc()
                        .getJdbcTemplate()
                        .execute(
                                "SELECT setval(pg_get_serial_sequence('mod04_"
                                        + table
                                        + "','id'),coalesce((SELECT max(id) FROM mod04_"
                                        + table
                                        + "),1),EXISTS(SELECT 1 FROM mod04_"
                                        + table
                                        + "))");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Precarga MOD-04 inválida; se revierte toda la operación", e);
        }
    }
}
