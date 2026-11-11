package pe.edu.pucp.areasverdes.mod04.servicios;

import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.number;
import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.values;

import java.math.BigDecimal;
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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.AreasVerdesApplication;
import pe.edu.pucp.areasverdes.mod01.servicios.ConsultaCampo;
import pe.edu.pucp.areasverdes.mod04.api.Entradas;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod05.ApiError;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
@Primary
@Transactional(readOnly = true)
public class OperacionCampoService implements ConsultaCampo {
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

    @Override
    public Set<Long> cuadrillasDelCapataz(long usuarioId) {
        return Set.copyOf(
                repo.jdbc()
                        .queryForList(
                                """
                                SELECT c.id FROM mod04_cuadrilla c JOIN mod04_personal p ON p.id=c.capataz_id
                                WHERE c.activa AND p.activo AND p.usuario_id=:usuario
                                """,
                                Map.of("usuario", usuarioId),
                                Long.class));
    }

    @Override
    public List<EncargoAbierto> encargosAbiertos(long usuarioId) {
        return repo.jdbc()
                .query(
                        """
                        SELECT e.id,e.descripcion FROM mod04_encargo e
                        JOIN mod04_personal p ON p.id=e.capataz_id
                        WHERE p.usuario_id=:usuario AND e.fecha_fin>=:hoy
                            AND e.estado NOT IN ('Completado','Cancelado') ORDER BY e.id
                        """,
                        Map.of("usuario", usuarioId, "hoy", LocalDate.now(clock)),
                        (fila, indice) ->
                                new EncargoAbierto(
                                        fila.getLong("id"), fila.getString("descripcion")));
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
        if (CAPATAZ.equals(body.tipoPersonal()) && body.usuarioId() == null)
            throw ApiError.invalid("Selecciona una cuenta de Capataz de Usuarios y roles");
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

    private long vincularCapataz(long usuarioId, Usuario autor) {
        var params = Map.of("id", usuarioId, "hoy", LocalDate.now(clock));
        var cuentas =
                repo.rows(
                        """
                        SELECT u.nombre FROM core_usuario u JOIN core_rol r ON r.id=u.rol_id
                        WHERE u.id=:id AND u.activo AND r.codigo='capataz'
                            AND (u.fecha_fin_vigencia IS NULL OR u.fecha_fin_vigencia>=:hoy)
                        FOR UPDATE OF u
                        """,
                        params);
        if (cuentas.isEmpty())
            throw ApiError.invalid("Selecciona una cuenta activa con el rol Capataz");
        var nuevas =
                repo.rows(
                        """
                        INSERT INTO mod04_personal (nombres,apellidos,tipo_personal,usuario_id)
                        VALUES (:nombre,'','Capataz',:id)
                        ON CONFLICT (usuario_id) DO NOTHING RETURNING *
                        """,
                        Map.of("id", usuarioId, "nombre", cuentas.getFirst().get("nombre")));
        if (!nuevas.isEmpty()) {
            var nueva = nuevas.getFirst();
            long id = number(nueva, "id");
            repo.audit("Personal", id, "Crear", autor.id(), Map.of(), nueva);
            return id;
        }
        return number(repo.one("SELECT id FROM mod04_personal WHERE usuario_id=:id", params), "id");
    }

    @Transactional
    public Map<String, Object> guardarCuadrilla(Long id, Entradas.Cuadrilla body, Usuario user) {
        exigirJefe(user);
        if ((body.capatazId() == null) == (body.capatazUsuarioId() == null))
            throw ApiError.invalid("Selecciona un capataz");
        sinDuplicados(body.operarios());
        if (!body.activa() && !body.operarios().isEmpty())
            throw ApiError.invalid("Una cuadrilla inactiva no admite operarios asignados");
        Long usuarioCapataz = body.capatazUsuarioId();
        if (usuarioCapataz == null && body.activa()) {
            Object cuenta = repo.get("personal", body.capatazId(), false).get("usuario_id");
            if (cuenta instanceof Number numero) usuarioCapataz = numero.longValue();
        }
        long capataz =
                usuarioCapataz == null ? body.capatazId() : vincularCapataz(usuarioCapataz, user);
        if (body.operarios().contains(capataz))
            throw ApiError.invalid("El capataz no puede figurar como operario");
        Map<String, Object> before = Map.of();
        if (id != null) {
            repo.get("cuadrilla", id, true);
            before = repo.cuadrilla(id);
        }
        // Bloqueo de personal en orden estable: serializa asignaciones del mismo operario entre
        // cuadrillas.
        var locks = new TreeSet<>(body.operarios());
        locks.add(capataz);
        for (long person : locks) personalValido(person, person == capataz ? CAPATAZ : "Operario");
        if (body.activa()
                && !repo.rows(
                                "SELECT id FROM mod04_cuadrilla WHERE activa AND capataz_id=:c AND"
                                        + " id<>:id",
                                Map.of("c", capataz, "id", id == null ? 0 : id))
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
                && (!body.activa() || number(before, "capataz_id") != capataz)
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
                        capataz,
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
        var squad = repo.get("cuadrilla", body.cuadrillaId(), true);
        if (!Boolean.TRUE.equals(squad.get("activa")))
            throw ApiError.conflict("La cuadrilla está inactiva");
        personalValido(number(squad, "capataz_id"), CAPATAZ);
        catalogos(body.tipoActividadId(), body.areaVerdeId());
        Map<String, Object> before = Map.of();
        if (id != null) {
            before = repo.get("encargo", id, true);
            if ("Cancelado".equals(before.get("estado")))
                throw ApiError.conflict("No se puede redefinir un encargo cancelado");
            if (!repo.rows("SELECT id FROM mod04_actividad WHERE encargo_id=:id", Map.of("id", id))
                    .isEmpty())
                throw ApiError.conflict(
                        "El encargo ya tiene ejecución registrada y no puede redefinirse");
        }
        boolean directo = "Directo".equals(body.periodicidad());
        LocalDate inicio = body.fechaInicio();
        LocalDate fin = body.fechaFin();
        LocalDate reunion = body.fechaReunion();
        if (directo) {
            if (inicio != null || fin != null || reunion != null)
                throw ApiError.invalid("El encargo directo solo requiere el plazo previsto");
            inicio =
                    "Directo".equals(before.get("periodicidad"))
                            ? (LocalDate) before.get("fecha_inicio")
                            : LocalDate.now(clock);
            fin = body.plazoPrevisto();
            if (fin.isBefore(inicio))
                throw ApiError.invalid("El plazo no puede ser anterior al inicio del encargo");
        } else {
            if (inicio == null || fin == null || reunion == null)
                throw ApiError.invalid("Indica la reunión y el periodo de planificación");
            long days = ChronoUnit.DAYS.between(inicio, fin) + 1;
            int max = body.periodicidad().equals("Semanal") ? 7 : 14;
            if (days < 1 || days > max)
                throw ApiError.invalid(
                        "El periodo debe tener entre 1 y " + max + " días, incluidos inicio y fin");
            if (body.plazoPrevisto().isBefore(inicio) || body.plazoPrevisto().isAfter(fin))
                throw ApiError.invalid("El plazo debe estar dentro del periodo de planificación");
            if (reunion.isAfter(fin))
                throw ApiError.invalid("La reunión no puede ser posterior al periodo");
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
                        reunion,
                        "fecha_inicio",
                        inicio,
                        "fecha_fin",
                        fin,
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

    @Transactional
    public Map<String, Object> cambiarEstadoEncargo(
            long id, Entradas.CambioEstadoEncargo body, Usuario user) {
        exigirLectura(user);
        boolean cancelar = "Cancelado".equals(body.estado());
        if (cancelar ? !jefe(user) : !user.roles().contains(CAPATAZ)) throw forbidden();
        var encargo = repo.get("encargo", id, true);
        if (!cancelar && !Objects.equals(personalUsuario(user), number(encargo, "capataz_id")))
            throw forbidden();
        String anterior = (String) encargo.get("estado");
        boolean permitida =
                switch (anterior) {
                    case "Por completar", "Observado" ->
                            cancelar || "En curso".equals(body.estado());
                    case "En curso" ->
                            cancelar || Set.of("Completado", "Observado").contains(body.estado());
                    default -> false;
                };
        if (!anterior.equals(body.estadoAnterior()) || !permitida)
            throw ApiError.conflict("El cambio de estado no está permitido para este encargo");
        String observaciones = Objects.toString(body.observaciones(), "").strip();
        if (cancelar && observaciones.isEmpty())
            throw ApiError.invalid("Indica el motivo de la cancelación");
        if ("Observado".equals(body.estado()) && observaciones.isEmpty())
            throw ApiError.invalid("Indica el motivo de la observación");
        var fecha = OffsetDateTime.now(clock);
        repo.update(
                "encargo",
                id,
                values(
                        "estado",
                        body.estado(),
                        "fecha_modificacion",
                        fecha,
                        "usuario_modificacion_id",
                        user.id()));
        repo.insert(
                "encargo_cambio_estado",
                values(
                        "encargo_id",
                        id,
                        "estado_anterior",
                        anterior,
                        "estado_nuevo",
                        body.estado(),
                        "observaciones",
                        observaciones,
                        "usuario_id",
                        user.id(),
                        "fecha",
                        fecha));
        return encargo(id, user);
    }

    public Map<String, Object> encargo(long id, Usuario user) {
        var result = repo.encargo(id);
        exigirCapataz(number(result, "capataz_id"), user, false);
        result.put("historial_estados", repo.historialEncargo(id));
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
                result.get("tipo_actividad_nombre") == null
                        ? repo.get("tipo_actividad", number(result, "tipo_actividad_id"), false)
                                .get("nombre")
                        : result.get("tipo_actividad_nombre"));
        result.put(
                "area_verde_nombre",
                repo.get("areaverde", number(result, "area_verde_id"), false).get("nombre"));
        result.put("participantes", repo.participantes(id));
        Object ejemplarId = result.get("ejemplar_id");
        if (ejemplarId != null) {
            var ejemplar =
                    repo.one(
                            "SELECT codigo,altura_total_m FROM mod02_ejemplar WHERE id=:id",
                            Map.of("id", ejemplarId));
            result.put("ejemplar_codigo", ejemplar.get("codigo"));
            result.put("ejemplar_altura_m", ejemplar.get("altura_total_m"));
        } else {
            result.put("ejemplar_codigo", null);
            result.put("ejemplar_altura_m", null);
        }
        Object verificador = result.get("verificado_por_usuario_id");
        result.put(
                "verificado_por_nombre",
                verificador == null
                        ? null
                        : repo.one(
                                        "SELECT nombre FROM core_usuario WHERE id=:id",
                                        Map.of("id", verificador))
                                .get("nombre"));
        return result;
    }

    @Transactional
    public Map<String, Object> verificarActividad(
            long id, Entradas.Verificacion body, Usuario user) {
        exigirJefe(user);
        var before = repo.get("actividad", id, true);
        repo.update(
                "actividad",
                id,
                values(
                        "verificacion_observaciones",
                        body.observaciones().strip(),
                        "verificado_por_usuario_id",
                        user.id(),
                        "fecha_verificacion",
                        OffsetDateTime.now(clock)));
        var after = repo.get("actividad", id, false);
        repo.audit("Actividad", id, "Verificar", user.id(), before, after);
        return actividad(id, user);
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
        var tipoActividad = repo.get("tipo_actividad", body.tipoActividadId(), false);
        long claseActividad = number(tipoActividad, "clase_id");
        if (body.claseActividadId() != null && body.claseActividadId() != claseActividad)
            throw ApiError.invalid("El tipo de actividad no pertenece a la clase seleccionada");
        if (body.fechaSolicitud() != null
                && body.fechaSolicitud().isAfter((LocalDate) part.get("fecha")))
            throw ApiError.invalid("La solicitud no puede ser posterior a la actividad");
        if (body.incidenciaId() != null && body.encargoId() != null)
            throw ApiError.invalid("Selecciona una incidencia o un encargo de origen");
        boolean podaMenor =
                "Poda menor".equalsIgnoreCase(String.valueOf(tipoActividad.get("nombre")).strip());
        boolean pendienteVinculacion = Boolean.TRUE.equals(body.pendienteVinculacion());
        if (podaMenor && (body.ejemplarId() == null) == !pendienteVinculacion)
            throw ApiError.invalid(
                    "La poda menor debe indicar un ejemplar o quedar pendiente de vinculación");
        if (!podaMenor && (body.ejemplarId() != null || pendienteVinculacion))
            throw ApiError.invalid("La referencia de ejemplar solo corresponde a una poda menor");
        if (body.ejemplarId() != null) validarEjemplarPoda(body.ejemplarId(), body.areaVerdeId());
        boolean hayCantidad = body.cantidadSolicitada() != null || body.cantidadEjecutada() != null;
        if (hayCantidad != (body.unidadMedidaId() != null))
            throw ApiError.invalid("Las cantidades requieren una unidad de medida");
        if (body.unidadMedidaId() != null
                && repo.rows(
                                "SELECT id FROM mod04_unidad_medida WHERE id=:id",
                                Map.of("id", body.unidadMedidaId()))
                        .isEmpty()) throw ApiError.invalid("La unidad de medida no existe");
        if (body.incidenciaId() != null
                && repo.rows(
                                "SELECT id FROM mod03_incidencia WHERE id=:id",
                                Map.of("id", body.incidenciaId()))
                        .isEmpty()) throw ApiError.invalid("La incidencia de origen no existe");
        if (body.encargoId() != null) {
            var task = repo.get("encargo", body.encargoId(), true);
            if ("Cancelado".equals(task.get("estado")))
                throw ApiError.conflict("No se puede registrar ejecución en un encargo cancelado");
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
                                "clase_actividad_id",
                                claseActividad,
                                "area_verde_id",
                                body.areaVerdeId(),
                                "fecha_solicitud",
                                body.fechaSolicitud(),
                                "incidencia_id",
                                body.incidenciaId(),
                                "superficie_m2",
                                body.superficieM2(),
                                "cantidad_solicitada",
                                body.cantidadSolicitada(),
                                "cantidad_ejecutada",
                                body.cantidadEjecutada(),
                                "unidad_medida_id",
                                body.unidadMedidaId(),
                                "tiempo_minutos",
                                body.tiempoMinutos(),
                                "ejemplar_id",
                                body.ejemplarId(),
                                "pendiente_vinculacion",
                                pendienteVinculacion,
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

    private void validarEjemplarPoda(long ejemplarId, long areaVerdeId) {
        var ejemplar =
                repo.one(
                        "SELECT area_verde_id,altura_total_m FROM mod02_ejemplar WHERE id=:id",
                        Map.of("id", ejemplarId));
        if (number(ejemplar, "area_verde_id") != areaVerdeId)
            throw ApiError.invalid("El ejemplar no pertenece al espacio verde seleccionado");
        Object altura = ejemplar.get("altura_total_m");
        if (altura == null)
            throw ApiError.invalid(
                    "El ejemplar no tiene altura registrada; actualiza el catastro antes de podar");
        if (((BigDecimal) altura).compareTo(BigDecimal.valueOf(5)) >= 0)
            throw ApiError.conflict(
                    "Los ejemplares de 5 metros o más deben reportarse como incidencia");
    }
}
