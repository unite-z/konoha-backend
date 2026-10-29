package pe.edu.pucp.areasverdes.mod04.servicios;

import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.number;
import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.values;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod04.api.Entradas;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod05.ApiError;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
@Transactional(readOnly = true)
public class OperacionCampoService {
    public static final String CAPATAZ = "Capataz";

    final Mod04Repository repo;

    final Clock clock;

    public OperacionCampoService(Mod04Repository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public static boolean jefe(Usuario user) {
        return user != null && user.activo() && user.roles().contains(Usuario.JEFE);
    }

    public static void exigirJefe(Usuario user) {
        if (!jefe(user)) throw forbidden();
    }

    static ApiError forbidden() {
        return new ApiError(403, "SinPermiso", "Tu usuario no tiene permisos para esta operación");
    }

    public void exigirLectura(Usuario user) {
        if (user == null
                || !user.activo()
                || user.roles().stream()
                        .noneMatch(r -> Usuario.ROLES.contains(r) || CAPATAZ.equals(r)))
            throw forbidden();
    }

    public Long personalUsuario(Usuario user) {
        exigirLectura(user);
        var people =
                repo.rows(
                        "SELECT id FROM mod04_personal WHERE usuario_id=:id AND activo AND"
                                + " tipo_personal='Capataz'",
                        Map.of("id", user.id()));
        return people.isEmpty() ? null : number(people.getFirst(), "id");
    }

    void exigirCapataz(long capataz, Usuario user, boolean escritura) {
        exigirLectura(user);
        if (jefe(user)) return;
        if (!escritura && user.roles().stream().anyMatch(Usuario.ROLES::contains)) return;
        if (!user.roles().contains(CAPATAZ) || !Objects.equals(personalUsuario(user), capataz))
            throw forbidden();
    }

    void personalValido(long id, String tipo) {
        var person = repo.get("personal", id, true);
        if (!Boolean.TRUE.equals(person.get("activo")) || !tipo.equals(person.get("tipo_personal")))
            throw ApiError.invalid("Selecciona personal activo de tipo " + tipo);
    }

    static void sinDuplicados(List<Long> ids) {
        if (new HashSet<>(ids).size() != ids.size())
            throw ApiError.invalid("La lista de operarios contiene duplicados");
    }

    void catalogos(long tipo, long area) {
        if (!Boolean.TRUE.equals(repo.get("tipo_actividad", tipo, false).get("activo"))
                || !Boolean.TRUE.equals(repo.get("areaverde", area, false).get("activo")))
            throw ApiError.invalid("El tipo de actividad y el área verde deben estar activos");
    }

    @Transactional
    public Map<String, Object> crearPersonal(Entradas.Personal body, Usuario user) {
        exigirJefe(user);
        if (body.usuarioId() != null) {
            if (!body.tipoPersonal().equals(CAPATAZ))
                throw ApiError.invalid("Solo un capataz puede vincularse a una cuenta");
            var accounts =
                    repo.rows(
                            """
                            SELECT u.id FROM core_usuario u JOIN core_rol r ON r.id=u.rol_id
                            WHERE u.id=:id AND u.activo AND r.codigo='capataz'
                            """,
                            Map.of("id", body.usuarioId()));
            if (accounts.isEmpty())
                throw ApiError.invalid("La cuenta debe estar activa y tener el rol Capataz");
        }
        long id =
                repo.insert(
                        "personal",
                        values(
                                "nombres",
                                body.nombres().strip(),
                                "apellidos",
                                body.apellidos().strip(),
                                "tipo_personal",
                                body.tipoPersonal(),
                                "usuario_id",
                                body.usuarioId(),
                                "activo",
                                true));
        var result = repo.get("personal", id, false);
        repo.audit("Personal", id, "Crear", user.id(), Map.of(), result);
        return result;
    }

    @Transactional
    public Map<String, Object> guardarCuadrilla(Long id, Entradas.Cuadrilla body, Usuario user) {
        exigirJefe(user);
        sinDuplicados(body.operarios());
        if (body.operarios().contains(body.capatazId()))
            throw ApiError.invalid("El capataz no puede figurar como operario");
        if (!body.activa() && !body.operarios().isEmpty())
            throw ApiError.invalid("Una cuadrilla inactiva no admite operarios asignados");
        Map<String, Object> before = Map.of();
        if (id != null) {
            repo.get("cuadrilla", id, true);
            before = repo.cuadrilla(id);
        }
        // Bloqueo de personal en orden estable: serializa asignaciones del mismo operario entre
        // cuadrillas.
        var locks = new TreeSet<>(body.operarios());
        locks.add(body.capatazId());
        for (long person : locks)
            personalValido(person, person == body.capatazId() ? CAPATAZ : "Operario");
        if (body.activa()
                && !repo.rows(
                                "SELECT id FROM mod04_cuadrilla WHERE activa AND capataz_id=:c AND"
                                        + " id<>:id",
                                Map.of("c", body.capatazId(), "id", id == null ? 0 : id))
                        .isEmpty())
            throw ApiError.conflict("El capataz ya tiene una cuadrilla activa");
        for (long operario : body.operarios()) {
            var current =
                    repo.rows(
                            "SELECT cuadrilla_id FROM mod04_asignacion_cuadrilla WHERE"
                                    + " operario_id=:o AND fecha_fin IS NULL",
                            Map.of("o", operario));
            if (!current.isEmpty()
                    && (id == null || number(current.getFirst(), "cuadrilla_id") != id))
                throw ApiError.conflict(
                        "El operario "
                                + operario
                                + " ya pertenece a otra cuadrilla; retíralo antes de reasignarlo");
        }
        if (id != null
                && (!body.activa() || number(before, "capataz_id") != body.capatazId())
                && !repo.rows(
                                "SELECT id FROM mod04_parte_diario WHERE cuadrilla_id=:id AND"
                                        + " estado='Abierto'",
                                Map.of("id", id))
                        .isEmpty())
            throw ApiError.conflict("No se puede desactivar o cambiar capataz con partes abiertos");
        var data =
                values(
                        "nombre",
                        body.nombre().strip(),
                        "capataz_id",
                        body.capatazId(),
                        "activa",
                        body.activa(),
                        "fecha_modificacion",
                        OffsetDateTime.now(clock),
                        "usuario_modificacion_id",
                        user.id());
        if (id == null) {
            data.put("usuario_creacion_id", user.id());
            id = repo.insert("cuadrilla", data);
        } else repo.update("cuadrilla", id, data);
        var current =
                repo.rows(
                        "SELECT id,operario_id FROM mod04_asignacion_cuadrilla WHERE"
                                + " cuadrilla_id=:id AND fecha_fin IS NULL FOR UPDATE",
                        Map.of("id", id));
        Set<Long> existing = new HashSet<>();
        for (var row : current) {
            long operario = number(row, "operario_id");
            existing.add(operario);
            if (!body.operarios().contains(operario))
                repo.update(
                        "asignacion_cuadrilla",
                        number(row, "id"),
                        values("fecha_fin", LocalDate.now(clock)));
        }
        for (long operario : body.operarios())
            if (!existing.contains(operario))
                repo.insert(
                        "asignacion_cuadrilla",
                        values(
                                "cuadrilla_id",
                                id,
                                "operario_id",
                                operario,
                                "fecha_inicio",
                                LocalDate.now(clock)));
        var after = repo.cuadrilla(id);
        repo.audit(
                "Cuadrilla",
                id,
                before.isEmpty() ? "Crear" : "Actualizar",
                user.id(),
                before,
                after);
        return after;
    }

    public Map<String, Object> cuadrilla(long id, Usuario user) {
        var result = repo.cuadrilla(id);
        exigirCapataz(number(result, "capataz_id"), user, false);
        return result;
    }
}
