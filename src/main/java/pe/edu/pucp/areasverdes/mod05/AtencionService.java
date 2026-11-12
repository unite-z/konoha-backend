package pe.edu.pucp.areasverdes.mod05;

import static pe.edu.pucp.areasverdes.mod05.Atencion.*;
import static pe.edu.pucp.areasverdes.mod05.Mod05Repository.number;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AtencionService {
    final Mod05Repository repo;
    final Clock clock;

    public AtencionService(Mod05Repository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public static Map<String, Object> values(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }

    static String text(String s) {
        return s == null ? "" : s.strip();
    }

    OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    Long unidad(String origen, Long id, String nombre, Usuario user) {
        nombre = text(nombre);
        if (!origen.equals("SolicitudUnidad")) {
            if (id != null || !nombre.isEmpty())
                throw ApiError.invalid("Solo SolicitudUnidad admite una unidad solicitante");
            return null;
        }
        if ((id == null) == nombre.isEmpty())
            throw ApiError.invalid("Indica una unidad existente o un nombre de unidad provisional");
        if (id != null) {
            if (!(boolean) repo.get("unidadinstitucional", id).get("activo"))
                throw ApiError.invalid("La unidad está inactiva");
            return id;
        }
        var existing =
                repo.rows(
                        "INSERT INTO mod05_unidadinstitucional(nombre,provisional,activo) VALUES"
                            + " (:nombre,true,true) ON CONFLICT(nombre) DO NOTHING RETURNING id",
                        Map.of("nombre", nombre));
        var u =
                repo.one(
                        "SELECT * FROM mod05_unidadinstitucional WHERE nombre=:nombre",
                        Map.of("nombre", nombre));
        if (!(boolean) u.get("activo")) throw ApiError.invalid("La unidad está inactiva");
        if (!existing.isEmpty())
            repo.audit("UnidadInstitucional", number(u, "id"), "Alta", user, Map.of(), u, "");
        return number(u, "id");
    }

    @Transactional
    public Map<String, Object> crear(Entradas.Crear input, Usuario user) {
        user.exigir(Usuario.GESTIONAR_ATENCION);
        var c = repo.lock("contrato", input.contrato_id());
        var proveedor = repo.get("proveedor", number(c, "proveedor_id"));
        var servicio = repo.get("tiposervicio", number(c, "tipo_servicio_id"));
        LocalDate hoy = LocalDate.now(clock);
        if (hoy.isBefore((LocalDate) c.get("vigencia_inicio"))
                || hoy.isAfter((LocalDate) c.get("vigencia_fin")))
            throw ApiError.invalid("El contrato no está vigente en la fecha de registro");
        if (!(boolean) proveedor.get("activo") || !(boolean) servicio.get("activo"))
            throw ApiError.invalid("Proveedor o tipo de servicio inactivo");
        if (!servicio.get("ejecutor").equals("Tercerizado"))
            throw ApiError.invalid("El contrato debe corresponder a un servicio tercerizado");
        Long unidad =
                unidad(
                        input.origen(),
                        input.unidad_solicitante_id(),
                        input.unidad_provisional(),
                        user);
        Set<String> destinos = new HashSet<>();
        for (var a : input.alcances()) {
            if ((a.area_verde_id() == null) == (a.ejemplar_id() == null))
                throw ApiError.invalid(
                        "Cada alcance debe indicar solo un área verde o un ejemplar");
            String key =
                    a.area_verde_id() != null
                            ? "area:" + a.area_verde_id()
                            : "ejemplar:" + a.ejemplar_id();
            if (!destinos.add(key))
                throw ApiError.invalid("No repitas un espacio o ejemplar en la misma atención");
            if (a.area_verde_id() != null) {
                if (!(boolean) repo.get("areaverde", a.area_verde_id()).get("activo"))
                    throw ApiError.invalid("El área verde está inactiva");
            } else repo.get("ejemplar", a.ejemplar_id());
            if ((a.metrado() == null) != (a.unidad_medida_id() == null))
                throw ApiError.invalid("Metrado y unidad deben registrarse juntos");
            if (a.unidad_medida_id() != null) repo.get("unidadmedida", a.unidad_medida_id());
        }
        List<Entradas.ProductoAplicado> productos =
                input.productos() == null ? List.of() : input.productos();
        Set<Long> productosElegidos = new HashSet<>();
        for (var p : productos) {
            if (!productosElegidos.add(p.producto_id()))
                throw ApiError.invalid("No repitas un producto en la misma atención");
            if (!(boolean) repo.get("producto_agronomico", p.producto_id()).get("activo"))
                throw ApiError.invalid("El producto está inactivo en el catálogo");
        }
        List<Long> plagas =
                input.plagas_controladas() == null ? List.of() : input.plagas_controladas();
        Set<Long> plagasElegidas = new HashSet<>();
        for (var plagaId : plagas) {
            if (!plagasElegidas.add(plagaId))
                throw ApiError.invalid("No repitas una plaga en la misma atención");
            if (!(boolean) repo.get("plaga", plagaId).get("activo"))
                throw ApiError.invalid("La plaga está inactiva en el catálogo");
        }
        var data =
                values(
                        "codigo",
                        "AT-" + UUID.randomUUID().toString().replace("-", ""),
                        "clase",
                        input.clase() == null ? Entradas.CLASE_PROGRAMADA : input.clase(),
                        "contrato_id",
                        input.contrato_id(),
                        "tipo_servicio_id",
                        number(c, "tipo_servicio_id"),
                        "descripcion",
                        input.descripcion().strip(),
                        "origen",
                        input.origen(),
                        "unidad_solicitante_id",
                        unidad,
                        "solicitado_por_id",
                        user.id(),
                        "numero_orden_compra",
                        "",
                        "estado",
                        REQUERIMIENTO,
                        "es_riesgo",
                        Boolean.TRUE.equals(input.es_riesgo())
                                || (boolean) servicio.get("es_riesgo"),
                        "comentarios_acuerdo_unidad",
                        "",
                        "fecha_creacion",
                        now(),
                        "fecha_modificacion",
                        now(),
                        "usuario_creacion_id",
                        user.id(),
                        "usuario_modificacion_id",
                        user.id());
        long id = repo.insert("atenciontercerizada", data);
        for (var a : input.alcances()) {
            long alcance =
                    repo.insert(
                            "alcanceatencion",
                            values(
                                    "atencion_id",
                                    id,
                                    "area_verde_id",
                                    a.area_verde_id(),
                                    "ejemplar_id",
                                    a.ejemplar_id(),
                                    "metrado",
                                    a.metrado(),
                                    "unidad_medida_id",
                                    a.unidad_medida_id()));
            repo.audit(
                    "AlcanceAtencion",
                    alcance,
                    "Alta",
                    user,
                    Map.of(),
                    repo.get("alcanceatencion", alcance),
                    "");
        }
        for (var p : productos) {
            long atencionProducto =
                    repo.insert(
                            "atencion_producto",
                            values(
                                    "atencion_id",
                                    id,
                                    "producto_id",
                                    p.producto_id(),
                                    "dosis",
                                    text(p.dosis())));
            repo.audit(
                    "AtencionProducto",
                    atencionProducto,
                    "Alta",
                    user,
                    Map.of(),
                    repo.get("atencion_producto", atencionProducto),
                    "");
        }
        for (var plagaId : plagas) {
            long atencionPlaga =
                    repo.insert("atencion_plaga", values("atencion_id", id, "plaga_id", plagaId));
            repo.audit(
                    "AtencionPlaga",
                    atencionPlaga,
                    "Alta",
                    user,
                    Map.of(),
                    repo.get("atencion_plaga", atencionPlaga),
                    "");
        }
        repo.audit(
                "AtencionTercerizada",
                id,
                "Alta",
                user,
                Map.of(),
                repo.get("atenciontercerizada", id),
                "");
        repo.history(id, "", REQUERIMIENTO, user);
        return repo.atencion(id);
    }

    Map<String, Object> guardar(
            long id,
            Map<String, Object> cambios,
            Map<String, Object> antes,
            Usuario user,
            String motivo) {
        cambios.put("fecha_modificacion", now());
        cambios.put("usuario_modificacion_id", user.id());
        repo.update("atenciontercerizada", id, cambios);
        var despues = repo.get("atenciontercerizada", id);
        repo.audit("AtencionTercerizada", id, "Modificacion", user, antes, despues, motivo);
        repo.history(id, (String) antes.get("estado"), (String) despues.get("estado"), user);
        return repo.atencion(id);
    }

    @Transactional
    public Map<String, Object> orden(long id, Entradas.Orden input, Usuario user) {
        user.exigir(Usuario.REGISTRAR_ATENCION);
        var antes = repo.lock("atenciontercerizada", id);
        var a = Atencion.from(antes);
        a.exigir(REQUERIMIENTO, OC);
        if (!a.ordenCompra().isBlank() && text(input.motivo()).isEmpty())
            throw ApiError.invalid("Indica el motivo de corrección de la OC");
        return guardar(
                id,
                values(
                        "numero_orden_compra",
                        input.numero_orden_compra().strip(),
                        "fecha_orden_compra",
                        now(),
                        "estado",
                        OC),
                antes,
                user,
                text(input.motivo()));
    }

    @Transactional
    public Map<String, Object> verificarSeguridad(
            long id, Entradas.VerificacionSeguridad input, Usuario user) {
        user.exigir(Usuario.VERIFICAR_SEGURIDAD);
        var antes = repo.lock("atenciontercerizada", id);
        var a = Atencion.from(antes);
        a.exigir(REQUERIMIENTO, OC);
        if (!a.esRiesgo()) throw ApiError.conflict("La atención no está marcada como de riesgo");
        var ultimas =
                repo.rows(
                        "SELECT medidas FROM mod05_verificacionseguridad WHERE atencion_id=:id"
                                + " ORDER BY id DESC LIMIT 1",
                        Map.of("id", id));
        String medidas = input.medidas().strip();
        if (!ultimas.isEmpty() && medidas.equals(ultimas.getFirst().get("medidas")))
            return repo.atencion(id);
        long verificacion =
                repo.insert(
                        "verificacionseguridad",
                        values(
                                "atencion_id",
                                id,
                                "medidas",
                                medidas,
                                "usuario_id",
                                user.id(),
                                "fecha_creacion",
                                now()));
        repo.audit(
                "AtencionTercerizada",
                id,
                "Modificacion",
                user,
                antes,
                repo.get("verificacionseguridad", verificacion),
                "Verificación de seguridad");
        return repo.atencion(id);
    }

    @Transactional
    public Map<String, Object> inicio(long id, Entradas.Inicio input, Usuario user) {
        user.exigir(Usuario.REGISTRAR_ATENCION);
        var antes = repo.lock("atenciontercerizada", id);
        var a = Atencion.from(antes);
        a.exigir(OC);
        if (a.esRiesgo()
                && repo.rows(
                                "SELECT id FROM mod05_verificacionseguridad WHERE atencion_id=:id",
                                Map.of("id", id))
                        .isEmpty())
            throw ApiError.conflict(
                    "Atención de riesgo: registra la verificación de seguridad antes de"
                            + " iniciar");
        return guardar(
                id,
                values("fecha_hora_inicio", input.fecha_hora_inicio(), "estado", EJECUCION),
                antes,
                user,
                "");
    }

    @Transactional
    public Map<String, Object> fin(long id, Entradas.Fin input, Usuario user) {
        user.exigir(Usuario.REGISTRAR_ATENCION);
        var antes = repo.lock("atenciontercerizada", id);
        var a = Atencion.from(antes);
        a.exigir(EJECUCION);
        if (input.fecha_hora_fin().isBefore(a.inicio()))
            throw ApiError.invalid("La fecha de fin no puede ser anterior al inicio");
        return guardar(
                id,
                values("fecha_hora_fin", input.fecha_hora_fin(), "estado", EJECUTADO),
                antes,
                user,
                "");
    }

    @Transactional
    public Map<String, Object> origen(long id, Entradas.Origen input, Usuario user) {
        user.exigir(Usuario.GESTIONAR_ATENCION);
        var antes = repo.lock("atenciontercerizada", id);
        Atencion.from(antes).exigir(REQUERIMIENTO, OC);
        Long unidad =
                unidad(
                        input.origen(),
                        input.unidad_solicitante_id(),
                        input.unidad_provisional(),
                        user);
        return guardar(
                id,
                values("origen", input.origen(), "unidad_solicitante_id", unidad),
                antes,
                user,
                input.motivo().strip());
    }

    @Transactional
    public Map<String, Object> acuerdo(long id, Entradas.Acuerdo input, Usuario user) {
        user.exigir(Usuario.GESTIONAR_ATENCION);
        var antes = repo.lock("atenciontercerizada", id);
        var a = Atencion.from(antes);
        // La unidad puede responder después de la conformidad: el acuerdo nunca cierra la atención.
        a.exigir(EJECUTADO, REPORTE, OBSERVADO, CONFORME);
        if (!a.origen().equals("SolicitudUnidad"))
            throw ApiError.invalid("El acuerdo corresponde únicamente a solicitudes de unidad");
        if ((input.acuerdo_unidad() == null) != (input.fecha_acuerdo_unidad() == null))
            throw ApiError.invalid(
                    "El acuerdo comunicado requiere fecha; el pendiente no lleva fecha");
        if (input.fecha_acuerdo_unidad() != null
                && input.fecha_acuerdo_unidad()
                        .isBefore(a.fin().atZoneSameInstant(Mod05Application.LIMA).toLocalDate()))
            throw ApiError.invalid("El acuerdo no puede ser anterior a la finalización");
        return guardar(
                id,
                values(
                        "acuerdo_unidad",
                        input.acuerdo_unidad(),
                        "fecha_acuerdo_unidad",
                        input.fecha_acuerdo_unidad(),
                        "comentarios_acuerdo_unidad",
                        text(input.comentarios_acuerdo_unidad())),
                antes,
                user,
                "");
    }

    /**
     * RF-SER-007: el jefe da la conformidad o observa la última revisión del informe. Conforme
     * cierra el ciclo; observar deja la atención «Observado» hasta que el proveedor suba otra
     * revisión (RF-SER-012). Es una decisión por revisión: la base lo impide con una restricción
     * única. No es el acuerdo de la unidad solicitante, que se registra aparte.
     */
    @Transactional
    public Map<String, Object> conformidad(long id, Entradas.Conformidad input, Usuario user) {
        user.exigir(Usuario.REGISTRAR_CONFORMIDAD);
        var antes = repo.lock("atenciontercerizada", id);
        var a = Atencion.from(antes);
        if (a.estado().equals(EJECUTADO))
            throw ApiError.conflict(
                    "RN-007: adjunta el informe del proveedor antes de dar la conformidad");
        a.exigir(REPORTE);
        var revisiones =
                repo.rows(
                        "SELECT id, fecha_recepcion FROM mod05_reporteproveedor"
                                + " WHERE atencion_id=:id ORDER BY revision DESC LIMIT 1",
                        Map.of("id", id));
        if (revisiones.isEmpty())
            throw ApiError.conflict(
                    "RN-007: la atención no tiene informe del proveedor para evaluar");
        var revision = revisiones.get(0);
        if (input.fecha_conformidad()
                .isBefore(LocalDate.parse(revision.get("fecha_recepcion").toString())))
            throw ApiError.invalid("La decisión no puede ser anterior a la recepción del informe");
        boolean observada = input.resultado().equals("Observada");
        String comentarios = text(input.comentarios());
        if (observada && comentarios.isEmpty())
            throw ApiError.invalid("Al observar el informe indica qué debe corregir el proveedor");
        long decision =
                repo.insert(
                        "conformidad",
                        values(
                                "atencion_id",
                                id,
                                "reporte_id",
                                number(revision, "id"),
                                "resultado",
                                input.resultado(),
                                "fecha_conformidad",
                                input.fecha_conformidad(),
                                "comentarios",
                                comentarios,
                                "usuario_id",
                                user.id()));
        repo.audit(
                "Conformidad",
                decision,
                "Alta",
                user,
                Map.of(),
                repo.get("conformidad", decision),
                "");
        return guardar(id, values("estado", observada ? OBSERVADO : CONFORME), antes, user, "");
    }
}
