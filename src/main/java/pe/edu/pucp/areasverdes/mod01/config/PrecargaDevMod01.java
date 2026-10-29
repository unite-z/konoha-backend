package pe.edu.pucp.areasverdes.mod01.config;

import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cuentas de prueba de MOD-01, una por rol, solo con el perfil {@code dev} y si {@code
 * MOD01_DEMO_PASSWORD} tiene valor. Es idempotente: no toca cuentas que ya existen. Nunca son
 * cuentas reales (Plan de Pruebas).
 */
@Configuration
@Profile("dev")
public class PrecargaDevMod01 {

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

    @Bean
    ApplicationRunner precargaMod01(
            NamedParameterJdbcTemplate jdbc,
            PasswordEncoder claves,
            @Value("${MOD01_DEMO_PASSWORD:}") String contrasena) {
        return args -> {
            if (contrasena.isBlank()) {
                return;
            }
            String hash = claves.encode(contrasena);
            int creadas = 0;
            for (String[] c : CUENTAS) {
                creadas +=
                        jdbc.update(
                                """
                                INSERT INTO core_usuario (nombre_usuario, correo, nombre,
                                    credencial_hash, rol_id)
                                SELECT :correo, :correo, :nombre, :hash, r.id FROM core_rol r
                                WHERE r.codigo = :rol
                                ON CONFLICT (nombre_usuario) DO NOTHING
                                """,
                                Map.of("correo", c[0], "nombre", c[1], "hash", hash, "rol", c[2]));
            }
            LoggerFactory.getLogger(PrecargaDevMod01.class)
                    .info("Precarga MOD-01: {} cuentas de prueba nuevas", creadas);
        };
    }
}
