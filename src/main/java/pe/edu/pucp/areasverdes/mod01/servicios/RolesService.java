package pe.edu.pucp.areasverdes.mod01.servicios;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository;
import pe.edu.pucp.areasverdes.mod01.datos.RolesRepository;
import pe.edu.pucp.areasverdes.mod01.datos.RolesRepository.Rol;
import pe.edu.pucp.areasverdes.mod01.datos.UsuariosRepository;

/**
 * Asignación y cambio de rol (RF-T-003, CU-MOD01-005, HU-MOD01-006) y permisos de cada rol
 * (AV-381). El rol superadmin no se lista, no se asigna y sus permisos no se editan: los tiene
 * todos.
 */
@Service
public class RolesService {

    public static final String ENTIDAD = "usuario";
    public static final String ENTIDAD_ROL = "rol";

    /** Solo del superadmin: quien edita la matriz no puede cederlo a otro rol. */
    public static final String GESTIONAR_ROLES = "CORE.ROL.GESTIONAR";

    static final int MOTIVO_MAXIMO = 500;

    public record RolConPermisos(
            String codigo,
            String nombre,
            String descripcion,
            boolean activo,
            List<String> permisos) {}

    public record CambioRol(
            String rolAnterior,
            String rolNuevo,
            String motivo,
            String autor,
            OffsetDateTime fechaHora) {}

    public record RolUsuario(long usuarioId, RolConPermisos rol, List<CambioRol> historial) {}

    public record CambioPermisos(
            List<String> anteriores,
            List<String> nuevos,
            String motivo,
            String autor,
            OffsetDateTime fechaHora) {}

    public record PermisosRol(RolConPermisos rol, List<CambioPermisos> historial) {}

    private final RolesRepository roles;
    private final UsuariosRepository usuarios;
    private final BitacoraRepository bitacora;
    private final RegistradorAuditoria auditoria;
    private final ConsultaCampo campo;

    public RolesService(
            RolesRepository roles,
            UsuariosRepository usuarios,
            BitacoraRepository bitacora,
            RegistradorAuditoria auditoria,
            ConsultaCampo campo) {
        this.roles = roles;
        this.usuarios = usuarios;
        this.bitacora = bitacora;
        this.auditoria = auditoria;
        this.campo = campo;
    }

    /** Roles con el resumen de sus permisos. El operario aparece inactivo (CU-MOD01-005 2a). */
    public List<RolConPermisos> listar() {
        Map<String, List<String>> permisos = roles.permisosPorRol();
        return roles.listar().stream()
                .filter(r -> !UsuarioSesion.SUPERADMIN.equals(r.codigo()))
                .map(r -> conPermisos(r, permisos.getOrDefault(r.codigo(), List.of())))
                .toList();
    }

    public List<RolesRepository.Permiso> catalogo() {
        return roles.catalogoPermisos();
    }

    /** Permisos de un rol editable y sus cambios (AV-381). */
    public PermisosRol permisosDe(String codigoRol) {
        Rol rol = rolEditable(codigoRol);
        return new PermisosRol(
                conPermisos(rol, List.copyOf(roles.permisos(rol.id()))),
                historialPermisos(rol.id()));
    }

    /**
     * Sustituye los permisos del rol (AV-381). Valen desde la siguiente petición de cada usuario
     * del rol, porque la sesión se revalida en cada una. Queda en la auditoría con autor, fecha y
     * motivo.
     */
    @Transactional
    public PermisosRol cambiarPermisos(
            String codigoRol, List<String> permisos, String motivo, UsuarioSesion autor) {
        String motivoLimpio = motivoValido(motivo);
        Rol rol = rolEditable(codigoRol);
        Set<String> conocidos = new LinkedHashSet<>();
        roles.catalogoPermisos().forEach(p -> conocidos.add(p.codigo()));
        Set<String> nuevos = new TreeSet<>(permisos == null ? List.of() : permisos);
        for (String codigo : nuevos) {
            if (!conocidos.contains(codigo)) {
                throw ErrorCore.invalido(
                        "permiso-desconocido", "El permiso " + codigo + " no existe");
            }
        }
        if (nuevos.contains(GESTIONAR_ROLES)) {
            throw ErrorCore.conflicto(
                    "permiso-reservado",
                    "Gestionar los permisos de cada rol es exclusivo del superadministrador");
        }
        Set<String> anteriores = roles.permisos(rol.id());
        if (anteriores.equals(nuevos)) {
            throw ErrorCore.conflicto("permisos-sin-cambio", "El rol ya tiene esos permisos");
        }
        roles.reemplazarPermisos(rol.id(), nuevos);
        auditoria.registrarCambio(
                ENTIDAD_ROL,
                rol.id(),
                "Modificacion",
                Map.of("permisos", List.copyOf(anteriores)),
                Map.of("permisos", List.copyOf(nuevos)),
                motivoLimpio,
                autor.id());
        return permisosDe(rol.codigo());
    }

    private Rol rolEditable(String codigoRol) {
        return roles.porCodigo(codigoRol == null ? "" : codigoRol)
                .filter(r -> !UsuarioSesion.SUPERADMIN.equals(r.codigo()))
                .orElseThrow(() -> ErrorCore.noEncontrado("El rol no existe"));
    }

