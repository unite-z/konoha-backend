package pe.edu.pucp.areasverdes.mod01.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod01.config.PropiedadesSeguridad;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;
import pe.edu.pucp.areasverdes.mod01.servicios.AutenticacionService;
import pe.edu.pucp.areasverdes.mod01.servicios.ErrorCore;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/** Inicio y cierre de sesión y contraseña propia (RF-T-001; HU-MOD01-001 a 003). */
@RestController
@RequestMapping("/api/v1/core/auth")
@Tag(name = "MOD-01 Sesión")
@SecurityScheme(
        name = "sesionCore",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = SeguridadMod01.COOKIE_SESION)
public class AuthController {

    public record Credenciales(
            @NotBlank @Size(max = 150) String identificador,
            @NotBlank @Size(max = 200) String contrasena,
            @Pattern(regexp = "oficina|campo") String canal,
            Boolean mantenerSesion) {}

    public record CambioContrasena(
            @NotBlank String contrasenaActual,
            @NotBlank String contrasenaNueva,
            @NotBlank String confirmacion) {}

    public record UsuarioActual(long id, String nombre, String nombreUsuario) {}

    public record RolActual(String codigo, String nombre) {}

    /** Lo que la interfaz necesita para armar el menú del rol (CU-MOD01-006 paso 1). */
    public record Sesion(
            UsuarioActual usuario,
            RolActual rol,
            List<String> permisos,
            boolean debeCambiarContrasena,
            OffsetDateTime expira) {

        static Sesion de(UsuarioSesion u, OffsetDateTime expira) {
            return new Sesion(
                    new UsuarioActual(u.id(), u.nombre(), u.nombreUsuario()),
                    new RolActual(u.rol(), u.rolNombre()),
                    List.copyOf(u.permisos()),
                    u.debeCambiarContrasena(),
                    expira);
        }
    }

    private final AutenticacionService autenticacion;
    private final PropiedadesSeguridad politica;
    private final Clock reloj;

    public AuthController(
            AutenticacionService autenticacion, PropiedadesSeguridad politica, Clock reloj) {
        this.autenticacion = autenticacion;
        this.politica = politica;
        this.reloj = reloj;
    }

    /**
     * Abre la sesión con correo (o nombre de usuario de las cuentas migradas) y contraseña. Ante
     * credenciales incorrectas responde un mensaje genérico; a los 5 fallos seguidos bloquea la
     * cuenta 15 minutos. {@code mantener_sesion} solo vale en el canal {@code campo}.
     */
    @Operation(summary = "Iniciar sesión")
    @ApiResponse(responseCode = "200", description = "Sesión abierta; cookie av_sesion")
    @ApiResponse(responseCode = "401", description = "Credenciales, cuenta bloqueada o no activa")
    @PostMapping({"/login", "/login/"})
    public ResponseEntity<Sesion> iniciar(@Valid @RequestBody Credenciales credenciales) {
        String canal = credenciales.canal() == null ? "oficina" : credenciales.canal();
        boolean mantener = Boolean.TRUE.equals(credenciales.mantenerSesion());
        var inicio =
                autenticacion.iniciar(
                        credenciales.identificador(), credenciales.contrasena(), canal, mantener);
        boolean persistente = mantener && "campo".equals(canal);
        var cookie =
                ResponseCookie.from(SeguridadMod01.COOKIE_SESION, inicio.token())
                        .httpOnly(true)
                        .secure(politica.cookieSegura())
                        .sameSite("Strict")
                        .path(SeguridadMod01.RUTA_COOKIE);
        if (persistente) {
            cookie.maxAge(Duration.between(OffsetDateTime.now(reloj), inicio.expira()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.build().toString())
                .body(Sesion.de(inicio.usuario(), inicio.expira()));
    }

    @Operation(summary = "Usuario, rol y permisos de la sesión actual")
    @SecurityRequirement(name = "sesionCore")
    @GetMapping({"/sesion", "/sesion/"})
    public Sesion sesion(@AuthenticationPrincipal UsuarioSesion usuario) {
        return Sesion.de(usuario, null);
    }

    /** Invalida la sesión de este dispositivo. Los registros locales del móvil no se tocan. */
    @Operation(summary = "Cerrar sesión")
    @SecurityRequirement(name = "sesionCore")
    @PostMapping({"/logout", "/logout/"})
    public ResponseEntity<Void> cerrar(@AuthenticationPrincipal UsuarioSesion usuario) {
        autenticacion.cerrar(usuario, "oficina");
        var borrar =
                ResponseCookie.from(SeguridadMod01.COOKIE_SESION, "")
                        .httpOnly(true)
                        .secure(politica.cookieSegura())
                        .sameSite("Strict")
                        .path(SeguridadMod01.RUTA_COOKIE)
                        .maxAge(0)
                        .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, borrar.toString()).build();
    }

    /** Cambia la propia contraseña y cierra las demás sesiones del usuario (CU-MOD01-002). */
    @Operation(summary = "Cambiar la propia contraseña")
    @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o débil")
    @SecurityRequirement(name = "sesionCore")
    @PostMapping({"/contrasena", "/contrasena/"})
    public ResponseEntity<Void> cambiarContrasena(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @Valid @RequestBody CambioContrasena cambio) {
        if (!cambio.contrasenaNueva().equals(cambio.confirmacion())) {
            throw ErrorCore.invalido(
                    "confirmacion-distinta", "La confirmación no coincide con la contraseña nueva");
        }
        autenticacion.cambiarContrasena(
                usuario, cambio.contrasenaActual(), cambio.contrasenaNueva());
        return ResponseEntity.noContent().build();
    }
}
