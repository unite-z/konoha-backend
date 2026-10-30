package pe.edu.pucp.areasverdes.mod01.servicios;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.config.PropiedadesSeguridad;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository.Cambio;
import pe.edu.pucp.areasverdes.mod01.datos.RolesRepository;
import pe.edu.pucp.areasverdes.mod01.datos.SesionesRepository;
import pe.edu.pucp.areasverdes.mod01.datos.UsuariosRepository;
import pe.edu.pucp.areasverdes.mod01.datos.UsuariosRepository.Cuenta;
import pe.edu.pucp.areasverdes.mod01.datos.UsuariosRepository.NuevaCuenta;

/**
 * Administración de cuentas por el jefe de sección (RF-T-002; CU-MOD01-003, 004 y 007). No existe
 * «eliminar»: solo desactivar, para conservar lo que la persona registró (HU-MOD01-005).
 */
@Service
public class UsuariosService {

    private static final Logger log = LoggerFactory.getLogger(UsuariosService.class);

    static final String ENTIDAD = RolesService.ENTIDAD;
    static final int DIAS_RECHAZOS = 30;
    static final int RECHAZOS_DESTACADOS = 3;

    public record Filtros(String rol, String estado, boolean vigenciaProxima) {}

    public record UsuarioResumen(
            long id,
            String correo,
            String nombre,
            String telefono,
            String rol,
            String rolNombre,
            String estado,
            LocalDate fechaFinVigencia,
            OffsetDateTime ultimoAcceso,
            boolean debeCambiarContrasena,
            List<String> destacados) {}

    public record NuevoUsuario(
            String correo,
            String nombre,
            String telefono,
            String rol,
            LocalDate fechaFinVigencia) {}

    public record CambioDatos(String nombre, String telefono, LocalDate fechaFinVigencia) {}

    /** La contraseña temporal solo viaja en esta respuesta: no hay envío de correo (ADR 0003). */
    public record ConContrasena(
            UsuarioResumen usuario, String contrasenaTemporal, List<String> avisos) {}

    private final UsuariosRepository usuarios;
    private final RolesRepository roles;
    private final SesionesRepository sesiones;
    private final BitacoraRepository bitacora;
    private final RegistradorAuditoria auditoria;
    private final ConsultaCampo campo;
    private final PasswordEncoder claves;
    private final PropiedadesSeguridad politica;
    private final Clock reloj;

