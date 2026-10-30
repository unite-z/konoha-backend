package pe.edu.pucp.areasverdes.mod01.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository.Acceso;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository.Cambio;
import pe.edu.pucp.areasverdes.mod01.servicios.Autorizacion;
import pe.edu.pucp.areasverdes.mod01.servicios.ErrorCore;
import pe.edu.pucp.areasverdes.mod01.servicios.RolesService;
import pe.edu.pucp.areasverdes.mod01.servicios.RolesService.RolConPermisos;
import pe.edu.pucp.areasverdes.mod01.servicios.RolesService.RolUsuario;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuariosService;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuariosService.CambioDatos;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuariosService.ConContrasena;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuariosService.Filtros;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuariosService.NuevoUsuario;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuariosService.UsuarioResumen;

/**
 * Usuarios, roles y registro de accesos (RF-T-002 y RF-T-003; CU-MOD01-003 a 005 y 007). Todo es
 * del jefe de sección: el resto de roles recibe 403 y el intento queda registrado.
 */
@RestController
@RequestMapping("/api/v1/core")
@Tag(name = "MOD-01 Usuarios y roles")
@SecurityRequirement(name = "sesionCore")
public class UsuariosController {

    static final String GESTIONAR = "CORE.USUARIO.GESTIONAR";

    public record Motivo(@NotBlank @Size(max = 500) String motivo) {}

    public record AsignacionRol(@NotBlank String rol, @NotBlank @Size(max = 500) String motivo) {}

    public record SesionesCerradas(int sesionesCerradas) {}

    private final UsuariosService usuarios;
    private final RolesService roles;
    private final BitacoraRepository bitacora;
    private final Autorizacion autorizacion;

