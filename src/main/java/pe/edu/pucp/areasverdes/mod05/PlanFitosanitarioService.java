package pe.edu.pucp.areasverdes.mod05;

import static pe.edu.pucp.areasverdes.mod05.Mod05Repository.number;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.datos.RolesRepository;
import pe.edu.pucp.areasverdes.mod03.ErrorPlanificacion;
import pe.edu.pucp.areasverdes.mod03.ProgramacionServicios;
import pe.edu.pucp.areasverdes.mod03.UsuarioPlanificacion;

/**
 * RF-FIT-001: plan fitosanitario estacional. No duplica la programación de MOD-03 (ciclos,
 * restricciones, duración): este servicio solo valida el contrato, aplica RN-010 (una aplicación
 * por estación) y, por cada una, crea el ciclo en MOD-03 y el requerimiento (atención) en MOD-05.
 *
 * <p>Los errores de MOD-03 ({@link ErrorPlanificacion}) no exponen su código HTTP fuera de su
 * paquete; se reportan aquí como 400 genérico con el mismo mensaje (más detalle en
 * `docs/estandares`, pendiente de homologar el formato de error entre módulos).
 */
@Service
public class PlanFitosanitarioService {
    static final String CONTROL_FITOSANITARIO = "Control fitosanitario";
    static final List<String> ESTACIONES = List.of("Verano", "Otoño", "Invierno", "Primavera");

    final Mod05Repository repo;
    final AtencionService atenciones;
    final ProgramacionServicios programacion;
    final RolesRepository roles;
    final Clock clock;

    public PlanFitosanitarioService(
            Mod05Repository repo,
            AtencionService atenciones,
            ProgramacionServicios programacion,
            RolesRepository roles,
            Clock clock) {
        this.repo = repo;
        this.atenciones = atenciones;
        this.programacion = programacion;
        this.roles = roles;
        this.clock = clock;
    }

    @Transactional
    public List<Map<String, Object>> crear(EntradasFitosanitario.Plan plan, Usuario user) {
        user.exigir(Usuario.GESTIONAR_ATENCION);
        var contrato = repo.lock("contrato", plan.contrato_id());
        var servicio = repo.get("tiposervicio", number(contrato, "tipo_servicio_id"));
        if (!CONTROL_FITOSANITARIO.equals(servicio.get("nombre")))
            throw ApiError.invalid("El contrato debe ser de control fitosanitario");
        if (!(boolean) servicio.get("activo"))
            throw ApiError.invalid("El tipo de servicio está inactivo");
        LocalDate hoy = LocalDate.now(clock);
        if (hoy.isBefore((LocalDate) contrato.get("vigencia_inicio"))
                || hoy.isAfter((LocalDate) contrato.get("vigencia_fin")))
            throw ApiError.invalid("El contrato no está vigente");

        var aplicaciones = plan.aplicaciones();
        Set<String> estaciones = new HashSet<>();
        for (var a : aplicaciones) {
            if (!estaciones.add(a.estacion()))
                throw ApiError.invalid("No repitas una estación en el mismo plan");
            if (a.fecha_fin().isBefore(a.fecha_inicio()))
                throw ApiError.invalid("El fin no puede ser anterior al inicio en " + a.estacion());
        }
        String motivo = AtencionService.text(plan.motivo());
        // RN-010: una aplicación por estación en todo el campus; si falta alguna, exige motivo.
        if (estaciones.size() < ESTACIONES.size() && motivo.isEmpty())
            throw ApiError.invalid(
                    "Indica el motivo: el plan no cubre las cuatro estaciones (RN-010). Estaciones"
                            + " sin programar: "
                            + ESTACIONES.stream()
                                    .filter(e -> !estaciones.contains(e))
                                    .collect(Collectors.joining(", ")));

        var usuarioPlanificacion = usuarioPlanificacion(user);
        List<Map<String, Object>> creadas = new ArrayList<>();
        for (var a : aplicaciones) {
            // Los espacios no se reenvían: mod05_areaverde es una copia propia de MOD-05 (deuda
            // conocida), no el id de mod02_area_verde que valida MOD-03. El detalle de qué se
            // atiende queda en la atención, que es la fuente de verdad aquí.
            try {
                programacion.crear(
                        new ProgramacionServicios.NuevaProgramacion(
                                number(contrato, "tipo_servicio_id"),
                                number(contrato, "proveedor_id"),
                                a.fecha_inicio(),
                                a.fecha_fin(),
                                motivo.isEmpty() ? null : motivo,
                                null,
                                null,
                                null,
                                null),
                        usuarioPlanificacion);
            } catch (ErrorPlanificacion e) {
                throw ApiError.invalid(e.getMessage());
            }
            var atencion =
                    atenciones.crear(
                            new Entradas.Crear(
                                    plan.contrato_id(),
                                    "Aplicación fitosanitaria estacional de " + a.estacion(),
                                    "ProgramacionEstacional",
                                    null,
                                    null,
                                    null,
                                    a.alcances(),
                                    null,
                                    null),
                            user);
            creadas.add(atencion);
        }
        return creadas;
    }