    public UsuariosService(
            UsuariosRepository usuarios,
            RolesRepository roles,
            SesionesRepository sesiones,
            BitacoraRepository bitacora,
            RegistradorAuditoria auditoria,
            ConsultaCampo campo,
            PasswordEncoder claves,
            PropiedadesSeguridad politica,
            Clock reloj) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.sesiones = sesiones;
        this.bitacora = bitacora;
        this.auditoria = auditoria;
        this.campo = campo;
        this.claves = claves;
        this.politica = politica;
        this.reloj = reloj;
    }

    /** Lista de CU-MOD01-007 con estado, vigencia, último acceso y destacados. */
    public List<UsuarioResumen> listar(Filtros filtros) {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        Map<Long, Integer> rechazos = bitacora.rechazosRecientes(ahora.minusDays(DIAS_RECHAZOS));
        return usuarios.listar().stream()
                .map(c -> resumen(c, ahora, rechazos.getOrDefault(c.id(), 0)))
                .filter(u -> filtros.rol() == null || filtros.rol().equals(u.rol()))
                .filter(u -> filtros.estado() == null || filtros.estado().equals(u.estado()))
                .filter(u -> !filtros.vigenciaProxima() || u.destacados().contains("vence-pronto"))
                .toList();
    }

    public UsuarioResumen obtener(long id) {
        return resumen(cuenta(id), OffsetDateTime.now(reloj), 0);
    }

    /** CU-MOD01-003. La cuenta nace activa y con contraseña temporal que obliga a cambiarla. */
    @Transactional
    public ConContrasena crear(NuevoUsuario nuevo, UsuarioSesion autor) {
        String correo = obligatorio(nuevo.correo(), "correo").toLowerCase(Locale.ROOT);
        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || correo.length() > 120) {
            throw ErrorCore.invalido("correo-invalido", "El correo no es válido");
        }
        String nombre = obligatorio(nuevo.nombre(), "nombre");
        var existente = usuarios.porCorreo(correo);
        if (existente.isPresent() || usuarios.existeNombreUsuario(correo)) {
            var datos = new LinkedHashMap<String, Object>();
            existente.ifPresent(
                    c -> {
                        datos.put("cuenta_id", c.id());
                        datos.put("puede_reactivar", !c.activo());
                    });
            throw new ErrorCore(
                    409,
                    "correo-en-uso",
                    existente.map(Cuenta::activo).orElse(true)
                            ? "Ya existe una cuenta con ese correo"
                            : "Ya existe una cuenta inactiva con ese correo. Puedes reactivarla",
                    datos);
        }
        var rol = rolAsignable(nuevo.rol());
        validarVigencia(nuevo.fechaFinVigencia());
        String temporal = PoliticaContrasena.temporal();
        long id =
                usuarios.insertar(
                        new NuevaCuenta(
                                correo,
                                correo,
                                nombre,
                                limpio(nuevo.telefono()),
                                claves.encode(temporal),
                                rol.id(),
                                nuevo.fechaFinVigencia(),
                                autor.id()));
        var valores = new LinkedHashMap<String, Object>();
        valores.put("correo", correo);
        valores.put("nombre", nombre);
        valores.put("rol", rol.codigo());
        valores.put("fecha_fin_vigencia", Objects.toString(nuevo.fechaFinVigencia(), null));
        auditoria.registrarAlta(ENTIDAD, id, valores, autor.id());
        List<String> avisos = new ArrayList<>();
        if (UsuarioSesion.CAPATAZ.equals(rol.codigo())
                && campo.cuadrillasDelCapataz(id).isEmpty()) {
            avisos.add(
                    "El capataz aún no tiene cuadrilla: no podrá registrar partes hasta que se le"
                            + " asigne una");
        }
        return new ConContrasena(obtener(id), temporal, avisos);
    }

    /**
     * CU-MOD01-004: edita nombre, teléfono y vigencia. El correo identifica la cuenta y no cambia.
     */
    @Transactional
    public UsuarioResumen actualizar(long id, CambioDatos cambio, UsuarioSesion autor) {
        Cuenta antes = cuenta(id);
        String nombre =
                cambio.nombre() == null ? antes.nombre() : obligatorio(cambio.nombre(), "nombre");
        String telefono = cambio.telefono() == null ? antes.telefono() : limpio(cambio.telefono());
        LocalDate fin = cambio.fechaFinVigencia();
        if (!Objects.equals(fin, antes.fechaFinVigencia())) {
            validarVigencia(fin);
        }
        var anteriores = new LinkedHashMap<String, Object>();
        var nuevos = new LinkedHashMap<String, Object>();
        anotar("nombre", antes.nombre(), nombre, anteriores, nuevos);
        anotar("telefono", antes.telefono(), telefono, anteriores, nuevos);
        anotar(
                "fecha_fin_vigencia",
                Objects.toString(antes.fechaFinVigencia(), null),
                Objects.toString(fin, null),
                anteriores,
                nuevos);
        if (!nuevos.isEmpty()) {
            usuarios.actualizarDatos(id, nombre, telefono, fin, autor.id());
            auditoria.registrarCambio(
                    ENTIDAD, id, "Modificacion", anteriores, nuevos, null, autor.id());
        }
        return obtener(id);
    }

    /**
     * CU-MOD01-004: pasa la cuenta a inactiva y cierra de inmediato todas sus sesiones, también la
     * del móvil (HU-MOD01-005, «Móvil perdido»). Lo registrado sigue firmado por la persona.
     */
    @Transactional
    public UsuarioResumen desactivar(long id, String motivo, UsuarioSesion autor) {
        String motivoLimpio = obligatorio(motivo, "motivo");
        Cuenta cuenta = cuenta(id);
        if (!cuenta.activo()) {
            throw ErrorCore.conflicto("cuenta-ya-inactiva", "La cuenta ya está inactiva");
        }
        if (cuenta.id() == autor.id()) {
            throw ErrorCore.conflicto("no-autodesactivar", "No puedes desactivar tu propia cuenta");
        }
        if (UsuarioSesion.JEFE.equals(cuenta.rolCodigo())
                && usuarios.contarActivosConRol(UsuarioSesion.JEFE) <= 1) {
            throw ErrorCore.conflicto(
                    "ultimo-jefe-seccion",
                    "No se puede desactivar al único jefe de sección activo");
        }
        var encargos = campo.encargosAbiertos(id);
        if (!encargos.isEmpty()) {
            throw new ErrorCore(
                    409,
                    "encargos-abiertos",
                    "El capataz tiene encargos abiertos. Reasígnalos antes de desactivar la cuenta",
                    Map.of("encargos", encargos));
        }
        usuarios.cambiarActivo(id, false, autor.id());
        sesiones.revocarTodas(id, null, OffsetDateTime.now(reloj), "desactivacion");
        auditoria.registrarCambio(
                ENTIDAD,
                id,
                "CambioEstado",
                Map.of("activo", true),
                Map.of("activo", false),
                motivoLimpio,
                autor.id());
        return obtener(id);
    }

    /** CU-MOD01-004: reactivar genera una contraseña temporal nueva. */
    @Transactional
    public ConContrasena activar(long id, UsuarioSesion autor) {
        Cuenta cuenta = cuenta(id);
        if (cuenta.activo()) {
            throw ErrorCore.conflicto("cuenta-ya-activa", "La cuenta ya está activa");
        }
        if (AutenticacionService.vencida(cuenta, LocalDate.now(reloj))) {
            throw ErrorCore.conflicto(
                    "vigencia-vencida", "Actualiza la fecha de fin de vigencia antes de reactivar");
        }
        String temporal = PoliticaContrasena.temporal();
        usuarios.cambiarCredencial(id, claves.encode(temporal), true);
        usuarios.cambiarActivo(id, true, autor.id());
        auditoria.registrarCambio(
                ENTIDAD,
                id,
                "CambioEstado",
                Map.of("activo", false),
                Map.of("activo", true),
                null,
                autor.id());
        return new ConContrasena(obtener(id), temporal, List.of());
    }

    /** CU-MOD01-001 3b: el jefe puede desbloquear antes de que pasen los 15 minutos. */
    @Transactional
    public UsuarioResumen desbloquear(long id, UsuarioSesion autor) {
        cuenta(id);
        usuarios.desbloquear(id);
        auditoria.registrarCambio(
                ENTIDAD,
                id,
                "Modificacion",
                Map.of(),
                Map.of("desbloqueo", true),
                null,
                autor.id());
        return obtener(id);
    }

    /** CU-MOD01-004 3d: cierra las sesiones sin desactivar la cuenta (móvil perdido). */
    @Transactional
    public int cerrarSesiones(long id, UsuarioSesion autor) {
        cuenta(id);
        int cerradas =
                sesiones.revocarTodas(id, null, OffsetDateTime.now(reloj), "cierre-por-jefe");
        auditoria.registrarCambio(
                ENTIDAD,
                id,
                "Modificacion",
                Map.of(),
                Map.of("sesiones_cerradas", cerradas),
                null,
                autor.id());
        return cerradas;
    }

    /** CU-MOD01-002 1b: contraseña temporal generada por el jefe; obliga a cambiarla. */
    @Transactional
    public ConContrasena contrasenaTemporal(long id, UsuarioSesion autor) {
        cuenta(id);
        String temporal = PoliticaContrasena.temporal();
        usuarios.cambiarCredencial(id, claves.encode(temporal), true);
        sesiones.revocarTodas(id, null, OffsetDateTime.now(reloj), "contrasena-temporal");
        auditoria.registrarCambio(
                ENTIDAD,
                id,
                "Modificacion",
                Map.of(),
                Map.of("contrasena", "temporal"),
                null,
                autor.id());
        return new ConContrasena(obtener(id), temporal, List.of());
    }

    /** Historial de la cuenta: altas, cambios de datos, de rol y de estado (CU-MOD01-007). */
    public List<Cambio> historial(long id) {
        cuenta(id);
        return bitacora.cambios(ENTIDAD, id);
    }

    /**
     * HU-MOD01-004, «Vigencia»: desactiva las cuentas cuya vigencia terminó. Es idempotente y corre
     * cada hora, así que también recupera las horas en que la api estuvo apagada. El autor del
     * cambio es la propia cuenta, porque no hay usuario de sistema.
     */
    @Scheduled(cron = "0 5 * * * *", zone = "America/Lima")
    @Transactional
    public int desactivarVencidas() {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        List<Long> vencidas = usuarios.vencidasActivas(ahora.toLocalDate());
        for (long id : vencidas) {
            usuarios.cambiarActivo(id, false, null);
            sesiones.revocarTodas(id, null, ahora, "fin-vigencia");
            auditoria.registrarCambio(
                    ENTIDAD,
                    id,
                    "CambioEstado",
                    Map.of("activo", true),
                    Map.of("activo", false),
                    "Fin de vigencia (automático)",
                    id);
        }
        if (!vencidas.isEmpty()) {
            log.info("Cuentas desactivadas por fin de vigencia: {}", vencidas.size());
        }
        return vencidas.size();
    }

    UsuarioResumen resumen(Cuenta c, OffsetDateTime ahora, int rechazos) {
        String estado;
        if (!c.activo()) {
            estado = "inactiva";
        } else if (c.bloqueadoHasta() != null && c.bloqueadoHasta().isAfter(ahora)) {
            estado = "bloqueada";
        } else {
            estado = "activa";
        }
        List<String> destacados = new ArrayList<>();
        OffsetDateTime referencia = c.ultimoAcceso() == null ? c.fechaCreacion() : c.ultimoAcceso();
        if (c.activo() && referencia.isBefore(ahora.minusDays(politica.diasSinUso()))) {
            destacados.add("sin-uso");
        }
        LocalDate hoy = ahora.toLocalDate();
        if (c.activo()
                && c.fechaFinVigencia() != null
                && !c.fechaFinVigencia().isBefore(hoy)
                && !c.fechaFinVigencia().isAfter(hoy.plusDays(politica.diasAvisoVigencia()))) {
            destacados.add("vence-pronto");
        }
        if (rechazos >= RECHAZOS_DESTACADOS) {
            destacados.add("rechazos");
        }
        return new UsuarioResumen(
                c.id(),
                c.correo(),
                c.nombre(),
                c.telefono(),
                c.rolCodigo(),
                c.rolNombre(),
                estado,
                c.fechaFinVigencia(),
                c.ultimoAcceso(),
                c.debeCambiarContrasena(),
                destacados);
    }

    private RolesRepository.Rol rolAsignable(String codigo) {
        var rol =
                roles.porCodigo(codigo == null ? "" : codigo)
                        .orElseThrow(
                                () -> ErrorCore.invalido("rol-desconocido", "El rol no existe"));
        if (!rol.activo()) {
            throw ErrorCore.conflicto(
                    "rol-no-habilitado",
                    "El rol "
                            + rol.nombre()
                            + " está previsto para una fase posterior. El operario se registra"
                            + " como persona de una cuadrilla");
        }
        return rol;
    }

    private void validarVigencia(LocalDate fin) {
        if (fin != null && fin.isBefore(LocalDate.now(reloj))) {
            throw ErrorCore.invalido(
                    "vigencia-pasada", "La fecha de fin de vigencia no puede estar en el pasado");
        }
    }

    private Cuenta cuenta(long id) {
        return usuarios.porId(id).orElseThrow(() -> ErrorCore.noEncontrado("El usuario no existe"));
    }

    private static String obligatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw ErrorCore.invalido(campo + "-obligatorio", "Indica el " + campo);
        }
        return valor.trim();
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static void anotar(
            String campo,
            Object antes,
            Object despues,
            Map<String, Object> anteriores,
            Map<String, Object> nuevos) {
        if (!Objects.equals(antes, despues)) {
            anteriores.put(campo, antes);
            nuevos.put(campo, despues);
        }
    }
}
