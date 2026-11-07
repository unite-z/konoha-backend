package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/** Catálogo de tipos de intervención tercerizada (AV-313, HU-MOD02-026). */
@SpringBootTest
@AutoConfigureMockMvc
class TiposIntervencionApiTest {

    private static final String CLAVE = "prueba-integration-local";
    private static final String DOMINIO = "@tipos.prueba.pucp.edu.pe";
    private static final String RUTA = "/api/v1/inventario/tipos-intervencion/";
    private static final String PREFIJO = "Prueba tipo ";
    private static final List<String> ROLES =
            List.of("jefe-seccion", "ingeniero-campo", "practicante", "capataz");

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry propiedades) {
        BaseDePruebas.registrar(propiedades);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    private Cookie jefe;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        // Solo se tocan los tipos que crea esta suite: el catálogo de referencia lo usan otras.
        jdbc.update(
                "DELETE FROM mod02_intervencion_arbolado WHERE tipo_intervencion_id IN"
                        + " (SELECT id FROM mod02_tipo_intervencion WHERE nombre LIKE :prefijo)",
                Map.of("prefijo", PREFIJO + "%"));
        jdbc.update(
                "DELETE FROM mod02_tipo_intervencion WHERE nombre LIKE :prefijo",
                Map.of("prefijo", PREFIJO + "%"));
        jdbc.update("DELETE FROM mod02_ejemplar WHERE codigo LIKE 'TIPO-%'", Map.of());
        for (String rol : ROLES) {
            jdbc.update(
                    """
                    INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                    SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                    """,
                    Map.of("correo", rol + DOMINIO, "rol", rol, "hash", claves.encode(CLAVE)));
        }
        jefe = sesion("jefe-seccion");
    }

    @Test
    void elCatalogoTraeLosCincoTiposDelRequisito() throws Exception {
        String cuerpo =
                http.perform(get(RUTA).cookie(sesion("capataz")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<String> nombres = JsonPath.read(cuerpo, "$[*].nombre");
        assertThat(nombres)
                .contains(
                        "Poda de ramas laterales",
                        "Poda de limpieza",
                        "Reducción de copa",
                        "Extracción",
                        "Traslado y reubicación");
    }

    @Test
    void elJefeAnadeUnTipoYSeOfreceAlRegistrarLaPodaSiEsPoda() throws Exception {
        String nombre = PREFIJO + UUID.randomUUID();
        crear(
                        """
                        {"nombre": "  %s ", "descripcion": "Corte de ramas secas", "es_poda": true}
                        """
                                .formatted(nombre))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nombre").value(nombre))
                .andExpect(jsonPath("$.descripcion").value("Corte de ramas secas"))
                .andExpect(jsonPath("$.es_poda").value(true))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.usos").value(0));

        assertThat(tiposDelRegistroDePoda()).contains(nombre);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM core_registro_auditoria"
                                        + " WHERE entidad = 'mod02_tipo_intervencion'",
                                Map.of(),
                                Long.class))
                .isEqualTo(1);
    }

    @Test
    void noAdmiteDuplicadosNiNombresVacios() throws Exception {
        String nombre = PREFIJO + UUID.randomUUID();
        crear("{\"nombre\": \"%s\"}".formatted(nombre)).andExpect(status().isCreated());

        crear("{\"nombre\": \"%s\"}".formatted(nombre.toUpperCase() + " "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("tipo-duplicado"));
        crear("{\"nombre\": \"Reducción de copa\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("tipo-duplicado"));
        crear("{\"nombre\": \"  \"}").andExpect(status().isBadRequest());
    }

    @Test
    void desactivarConservaLasIntervencionesYLoQuitaDeLasOpciones() throws Exception {
        String nombre = PREFIJO + UUID.randomUUID();
        long id = idDe(crear("{\"nombre\": \"%s\", \"es_poda\": true}".formatted(nombre)));
        long intervencion = intervencionCon(id);

        cambiar(id, "{\"activo\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.usos").value(1));

        String activos =
                http.perform(get(RUTA).cookie(jefe)).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(activos, "$[*].nombre")).doesNotContain(nombre);
        http.perform(get(RUTA).queryParam("incluir_inactivos", "true").cookie(jefe))
                .andExpect(jsonPath("$[?(@.id == %d)].activo".formatted(id)).value(false));
        assertThat(tiposDelRegistroDePoda()).doesNotContain(nombre);
        http.perform(
                        get("/api/v1/inventario/intervenciones-poda/" + intervencion + "/")
                                .cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo_intervencion_nombre").value(nombre));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT accion FROM core_registro_auditoria"
                                        + " WHERE entidad = 'mod02_tipo_intervencion'"
                                        + " ORDER BY id DESC LIMIT 1",
                                Map.of(),
                                String.class))
                .isEqualTo("BajaLogica");

        cambiar(id, "{\"activo\": true, \"descripcion\": \"Nuevo alcance\"}")
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.descripcion").value("Nuevo alcance"));
    }

    @Test
    void cambiosInvalidos() throws Exception {
        long id = idDe(crear("{\"nombre\": \"%s\"}".formatted(PREFIJO + UUID.randomUUID())));
        cambiar(id, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("cambio-vacio"));
        cambiar(id, "{\"nombre\": \" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("nombre-obligatorio"));
        cambiar(id, "{\"nombre\": \"Extracción\"}").andExpect(status().isConflict());
        cambiar(999999, "{\"activo\": false}").andExpect(status().isNotFound());
    }

    @Test
    void soloElJefeGestionaYLosCuatroRolesConsultan() throws Exception {
        long id = idDe(crear("{\"nombre\": \"%s\"}".formatted(PREFIJO + UUID.randomUUID())));
        for (String rol : ROLES) {
            Cookie cookie = sesion(rol);
            http.perform(get(RUTA).cookie(cookie)).andExpect(status().isOk());
            if (!rol.equals("jefe-seccion")) {
                http.perform(
                                post(RUTA)
                                        .cookie(cookie)
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"nombre\": \"%s\"}".formatted(PREFIJO + rol)))
                        .andExpect(status().isForbidden());
                http.perform(
                                patch(RUTA + id)
                                        .cookie(cookie)
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"activo\": false}"))
                        .andExpect(status().isForbidden());
            }
        }
        http.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }

    private List<String> tiposDelRegistroDePoda() throws Exception {
        String cuerpo =
                http.perform(get("/api/v1/inventario/intervenciones-poda/catalogos/").cookie(jefe))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JsonPath.read(cuerpo, "$.tipos[*].nombre");
    }

    /** Intervención mínima con el tipo, para comprobar que desactivarlo no la toca. */
    private long intervencionCon(long tipo) {
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('mod02_ejemplar', 'id'),"
                        + " coalesce(max(id), 0) + 1, false) FROM mod02_ejemplar",
                Map.of(),
                Long.class);
        long ejemplar =
                jdbc.queryForObject(
                        "INSERT INTO mod02_ejemplar (codigo) VALUES ('TIPO-1') RETURNING id",
                        Map.of(),
                        Long.class);
        return jdbc.queryForObject(
                """
                INSERT INTO mod02_intervencion_arbolado (ejemplar_id, categoria,
                    tipo_intervencion_id, usuario_creacion_id, usuario_modificacion_id)
                SELECT :ejemplar, 'AlturaDemanda', :tipo, min(id), min(id) FROM core_usuario
                RETURNING id
                """,
                Map.of("ejemplar", ejemplar, "tipo", tipo),
                Long.class);
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return http.perform(
                post(RUTA)
                        .cookie(jefe)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private ResultActions cambiar(long id, String cuerpo) throws Exception {
        return http.perform(
                patch(RUTA + id)
                        .cookie(jefe)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private static long idDe(ResultActions respuesta) throws Exception {
        Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private Cookie sesion(String rol) throws Exception {
        return http.perform(
                        post("/api/v1/core/auth/login/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"identificador\": \"%s\", \"contrasena\": \"%s\"}"
                                                .formatted(rol + DOMINIO, CLAVE)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(SeguridadMod01.COOKIE_SESION);
    }
}
