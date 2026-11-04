package pe.edu.pucp.areasverdes.mod03;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

/**
 * Base de las pruebas de API de MOD-03: base PostGIS real terminada en {@code _test} y usuarios
 * {@code jefe}, {@code ingeniero}, {@code practicante} y {@code sinrol}, todos con {@link #CLAVE}.
 * Cada uno tiene su cuenta de MOD-01 con el mismo id, como en producción (copia de {@code V6}),
 * para la auditoría común, cuyo autor apunta a {@code core_usuario}.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiPlanificacion {

    static final String CLAVE = "prueba-integration-local";

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    @BeforeEach
    void usuarios() {
        jdbc.getJdbcTemplate().execute("TRUNCATE mod05_usuario RESTART IDENTITY CASCADE");
        usuario("jefe", "Jefe de sección");
        usuario("ingeniero", "Ingeniero de campo");
        usuario("practicante", "Practicante autorizado");
        usuario("sinrol", null);
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        INSERT INTO core_usuario (id, nombre_usuario, correo, nombre, credencial_hash,
                            rol_id)
                        SELECT u.id, 'mod05-' || u.usuario, 'mod05-' || u.usuario || '@prueba.pucp.edu.pe',
                            u.nombre, 'sin-clave', r.id
                        FROM mod05_usuario u, core_rol r WHERE r.codigo = 'jefe-seccion'
                        """);
    }

    private void usuario(String nombre, String rol) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario, nombre, password_hash, activo)"
                                + " VALUES (:u, :u, :h, true) RETURNING id",
                        Map.of("u", nombre, "h", claves.encode(CLAVE)),
                        Long.class);
        if (rol != null) {
            jdbc.update(
                    "INSERT INTO mod05_usuario_rol(usuario_id, rol) VALUES (:id, :rol)",
                    Map.of("id", id, "rol", rol));
        }
    }
}
