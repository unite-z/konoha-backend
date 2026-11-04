package pe.edu.pucp.areasverdes.mod03;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Registro, bandeja y seguimiento de incidencias (AV-274, RF-INC-001, HU-020 y CU-020). */
class IncidenciasApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/incidencias/";

    @Autowired ObjectMapper json;

    /** PNG de 1 × 1. */
    static final byte[] PNG =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @BeforeEach
    void espacio() {
        var sql = jdbc.getJdbcTemplate();
        long sector =
                sql.queryForObject(
                        "INSERT INTO mod02_sector (nombre, usuario_creacion_id,"
                            + " usuario_modificacion_id) VALUES ('Sociales', 1, 1) RETURNING id",
                        Long.class);
        long area =
                sql.queryForObject(
                        "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                                + " usuario_creacion_id, usuario_modificacion_id) VALUES ('Jardín"
                                + " de la Biblioteca', 'Biblioteca', 'Campus', 120, 1, 1)"
                                + " RETURNING id",
                        Long.class);
        sql.update(
                "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, usuario_id)"
                        + " VALUES (?, ?, 1)",
                area,
                sector);
        sql.update("INSERT INTO mod02_ejemplar (codigo) VALUES ('T-0412') ON CONFLICT DO NOTHING");
    }

    static String cuerpo(String canal, String reportante) {
        return ("{\"canal\": \"%s\", \"tipo\": \"Rama caída\", \"reportado_por\": %s,"
                        + " \"espacio\": \"Jardín de la Biblioteca\", \"ejemplar\": \"T-0412\","
                        + " \"descripcion\": \"Rama grande sobre el paso\"}")
                .formatted(canal, reportante);
    }

    ResultActions registrar(String usuario, String json) throws Exception {
        return http.perform(
                post(RUTA)
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    ResultActions registrarConFoto(String usuario, String json, UUID clave) throws Exception {
        var peticion =
                multipart(RUTA)
                        .file(new MockMultipartFile("foto", "rama.png", "image/png", PNG))
                        .param("datos", json)
                        .with(httpBasic(usuario, CLAVE));
        if (clave != null) {
            peticion.header("Idempotency-Key", clave.toString());
        }
        return http.perform(peticion);
    }

    @Test
    void laOficinaRegistraEnNombreDeUnaPersonaExterna() throws Exception {
        registrar("jefe", cuerpo("Telefono", "{\"nombre\": \"Ana Quispe\", \"contacto\": \"999\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value(matchesPattern("INC-\\d{4}-\\d{3}")))
                .andExpect(jsonPath("$.estado").value("Registrada"))
                .andExpect(jsonPath("$.canal").value("Telefono"))
                .andExpect(jsonPath("$.tipo_sencilla").value(true))
                .andExpect(jsonPath("$.espacio").value("Jardín de la Biblioteca"))
                .andExpect(jsonPath("$.zona").value("Sociales"))
                .andExpect(jsonPath("$.ejemplar.codigo").value("T-0412"))
                .andExpect(jsonPath("$.reportado_por").value("Ana Quispe"))
                .andExpect(jsonPath("$.foto_url").isEmpty())
                .andExpect(jsonPath("$.historial[0].texto").value("Registrada · jefe"));
    }

    @Test
    void desdeLaAppLaFotoEsObligatoria() throws Exception {
        registrar("ingeniero", cuerpo("App", "{\"usuario_id\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("foto-obligatoria"));
        registrarConFoto("ingeniero", cuerpo("App", "{\"usuario_id\": 2}"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportado_por").value("ingeniero"))
                .andExpect(
                        jsonPath("$.foto_url")
                                .value(matchesPattern("/api/v1/core/adjuntos/\\d+/contenido/")));
    }

    @Test
    void validaTipoEspacioYReportante() throws Exception {
        registrar("jefe", cuerpo("Telefono", "{\"nombre\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("reportante-requerido"));
        registrar("jefe", cuerpo("Fax", "{\"nombre\": \"Ana\"}"))
                .andExpect(jsonPath("$.codigo").value("canal-invalido"));
        registrar(
                        "jefe",
                        cuerpo("Telefono", "{\"nombre\": \"Ana\"}")
                                .replace("Jardín de la Biblioteca", "Otro jardín"))
                .andExpect(jsonPath("$.codigo").value("espacio-invalido"));
        registrar("sinrol", cuerpo("Telefono", "{\"nombre\": \"Ana\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void elMismoEnvioDiferidoNoDuplica() throws Exception {
        UUID clave = UUID.randomUUID();
        String json = cuerpo("App", "{\"usuario_id\": 2}");
        String primera =
                registrarConFoto("ingeniero", json, clave)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String segunda =
                registrarConFoto("ingeniero", json, clave)
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(primera, segunda);
        assertEquals(
                1,
                jdbc.getJdbcTemplate()
                        .queryForObject("SELECT count(*) FROM mod03_incidencia", Integer.class));
    }

    @Test
    void bandejaDelJefeYSeguimientoDelReportante() throws Exception {
        registrarConFoto("ingeniero", cuerpo("App", "{\"usuario_id\": 2}"), null);
        registrar("jefe", cuerpo("Telefono", "{\"nombre\": \"Ana\"}"));
        http.perform(get(RUTA).with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        http.perform(get(RUTA).with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isForbidden());
        String mias =
                http.perform(get(RUTA + "mias/").with(httpBasic("ingeniero", CLAVE)))
                        .andExpect(jsonPath("$.length()").value(1))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id = json.readTree(mias).get(0).get("id").asLong();
        http.perform(get(RUTA + "mias/" + id + "/").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pasos[0].estado").value("Registrada"))
                .andExpect(jsonPath("$.minutos_cierre").isEmpty());
        http.perform(get(RUTA + "mias/" + id + "/").with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void elCatalogoTraeTiposEspaciosYUsuarios() throws Exception {
        http.perform(get(RUTA + "catalogos-registro/").with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos").value(hasItem("Rama caída")))
                .andExpect(jsonPath("$.espacios[0].nombre").value("Jardín de la Biblioteca"))
                .andExpect(jsonPath("$.usuarios").isArray());
    }

    long registrada() throws Exception {
        String ficha =
                registrar("jefe", cuerpo("Telefono", "{\"nombre\": \"Ana\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(ficha).get("id").asLong();
    }

    ResultActions duplicados(String parametros) throws Exception {
        return http.perform(
                get(RUTA + "posibles-duplicados/?tipo=Rama caída" + parametros)
                        .with(httpBasic("ingeniero", CLAVE)));
    }

    @Test
    void avisaDeLaAbiertaDelMismoEjemplarOEspacio() throws Exception {
        long id = registrada();
        duplicados("&espacio=Jardín de la Biblioteca&ejemplar=T-0412")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].ejemplar").value("T-0412"));
        duplicados("&espacio=Jardín de la Biblioteca").andExpect(jsonPath("$.length()").value(1));
        duplicados("&espacio=Otro jardín").andExpect(jsonPath("$.length()").value(0));
        jdbc.getJdbcTemplate().update("UPDATE mod03_incidencia SET estado = 'Cerrada'");
        duplicados("&espacio=Jardín de la Biblioteca").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anadirALaExistenteSumaElReporteYLaFoto() throws Exception {
        long id = registrada();
        http.perform(
                        multipart(RUTA + id + "/reportes/")
                                .file(new MockMultipartFile("foto", "otra.png", "image/png", PNG))
                                .param("descripcion", "Ahora bloquea la vereda")
                                .with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isNoContent());
        assertEquals(
                "Ahora bloquea la vereda",
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT descripcion FROM mod03_reporte_incidencia", String.class));
        http.perform(get(RUTA + id + "/").with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.foto_url").isNotEmpty());
        http.perform(
                        multipart(RUTA + "999999/reportes/")
                                .param("descripcion", "x")
                                .with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isNotFound());
        jdbc.getJdbcTemplate().update("UPDATE mod03_incidencia SET estado = 'Descartada'");
        http.perform(
                        multipart(RUTA + id + "/reportes/")
                                .param("descripcion", "x")
                                .with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("incidencia-cerrada"));
    }

    ResultActions accion(String usuario, long id, String accion, String cuerpo) throws Exception {
        return http.perform(
                post(RUTA + id + "/" + accion + "/")
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    @Test
    void elJefeClasificaYCambiarLaViaSugeridaPideMotivo() throws Exception {
        long id = registrada();
        accion(
                        "jefe",
                        id,
                        "clasificar",
                        "{\"severidad\": \"Alta\", \"impacto\": \"Medio\","
                                + " \"via\": \"Tercerizada\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("motivo-requerido"));
        accion(
                        "ingeniero",
                        id,
                        "clasificar",
                        "{\"severidad\": \"Alta\", \"impacto\": \"Medio\","
                                + " \"via\": \"PersonalPropio\"}")
                .andExpect(status().isForbidden());
        accion(
                        "jefe",
                        id,
                        "clasificar",
                        "{\"severidad\": \"Alta\", \"impacto\": \"Medio\", \"via\":"
                                + " \"Tercerizada\", \"motivo_cambio_via\": \"Rama a 8 m\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Clasificada"))
                .andExpect(jsonPath("$.severidad").value("Alta"))
                .andExpect(jsonPath("$.impacto").value("Medio"))
                .andExpect(jsonPath("$.via").value("Tercerizada"))
                .andExpect(
                        jsonPath("$.historial[0].texto")
                                .value("Clasificada · jefe (cambio de vía: Rama a 8 m)"));
        accion(
                        "jefe",
                        id,
                        "clasificar",
                        "{\"severidad\": \"Alta\", \"impacto\": \"Medio\","
                                + " \"via\": \"PersonalPropio\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("transicion-invalida"));
    }

    @Test
    void descartarPideMotivoYCierraLaIncidencia() throws Exception {
        long id = registrada();
        accion("jefe", id, "descartar", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("motivo-requerido"));
        accion("jefe", id, "descartar", "{\"motivo\": \"Ya estaba atendida\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Descartada"))
                .andExpect(jsonPath("$.fecha_cierre").isNotEmpty());
    }

    @Test
    void laInspeccionVaAUnIngenieroYLaIncidenciaSigueRegistrada() throws Exception {
        long id = registrada();
        http.perform(get(RUTA + "inspectores/").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre").value(hasItem("ingeniero")));
        accion("jefe", id, "inspeccion", "{\"responsable_id\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("inspector-invalido"));
        accion("jefe", id, "inspeccion", "{\"responsable_id\": 2, \"que_verificar\": \"Altura\"}")
                .andExpect(status().isNoContent());
        http.perform(get(RUTA + id + "/").with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.estado").value("Registrada"));
    }
}