    private static String motivoValido(String motivo) {
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) {
            throw ErrorCore.invalido("motivo-obligatorio", "Indica el motivo del cambio");
        }
        if (limpio.length() > MOTIVO_MAXIMO) {
            throw ErrorCore.invalido(
                    "motivo-demasiado-largo",
                    "El motivo no puede superar los " + MOTIVO_MAXIMO + " caracteres");
        }
        return limpio;
    }

    @SuppressWarnings("unchecked")
    private List<CambioPermisos> historialPermisos(long rolId) {
        return bitacora.cambios(ENTIDAD_ROL, rolId).stream()
                .filter(c -> "Modificacion".equals(c.accion()))
                .map(
                        c ->
                                new CambioPermisos(
                                        (List<String>)
                                                c.valoresAnteriores()
                                                        .getOrDefault("permisos", List.of()),
                                        (List<String>)
                                                c.valoresNuevos()
                                                        .getOrDefault("permisos", List.of()),
                                        c.motivo(),
                                        c.usuarioNombre(),
                                        c.fechaHora()))
                .toList();
    }

    public RolUsuario rolDe(long usuarioId) {
        var cuenta =
                usuarios.porId(usuarioId)
                        .filter(c -> !UsuarioSesion.SUPERADMIN.equals(c.rolCodigo()))
                        .orElseThrow(() -> ErrorCore.noEncontrado("El usuario no existe"));
        Rol rol = roles.porCodigo(cuenta.rolCodigo()).orElseThrow();
        return new RolUsuario(
                usuarioId,
                conPermisos(rol, List.copyOf(roles.permisos(rol.id()))),
                historial(usuarioId));
    }

    /**
     * Sustituye el rol del usuario por {@code codigoRol} (un solo rol) y lo registra con autor,
     * fecha y motivo. Los permisos nuevos valen desde la siguiente petición del usuario, porque la
     * sesión se revalida en cada una.
     */
    @Transactional
    public RolUsuario cambiar(
            long usuarioId, String codigoRol, String motivo, UsuarioSesion autor) {
        String motivoLimpio = motivo == null ? "" : motivo.trim();
        if (motivoLimpio.isEmpty()) {
            throw ErrorCore.invalido("motivo-obligatorio", "Indica el motivo del cambio de rol");
        }
        if (motivoLimpio.length() > MOTIVO_MAXIMO) {
            throw ErrorCore.invalido(
                    "motivo-demasiado-largo",
                    "El motivo no puede superar los " + MOTIVO_MAXIMO + " caracteres");
        }
        var cuenta =
                usuarios.porIdBloqueado(usuarioId)
                        .filter(c -> !UsuarioSesion.SUPERADMIN.equals(c.rolCodigo()))
                        .orElseThrow(() -> ErrorCore.noEncontrado("El usuario no existe"));
        Rol nuevo =
                roles.porCodigo(codigoRol == null ? "" : codigoRol)
                        .filter(r -> !UsuarioSesion.SUPERADMIN.equals(r.codigo()))
                        .orElseThrow(
                                () -> ErrorCore.invalido("rol-desconocido", "El rol no existe"));
        if (!nuevo.activo()) {
            throw ErrorCore.conflicto(
                    "rol-no-habilitado",
                    "El rol "
                            + nuevo.nombre()
                            + " está previsto para una fase posterior. El operario se registra"
                            + " como persona de una cuadrilla");
        }
        if (nuevo.id() == cuenta.rolId()) {
            throw ErrorCore.conflicto("rol-sin-cambio", "El usuario ya tiene ese rol");
        }
        if (UsuarioSesion.JEFE.equals(cuenta.rolCodigo())
                && cuenta.activo()
                && usuarios.contarActivosConRol(UsuarioSesion.JEFE) <= 1) {
            throw ErrorCore.conflicto(
                    "ultimo-jefe-seccion",
                    "No se puede quitar el rol al único jefe de sección activo");
        }
        if (UsuarioSesion.CAPATAZ.equals(cuenta.rolCodigo())
                && (!campo.cuadrillasDelCapataz(usuarioId).isEmpty()
                        || !campo.encargosAbiertos(usuarioId).isEmpty())) {
            throw ErrorCore.conflicto(
                    "capataz-con-cuadrilla",
                    "Antes de cambiar el rol, reasigna la cuadrilla y los encargos del capataz");
        }
        usuarios.cambiarRol(usuarioId, nuevo.id(), autor.id());
        auditoria.registrarCambio(
                ENTIDAD,
                usuarioId,
                "CambioRol",
                Map.of("rol", cuenta.rolCodigo()),
                Map.of("rol", nuevo.codigo()),
                motivoLimpio,
                autor.id());
        return rolDe(usuarioId);
    }

    List<CambioRol> historial(long usuarioId) {
        return bitacora.cambios(ENTIDAD, usuarioId).stream()
                .filter(c -> "CambioRol".equals(c.accion()))
                .map(
                        c ->
                                new CambioRol(
                                        String.valueOf(c.valoresAnteriores().get("rol")),
                                        String.valueOf(c.valoresNuevos().get("rol")),
                                        c.motivo(),
                                        c.usuarioNombre(),
                                        c.fechaHora()))
                .toList();
    }

    private static RolConPermisos conPermisos(Rol rol, List<String> permisos) {
        return new RolConPermisos(
                rol.codigo(), rol.nombre(), rol.descripcion(), rol.activo(), permisos);
    }
}