    public UsuariosController(
            UsuariosService usuarios,
            RolesService roles,
            BitacoraRepository bitacora,
            Autorizacion autorizacion) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.bitacora = bitacora;
        this.autorizacion = autorizacion;
    }

    /** Estado: {@code activa}, {@code inactiva} o {@code bloqueada}. */
    @Operation(summary = "Lista de cuentas con rol, estado, vigencia, último acceso y destacados")
    @GetMapping({"/usuarios", "/usuarios/"})
    public Pagina<UsuarioResumen> listar(
            @AuthenticationPrincipal UsuarioSesion yo,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) String estado,
            @RequestParam(name = "vigencia_proxima", defaultValue = "false") boolean vigencia,
            @RequestParam(defaultValue = "1") int page) {
        autorizacion.exigir(yo, GESTIONAR);
        return Pagina.de(usuarios.listar(new Filtros(rol, estado, vigencia)), pagina(page));
    }

    /** Responde con la contraseña temporal, que solo se muestra esta vez. */
    @Operation(summary = "Dar de alta un usuario")
    @ApiResponse(responseCode = "409", description = "Correo en uso u operario no habilitado")
    @PostMapping({"/usuarios", "/usuarios/"})
    public ResponseEntity<ConContrasena> crear(
            @AuthenticationPrincipal UsuarioSesion yo, @RequestBody NuevoUsuario nuevo) {
        autorizacion.exigir(yo, GESTIONAR);
        var creado = usuarios.crear(nuevo, yo);
        return ResponseEntity.created(
                        URI.create("/api/v1/core/usuarios/" + creado.usuario().id() + "/"))
                .body(creado);
    }

    @Operation(summary = "Detalle de una cuenta")
    @GetMapping({"/usuarios/{id}", "/usuarios/{id}/"})
    public UsuarioResumen obtener(
            @AuthenticationPrincipal UsuarioSesion yo, @PathVariable long id) {
        autorizacion.exigir(yo, GESTIONAR);
        return usuarios.obtener(id);
    }

    /** Se envían los tres campos; {@code fecha_fin_vigencia} nulo quita la vigencia. */
    @Operation(summary = "Editar nombre, teléfono y vigencia")
    @PatchMapping({"/usuarios/{id}", "/usuarios/{id}/"})
    public UsuarioResumen actualizar(
            @AuthenticationPrincipal UsuarioSesion yo,
            @PathVariable long id,
            @RequestBody CambioDatos cambio) {
        autorizacion.exigir(yo, GESTIONAR);
        return usuarios.actualizar(id, cambio, yo);
    }

    @Operation(summary = "Desactivar una cuenta y cerrar todas sus sesiones")
    @ApiResponse(
            responseCode = "409",
            description = "Último jefe, la propia cuenta o encargos abiertos")
    @PostMapping({"/usuarios/{id}/desactivar", "/usuarios/{id}/desactivar/"})
    public UsuarioResumen desactivar(
            @AuthenticationPrincipal UsuarioSesion yo,
            @PathVariable long id,
            @Valid @RequestBody Motivo motivo) {
        autorizacion.exigir(yo, GESTIONAR);
        return usuarios.desactivar(id, motivo.motivo(), yo);
    }

    @Operation(summary = "Reactivar una cuenta con contraseña temporal")
    @PostMapping({"/usuarios/{id}/activar", "/usuarios/{id}/activar/"})
    public ConContrasena activar(@AuthenticationPrincipal UsuarioSesion yo, @PathVariable long id) {
        autorizacion.exigir(yo, GESTIONAR);
        return usuarios.activar(id, yo);
    }

    @Operation(summary = "Desbloquear una cuenta antes de que pase el bloqueo")
    @PostMapping({"/usuarios/{id}/desbloquear", "/usuarios/{id}/desbloquear/"})
    public UsuarioResumen desbloquear(
            @AuthenticationPrincipal UsuarioSesion yo, @PathVariable long id) {
        autorizacion.exigir(yo, GESTIONAR);
        return usuarios.desbloquear(id, yo);
    }

    @Operation(summary = "Cerrar todas las sesiones de una cuenta sin desactivarla")
    @PostMapping({"/usuarios/{id}/cerrar-sesiones", "/usuarios/{id}/cerrar-sesiones/"})
    public SesionesCerradas cerrarSesiones(
            @AuthenticationPrincipal UsuarioSesion yo, @PathVariable long id) {
        autorizacion.exigir(yo, GESTIONAR);
        return new SesionesCerradas(usuarios.cerrarSesiones(id, yo));
    }

    @Operation(summary = "Generar una contraseña temporal que obliga a cambiarla")
    @PostMapping({"/usuarios/{id}/contrasena-temporal", "/usuarios/{id}/contrasena-temporal/"})
    public ConContrasena contrasenaTemporal(
            @AuthenticationPrincipal UsuarioSesion yo, @PathVariable long id) {
        autorizacion.exigir(yo, GESTIONAR);
        return usuarios.contrasenaTemporal(id, yo);
    }

    @Operation(summary = "Historial de la cuenta: altas, cambios de datos, de rol y de estado")
    @GetMapping({"/usuarios/{id}/historial", "/usuarios/{id}/historial/"})
    public List<Cambio> historial(
            @AuthenticationPrincipal UsuarioSesion yo, @PathVariable long id) {
        autorizacion.exigir(yo, GESTIONAR);
        return usuarios.historial(id);
    }

    @Operation(summary = "Rol actual de un usuario y sus cambios de rol")
    @GetMapping({"/usuarios/{id}/rol", "/usuarios/{id}/rol/"})
    public RolUsuario rol(@AuthenticationPrincipal UsuarioSesion yo, @PathVariable long id) {
        autorizacion.exigir(yo, GESTIONAR);
        return roles.rolDe(id);
    }

    /** Sustituye el rol (un solo rol por usuario) y vale desde la siguiente acción del usuario. */
    @Operation(summary = "Asignar o cambiar el rol de un usuario")
    @ApiResponse(
            responseCode = "409",
            description = "Operario no habilitado, mismo rol, último jefe o capataz con cuadrilla")
    @PutMapping({"/usuarios/{id}/rol", "/usuarios/{id}/rol/"})
    public RolUsuario cambiarRol(
            @AuthenticationPrincipal UsuarioSesion yo,
            @PathVariable long id,
            @Valid @RequestBody AsignacionRol asignacion) {
        autorizacion.exigir(yo, GESTIONAR);
        return roles.cambiar(id, asignacion.rol(), asignacion.motivo(), yo);
    }

    @Operation(summary = "Roles con el resumen de sus permisos (operario previsto, inactivo)")
    @GetMapping({"/roles", "/roles/"})
    public List<RolConPermisos> listarRoles(@AuthenticationPrincipal UsuarioSesion yo) {
        autorizacion.exigir(yo, GESTIONAR);
        return roles.listar();
    }

    /** Resultados: Exitoso, Fallido, Bloqueado, Denegado, SinPermiso y CierreSesion. */
    @Operation(summary = "Registro de accesos (no se puede modificar ni borrar)")
    @GetMapping({"/accesos", "/accesos/"})
    public Pagina<Acceso> accesos(
            @AuthenticationPrincipal UsuarioSesion yo,
            @RequestParam(name = "usuario_id", required = false) Long usuarioId,
            @RequestParam(required = false) String resultado,
            @RequestParam(defaultValue = "1") int page) {
        autorizacion.exigir(yo, GESTIONAR);
        int p = pagina(page);
        return Pagina.conTotal(
                bitacora.accesos(usuarioId, resultado, Pagina.TAMANO, (p - 1) * Pagina.TAMANO),
                bitacora.contarAccesos(usuarioId, resultado),
                p);
    }

    static int pagina(int page) {
        if (page < 1) {
            throw new ErrorCore(400, "pagina-invalida", "La página empieza en 1", Map.of());
        }
        return page;
    }
}
