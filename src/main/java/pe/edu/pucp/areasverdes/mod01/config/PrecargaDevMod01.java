package pe.edu.pucp.areasverdes.mod01.config;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cuentas de MOD-01 en desarrollo (perfil {@code dev}). Nunca son cuentas reales (Plan de Pruebas).
 *
 * <ol>
 *   <li>Copia a {@code core_usuario} las cuentas de {@code mod05_usuario} que falten, con el mismo
 *       {@code id} y la misma contraseña. En una base nueva la precarga de MOD-05 crea {@code
 *       mod05_jefe} y compañía después de las migraciones, así que la copia de {@code V6} no los
 *       ve. Así se entra con las cuentas de siempre sin configurar nada más.
 *   <li>Si {@code MOD01_DEMO_PASSWORD} tiene valor, crea además una cuenta por rol ({@code
 *       jefe@pucp.edu.pe}, {@code capataz@pucp.edu.pe}...).
 * </ol>
 *
 * Corre al terminar el arranque, después de las precargas de los demás módulos. Es idempotente.
 */
@Component
@Profile("dev")
public class PrecargaDevMod01 {

    private static final Logger log = LoggerFactory.getLogger(PrecargaDevMod01.class);

    static final List<String[]> CUENTAS =
            List.of(
                    new String[] {"jefe@pucp.edu.pe", "Jefe de prueba", "jefe-seccion"},
                    new String[] {
                        "ingeniero@pucp.edu.pe", "Ingeniero de prueba", "ingeniero-campo"
                    },
                    new String[] {
                        "practicante@pucp.edu.pe", "Practicante de prueba", "practicante"
                    },
                    new String[] {"capataz@pucp.edu.pe", "Capataz de prueba", "capataz"});

    /** La misma regla que {@code V6__core_roles_usuarios.sql}: un solo rol, el de más alcance. */
    static final String COPIA_MOD05 =
            """
            INSERT INTO core_usuario (id, nombre_usuario, correo, nombre, credencial_hash, rol_id,
                activo)
            SELECT u.id,
                   u.usuario,
                   CASE WHEN u.usuario ~ '^[^@\\s]+@[^@\\s]+$' THEN lower(u.usuario) END,
                   u.nombre,
                   u.password_hash,
                   (SELECT r.id FROM core_rol r WHERE r.codigo = coalesce(m.codigo, 'practicante')),
                   u.activo AND m.codigo IS NOT NULL
            FROM mod05_usuario u
            LEFT JOIN LATERAL (
                SELECT CASE ur.rol
                           WHEN 'Jefe de sección' THEN 'jefe-seccion'
                           WHEN 'Ingeniero de campo' THEN 'ingeniero-campo'
                           WHEN 'Capataz' THEN 'capataz'
                           ELSE 'practicante'
                       END AS codigo
                FROM mod05_usuario_rol ur
                WHERE ur.usuario_id = u.id
                ORDER BY CASE ur.rol
                             WHEN 'Jefe de sección' THEN 1
                             WHEN 'Ingeniero de campo' THEN 2
                             WHEN 'Practicante autorizado' THEN 3
                             ELSE 4
                         END
                LIMIT 1
            ) m ON true
            WHERE NOT EXISTS (SELECT 1 FROM core_usuario c
                              WHERE c.id = u.id OR c.nombre_usuario = u.usuario)
            ON CONFLICT DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder claves;
    private final String contrasena;

    public PrecargaDevMod01(
            NamedParameterJdbcTemplate jdbc,
            PasswordEncoder claves,
            @Value("${MOD01_DEMO_PASSWORD:}") String contrasena) {
        this.jdbc = jdbc;
        this.claves = claves;
        this.contrasena = contrasena;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void precargar() {
        int copiadas = copiarCuentasMod05(jdbc);
        int creadas =
                contrasena.isBlank() ? 0 : crearCuentasDePrueba(jdbc, claves.encode(contrasena));
        log.info(
                "Precarga MOD-01: {} cuentas copiadas de MOD-05 y {} cuentas de prueba nuevas",
                copiadas,
                creadas);
    }

    /** Copia las cuentas de MOD-05 que falten. Va antes de crear otras para conservar sus id. */
    public static int copiarCuentasMod05(NamedParameterJdbcTemplate jdbc) {
        int copiadas = jdbc.update(COPIA_MOD05, Map.of());
        ajustarSecuencia(jdbc);
        return copiadas;
    }

    public static int crearCuentasDePrueba(NamedParameterJdbcTemplate jdbc, String hash) {
        int creadas = 0;
        for (String[] c : CUENTAS) {
            creadas +=
                    jdbc.update(
                            """
                            INSERT INTO core_usuario (nombre_usuario, correo, nombre,
                                credencial_hash, rol_id)
                            SELECT :correo, :correo, :nombre, :hash, r.id FROM core_rol r
                            WHERE r.codigo = :rol
                            ON CONFLICT DO NOTHING
                            """,
                            Map.of("correo", c[0], "nombre", c[1], "hash", hash, "rol", c[2]));
        }
        return creadas;
    }

    /** Tras insertar con id explícito, la secuencia sigue por encima del mayor id. */
    static void ajustarSecuencia(NamedParameterJdbcTemplate jdbc) {
        jdbc.getJdbcTemplate()
                .execute(
                        "SELECT setval(pg_get_serial_sequence('core_usuario', 'id'),"
                                + " coalesce((SELECT max(id) FROM core_usuario), 0) + 1, false)");
    }
}
