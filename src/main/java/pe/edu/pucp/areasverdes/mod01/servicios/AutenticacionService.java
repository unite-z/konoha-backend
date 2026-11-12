package pe.edu.pucp.areasverdes.mod01.servicios;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.config.PropiedadesSeguridad;
import pe.edu.pucp.areasverdes.mod01.datos.RolesRepository;
import pe.edu.pucp.areasverdes.mod01.datos.SesionesRepository;
import pe.edu.pucp.areasverdes.mod01.datos.UsuariosRepository;
import pe.edu.pucp.areasverdes.mod01.datos.UsuariosRepository.Cuenta;

/**
 * Inicio y cierre de sesión, validación de la sesión en cada petición y cambio de la propia
 * contraseña (RF-T-001; CU-MOD01-001 y 002).
 */
@Service
public class AutenticacionService {

    public static final String CANAL_OFICINA = "oficina";
    public static final String CANAL_CAMPO = "campo";

    /** Mensaje genérico de HU-MOD01-001: no dice qué dato falla. */
    static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";

    private static final SecureRandom AZAR = new SecureRandom();

    public record InicioSesion(String token, UsuarioSesion usuario, OffsetDateTime expira) {}

    private final UsuariosRepository usuarios;
    private final RolesRepository roles;
    private final SesionesRepository sesiones;
    private final RegistroAccesos accesos;
    private final RegistradorAuditoria auditoria;
    private final PasswordEncoder claves;
    private final PropiedadesSeguridad politica;
    private final Clock reloj;

    /** Hash con el que se compara cuando la cuenta no existe, para no delatarlo por el tiempo. */
    private final String senuelo;

