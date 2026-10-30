package pe.edu.pucp.areasverdes.mod01.servicios;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository;
import pe.edu.pucp.areasverdes.mod01.datos.RolesRepository;
import pe.edu.pucp.areasverdes.mod01.datos.RolesRepository.Rol;
import pe.edu.pucp.areasverdes.mod01.datos.UsuariosRepository;

/** Asignación y cambio de rol (RF-T-003, CU-MOD01-005, HU-MOD01-006). */
@Service
public class RolesService {

    public static final String ENTIDAD = "usuario";
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
                .map(r -> conPermisos(r, permisos.getOrDefault(r.codigo(), List.of())))
                .toList();
    }

    public RolUsuario rolDe(long usuarioId) {
        var cuenta =
                usuarios.porId(usuarioId)
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
                usuarios.porId(usuarioId)
                        .orElseThrow(() -> ErrorCore.noEncontrado("El usuario no existe"));
        Rol nuevo =
                roles.porCodigo(codigoRol == null ? "" : codigoRol)
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
