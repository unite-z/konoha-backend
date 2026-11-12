package pe.edu.pucp.areasverdes.comun;

import java.io.IOException;
import java.nio.file.Files;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Contexto único de las pruebas que recorren todos los endpoints. Compartirlo evita abrir otro
 * contexto de Spring (y su pool de conexiones) por cada prueba global.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class BasePruebaGlobal {

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry p) throws IOException {
        BaseDePruebas.registrar(p);
        String archivos = Files.createTempDirectory("prueba-global-").toString();
        p.add("mod05.archivos", () -> archivos);
    }

    @Autowired protected MockMvc http;

    @Autowired protected NamedParameterJdbcTemplate jdbc;

    @Autowired protected PasswordEncoder claves;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    protected RequestMappingHandlerMapping rutas;
}
