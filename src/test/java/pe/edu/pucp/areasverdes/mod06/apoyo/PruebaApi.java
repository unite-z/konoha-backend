package pe.edu.pucp.areasverdes.mod06.apoyo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.permisos.Permisos;

/**
 * Base de las pruebas de API ({@code api_client} de {@code conftest.py}): Spring Boot con MockMvc y
 * los dobles de {@link DoblesPrueba}. Sin base de datos, sin red y sin Gemini.
 */
// RF-IA-001 lee el reporte de la misma FuenteDatos de prueba (FuenteDatosMemoria), no del MOD-05.
@SpringBootTest(
        properties = {
            "mod06.data-source=mock",
            "mod06.gemini-api-key=",
            "mod06.gemini-model=",
            "mod06.ia.fuente-reportes=mock"
        })
@AutoConfigureMockMvc
@Import(DoblesPrueba.class)
public abstract class PruebaApi {

    /**
     * El contexto incluye el MOD-05, que necesita PostgreSQL. Se usa la misma base exclusiva {@code
     * *_test} que {@code Mod05IntegrationTest}, nunca la de desarrollo. El MOD-06 no lee ni escribe
     * en ella.
     */
    @DynamicPropertySource
    static void baseDatosPrueba(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
    }

    @Autowired protected MockMvc http;
    @Autowired protected DoblesPrueba.SelectorPrueba selector;
    @Autowired protected DoblesPrueba.FabricaPrueba fabrica;
    @Autowired protected DoblesPrueba.UsuarioPrueba usuario;
    @Autowired protected DoblesPrueba.PermisosPrueba permisos;

    @BeforeEach
    void reiniciarDobles() {
        DoblesPrueba.reiniciar(selector, fabrica, usuario, permisos);
    }

    /** {@code usar_fuente(datos, archivos)}. */
    protected FuenteDatosMemoria usarFuente(FuenteDatosMemoria fuente) {
        selector.usar(fuente);
        return fuente;
    }

    /** {@code usar_cliente_ia(propuesta, error)}. */
    protected ClienteIAFalso usarClienteIa(ClienteIAFalso cliente) {
        fabrica.usar(cliente);
        return cliente;
    }

    /** {@code api_client.force_authenticate(user=usuario_con_rol(rol, id_usuario))}. */
    protected void autenticar(String rol, Object idUsuario) {
        DoblesPrueba.conRol(usuario, permisos, rol, idUsuario);
    }

    protected void autenticar(String rol) {
        autenticar(rol, 99L);
    }

    protected MockHttpServletResponse getUrl(String url) throws Exception {
        return http.perform(get(url)).andReturn().getResponse();
    }

    /** {@code api_client.post(url, cuerpo, format="json")}. */
    protected MockHttpServletResponse postJson(String url, Object cuerpo) throws Exception {
        return http.perform(
                        post(url)
                                .contentType("application/json")
                                .content(JsonPy.dumpsRespuesta(Datos.normalizar(cuerpo))))
                .andReturn()
                .getResponse();
    }

    protected static String texto(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> json(MockHttpServletResponse r) throws Exception {
        return (Map<String, Object>) JsonPy.loads(texto(r));
    }

    protected static final String MENSAJE_ROL = Permisos.MENSAJE_ROL_SIN_ACCESO;
}
