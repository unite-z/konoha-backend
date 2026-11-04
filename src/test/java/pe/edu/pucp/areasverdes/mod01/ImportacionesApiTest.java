package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Importación desde Excel (RF-T-009): validar no toca datos, confirmar aplica y es repetible. */
class ImportacionesApiTest extends ApiCore {

    static final List<String> CABECERAS =
            List.of(
                    "Código de catastro",
                    "Referencia de catastro",
                    "Especie",
                    "Tipo de vegetación",
                    "Área verde",
                    "Ubicación",
                    "Cantidad");

    private String prefijo;
    private String especie;

    @BeforeEach
    void catalogo() {
        prefijo = "T" + UUID.randomUUID().toString().substring(0, 6);
        especie = "Ceiba " + prefijo;
        jdbc.update(
                """
                INSERT INTO mod02_especie_vegetal (nombre_comun, nombre_cientifico, forma_biologica)
                VALUES (:n, :n, 'Árbol')
                """,
                Map.of("n", especie));
    }

    /** Otras suites cuentan las especies y los ejemplares de esta base: no se deja nada. */
    @AfterEach
    void limpiar() {
        jdbc.update(
                "DELETE FROM mod02_ejemplar WHERE codigo_catastro LIKE :p",
                Map.of("p", prefijo + "%"));
        jdbc.update(
                "DELETE FROM mod02_especie_vegetal WHERE nombre_comun = :n", Map.of("n", especie));
    }

    private static byte[] xlsx(List<String> cabeceras, List<List<String>> filas)
            throws IOException {
        var salida = new ByteArrayOutputStream();
        try (Workbook libro = new Workbook(salida, "pruebas", "1.0")) {
            Worksheet hoja = libro.newWorksheet("Datos");
            for (int c = 0; c < cabeceras.size(); c++) {
                hoja.value(0, c, cabeceras.get(c));
            }
            for (int f = 0; f < filas.size(); f++) {
                for (int c = 0; c < filas.get(f).size(); c++) {
                    hoja.value(f + 1, c, filas.get(f).get(c));
                }
            }
        }
        return salida.toByteArray();
    }

    private List<String> fila(String sufijo, String especie, String cantidad) {
        return List.of(prefijo + sufijo, "", especie, "Árbol", "", "Patio", cantidad);
    }

    private ResultActions subir(byte[] archivo, String tipo, Cookie sesion) throws Exception {
        return http.perform(
                escribir(
                        multipart("/api/v1/core/importaciones/")
                                .file(
                                        new MockMultipartFile(
                                                "archivo",
                                                "catastro.xlsx",
                                                "application/octet-stream",
                                                archivo))
                                .param("tipo", tipo),
                        sesion,
                        null));
    }

    private JsonNode validar(byte[] archivo, Cookie sesion) throws Exception {
        return cuerpo(
                subir(archivo, "catastro", sesion).andExpect(status().isCreated()).andReturn());
    }

    private JsonNode confirmar(long id, Cookie sesion) throws Exception {
        return cuerpo(
                http.perform(
                                escribir(
                                        post("/api/v1/core/importaciones/" + id + "/confirmar/"),
                                        sesion,
                                        null))
                        .andExpect(status().isOk())
                        .andReturn());
    }

