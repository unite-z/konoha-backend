package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.stream.Collectors;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/** Contrato y matriz de permisos de las campañas de poda (AV-308, RN-011, RN-018, RI-31). */
@SpringBootTest
@AutoConfigureMockMvc
class CampanasPodaApiTest {

    private static final String CLAVE = "prueba-integration-local";
    private static final String DOMINIO = "@mod02.prueba.pucp.edu.pe";
    private static final String RUTA = "/api/v1/inventario/campanas-poda/";
    private static final List<String> ROLES =
            List.of("jefe-seccion", "ingeniero-campo", "practicante", "capataz");

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry propiedades) {
        BaseDePruebas.registrar(propiedades);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    private long ejemplarA;
    private long ejemplarB;
    private Cookie jefe;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM mod02_ejemplar WHERE codigo LIKE 'CAMP-%'", Map.of());
        for (String rol : ROLES) {
            jdbc.update(
                    """
                    INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                    SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                    """,
                    Map.of("correo", rol + DOMINIO, "rol", rol, "hash", claves.encode(CLAVE)));
        }
        ejemplarA = ejemplar("CAMP-A");
        ejemplarB = ejemplar("CAMP-B");
        jefe = sesion("jefe-seccion");
    }

    @Test
    void planificaDentroDelCierreYCalculaLaCapacidad() throws Exception {
        cierre("2026-12-20", "2027-01-19");

        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Planificada", ejemplarA, ejemplarB))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.anio").value(2026))
                .andExpect(jsonPath("$.estado").value("Planificada"))
                .andExpect(jsonPath("$.total_ejemplares").value(2))
                .andExpect(jsonPath("$.capacidad.dias").value(10))
                .andExpect(jsonPath("$.capacidad.estimada").value(10))
                .andExpect(jsonPath("$.capacidad.requerido_por_dia").value("0.20"))
                .andExpect(jsonPath("$.capacidad.excedida").value(false));
    }

    @Test
    void superarLaCapacidadAvisaSinBloquear() throws Exception {
        cierre("2026-12-20", "2027-01-19");

        crear(cuerpo("Anual", "2026-12-20", "2026-12-20", "Planificada", ejemplarA, ejemplarB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacidad.excedida").value(true));
    }

    @Test
    void diezDiasSeAceptanYOnceNo() throws Exception {
        cierre("2026-12-20", "2027-01-19");

        crear(cuerpo("Anual", "2026-12-20", "2026-12-30", "Planificada", ejemplarA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("campana-duracion-excedida"));
        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Planificada", ejemplarA))
                .andExpect(status().isCreated());
    }

    @Test
    void laDuracionMaximaSaleDeLaConfiguracionDeMod03() throws Exception {
        cierre("2026-12-20", "2027-01-19");
        // Otras suites vacían el catálogo de servicios: esta no depende del orden de ejecución.
        jdbc.update(
                """
                INSERT INTO mod05_tiposervicio (nombre, ejecutor, es_riesgo, activo, es_periodico)
                SELECT 'Poda mayor programada', 'Tercerizado', false, true, true
                WHERE NOT EXISTS (SELECT 1 FROM mod05_tiposervicio
                    WHERE nombre = 'Poda mayor programada')
                """,
                Map.of());
        int configuradas =
                jdbc.update(
                        """
                        INSERT INTO mod03_duracion_servicio (tipo_servicio_id, duracion_maxima_dias,
                            usuario_creacion_id, usuario_modificacion_id)
                        SELECT t.id, 5, u.id, u.id FROM mod05_tiposervicio t, core_usuario u
                        WHERE t.nombre = 'Poda mayor programada' ORDER BY t.id, u.id LIMIT 1
                        """,
                        Map.of());
        assertThat(configuradas).isEqualTo(1);

        crear(cuerpo("Anual", "2026-12-20", "2026-12-25", "Planificada", ejemplarA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("campana-duracion-excedida"));
    }

    @Test
    void laCampanaAnualDebeCaerDentroDelCierre() throws Exception {
        cierre("2026-12-20", "2027-01-19");

        crear(cuerpo("Anual", "2026-12-15", "2026-12-20", "Planificada", ejemplarA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("campana-fuera-de-cierre"));
    }

    @Test
    void elCierrePuedeCruzarElCambioDeAnio() throws Exception {
        cierre("2026-12-20", "2027-01-19");

        crear(cuerpo("Anual", "2026-12-28", "2027-01-06", "Planificada", ejemplarA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anio").value(2026));
    }

    @Test
    void sinCierreRegistradoNoSePlanifica() throws Exception {
        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Planificada", ejemplarA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("cierre-no-registrado"));
    }

    @Test
    void unBorradorNoExigeLasReglasHastaPlanificarlo() throws Exception {
        long id =
                idDe(
                        crear(cuerpo("Anual", "2026-11-01", "2026-11-30", "Borrador"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.estado").value("Borrador")));

        accion(id, "planificacion")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("campana-sin-ejemplares"));
    }

    @Test
    void laSegundaCampanaAnualSeRechazaYPuedeSerExtraordinaria() throws Exception {
        cierre("2026-12-20", "2027-01-19");
        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Planificada", ejemplarA))
                .andExpect(status().isCreated());

        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Planificada", ejemplarB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("campana-anual-existente"));
        crear(cuerpo("Extraordinaria", "2026-06-01", "2026-06-05", "Planificada", ejemplarB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("motivo-obligatorio"));
        crear(
                        """
                        {"nombre": "Riesgo", "clase": "Extraordinaria",
                         "motivo_extraordinaria": "Atención urgente por riesgo",
                         "fecha_inicio": "2026-06-01", "fecha_fin": "2026-06-05",
                         "objetivo_diario": 12, "ejemplar_ids": [%d], "estado": "Planificada"}
                        """
                                .formatted(ejemplarB))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clase").value("Extraordinaria"));
    }

    @Test
    void laExtraordinariaTambienRespetaElMaximoDeDias() throws Exception {
        crear(
                        """
                        {"nombre": "Riesgo", "clase": "Extraordinaria", "motivo_extraordinaria": "Riesgo",
                         "fecha_inicio": "2026-06-01", "fecha_fin": "2026-06-11",
                         "objetivo_diario": 12, "ejemplar_ids": [%d], "estado": "Planificada"}
                        """
                                .formatted(ejemplarA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("campana-duracion-excedida"));
    }

    @Test
    void retirarLaCampanaLiberaElAnio() throws Exception {
        long id =
                idDe(
                        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Borrador"))
                                .andExpect(status().isCreated()));

        http.perform(delete(RUTA + id + "/").cookie(jefe).with(csrf()))
                .andExpect(status().isNoContent());
        http.perform(get(RUTA + id + "/").cookie(jefe)).andExpect(status().isNotFound());
        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Borrador"))
                .andExpect(status().isCreated());
    }

    @Test
    void elCicloDeEstadosAvanzaSoloEnOrden() throws Exception {
        cierre("2026-12-20", "2027-01-19");
        long id =
                idDe(
                        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Borrador", ejemplarA))
                                .andExpect(status().isCreated()));

        accion(id, "inicio")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("transicion-no-permitida"));
        accion(id, "planificacion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Planificada"));
        accion(id, "inicio")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EnEjecucion"));

        cambiar(id, "{\"nombre\": \"Otro\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("transicion-no-permitida"));
        http.perform(delete(RUTA + id + "/").cookie(jefe).with(csrf()))
                .andExpect(status().isConflict());

        accion(id, "fin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Finalizada"));
        assertThat(auditorias(id)).isEqualTo(4);
    }

    @Test
    void cambiarReemplazaLaListaYRevalidaUnaPlanificada() throws Exception {
        cierre("2026-12-20", "2027-01-19");
        long id =
                idDe(
                        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Planificada", ejemplarA))
                                .andExpect(status().isCreated()));

        cambiar(id, "{\"ejemplar_ids\": [%d, %d, %d]}".formatted(ejemplarA, ejemplarB, ejemplarB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_ejemplares").value(2));
        cambiar(id, "{\"ejemplar_ids\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("campana-sin-ejemplares"));
        cambiar(id, "{\"fecha_fin\": \"2026-12-30\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("campana-duracion-excedida"));
        cambiar(id, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("cambio-vacio"));
    }

    @Test
    void laListaConReferenciasAlCatastroSeConsultaYFiltra() throws Exception {
        jdbc.update(
                "UPDATE mod02_ejemplar SET referencia_catastro = 'CAT-77' WHERE id = :id",
                Map.of("id", ejemplarA));
        long id =
                idDe(
                        crear(
                                        cuerpo(
                                                "Anual",
                                                "2026-12-20",
                                                "2026-12-29",
                                                "Borrador",
                                                ejemplarA,
                                                ejemplarB))
                                .andExpect(status().isCreated()));

        http.perform(get(RUTA + id + "/ejemplares/").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        http.perform(get(RUTA + id + "/ejemplares/").queryParam("q", "CAT-77").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].referencia_catastro").value("CAT-77"));
    }

    @Test
    void rechazaEjemplaresInexistentesYFechasInvertidas() throws Exception {
        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Borrador", 999_999_999L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ejemplar-inexistente"));
        crear(cuerpo("Anual", "2026-12-29", "2026-12-20", "Borrador"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fechas-invalidas"));
    }

    @Test
    void laVentanaDeCierreSaleDelCalendario() throws Exception {
        http.perform(get(RUTA + "ventana-cierre/").queryParam("anio", "2026").cookie(jefe))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("cierre-no-registrado"));

        cierre("2026-12-20", "2027-01-19");

        http.perform(get(RUTA + "ventana-cierre/").queryParam("anio", "2026").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha_inicio").value("2026-12-20"))
                .andExpect(jsonPath("$.fecha_fin").value("2027-01-19"))
                .andExpect(jsonPath("$.duracion_maxima_dias").value(10));
    }

    @Test
    void listarFiltraPorAnioYEstado() throws Exception {
        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Borrador"))
                .andExpect(status().isCreated());

        http.perform(get(RUTA).queryParam("anio", "2026").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].nombre").value("Cierre 2026-2027"));
        http.perform(get(RUTA).queryParam("anio", "2027").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        http.perform(get(RUTA).queryParam("estado", "Planificada").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void losCuatroRolesConsultan() throws Exception {
        long id =
                idDe(
                        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Borrador"))
                                .andExpect(status().isCreated()));

        for (String rol : ROLES) {
            Cookie cookie = sesion(rol);
            http.perform(get(RUTA).cookie(cookie)).andExpect(status().isOk());
            http.perform(get(RUTA + id + "/").cookie(cookie)).andExpect(status().isOk());
            http.perform(get(RUTA + id + "/ejemplares/").cookie(cookie)).andExpect(status().isOk());
        }
    }

    @Test
    void soloElJefePlanifica() throws Exception {
        long id =
                idDe(
                        crear(cuerpo("Anual", "2026-12-20", "2026-12-29", "Borrador"))
                                .andExpect(status().isCreated()));
        List<MockHttpServletRequestBuilder> reservadas =
                List.of(
                        get(RUTA + "ventana-cierre/").queryParam("anio", "2026"),
                        post(RUTA)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo("Anual", "2027-12-20", "2027-12-29", "Borrador")),
                        patch(RUTA + id + "/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nombre\": \"Otro\"}"),
                        delete(RUTA + id + "/"),
                        post(RUTA + id + "/planificacion/"),
                        post(RUTA + id + "/inicio/"),
                        post(RUTA + id + "/fin/"));

        for (String rol : List.of("ingeniero-campo", "practicante", "capataz")) {
            Cookie cookie = sesion(rol);
            for (MockHttpServletRequestBuilder peticion : reservadas) {
                http.perform(peticion.cookie(cookie).with(csrf()))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.codigo").value("sin-permiso"));
            }
        }
    }

    @Test
    void sinSesionNoHayAcceso() throws Exception {
        http.perform(get(RUTA)).andExpect(status().isUnauthorized());
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
                patch(RUTA + id + "/")
                        .cookie(jefe)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private ResultActions accion(long id, String accion) throws Exception {
        return http.perform(post(RUTA + id + "/" + accion + "/").cookie(jefe).with(csrf()));
    }

    private static long idDe(ResultActions respuesta) throws Exception {
        Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String cuerpo(
            String clase, String inicio, String fin, String estado, long... ejemplares) {
        String lista =
                java.util.Arrays.stream(ejemplares)
                        .mapToObj(Long::toString)
                        .collect(Collectors.joining(", "));
        return """
        {"nombre": "Cierre 2026-2027", "clase": "%s", "fecha_inicio": "%s", "fecha_fin": "%s",
         "objetivo_diario": 1, "ejemplar_ids": [%s], "estado": "%s"}
        """
                .formatted(clase, inicio, fin, lista, estado);
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

    private long ejemplar(String codigo) {
        return jdbc.queryForObject(
                "INSERT INTO mod02_ejemplar (id, codigo) SELECT coalesce(max(id), 0) + 1, :codigo"
                        + " FROM mod02_ejemplar RETURNING id",
                Map.of("codigo", codigo),
                Long.class);
    }

    private void cierre(String inicio, String fin) {
        jdbc.update(
                """
                INSERT INTO mod03_restriccion_calendario (tipo_restriccion_id, fecha_inicio, fecha_fin,
                    usuario_creacion_id, usuario_modificacion_id)
                SELECT t.id, CAST(:inicio AS date), CAST(:fin AS date), u.id, u.id
                FROM mod03_tipo_restriccion t, core_usuario u
                WHERE t.nombre = 'Cierre del campus' ORDER BY u.id LIMIT 1
                """,
                Map.of("inicio", inicio, "fin", fin));
    }

    private int auditorias(long id) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM core_registro_auditoria
                WHERE entidad = 'mod02_campana_poda' AND registro_id = :id
                """,
                Map.of("id", id),
                Integer.class);
    }
}