    /**
     * RF-FIT-006: una aplicación adicional del ejemplar marcado con atención especial. RN-010: no
     * se programan más de las {@code aplicaciones_adicionales} fijadas al marcarlo (una o dos al
     * año); cada una queda enlazada al ejemplar, a su programación en MOD-03 y a la atención que
     * crea.
     */
    @Transactional
    public Map<String, Object> programarAplicacionAdicional(
            long especialId, EntradasFitosanitario.AplicacionAdicional in, Usuario user) {
        user.exigir(Usuario.GESTIONAR_ATENCION);
        var especial = repo.lock("atencion_especial", especialId);
        if ((boolean) especial.get("cancelado"))
            throw ApiError.invalid(
                    "Este seguimiento está cancelado: no se pueden programar más aplicaciones");
        long realizadas = number(especial, "aplicaciones_realizadas");
        long adicionales = number(especial, "aplicaciones_adicionales");
        if (realizadas >= adicionales)
            throw ApiError.invalid(
                    "Ya se programaron las "
                            + adicionales
                            + " aplicaciones adicionales de este ejemplar");
        var contrato = repo.lock("contrato", in.contrato_id());
        var servicio = repo.get("tiposervicio", number(contrato, "tipo_servicio_id"));
        if (!CONTROL_FITOSANITARIO.equals(servicio.get("nombre")))
            throw ApiError.invalid("El contrato debe ser de control fitosanitario");
        if (!(boolean) servicio.get("activo"))
            throw ApiError.invalid("El tipo de servicio está inactivo");
        LocalDate hoy = LocalDate.now(clock);
        if (hoy.isBefore((LocalDate) contrato.get("vigencia_inicio"))
                || hoy.isAfter((LocalDate) contrato.get("vigencia_fin")))
            throw ApiError.invalid("El contrato no está vigente");
        if (in.fecha_fin().isBefore(in.fecha_inicio()))
            throw ApiError.invalid("El fin no puede ser anterior al inicio");

        String motivo = AtencionService.text(in.motivo());
        try {
            programacion.crear(
                    new ProgramacionServicios.NuevaProgramacion(
                            number(contrato, "tipo_servicio_id"),
                            number(contrato, "proveedor_id"),
                            in.fecha_inicio(),
                            in.fecha_fin(),
                            motivo.isEmpty() ? null : motivo,
                            null,
                            null,
                            null,
                            null),
                    usuarioPlanificacion(user));
        } catch (ErrorPlanificacion e) {
            throw ApiError.invalid(e.getMessage());
        }
        var atencion =
                atenciones.crear(
                        new Entradas.Crear(
                                in.contrato_id(),
                                "Aplicación adicional de atención especial",
                                "Demanda",
                                null,
                                null,
                                null,
                                List.of(
                                        new Entradas.Alcance(
                                                null, number(especial, "ejemplar_id"), null, null)),
                                null,
                                null),
                        user);
        repo.update(
                "atenciontercerizada",
                number(atencion, "id"),
                Map.of("atencion_especial_id", especialId));
        repo.update(
                "atencion_especial", especialId, Map.of("aplicaciones_realizadas", realizadas + 1));
        var actualizado =
                repo.one(
                        AtencionEspecialService.ESPECIALES + " WHERE ae.id=:id",
                        Map.of("id", especialId));
        repo.audit(
                "AtencionEspecial",
                especialId,
                "AplicacionProgramada",
                user,
                especial,
                actualizado,
                "");
        return actualizado;
    }

    /** Traduce el usuario de MOD-05 a los permisos del rol de MOD-01 equivalente (como MOD-03). */
    private UsuarioPlanificacion usuarioPlanificacion(Usuario user) {
        return new UsuarioPlanificacion(user.id(), user.nombre(), user.permisos());
    }
}
