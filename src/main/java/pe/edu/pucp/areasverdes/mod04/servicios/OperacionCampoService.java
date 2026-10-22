package pe.edu.pucp.areasverdes.mod04.servicios;

import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.number;
import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.values;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.AreasVerdesApplication;
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
                            SELECT u.id FROM mod05_usuario u JOIN mod05_usuario_rol r ON r.usuario_id=u.id
                            WHERE u.id=:id AND u.activo AND r.rol='Capataz'
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

    @Transactional
    public Map<String, Object> guardarEncargo(Long id, Entradas.Encargo body, Usuario user) {
        exigirJefe(user);
        long days = ChronoUnit.DAYS.between(body.fechaInicio(), body.fechaFin()) + 1;
        int max = body.periodicidad().equals("Semanal") ? 7 : 14;
        if (days < 1 || days > max)
            throw ApiError.invalid(
                    "El periodo debe tener entre 1 y " + max + " días, incluidos inicio y fin");
        if (body.plazoPrevisto().isBefore(body.fechaInicio())
                || body.plazoPrevisto().isAfter(body.fechaFin()))
            throw ApiError.invalid("El plazo debe estar dentro del periodo de planificación");
        if (body.fechaReunion().isAfter(body.fechaFin()))
            throw ApiError.invalid("La reunión no puede ser posterior al periodo");
        var squad = repo.get("cuadrilla", body.cuadrillaId(), true);
        if (!Boolean.TRUE.equals(squad.get("activa")))
            throw ApiError.conflict("La cuadrilla está inactiva");
        personalValido(number(squad, "capataz_id"), CAPATAZ);
        catalogos(body.tipoActividadId(), body.areaVerdeId());
        Map<String, Object> before = Map.of();
        if (id != null) {
            before = repo.get("encargo", id, true);
            if (!repo.rows("SELECT id FROM mod04_actividad WHERE encargo_id=:id", Map.of("id", id))
                    .isEmpty())
                throw ApiError.conflict(
                        "El encargo ya tiene ejecución registrada y no puede redefinirse");
        }
        var data =
                values(
                        "cuadrilla_id",
                        body.cuadrillaId(),
                        "capataz_id",
                        squad.get("capataz_id"),
                        "tipo_actividad_id",
                        body.tipoActividadId(),
                        "area_verde_id",
                        body.areaVerdeId(),
                        "descripcion",
                        body.descripcion().strip(),
                        "periodicidad",
                        body.periodicidad(),
                        "fecha_reunion",
                        body.fechaReunion(),
                        "fecha_inicio",
                        body.fechaInicio(),
                        "fecha_fin",
                        body.fechaFin(),
                        "plazo_previsto",
                        body.plazoPrevisto(),
                        "fecha_modificacion",
                        OffsetDateTime.now(clock),
                        "usuario_modificacion_id",
                        user.id());
        if (id == null) {
            data.put("usuario_creacion_id", user.id());
            id = repo.insert("encargo", data);
        } else repo.update("encargo", id, data);
        var result = repo.encargo(id);
        repo.audit(
                "Encargo",
                id,
                before.isEmpty() ? "Crear" : "Actualizar",
                user.id(),
                before,
                result);
        return result;
    }

    public Map<String, Object> encargo(long id, Usuario user) {
        var result = repo.encargo(id);
        exigirCapataz(number(result, "capataz_id"), user, false);
        return result;
    }

    Map<String, Object> parteDeActividad(long id, Usuario user, boolean write) {
        var activity = repo.get("actividad", id, write);
        var part = repo.get("parte_diario", number(activity, "parte_id"), write);
        exigirCapataz(number(part, "capataz_id"), user, write);
        if (write && !part.get("estado").equals("Abierto"))
            throw ApiError.conflict(
                    "El parte está cerrado; no se pueden cambiar sus participantes");
        return part;
    }

    public Map<String, Object> participantes(long id, Usuario user) {
        parteDeActividad(id, user, false);
        return repo.participantes(id);
    }

    public List<Map<String, Object>> operariosDisponibles(long id, Usuario user) {
        var part = parteDeActividad(id, user, false);
        return repo.rows(
                """
                SELECT p.id,p.nombres,p.apellidos,p.activo FROM mod04_personal p
                WHERE p.activo AND p.tipo_personal='Operario' AND EXISTS (
                    SELECT 1 FROM mod04_asignacion_cuadrilla a WHERE a.operario_id=p.id AND a.cuadrilla_id=:c
                    AND a.fecha_inicio<=:fecha AND (a.fecha_fin IS NULL OR a.fecha_fin>:fecha)) ORDER BY p.id
                """,
                Map.of("c", part.get("cuadrilla_id"), "fecha", part.get("fecha")));
    }

    public Map<String, Object> actividad(long id, Usuario user) {
        var part = parteDeActividad(id, user, false);
        var result = repo.get("actividad", id, false);
        result.put("cuadrilla_id", part.get("cuadrilla_id"));
        result.put("capataz_id", part.get("capataz_id"));
        result.put("fecha", part.get("fecha"));
        result.put("estado", part.get("estado"));
        result.put(
                "cuadrilla_nombre",
                repo.get("cuadrilla", number(part, "cuadrilla_id"), false).get("nombre"));
        result.put(
                "tipo_actividad_nombre",
                repo.get("tipo_actividad", number(result, "tipo_actividad_id"), false)
                        .get("nombre"));
        result.put(
                "area_verde_nombre",
                repo.get("areaverde", number(result, "area_verde_id"), false).get("nombre"));
        result.put("participantes", repo.participantes(id));
        return result;
    }

    @Transactional
    public Map<String, Object> guardarParticipantes(
            long id, Entradas.Participantes body, Usuario user) {
        sinDuplicados(body.operarios());
        var part = parteDeActividad(id, user, true);
        for (long person : new TreeSet<>(body.operarios())) {
            personalValido(person, "Operario");
            var assigned =
                    repo.rows(
                            """
                            SELECT id FROM mod04_asignacion_cuadrilla WHERE cuadrilla_id=:c AND operario_id=:o
                              AND fecha_inicio<=:fecha AND (fecha_fin IS NULL OR fecha_fin>:fecha)
                            """,
                            Map.of(
                                    "c",
                                    part.get("cuadrilla_id"),
                                    "o",
                                    person,
                                    "fecha",
                                    part.get("fecha")));
            if (assigned.isEmpty())
                throw ApiError.invalid(
                        "El operario "
                                + person
                                + " no pertenecía a la cuadrilla en la fecha de la actividad");
        }
        var before = repo.participantes(id);
        repo.jdbc()
                .update(
                        "DELETE FROM mod04_participacion_actividad WHERE actividad_id=:id",
                        Map.of("id", id));
        for (long person : body.operarios())
            repo.jdbc()
                    .update(
                            "INSERT INTO mod04_participacion_actividad VALUES (:a,:o)",
                            Map.of("a", id, "o", person));
        var result = repo.participantes(id);
        repo.audit("ParticipacionActividad", id, "Reemplazar", user.id(), before, result);
        return result;
    }

    @Transactional
    public Map<String, Object> crearParte(Entradas.Parte body, Usuario user) {
        var squad = repo.get("cuadrilla", body.cuadrillaId(), true);
        exigirCapataz(number(squad, "capataz_id"), user, true);
        if (!Boolean.TRUE.equals(squad.get("activa")))
            throw ApiError.conflict("La cuadrilla está inactiva");
        if (body.fecha().isAfter(LocalDate.now(clock)))
            throw ApiError.invalid("El parte no puede tener fecha futura");
        long id =
                repo.insert(
                        "parte_diario",
                        values(
                                "cuadrilla_id",
                                body.cuadrillaId(),
                                "capataz_id",
                                squad.get("capataz_id"),
                                "fecha",
                                body.fecha(),
                                "usuario_creacion_id",
                                user.id()));
        var result = repo.get("parte_diario", id, false);
        repo.audit("ParteDiario", id, "Crear", user.id(), Map.of(), result);
        return result;
    }

    @Transactional
    public Map<String, Object> crearActividad(Entradas.Actividad body, Usuario user) {
        var part = repo.get("parte_diario", body.parteId(), true);
        exigirCapataz(number(part, "capataz_id"), user, true);
        if (!part.get("estado").equals("Abierto")) throw ApiError.conflict("El parte está cerrado");
        if (!body.fechaHoraActividad()
                .atZoneSameInstant(AreasVerdesApplication.LIMA)
                .toLocalDate()
                .equals(part.get("fecha")))
            throw ApiError.invalid("La actividad debe corresponder a la fecha del parte en Lima");
        if (body.fechaHoraActividad().toInstant().isAfter(clock.instant()))
            throw ApiError.invalid("La actividad no puede estar en el futuro");
        catalogos(body.tipoActividadId(), body.areaVerdeId());
        if (body.encargoId() != null) {
            var task = repo.get("encargo", body.encargoId(), true);
            LocalDate day = (LocalDate) part.get("fecha");
            if (number(task, "cuadrilla_id") != number(part, "cuadrilla_id")
                    || number(task, "capataz_id") != number(part, "capataz_id")
                    || number(task, "tipo_actividad_id") != body.tipoActividadId()
                    || number(task, "area_verde_id") != body.areaVerdeId()
                    || day.isBefore((LocalDate) task.get("fecha_inicio"))
                    || day.isAfter((LocalDate) task.get("fecha_fin")))
                throw ApiError.invalid(
                        "El encargo no coincide con la cuadrilla, capataz, actividad, espacio o"
                                + " periodo");
        }
        long id =
                repo.insert(
                        "actividad",
                        values(
                                "parte_id",
                                body.parteId(),
                                "tipo_actividad_id",
                                body.tipoActividadId(),
                                "area_verde_id",
                                body.areaVerdeId(),
                                "fecha_hora_actividad",
                                body.fechaHoraActividad(),
                                "encargo_id",
                                body.encargoId(),
                                "observaciones",
                                body.observaciones(),
                                "usuario_creacion_id",
                                user.id()));
        var result = repo.get("actividad", id, false);
        repo.audit("Actividad", id, "Crear", user.id(), Map.of(), result);
        return actividad(id, user);
    }
}
