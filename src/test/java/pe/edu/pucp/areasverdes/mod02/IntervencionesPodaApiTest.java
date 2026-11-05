package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Base64;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/** Registro de poda con su evidencia, permisos y avance de la campaña (AV-309, HU-MOD02-022). */
@SpringBootTest
@AutoConfigureMockMvc
class IntervencionesPodaApiTest {

    private static final String CLAVE = "prueba-integration-local";
    private static final String DOMINIO = "@poda.prueba.pucp.edu.pe";
    private static final String RUTA = "/api/v1/inventario/intervenciones-poda/";
    private static final String CAMPANAS = "/api/v1/inventario/campanas-poda/";
    private static final List<String> ROLES =
            List.of("jefe-seccion", "ingeniero-campo", "practicante", "capataz");

    /** PNG de 1 × 1 píxel, para que el validador de adjuntos lo acepte como imagen real. */
    private static final byte[] PNG =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
                                    + "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private static final String ARCHIVOS;

    static {
        try {
            ARCHIVOS = Files.createTempDirectory("mod02-poda-").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry propiedades) {
        BaseDePruebas.registrar(propiedades);
        propiedades.add("mod05.archivos", () -> ARCHIVOS);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    private long ejemplarA;
    private long ejemplarB;
    private long fuera;
    private long campana;
    private long tipo;
    private long unidad;
    private Cookie ingeniero;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM core_adjunto WHERE entidad = 'intervencion'", Map.of());
        jdbc.update("DELETE FROM mod02_ejemplar WHERE codigo LIKE 'PODA-%'", Map.of());
        for (String rol : ROLES) {
            jdbc.update(
                    """
                    INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                    SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                    """,
                    Map.of("correo", rol + DOMINIO, "rol", rol, "hash", claves.encode(CLAVE)));
        }
        ejemplarA = ejemplar("PODA-A");
        ejemplarB = ejemplar("PODA-B");
        fuera = ejemplar("PODA-FUERA");
        campana = campana("EnEjecucion");
        lista(campana, ejemplarA, ejemplarB);
        tipo =
                jdbc.queryForObject(
                        "SELECT id FROM mod02_tipo_intervencion WHERE nombre = 'Poda de limpieza'",
                        Map.of(),
                        Long.class);
        // Otras suites vacían el catálogo de unidades: esta no depende del orden de ejecución.
        jdbc.update(
                """
                INSERT INTO mod05_unidadmedida (nombre, simbolo)
                SELECT 'Rama', 'rama' WHERE NOT EXISTS
                    (SELECT 1 FROM mod05_unidadmedida WHERE nombre = 'Rama')
                """,
                Map.of());
        unidad =
                jdbc.queryForObject(
                        "SELECT min(id) FROM mod05_unidadmedida WHERE nombre = 'Rama'",
                        Map.of(),
                        Long.class);
        ingeniero = sesion("ingeniero-campo");
    }

    @Test
    void registraLaPodaCompletaConSusCantidades() throws Exception {
        crear(
                        """
                        {"ejemplar_id": %d, "campana_id": %d, "tipo_intervencion_id": %d,
                         "fecha_ejecucion": "2025-12-21", "reduccion_copa_pct": "15.50",
                         "ramas_laterales_cortadas": 3, "cantidad_solicitada": "4",
                         "cantidad_ejecutada": "3", "unidad_medida_id": %d, "prioridad": "Alta"}
                        """
                                .formatted(ejemplarA, campana, tipo, unidad))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.categoria").value("MayorCampana"))
                .andExpect(jsonPath("$.ejemplar_codigo").value("PODA-A"))
                .andExpect(jsonPath("$.tipo_intervencion_nombre").value("Poda de limpieza"))
                .andExpect(jsonPath("$.reduccion_copa_pct").value("15.50"))
                .andExpect(jsonPath("$.ramas_laterales_cortadas").value(3))
                .andExpect(jsonPath("$.cantidad_ejecutada").value("3.00"))
                .andExpect(jsonPath("$.unidad_medida_simbolo").value("rama"))
                .andExpect(jsonPath("$.prioridad").value("Alta"))
                .andExpect(jsonPath("$.estado").value("Incompleta"));
    }

    @Test
    void conFotoQuedaVerificadaYActualizaElAvance() throws Exception {
        long id = registrar(ejemplarA);
        avance(0, 1, "0.0");

        http.perform(
                        multipart("/api/v1/core/adjuntos/")
                                .file(
                                        new MockMultipartFile(
                                                "archivo", "poda.png", "image/png", PNG))
                                .param("entidad", "intervencion")
                                .param("registro_id", String.valueOf(id))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .cookie(ingeniero)
                                .with(csrf()))
                .andExpect(status().isCreated());

        http.perform(get(RUTA + id + "/").cookie(ingeniero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Verificada"));
        avance(1, 0, "50.0");
        http.perform(
                        get(CAMPANAS + campana + "/ejemplares/")
                                .queryParam("q", "PODA-A")
                                .cookie(ingeniero))
                .andExpect(jsonPath("$.results[0].estado_intervencion").value("Verificada"));
        http.perform(
                        get(CAMPANAS + campana + "/ejemplares/")
                                .queryParam("q", "PODA-B")
                                .cookie(ingeniero))
                .andExpect(jsonPath("$.results[0].estado_intervencion").value("Pendiente"));
    }

    @Test
    void unDocumentoNoEsFotografiaYSigueIncompleta() throws Exception {
        long id = registrar(ejemplarA);
        long usuario =
                jdbc.queryForObject("SELECT min(id) FROM core_usuario", Map.of(), Long.class);
        long archivo =
                jdbc.queryForObject(
                        """
                        INSERT INTO core_archivo (nombre_original, tipo_mime, tamano_bytes,
                            hash_contenido, clave)
                        VALUES ('a.pdf', 'application/pdf', 1, repeat('a', 64), :clave) RETURNING id
                        """,
                        Map.of("clave", "prueba/" + UUID.randomUUID()),
                        Long.class);
        jdbc.update(
                """
                INSERT INTO core_adjunto (uuid_cliente, entidad, registro_id, archivo_id,
                    hash_contenido, usuario_creacion_id)
                VALUES (CAST(:uuid AS uuid), 'intervencion', :id, :archivo, repeat('a', 64), :usuario)
                """,
                Map.of(
                        "uuid",
                        UUID.randomUUID().toString(),
                        "id",
                        id,
                        "archivo",
                        archivo,
                        "usuario",
                        usuario));

        http.perform(get(RUTA + id + "/").cookie(ingeniero))
                .andExpect(jsonPath("$.estado").value("Incompleta"));
        avance(0, 1, "0.0");
    }

    @Test
    void validaElOrigenYLaListaDeLaCampana() throws Exception {
        crear(cuerpoSinOrigen(ejemplarA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("origen-obligatorio"));
        crear(cuerpo(fuera, "2025-12-21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ejemplar-fuera-de-campana"));
        crear(cuerpo(ejemplarA, "2025-12-19"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fecha-fuera-de-periodo"));
        crear(cuerpo(ejemplarA, "2999-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fecha-futura"));
    }

    @Test
    void soloSeRegistraEnUnaCampanaEnEjecucion() throws Exception {
        long planificada = campana("Planificada");
        lista(planificada, ejemplarA);

        crear(
                        """
                        {"ejemplar_id": %d, "campana_id": %d, "tipo_intervencion_id": %d,
                         "fecha_ejecucion": "2025-12-21"}
                        """
                                .formatted(ejemplarA, planificada, tipo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("campana-no-en-ejecucion"));
    }

    @Test
    void validaTipoUnidadYRangos() throws Exception {
        crear(
                        """
                        {"ejemplar_id": %d, "campana_id": %d, "tipo_intervencion_id": 999999,
                         "fecha_ejecucion": "2025-12-21"}
                        """
                                .formatted(ejemplarA, campana))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("tipo-invalido"));
        crear(
                        """
                        {"ejemplar_id": %d, "campana_id": %d, "tipo_intervencion_id": %d,
                         "fecha_ejecucion": "2025-12-21", "cantidad_solicitada": "4"}
                        """
                                .formatted(ejemplarA, campana, tipo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("unidad-obligatoria"));
        crear(
                        """
                        {"ejemplar_id": %d, "campana_id": %d, "tipo_intervencion_id": %d,
                         "fecha_ejecucion": "2025-12-21", "reduccion_copa_pct": "101"}
                        """
                                .formatted(ejemplarA, campana, tipo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laExtraccionNoEsUnTipoDePoda() throws Exception {
        long extraccion =
                jdbc.queryForObject(
                        "SELECT id FROM mod02_tipo_intervencion WHERE nombre = 'Extracción'",
                        Map.of(),
                        Long.class);

        crear(
                        """
                        {"ejemplar_id": %d, "campana_id": %d, "tipo_intervencion_id": %d,
                         "fecha_ejecucion": "2025-12-21"}
                        """
                                .formatted(ejemplarA, campana, extraccion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("tipo-invalido"));
    }

    @Test
    void corregirYAnularDejanAuditoriaYSacanDelAvance() throws Exception {
        long id = registrar(ejemplarA);

        http.perform(
                        patch(RUTA + id + "/")
                                .cookie(ingeniero)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ramas_laterales_cortadas\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ramas_laterales_cortadas").value(7))
                .andExpect(jsonPath("$.fecha_ejecucion").value("2025-12-21"));
        http.perform(
                        patch(RUTA + id + "/")
                                .cookie(ingeniero)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("cambio-vacio"));
        http.perform(delete(RUTA + id + "/").cookie(ingeniero).with(csrf()))
                .andExpect(status().isNoContent());

        http.perform(get(RUTA + id + "/").cookie(ingeniero)).andExpect(status().isNotFound());
        avance(0, 0, "0.0");
        assertThat(
                        jdbc.queryForObject(
                                """
                                SELECT count(*) FROM core_registro_auditoria
                                WHERE entidad = 'mod02_intervencion_arbolado' AND registro_id = :id
                                """,
                                Map.of("id", id),
                                Integer.class))
                .isEqualTo(3);
    }

    @Test
    void unaCampanaFinalizadaNoSeCorrige() throws Exception {
        long id = registrar(ejemplarA);
        jdbc.update(
                "UPDATE mod02_campana_poda SET estado = 'Finalizada' WHERE id = :id",
                Map.of("id", campana));

        http.perform(
                        patch(RUTA + id + "/")
                                .cookie(ingeniero)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"ramas_laterales_cortadas\": 7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("campana-no-en-ejecucion"));
    }

    @Test
    void elHistorialDelEjemplarSeConsultaPorSuId() throws Exception {
        registrar(ejemplarA);
        registrar(ejemplarB);

        http.perform(
                        get(RUTA)
                                .queryParam("ejemplar_id", String.valueOf(ejemplarA))
                                .cookie(ingeniero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].ejemplar_codigo").value("PODA-A"));
        http.perform(get(RUTA).queryParam("campana_id", String.valueOf(campana)).cookie(ingeniero))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void losCatalogosTraenSoloTiposDePoda() throws Exception {
        http.perform(get(RUTA + "catalogos/").cookie(ingeniero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos.length()").value(3))
                .andExpect(jsonPath("$.unidades[?(@.nombre == 'Rama')]").exists());
    }

    @Test
    void lasIncidenciasDeOrigenSeListanYDebenSerDelEjemplar() throws Exception {
        http.perform(
                        get(RUTA + "incidencias/")
                                .queryParam("ejemplar_id", String.valueOf(ejemplarA))
                                .cookie(ingeniero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        crear(
                        """
                        {"ejemplar_id": %d, "campana_id": %d, "tipo_intervencion_id": %d,
                         "fecha_ejecucion": "2025-12-21", "incidencia_id": 999999}
                        """
                                .formatted(ejemplarA, campana, tipo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("incidencia-inexistente"));
    }

    @Test
    void elRendimientoSoloCuentaPodasVerificadasYSinRepetir() throws Exception {
        String ruta = CAMPANAS + campana + "/rendimiento/";
        http.perform(get(ruta).cookie(ingeniero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificados").value(0))
                .andExpect(jsonPath("$.dias_con_ejecucion").value(0))
                .andExpect(jsonPath("$.promedio").doesNotExist())
                .andExpect(jsonPath("$.tramos.length()").value(0));

        long sinFoto = registrar(ejemplarB);
        http.perform(get(ruta).cookie(ingeniero)).andExpect(jsonPath("$.verificados").value(0));

        long primera = registrar(ejemplarA);
        foto(primera);
        long repetida = registrar(ejemplarA);
        foto(repetida);
        http.perform(get(ruta).cookie(ingeniero))
                .andExpect(jsonPath("$.verificados").value(1))
                .andExpect(jsonPath("$.dias_con_ejecucion").value(1))
                .andExpect(jsonPath("$.promedio").value("1.00"));

        foto(sinFoto);
        http.perform(get(ruta).cookie(ingeniero))
                .andExpect(jsonPath("$.verificados").value(2))
                .andExpect(jsonPath("$.promedio").value("2.00"))
                .andExpect(jsonPath("$.destacado").value(false))
                .andExpect(jsonPath("$.umbral_alerta").value(18))
                .andExpect(jsonPath("$.tramos[0].atendidos").value(2));

        http.perform(
                        patch(RUTA + sinFoto + "/")
                                .cookie(ingeniero)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fecha_ejecucion\": \"2025-12-22\"}"))
                .andExpect(status().isOk());
        http.perform(get(ruta).cookie(ingeniero))
                .andExpect(jsonPath("$.dias_con_ejecucion").value(2))
                .andExpect(jsonPath("$.promedio").value("1.00"));

        http.perform(delete(RUTA + sinFoto + "/").cookie(ingeniero).with(csrf()))
                .andExpect(status().isNoContent());
        http.perform(get(ruta).cookie(ingeniero))
                .andExpect(jsonPath("$.verificados").value(1))
                .andExpect(jsonPath("$.dias_con_ejecucion").value(1));
    }

    @Test
    void elRendimientoLoConsultanLosCuatroRolesYExigeCampana() throws Exception {
        for (String rol : ROLES) {
            http.perform(get(CAMPANAS + campana + "/rendimiento/").cookie(sesion(rol)))
                    .andExpect(status().isOk());
        }
        http.perform(get(CAMPANAS + "999999/rendimiento/").cookie(ingeniero))
                .andExpect(status().isNotFound());
    }

    @Test
    void losCuatroRolesConsultan() throws Exception {
        long id = registrar(ejemplarA);

        for (String rol : ROLES) {
            Cookie cookie = sesion(rol);
            http.perform(get(RUTA).cookie(cookie)).andExpect(status().isOk());
            http.perform(get(RUTA + "catalogos/").cookie(cookie)).andExpect(status().isOk());
            http.perform(get(RUTA + id + "/").cookie(cookie)).andExpect(status().isOk());
        }
    }

    @Test
    void soloElIngenieroYElPracticanteRegistran() throws Exception {
        long id = registrar(ejemplarA);

        for (String rol : List.of("jefe-seccion", "capataz")) {
            Cookie cookie = sesion(rol);
            http.perform(
                            post(RUTA)
                                    .cookie(cookie)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(cuerpo(ejemplarB, "2025-12-21")))
                    .andExpect(status().isForbidden());
            http.perform(
                            patch(RUTA + id + "/")
                                    .cookie(cookie)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"ramas_laterales_cortadas\": 1}"))
                    .andExpect(status().isForbidden());
            http.perform(delete(RUTA + id + "/").cookie(cookie).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        http.perform(
                        post(RUTA)
                                .cookie(sesion("practicante"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo(ejemplarB, "2025-12-21")))
                .andExpect(status().isCreated());
    }

    @Test
    void sinSesionNoHayAcceso() throws Exception {
        http.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }

    /** Adjunta la imagen de prueba a la intervención. */
    private void foto(long intervencion) throws Exception {
        http.perform(
                        multipart("/api/v1/core/adjuntos/")
                                .file(
                                        new MockMultipartFile(
                                                "archivo", "poda.png", "image/png", PNG))
                                .param("entidad", "intervencion")
                                .param("registro_id", String.valueOf(intervencion))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .cookie(ingeniero)
                                .with(csrf()))
                .andExpect(status().isCreated());
    }

    private void avance(int verificables, int incompletos, String porcentaje) throws Exception {
        http.perform(get(CAMPANAS + campana + "/").cookie(ingeniero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avance.verificables").value(verificables))
                .andExpect(jsonPath("$.avance.incompletos").value(incompletos))
                .andExpect(jsonPath("$.avance.porcentaje").value(porcentaje));
    }

    private long registrar(long ejemplar) throws Exception {
        ResultActions respuesta =
                crear(cuerpo(ejemplar, "2025-12-21")).andExpect(status().isCreated());
        Number id = JsonPath.read(respuesta.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return http.perform(
                post(RUTA)
                        .cookie(ingeniero)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private String cuerpo(long ejemplar, String fecha) {
        return """
        {"ejemplar_id": %d, "campana_id": %d, "tipo_intervencion_id": %d, "fecha_ejecucion": "%s"}
        """
                .formatted(ejemplar, campana, tipo, fecha);
    }

    private String cuerpoSinOrigen(long ejemplar) {
        return """
        {"ejemplar_id": %d, "tipo_intervencion_id": %d, "fecha_ejecucion": "2025-12-21"}
        """
                .formatted(ejemplar, tipo);
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

    /** Otras suites insertan ids a mano: alinea el contador sin dejarlo atrasado para ellas. */
    private long ejemplar(String codigo) {
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('mod02_ejemplar', 'id'),"
                        + " coalesce(max(id), 0) + 1, false) FROM mod02_ejemplar",
                Map.of(),
                Long.class);
        return jdbc.queryForObject(
                "INSERT INTO mod02_ejemplar (codigo) VALUES (:codigo) RETURNING id",
                Map.of("codigo", codigo),
                Long.class);
    }

    /** Campaña en el pasado: la fecha de una poda no puede ser futura. */
    private long campana(String estado) {
        return jdbc.queryForObject(
                """
                INSERT INTO mod02_campana_poda (anio, nombre, clase, motivo_extraordinaria,
                    fecha_inicio, fecha_fin, objetivo_diario, estado, usuario_creacion_id,
                    usuario_modificacion_id)
                SELECT 2025, 'Prueba', 'Extraordinaria', 'Prueba', DATE '2025-12-20',
                    DATE '2025-12-29', 12, :estado, min(id), min(id)
                FROM core_usuario
                RETURNING id
                """,
                Map.of("estado", estado),
                Long.class);
    }

    private void lista(long campanaId, long... ejemplares) {
        for (long ejemplar : ejemplares) {
            jdbc.update(
                    "INSERT INTO mod02_campana_ejemplar (campana_id, ejemplar_id)"
                            + " VALUES (:campana, :ejemplar)",
                    Map.of("campana", campanaId, "ejemplar", ejemplar));
        }
    }
}
