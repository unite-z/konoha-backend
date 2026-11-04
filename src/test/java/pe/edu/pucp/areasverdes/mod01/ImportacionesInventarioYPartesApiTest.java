package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Inventario y partes (RF-T-009): altas, actualizaciones, errores y cargas repetidas. */
class ImportacionesInventarioYPartesApiTest extends ApiCore {

    static final List<String> INVENTARIO =
            List.of("Nombre", "Lugar", "Ubicación", "Superficie (m²)", "Tipo de uso");
    static final List<String> PARTES =
            List.of("Cuadrilla", "Fecha", "Hora", "Tipo de actividad", "Área verde");

    private String prefijo;

    @BeforeEach
    void prefijo() {
        prefijo = "I" + UUID.randomUUID().toString().substring(0, 6);
    }

    /** Otras suites cuentan los registros de esta base: no se deja nada. */
    @AfterEach
    void limpiar() {
        Map<String, String> p = Map.of("p", prefijo + "%");
        jdbc.update(
                """
                DELETE FROM mod04_actividad WHERE tipo_actividad_id IN
                    (SELECT id FROM mod04_tipo_actividad WHERE nombre LIKE :p)
                """,
                p);
        jdbc.update(
                """
                DELETE FROM mod04_parte_diario WHERE cuadrilla_id IN
                    (SELECT id FROM mod04_cuadrilla WHERE nombre LIKE :p)
                """,
                p);
        jdbc.update("DELETE FROM mod04_cuadrilla WHERE nombre LIKE :p", p);
        jdbc.update("DELETE FROM mod04_tipo_actividad WHERE nombre LIKE :p", p);
        jdbc.update("DELETE FROM mod04_personal WHERE apellidos LIKE :p", p);
        jdbc.update("DELETE FROM mod05_areaverde WHERE nombre LIKE :p", p);
        jdbc.update("DELETE FROM mod02_area_verde WHERE nombre LIKE :p", p);
    }

    private JsonNode validar(
            String tipo, List<String> cabeceras, List<List<String>> filas, Cookie s)
            throws Exception {
        return cuerpo(
                http.perform(
                                escribir(
                                        multipart("/api/v1/core/importaciones/")
                                                .file(
                                                        new MockMultipartFile(
                                                                "archivo",
                                                                tipo + ".xlsx",
                                                                "application/octet-stream",
                                                                xlsx(cabeceras, filas)))
                                                .param("tipo", tipo),
                                        s,
                                        null))
                        .andExpect(status().isCreated())
                        .andReturn());
    }

    private void confirmar(JsonNode importacion, Cookie s) throws Exception {
        http.perform(
                        escribir(
                                post(
                                        "/api/v1/core/importaciones/"
                                                + importacion.get("id").asLong()
                                                + "/confirmar/"),
                                s,
                                null))
                .andExpect(status().isOk());
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

    private long areas() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mod02_area_verde WHERE nombre LIKE :p",
                Map.of("p", prefijo + "%"),
                Long.class);
    }

    @Test
    void elInventarioDaDeAltaActualizaYNoDuplicaAlRepetir() throws Exception {
        Cookie jefe = sesion("jefe");
        String nombre = prefijo + " Jardín";
        List<List<String>> filas =
                List.of(
                        List.of(nombre, "Campus", "Tinguiña", "120.50", ""),
                        List.of(prefijo + " Sin superficie", "Campus", "Tinguiña", "0", ""),
                        List.of(prefijo + " Otro", "Campus", "Tinguiña", "10", "Inexistente"));

        JsonNode primera = validar("inventario", INVENTARIO, filas, jefe);
        assertEquals(1, primera.get("altas").asInt());
        assertEquals(2, primera.get("errores").asInt());
        confirmar(primera, jefe);
        assertEquals(1, areas());

        JsonNode repetida = validar("inventario", INVENTARIO, filas, jefe);
        assertEquals(1, repetida.get("sin_cambios").asInt());
        confirmar(repetida, jefe);
        assertEquals(1, areas());

        JsonNode cambiada =
                validar(
                        "inventario",
                        INVENTARIO,
                        List.of(List.of(nombre, "Campus", "Tinguiña", "200", "")),
                        jefe);
        assertEquals(1, cambiada.get("actualizaciones").asInt());
        confirmar(cambiada, jefe);
        assertEquals(1, areas());
    }

    @Test
    void losPartesCreanLaActividadYElParteSinDuplicarAlRepetir() throws Exception {
        Cookie jefe = sesion("jefe");
        long usuario = ids.get("jefe");
        long capataz =
                jdbc.queryForObject(
                        "INSERT INTO mod04_personal (nombres, apellidos, tipo_personal)"
                                + " VALUES ('Ana', :a, 'Capataz') RETURNING id",
                        Map.of("a", prefijo),
                        Long.class);
        String cuadrilla = prefijo + " Cuadrilla";
        jdbc.update(
                """
                INSERT INTO mod04_cuadrilla (nombre, capataz_id, usuario_creacion_id,
                    usuario_modificacion_id)
                VALUES (:n, :c, :u, :u)
                """,
                Map.of("n", cuadrilla, "c", capataz, "u", usuario));
        String tipo = prefijo + " Riego";
        jdbc.update("INSERT INTO mod04_tipo_actividad (nombre) VALUES (:n)", Map.of("n", tipo));
        String area = prefijo + " Área";
        jdbc.update(
                "INSERT INTO mod05_areaverde (nombre, activo) VALUES (:n, true)",
                Map.of("n", area));
        List<List<String>> filas =
                List.of(
                        List.of(cuadrilla, "2026-10-01", "08:30", tipo, area),
                        List.of(cuadrilla, "46000", "0.5", tipo, area),
                        List.of(cuadrilla, "2026-10-01", "08:30", tipo, area),
                        List.of("Otra", "2026-10-01", "08:30", tipo, area),
                        List.of(cuadrilla, "2999-01-01", "08:30", tipo, area));

        JsonNode primera = validar("partes", PARTES, filas, jefe);
        assertEquals(2, primera.get("altas").asInt());
        assertEquals(1, primera.get("revisar").asInt());
        assertEquals(2, primera.get("errores").asInt());
        confirmar(primera, jefe);
        String actividades =
                """
                SELECT count(*) FROM mod04_actividad a
                JOIN mod04_tipo_actividad t ON t.id = a.tipo_actividad_id WHERE t.nombre = :t
                """;
        assertEquals(2, jdbc.queryForObject(actividades, Map.of("t", tipo), Long.class));

        JsonNode repetida = validar("partes", PARTES, filas, jefe);
        assertEquals(0, repetida.get("altas").asInt());
        assertEquals(2, repetida.get("sin_cambios").asInt());
        confirmar(repetida, jefe);
        assertEquals(2, jdbc.queryForObject(actividades, Map.of("t", tipo), Long.class));
    }

    @Test
    void importarPartesSoloEstaAlAlcanceDeQuienLosVerifica() throws Exception {
        http.perform(
                        escribir(
                                multipart("/api/v1/core/importaciones/")
                                        .file(
                                                new MockMultipartFile(
                                                        "archivo",
                                                        "partes.xlsx",
                                                        "application/octet-stream",
                                                        xlsx(PARTES, List.of())))
                                        .param("tipo", "partes"),
                                sesion("ingeniero"),
                                null))
                .andExpect(status().isForbidden());
    }
}
