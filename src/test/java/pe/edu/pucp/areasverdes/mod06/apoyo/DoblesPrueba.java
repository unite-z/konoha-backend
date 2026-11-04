package pe.edu.pucp.areasverdes.mod06.apoyo;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.SelectorFuenteDatos;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteGemini;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;
import pe.edu.pucp.areasverdes.mod06.ia.FabricaClienteIA;
import pe.edu.pucp.areasverdes.mod06.permisos.Permisos;
import pe.edu.pucp.areasverdes.mod06.permisos.ProveedorUsuario;
import pe.edu.pucp.areasverdes.mod06.permisos.Usuario;

/**
 * Dobles para las pruebas de la API (fixtures {@code usar_fuente}, {@code usar_cliente_ia}, {@code
 * usuario_con_rol} y {@code sin_ia_real} de {@code conftest.py}). Cada prueba llama a {@link
 * #reiniciar()} antes de empezar.
 */
@TestConfiguration
public class DoblesPrueba {

    /** {@code usar_fuente}: instala una fuente de datos para todo el back-end. */
    public static class SelectorPrueba extends SelectorFuenteDatos {
        private FuenteDatos fuente;

        public SelectorPrueba(Mod06Propiedades propiedades) {
            super(propiedades);
        }

        public void usar(FuenteDatos f) {
            this.fuente = f;
        }

        @Override
        public FuenteDatos obtenerFuenteDatos() {
            return fuente != null ? fuente : super.obtenerFuenteDatos();
        }
    }

    /** {@code usar_cliente_ia} y {@code settings.GEMINI_*}. */
    public static class FabricaPrueba extends FabricaClienteIA {
        private ClienteIA cliente;
        private String clave;
        private String modelo;

        public FabricaPrueba(Mod06Propiedades propiedades) {
            super(propiedades);
        }

        public void usar(ClienteIA c) {
            this.cliente = c;
        }

        /**
         * Simula {@code settings.GEMINI_API_KEY} y {@code settings.GEMINI_MODEL}, con el SDK
         * prohibido ({@code sin_ia_real}).
         */
        public void configuracion(String clave, String modelo) {
            this.clave = clave;
            this.modelo = modelo;
        }

        @Override
        public ClienteIA obtenerClienteIa() {
            if (cliente != null) return cliente;
            if (clave != null) {
                return new ClienteGemini(
                        clave,
                        modelo,
                        parametros -> {
                            throw new AssertionError("Las pruebas no deben llamar a Gemini");
                        });
            }
            throw new AssertionError("Las pruebas no deben llamar a Gemini");
        }
    }

    /** Usuario de la petición ({@code api_client.force_authenticate}). */
    public static class UsuarioPrueba extends ProveedorUsuario {
        private Usuario usuario;

        public void usar(Usuario u) {
            this.usuario = u;
        }

        @Override
        public Usuario usuario(HttpServletRequest peticion) {
            return usuario;
        }
    }

    /**
     * Las pruebas de la API simulan al usuario con {@link UsuarioPrueba}: la petición lleva un
     * autenticado cualquiera para pasar la cadena de seguridad, que cubre {@code
     * MatrizPermisosMod06ApiTest} con sesiones reales.
     */
    @Bean
    MockMvcBuilderCustomizer peticionesAutenticadas() {
        return constructor ->
                constructor.defaultRequest(get("/").with(user("prueba")).with(csrf()));
    }

    @Bean
    @Primary
    SelectorPrueba selectorPrueba(Mod06Propiedades propiedades) {
        return new SelectorPrueba(propiedades);
    }

    @Bean
    @Primary
    FabricaPrueba fabricaPrueba(Mod06Propiedades propiedades) {
        return new FabricaPrueba(propiedades);
    }

    @Bean
    @Primary
    UsuarioPrueba usuarioPrueba() {
        return new UsuarioPrueba();
    }

    /** Permisos que MOD-01 concede hoy a cada rol ({@code V9__core_permisos.sql}). */
    private static final Map<String, Set<String>> PERMISOS_POR_ROL =
            Map.of(
                    "Jefe de sección",
                    Set.of(Permisos.CONSULTAR_REPORTES, Permisos.PROPONER_IA, Permisos.VALIDAR_IA),
                    "Ingeniero de campo",
                    Set.of(Permisos.CONSULTAR_REPORTES, Permisos.PROPONER_IA),
                    "Practicante",
                    Set.of(Permisos.CONSULTAR_REPORTES, Permisos.PROPONER_IA));

    /** Estado inicial de los dobles: un usuario sin id con todos los permisos. */
    public static void reiniciar(
            SelectorPrueba selector, FabricaPrueba fabrica, UsuarioPrueba usuario) {
        selector.usar(null);
        fabrica.usar(null);
        fabrica.configuracion(null, null);
        conRol(usuario, "Jefe de sección", null);
    }

    /** {@code usuario_con_rol(rol, id_usuario)}. */
    public static void conRol(UsuarioPrueba usuario, String rol, Object idUsuario) {
        usuario.usar(new Usuario(true, idUsuario, PERMISOS_POR_ROL.getOrDefault(rol, Set.of())));
    }
}
