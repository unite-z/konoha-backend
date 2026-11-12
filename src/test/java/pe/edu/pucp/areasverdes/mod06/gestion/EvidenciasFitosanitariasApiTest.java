package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod06.apoyo.ClienteIAFalso;

/**
 * RF-IA-006 contra PostgreSQL con {@link ClienteIAFalso}. Ejemplar 1 de {@code
 * escenario-gestion.sql}; las fotos van a un directorio temporal.
 */
@Import(PriorizacionInconsistenciasApiTest.IaFalsa.class)
class EvidenciasFitosanitariasApiTest extends ApiGestion {

    /** Una cabecera PNG basta: el backend valida la firma, no decodifica la imagen. */
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};

    @DynamicPropertySource
    static void almacenamientoTemporal(DynamicPropertyRegistry p) throws Exception {
        Path carpeta = Files.createTempDirectory("mod06-evidencias");
        p.add("mod05.archivos", carpeta::toString);
    }

    @Autowired PriorizacionInconsistenciasApiTest.FabricaFalsa fabrica;

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
        jdbc.getJdbcTemplate().execute("TRUNCATE mod06_sugerencia_ia RESTART IDENTITY");
    }

    ResultActions analizar(String calidad, byte[] foto) throws Exception {
        fabrica.cliente =
                new ClienteIAFalso(
                        Map.of(
                                "fotografias",
                                List.of(Map.of("indice", 0, "calidad", calidad)),
                                "hipotesis",
                                List.of(
                                        Map.of(
                                                "categoria",
                                                "Plaga",
                                                "nombre",
                                                "Pulgón",
                                                "confianza",
                                                0.7,
                                                "nivel_confianza",
                                                "Media",
                                                "evidencias",
                                                List.of("Colonias en el envés"),
                                                "tratamiento",
                                                "Aplicar insecticida")),
                                "explicacion",
                                "Prueba",
                                "limitaciones",
                                List.of()));
        return http.perform(
                multipart("/api/v1/ia/evidencias-fitosanitarias/")
                        .file(new MockMultipartFile("fotografias", "hoja.png", "image/png", foto))
                        .part(
                                new MockPart(
                                        "datos",
                                        "{\"ejemplar_id\": 1, \"sintomas\": \"Hojas pegajosas\"}"
                                                .getBytes(StandardCharsets.UTF_8)))
                        .cookie(sesion("ingeniero"))
                        .with(csrf()));
    }

    static long id(ResultActions r) throws Exception {
        return Long.parseLong(
                r.andReturn()
                        .getResponse()
                        .getContentAsString()
                        .replaceAll("(?s)^\\{\"id\":(\\d+).*", "$1"));
    }

    ResultActions validar(long id, String cuerpo) throws Exception {
        return http.perform(
                post("/api/v1/ia/evidencias-fitosanitarias/" + id + "/validar/")
                        .cookie(sesion("jefe"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    @Test
    void lasHipotesisNuncaGuardanTratamientosYLaFotoSeSirveConSesion() throws Exception {
        long id =
                id(
                        analizar("Suficiente", PNG)
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.contenido.calidad_suficiente").value(true))
                                .andExpect(jsonPath("$.contenido.hipotesis[0].clave").value("h1"))
                                .andExpect(
                                        jsonPath("$.contenido.hipotesis[0].nivel_confianza")
                                                .value("Media"))
                                .andExpect(
                                        jsonPath("$.contenido.hipotesis[0].tratamiento")
                                                .doesNotExist())
                                .andExpect(
                                        jsonPath("$.contenido.fotografias[0].archivo_id").value(1))
                                .andExpect(
                                        jsonPath("$.contenido.fotografias[0].clave").doesNotExist())
                                .andExpect(jsonPath("$.contenido.origen").value("ia")));

        byte[] bytes =
                http.perform(
                                get("/api/v1/ia/evidencias-fitosanitarias/"
                                                + id
                                                + "/fotografias/1/")
                                        .cookie(sesion("practicante")))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "image/png"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertArrayEquals(PNG, bytes);
        http.perform(
                        get("/api/v1/ia/evidencias-fitosanitarias/" + id + "/fotografias/9/")
                                .cookie(sesion("jefe")))
                .andExpect(status().isNotFound());

        validar(id, "{\"resultado\": \"Confirmada\", \"hipotesis_clave\": \"h9\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("datos-invalidos"));
        validar(
                        id,
                        "{\"resultado\": \"Confirmada\", \"hipotesis_clave\": \"h1\","
                                + " \"comentario\": \"Visto en campo\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Validada"))
                .andExpect(jsonPath("$.contenido.validada_por").value("jefe"))
                .andExpect(jsonPath("$.contenido.validacion.resultado").value("Confirmada"));
        validar(id, "{\"resultado\": \"NoConcluyente\"}").andExpect(status().isConflict());
    }

    @Test
    void conCalidadInsuficienteNoHayHipotesisPeroEs201() throws Exception {
        analizar("Insuficiente", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contenido.calidad_suficiente").value(false))
                .andExpect(jsonPath("$.contenido.hipotesis.length()").value(0))
                .andExpect(jsonPath("$.contenido.fotografias[0].motivo_calidad").isNotEmpty());
    }

    @Test
    void unArchivoQueNoEsImagenEs415() throws Exception {
        analizar("Suficiente", "no soy una foto".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("tipo-no-admitido"));
    }

    @Test
    void laEvaluacionManualQuedaValidadaSinIa() throws Exception {
        http.perform(
                        post("/api/v1/ia/evidencias-fitosanitarias/manual/")
                                .cookie(sesion("jefe"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"ejemplar_id\": 1, \"resultado\": \"OtraCategoria\","
                                                + " \"categoria\": \"EstresHidrico\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("Validada"))
                .andExpect(jsonPath("$.contenido.origen").value("manual"))
                .andExpect(jsonPath("$.contenido.validacion.categoria").value("EstresHidrico"));
    }
}
