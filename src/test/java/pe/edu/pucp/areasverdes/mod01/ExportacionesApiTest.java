package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** Exportación a Excel (RF-T-011): archivo legible, solo lectura y permisos de cada entidad. */
class ExportacionesApiTest extends ApiCore {

    private String codigo;

    @BeforeEach
    void ejemplar() {
        jdbc.update("DELETE FROM mod02_ejemplar WHERE codigo LIKE 'XLS-%'", Map.of());
        codigo = "XLS-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
                "INSERT INTO mod02_ejemplar (codigo, ubicacion) VALUES (:c, '=1+1')",
                Map.of("c", codigo));
    }

    private MockHttpServletResponse exportar(String tipo, String usuario) throws Exception {
        return http.perform(get("/api/v1/core/exportaciones/" + tipo + "/").cookie(sesion(usuario)))
                .andReturn()
                .getResponse();
    }

    private List<Row> filas(byte[] archivo) throws Exception {
        try (var libro = new ReadableWorkbook(new ByteArrayInputStream(archivo))) {
            return libro.getFirstSheet().read();
        }
    }

    @Test
    void losEjemplaresSalenEnUnXlsxLegibleConSusCabeceras() throws Exception {
        var respuesta = exportar("ejemplares", "ingeniero");

        assertEquals(200, respuesta.getStatus());
        assertTrue(respuesta.getContentType().contains("spreadsheetml.sheet"));
        assertTrue(respuesta.getHeader("Content-Disposition").contains("ejemplares.xlsx"));
        List<Row> filas = filas(respuesta.getContentAsByteArray());
        assertEquals("Código", filas.get(0).getCellText(0));
        Row fila =
                filas.stream()
                        .filter(f -> codigo.equals(f.getCellText(0)))
                        .findFirst()
                        .orElseThrow();
        assertEquals("=1+1", fila.getCellText(6), "Un texto con «=» no se ejecuta como fórmula");
    }

    @Test
    void exportarNoModificaLosRegistros() throws Exception {
        String consulta = "SELECT count(*) FROM core_registro_auditoria";
        Long antes = jdbc.queryForObject(consulta, Map.of(), Long.class);

        assertEquals(200, exportar("ejemplares", "jefe").getStatus());

        assertEquals(antes, jdbc.queryForObject(consulta, Map.of(), Long.class));
    }

    @Test
    void cadaListadoExigeElPermisoDeSuEntidad() throws Exception {
        assertEquals(200, exportar("actividades", "capataz").getStatus());
        assertEquals(403, exportar("atenciones", "capataz").getStatus());
        assertEquals(200, exportar("ejemplares", "capataz").getStatus());
        assertEquals(200, exportar("actividades", "practicante").getStatus());
        assertEquals(200, exportar("atenciones", "jefe").getStatus());
    }

    @Test
    void elCapatazSoloExportaActividadesDeSuCuadrilla() throws Exception {
        long propia = actividadDeCuadrilla("Propia", ids.get("capataz"));
        long otroUsuario = cuenta("otro-capataz", "capataz");
        actividadDeCuadrilla("Ajena", otroUsuario);

        List<Row> resultado = filas(exportar("actividades", "capataz").getContentAsByteArray());

        assertEquals(2, resultado.size());
        assertEquals(String.valueOf(propia), resultado.get(1).getCellText(0));
        assertEquals("Cuadrilla Propia", resultado.get(1).getCellText(4));
    }

    @Test
    void lasColumnasNoIncluyenImportesNiDatosPersonales() throws Exception {
        for (String tipo : List.of("actividades", "atenciones", "ejemplares", "incidencias")) {
            Row cabecera = filas(exportar(tipo, "jefe").getContentAsByteArray()).get(0);
            for (int c = 0; c < cabecera.getCellCount(); c++) {
                String texto = cabecera.getCellText(c).toLowerCase();
                assertFalse(texto.contains("monto") || texto.contains("documento"), texto);
            }
        }
    }

    @Test
    void lasIncidenciasSeExportanConLasDeMod03() throws Exception {
        assertEquals(200, exportar("incidencias", "jefe").getStatus());
        assertEquals(200, exportar("incidencias", "capataz").getStatus());
    }

    @Test
    void unTipoDesconocidoSeRechaza() throws Exception {
        http.perform(get("/api/v1/core/exportaciones/usuarios/").cookie(sesion("jefe")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("tipo-desconocido"));
    }

    private long actividadDeCuadrilla(String sufijo, long usuarioCapataz) {
        long capataz =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod04_personal
                            (nombres, apellidos, tipo_personal, usuario_id)
                        VALUES (:nombre, 'Prueba', 'Capataz', :usuario) RETURNING id
                        """,
                        Map.of("nombre", "Capataz " + sufijo, "usuario", usuarioCapataz),
                        Long.class);
        long cuadrilla =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod04_cuadrilla
                            (nombre, capataz_id, usuario_creacion_id, usuario_modificacion_id)
                        VALUES (:nombre, :capataz, :jefe, :jefe) RETURNING id
                        """,
                        Map.of(
                                "nombre",
                                "Cuadrilla " + sufijo,
                                "capataz",
                                capataz,
                                "jefe",
                                ids.get("jefe")),
                        Long.class);
        long tipo =
                jdbc.queryForObject(
                        "INSERT INTO mod04_tipo_actividad (nombre) VALUES (:nombre) RETURNING id",
                        Map.of("nombre", "Actividad " + sufijo),
                        Long.class);
        long area =
                jdbc.queryForObject(
                        "INSERT INTO mod05_areaverde (nombre, activo) VALUES (:nombre, true)"
                                + " RETURNING id",
                        Map.of("nombre", "Área " + sufijo),
                        Long.class);
        long parte =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod04_parte_diario
                            (cuadrilla_id, capataz_id, fecha, usuario_creacion_id)
                        VALUES (:cuadrilla, :capataz, current_date, :usuario) RETURNING id
                        """,
                        Map.of(
                                "cuadrilla", cuadrilla,
                                "capataz", capataz,
                                "usuario", usuarioCapataz),
                        Long.class);
        return jdbc.queryForObject(
                """
                INSERT INTO mod04_actividad
                    (parte_id, tipo_actividad_id, area_verde_id, fecha_hora_actividad,
                     usuario_creacion_id)
                VALUES (:parte, :tipo, :area, now(), :usuario) RETURNING id
                """,
                Map.of(
                        "parte", parte,
                        "tipo", tipo,
                        "area", area,
                        "usuario", usuarioCapataz),
                Long.class);
    }
}