    private long ejemplares() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mod02_ejemplar WHERE codigo_catastro LIKE :p",
                Map.of("p", prefijo + "%"),
                Long.class);
    }

    @Test
    void validarInformaSinIncorporarYConfirmarSoloAplicaLasValidas() throws Exception {
        Cookie jefe = sesion("jefe");
        byte[] archivo =
                xlsx(
                        CABECERAS,
                        List.of(
                                fila("-1", especie, "2"),
                                fila("-2", "", "1"),
                                fila("-3", "Desconocida", "1"),
                                fila("-4", especie, "-1"),
                                fila("-1", especie, "3")));

        JsonNode validada = validar(archivo, jefe);

        assertEquals(5, validada.get("total").asInt());
        assertEquals(1, validada.get("altas").asInt());
        assertEquals(3, validada.get("errores").asInt());
        assertEquals(1, validada.get("revisar").asInt());
        assertEquals(0, ejemplares(), "Validar no incorpora datos");
        long id = validada.get("id").asLong();
        http.perform(get("/api/v1/core/importaciones/" + id + "/filas/").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5))
                .andExpect(jsonPath("$.results[1].resultado").value("Error"))
                .andExpect(jsonPath("$.results[1].detalle").value("Falta especie"))
                .andExpect(jsonPath("$.results[4].resultado").value("Revisar"));

        JsonNode confirmada = confirmar(id, jefe);

        assertEquals("Confirmada", confirmada.get("estado").asText());
        assertEquals(1, ejemplares(), "Las filas con error o por revisar no se incorporan");
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT cantidad FROM mod02_ejemplar WHERE codigo_catastro = :c",
                        Map.of("c", prefijo + "-1"),
                        Integer.class));
    }

    @Test
    void repetirLaCargaNoDuplicaYDetectaLasActualizaciones() throws Exception {
        Cookie jefe = sesion("jefe");
        byte[] archivo = xlsx(CABECERAS, List.of(fila("-1", especie, "2")));
        confirmar(validar(archivo, jefe).get("id").asLong(), jefe);
        assertEquals(1, ejemplares());

        JsonNode repetida = validar(archivo, jefe);
        assertEquals(0, repetida.get("altas").asInt());
        assertEquals(1, repetida.get("sin_cambios").asInt());
        confirmar(repetida.get("id").asLong(), jefe);
        assertEquals(1, ejemplares(), "La misma carga no duplica");

        JsonNode cambiada = validar(xlsx(CABECERAS, List.of(fila("-1", especie, "9"))), jefe);
        assertEquals(1, cambiada.get("actualizaciones").asInt());
        confirmar(cambiada.get("id").asLong(), jefe);
        assertEquals(1, ejemplares());
        assertEquals(
                9,
                jdbc.queryForObject(
                        "SELECT cantidad FROM mod02_ejemplar WHERE codigo_catastro = :c",
                        Map.of("c", prefijo + "-1"),
                        Integer.class));
    }

    @Test
    void confirmarDosVecesSeRechazaYElResultadoQuedaRegistrado() throws Exception {
        Cookie jefe = sesion("jefe");
        long id =
                validar(xlsx(CABECERAS, List.of(fila("-1", especie, "1"))), jefe)
                        .get("id")
                        .asLong();
        confirmar(id, jefe);

        http.perform(escribir(post("/api/v1/core/importaciones/" + id + "/confirmar/"), jefe, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("importacion-ya-confirmada"));
        http.perform(get("/api/v1/core/importaciones/" + id + "/").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivo").value("catastro.xlsx"))
                .andExpect(jsonPath("$.usuario").value("jefe"))
                .andExpect(jsonPath("$.fecha_confirmacion").exists());
    }

    @Test
    void siLosDatosCambianAntesDeConfirmarSeVuelveAClasificar() throws Exception {
        Cookie jefe = sesion("jefe");
        byte[] archivo = xlsx(CABECERAS, List.of(fila("-1", especie, "5")));
        JsonNode primera = validar(archivo, jefe);
        JsonNode segunda = validar(archivo, jefe);
        confirmar(primera.get("id").asLong(), jefe);

        JsonNode confirmada = confirmar(segunda.get("id").asLong(), jefe);

        assertEquals(0, confirmada.get("altas").asInt());
        assertEquals(1, confirmada.get("sin_cambios").asInt());
        assertEquals(1, ejemplares());
    }

    @Test
    void rechazaArchivosInvalidosYColumnasFaltantes() throws Exception {
        Cookie jefe = sesion("jefe");
        subir("no es un excel".getBytes(), "catastro", jefe)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("archivo-invalido"));
        subir(xlsx(List.of("Nombre"), List.of()), "catastro", jefe)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("columnas-faltantes"));
        subir(xlsx(CABECERAS, List.of()), "desconocido", jefe)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("tipo-desconocido"));
    }

    @Test
    void laRutaDeJardinesAvisaDeQueNoEstaDisponible() throws Exception {
        subir(xlsx(CABECERAS, List.of()), "ruta", sesion("jefe"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("entidad-no-disponible"));
    }

    @Test
    void quienNoRegistraInventarioNoImportaNiConsulta() throws Exception {
        Cookie jefe = sesion("jefe");
        long id =
                validar(xlsx(CABECERAS, List.of(fila("-1", especie, "1"))), jefe)
                        .get("id")
                        .asLong();
        Cookie capataz = sesion("capataz");

        subir(xlsx(CABECERAS, List.of()), "catastro", capataz).andExpect(status().isForbidden());
        http.perform(get("/api/v1/core/importaciones/" + id + "/").cookie(capataz))
                .andExpect(status().isForbidden());
        http.perform(
                        escribir(
                                post("/api/v1/core/importaciones/" + id + "/confirmar/"),
                                capataz,
                                null))
                .andExpect(status().isForbidden());
        assertEquals(0, ejemplares());
    }
}
