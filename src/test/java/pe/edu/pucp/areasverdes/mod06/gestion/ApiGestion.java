package pe.edu.pucp.areasverdes.mod06.gestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Base de las pruebas de API de los reportes de gestión, copia de {@code mod03.ApiPlanificacion}:
 * base PostGIS real terminada en {@code _test} y usuarios {@code jefe}, {@code ingeniero}, {@code
 * practicante}, {@code sinrol} e {@code inactivo} (jefe dado de baja), todos con {@link #CLAVE}.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiGestion {

    static final String CLAVE = "prueba-integration-local";

    /**
     * Base común de pruebas. El pool se limita a 2 conexiones: Spring guarda vivo cada contexto de
     * prueba y, con el tamaño por defecto (10), la suma de contextos agota las conexiones de
     * PostgreSQL en CI ("too many clients already").
     */
    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
        p.add("spring.datasource.hikari.maximum-pool-size", () -> 2);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    @BeforeEach
    void usuarios() {
        jdbc.getJdbcTemplate().execute("TRUNCATE mod05_usuario RESTART IDENTITY CASCADE");
        usuario("jefe", Usuario.JEFE, true);
        usuario("ingeniero", "Ingeniero de campo", true);
        usuario("practicante", "Practicante autorizado", true);
        usuario("sinrol", null, true);
        usuario("inactivo", Usuario.JEFE, false);
    }

    /** Carga src/test/resources/mod06/escenario-gestion.sql (se crea en la parte 4). */
    void cargarEscenario() throws IOException {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod06/escenario-gestion.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
    }

    private void usuario(String nombre, String rol, boolean activo) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario, nombre, password_hash, activo)"
                                + " VALUES (:u, :u, :h, :activo) RETURNING id",
                        Map.of("u", nombre, "h", claves.encode(CLAVE), "activo", activo),
                        Long.class);
        if (rol != null) {
            jdbc.update(
                    "INSERT INTO mod05_usuario_rol(usuario_id, rol) VALUES (:id, :rol)",
                    Map.of("id", id, "rol", rol));
        }
    }
}
