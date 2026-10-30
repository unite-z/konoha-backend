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