    public AutenticacionService(
            UsuariosRepository usuarios,
            RolesRepository roles,
            SesionesRepository sesiones,
            RegistroAccesos accesos,
            RegistradorAuditoria auditoria,
            PasswordEncoder claves,
            PropiedadesSeguridad politica,
            Clock reloj) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.sesiones = sesiones;
        this.accesos = accesos;
        this.auditoria = auditoria;
        this.claves = claves;
        this.politica = politica;
        this.reloj = reloj;
        this.senuelo = claves.encode(nuevoToken());
    }

    /**
     * CU-MOD01-001. El contador de intentos se guarda aunque el inicio falle: por eso no se deshace
     * la transacción ante {@link ErrorCore}.
     */
    @Transactional(noRollbackFor = ErrorCore.class)
    public InicioSesion iniciar(
            String identificador, String contrasena, String canal, boolean mantener) {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        Optional<Cuenta> encontrada = usuarios.porIdentificador(identificador.trim());
        if (encontrada.isEmpty()) {
            claves.matches(contrasena, senuelo);
            accesos.registrar(null, identificador, canal, "Fallido", "cuenta-inexistente", null);
            throw ErrorCore.noAutenticado(CREDENCIALES_INVALIDAS);
        }
        Cuenta cuenta = encontrada.get();
        if (cuenta.bloqueadoHasta() != null && cuenta.bloqueadoHasta().isAfter(ahora)) {
            accesos.registrar(cuenta.id(), null, canal, "Bloqueado", "cuenta-bloqueada", null);
            throw new ErrorCore(
                    401,
                    "cuenta-bloqueada",
                    "La cuenta está bloqueada temporalmente por varios intentos fallidos. Vuelve a"
                            + " intentarlo más tarde o contacta con el jefe de sección");
        }
        if (!claves.matches(contrasena, cuenta.credencialHash())) {
            registrarFallo(cuenta, canal, ahora);
            throw ErrorCore.noAutenticado(CREDENCIALES_INVALIDAS);
        }
        if (!cuenta.activo() || vencida(cuenta, ahora.toLocalDate())) {
            accesos.registrar(
                    cuenta.id(),
                    null,
                    canal,
                    "Denegado",
                    cuenta.activo() ? "cuenta-vencida" : "cuenta-inactiva",
                    null);
            throw new ErrorCore(
                    401,
                    "cuenta-no-activa",
                    "Tu cuenta no está activa. Contacta con el jefe de sección");
        }
        usuarios.registrarAcceso(cuenta.id(), ahora);
        boolean persistente = mantener && CANAL_CAMPO.equals(canal);
        OffsetDateTime expira =
                persistente
                        ? ahora.plusDays(politica.diasSesionPersistente())
                        : ahora.plusHours(politica.horasSesion());
        String token = nuevoToken();
        long sesionId =
                sesiones.crear(resumen(token), cuenta.id(), canal, persistente, ahora, expira);
        accesos.registrar(cuenta.id(), null, canal, "Exitoso", null, null);
        return new InicioSesion(token, aSesion(cuenta, sesionId), expira);
    }

    private void registrarFallo(Cuenta cuenta, String canal, OffsetDateTime ahora) {
        int intentos = cuenta.intentosFallidos() + 1;
        if (intentos >= politica.intentosMaximos()) {
            usuarios.registrarFallo(cuenta.id(), 0, ahora.plusMinutes(politica.minutosBloqueo()));
            accesos.registrar(cuenta.id(), null, canal, "Bloqueado", "intentos-agotados", null);
        } else {
            usuarios.registrarFallo(cuenta.id(), intentos, null);
            accesos.registrar(cuenta.id(), null, canal, "Fallido", "contrasena-incorrecta", null);
        }
    }

    /**
     * Valida la sesión de una petición (CU-MOD01-006 paso 2). Lee cuenta, rol y permisos de la
     * base, así que un cambio de rol o una desactivación valen desde la siguiente petición.
     */
    @Transactional
    public Optional<UsuarioSesion> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        var encontrada = sesiones.porToken(resumen(token));
        if (encontrada.isEmpty() || encontrada.get().revocada() != null) {
            return Optional.empty();
        }
        var sesion = encontrada.get();
        if (!sesion.expira().isAfter(ahora)) {
            sesiones.revocar(sesion.id(), ahora, "expirada");
            return Optional.empty();
        }
        var limiteInactividad = Duration.ofMinutes(politica.minutosInactividad());
        if (!sesion.persistente()
                && sesion.ultimaActividad().plus(limiteInactividad).isBefore(ahora)) {
            sesiones.revocar(sesion.id(), ahora, "inactividad");
            return Optional.empty();
        }
        var cuenta = usuarios.porId(sesion.usuarioId());
        if (cuenta.isEmpty()
                || !cuenta.get().activo()
                || vencida(cuenta.get(), ahora.toLocalDate())) {
            sesiones.revocar(sesion.id(), ahora, "cuenta-no-activa");
            return Optional.empty();
        }
        if (sesion.ultimaActividad().plusMinutes(1).isBefore(ahora)) {
            sesiones.tocar(sesion.id(), ahora);
        }
        return Optional.of(aSesion(cuenta.get(), sesion.id()));
    }

    /** CU-MOD01-002 paso 4: invalida solo la sesión de este dispositivo. */
    @Transactional
    public void cerrar(UsuarioSesion usuario, String canal) {
        sesiones.revocar(usuario.sesionId(), OffsetDateTime.now(reloj), "cierre");
        accesos.registrar(usuario.id(), null, canal, "CierreSesion", null, null);
    }

    /**
     * CU-MOD01-002: cambia la propia contraseña, la guarda cifrada y cierra las demás sesiones. Con
     * contraseña temporal (HU-MOD01-003, «Primer acceso») levanta la obligación de cambiarla.
     */
    @Transactional
    public void cambiarContrasena(UsuarioSesion usuario, String actual, String nueva) {
        Cuenta cuenta =
                usuarios.porId(usuario.id())
                        .orElseThrow(() -> ErrorCore.noAutenticado("Sesión no válida"));
        if (actual == null || !claves.matches(actual, cuenta.credencialHash())) {
            throw ErrorCore.invalido(
                    "contrasena-actual-incorrecta", "La contraseña actual no es correcta");
        }
        validarNueva(nueva, cuenta.credencialHash());
        usuarios.cambiarCredencial(cuenta.id(), claves.encode(nueva), false);
        sesiones.revocarTodas(
                cuenta.id(), usuario.sesionId(), OffsetDateTime.now(reloj), "cambio-contrasena");
        auditoria.registrarCambio(
                "usuario",
                cuenta.id(),
                "Modificacion",
                Map.of(),
                Map.of("contrasena", "cambiada"),
                null,
                usuario.id());
    }

    void validarNueva(String nueva, String hashActual) {
        List<String> reglas = PoliticaContrasena.incumplidas(nueva);
        if (!reglas.isEmpty()) {
            throw new ErrorContrasena(reglas);
        }
        if (claves.matches(nueva, hashActual)) {
            throw new ErrorContrasena(List.of("Debe ser distinta de la contraseña actual"));
        }
    }

    UsuarioSesion aSesion(Cuenta cuenta, long sesionId) {
        return new UsuarioSesion(
                cuenta.id(),
                cuenta.nombreUsuario(),
                cuenta.nombre(),
                cuenta.rolCodigo(),
                cuenta.rolNombre(),
                roles.permisos(cuenta.rolId()),
                cuenta.debeCambiarContrasena(),
                sesionId);
    }

    static boolean vencida(Cuenta cuenta, LocalDate hoy) {
        return cuenta.fechaFinVigencia() != null && cuenta.fechaFinVigencia().isBefore(hoy);
    }

    static String nuevoToken() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String resumen(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Contraseña que no cumple la política: lleva las reglas incumplidas (HU-MOD01-003). */
    public static class ErrorContrasena extends ErrorCore {
        public ErrorContrasena(List<String> reglas) {
            super(
                    400,
                    "contrasena-debil",
                    "La contraseña no cumple la política",
                    Map.of("reglas", List.copyOf(reglas)));
        }
    }
}
