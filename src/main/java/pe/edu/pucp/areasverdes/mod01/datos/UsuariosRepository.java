package pe.edu.pucp.areasverdes.mod01.datos;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Cuentas de MOD-01 ({@code core_usuario}). Nunca se borran: solo se desactivan (HU-MOD01-005). */
@Repository
public class UsuariosRepository {

    public record Cuenta(
            long id,
            String nombreUsuario,
            String correo,
            String nombre,
            String telefono,
            String credencialHash,
            long rolId,
            String rolCodigo,
            String rolNombre,
            boolean activo,
            LocalDate fechaFinVigencia,
            boolean debeCambiarContrasena,
            int intentosFallidos,
            OffsetDateTime bloqueadoHasta,
            OffsetDateTime ultimoAcceso,
            OffsetDateTime fechaCreacion) {}

    public record NuevaCuenta(
            String nombreUsuario,
            String correo,
            String nombre,
            String telefono,
            String credencialHash,
            long rolId,
            LocalDate fechaFinVigencia,
            long autorId) {}

    private static final String SELECCION =
            """
            SELECT u.id, u.nombre_usuario, u.correo, u.nombre, u.telefono, u.credencial_hash,
                   u.rol_id, r.codigo rol_codigo, r.nombre rol_nombre, u.activo,
                   u.fecha_fin_vigencia, u.debe_cambiar_contrasena, u.intentos_fallidos,
                   u.bloqueado_hasta, u.ultimo_acceso, u.fecha_creacion
            FROM core_usuario u JOIN core_rol r ON r.id = u.rol_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final DataClassRowMapper<Cuenta> filas = new DataClassRowMapper<>(Cuenta.class);

    public UsuariosRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Cuenta> porId(long id) {
        return jdbc.query(SELECCION + " WHERE u.id = :id", Map.of("id", id), filas).stream()
                .findFirst();
    }

    /** Busca por correo (sin distinguir mayúsculas) o por nombre de usuario (cuentas de MOD-05). */
    public Optional<Cuenta> porIdentificador(String identificador) {
        return jdbc
                .query(
                        SELECCION
                                + " WHERE lower(u.correo) = lower(:t) OR u.nombre_usuario = :t"
                                + " ORDER BY (lower(u.correo) = lower(:t)) DESC LIMIT 1",
                        Map.of("t", identificador),
                        filas)
                .stream()
                .findFirst();
    }

    public Optional<Cuenta> porCorreo(String correo) {
        return jdbc
                .query(SELECCION + " WHERE lower(u.correo) = lower(:c)", Map.of("c", correo), filas)
                .stream()
                .findFirst();
    }

    public boolean existeNombreUsuario(String nombreUsuario) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM core_usuario WHERE nombre_usuario = :n)",
                        Map.of("n", nombreUsuario),
                        Boolean.class));
    }

    public List<Cuenta> listar() {
        return jdbc.query(SELECCION + " ORDER BY u.nombre, u.id", filas);
    }

    public long insertar(NuevaCuenta nueva) {
        var p = new MapSqlParameterSource();
        p.addValue("nombreUsuario", nueva.nombreUsuario());
        p.addValue("correo", nueva.correo());
        p.addValue("nombre", nueva.nombre());
        p.addValue("telefono", nueva.telefono());
        p.addValue("hash", nueva.credencialHash());
        p.addValue("rol", nueva.rolId());
        p.addValue("fin", nueva.fechaFinVigencia());
        p.addValue("autor", nueva.autorId());
        return jdbc.queryForObject(
                """
                INSERT INTO core_usuario (nombre_usuario, correo, nombre, telefono, credencial_hash,
                    rol_id, fecha_fin_vigencia, debe_cambiar_contrasena, usuario_creacion_id,
                    usuario_modificacion_id)
                VALUES (:nombreUsuario, :correo, :nombre, :telefono, :hash, :rol, :fin, true,
                    :autor, :autor)
                RETURNING id
                """,
                p,
                Long.class);
    }

    public void actualizarDatos(
            long id, String nombre, String telefono, LocalDate fechaFinVigencia, long autorId) {
        var p = new HashMap<String, Object>();
        p.put("id", id);
        p.put("nombre", nombre);
        p.put("telefono", telefono);
        p.put("fin", fechaFinVigencia);
        p.put("autor", autorId);
        jdbc.update(
                """
                UPDATE core_usuario SET nombre = :nombre, telefono = :telefono,
                    fecha_fin_vigencia = :fin, fecha_modificacion = now(),
                    usuario_modificacion_id = :autor
                WHERE id = :id
                """,
                p);
    }

    public void cambiarRol(long id, long rolId, long autorId) {
        jdbc.update(
                """
                UPDATE core_usuario SET rol_id = :rol, fecha_modificacion = now(),
                    usuario_modificacion_id = :autor
                WHERE id = :id
                """,
                Map.of("id", id, "rol", rolId, "autor", autorId));
    }

    public void cambiarActivo(long id, boolean activo, Long autorId) {
        var p = new HashMap<String, Object>();
        p.put("id", id);
        p.put("activo", activo);
        p.put("autor", autorId);
        jdbc.update(
                """
                UPDATE core_usuario SET activo = :activo, fecha_modificacion = now(),
                    usuario_modificacion_id = coalesce(:autor, usuario_modificacion_id)
                WHERE id = :id
                """,
                p);
    }

    public void cambiarCredencial(long id, String hash, boolean debeCambiar) {
        jdbc.update(
                """
                UPDATE core_usuario SET credencial_hash = :hash, debe_cambiar_contrasena = :debe,
                    intentos_fallidos = 0, bloqueado_hasta = NULL, fecha_modificacion = now()
                WHERE id = :id
                """,
                Map.of("id", id, "hash", hash, "debe", debeCambiar));
    }

    public void registrarFallo(long id, int intentos, OffsetDateTime bloqueadoHasta) {
        var p = new HashMap<String, Object>();
        p.put("id", id);
        p.put("intentos", intentos);
        p.put("hasta", bloqueadoHasta);
        jdbc.update(
                "UPDATE core_usuario SET intentos_fallidos = :intentos, bloqueado_hasta = :hasta"
                        + " WHERE id = :id",
                p);
    }

    public void registrarAcceso(long id, OffsetDateTime instante) {
        jdbc.update(
                "UPDATE core_usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL,"
                        + " ultimo_acceso = :t WHERE id = :id",
                Map.of("id", id, "t", instante));
    }

    public void desbloquear(long id) {
        jdbc.update(
                "UPDATE core_usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE id ="
                        + " :id",
                Map.of("id", id));
    }

    public int contarActivosConRol(String codigoRol) {
        Integer n =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM core_usuario u JOIN core_rol r ON r.id = u.rol_id
                        WHERE u.activo AND r.codigo = :rol
                        """,
                        Map.of("rol", codigoRol),
                        Integer.class);
        return n == null ? 0 : n;
    }

    /** Cuentas activas cuya vigencia terminó antes de {@code hoy} (HU-MOD01-004, «Vigencia»). */
    public List<Long> vencidasActivas(LocalDate hoy) {
        return jdbc.queryForList(
                "SELECT id FROM core_usuario WHERE activo AND fecha_fin_vigencia < :hoy",
                Map.of("hoy", hoy),
                Long.class);
    }
}
