package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.ExportacionReportesService;

/**
 * RF-REP-009 sobre {@code escenario-gestion.sql} y {@code escenario-servicio-programado.sql}: en
 * octubre de 2026 el servicio 2 (Control fitosanitario) tiene 2 atenciones. Los archivos van a un
 * directorio temporal, nunca a {@code private-media/}.
 */
class ExportacionesReportesApiTest extends ApiGestion {

    static final String RUTA = "/api/v1/reportes/exportaciones/";

    @DynamicPropertySource
    static void almacenamientoTemporal(DynamicPropertyRegistry p) throws Exception {
        Path carpeta = Files.createTempDirectory("mod06-exportaciones");
        p.add("mod05.archivos", carpeta::toString);
    }

    @BeforeEach
    void escenario() throws Exception {
        jdbc.getJdbcTemplate().execute("TRUNCATE mod06_reporte_generado RESTART IDENTITY");
        cargarEscenario();
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod06/escenario-servicio-programado.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
    }

    ResultActions exportar(String usuario, String cuerpo) throws Exception {
        return http.perform(
                post(RUTA)
                        .cookie(sesion(usuario))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    static String servicio(String formato, int tipoServicio) {
        return """
        {"tipo": "ServicioTercerizado", "formato": "%s",
         "parametros": {"desde": "2026-10-01", "hasta": "2026-10-31", "tipo_servicio_id": %d}}
        """
                .formatted(formato, tipoServicio);
    }

    @Test
    void losTiposDicenSusFormatosYLaMatrizNoDisponible() throws Exception {
        http.perform(get(RUTA + "tipos/").cookie(sesion("ingeniero")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.tipo == 'MatrizIncidencias')].disponible").value(false))
                .andExpect(jsonPath("$[?(@.tipo == 'GestionOSG')].limite_filas.PDF").value(5000));
    }

    @Test
    void elJefeExportaAExcelYLoDescarga() throws Exception {
        exportar("jefe", servicio("Excel", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ServicioTercerizado"))
                .andExpect(jsonPath("$.etiqueta_tipo").value("Reporte por servicio tercerizado"))
                .andExpect(jsonPath("$.filas").value(2))
                .andExpect(jsonPath("$.usuario").isNotEmpty())
                .andExpect(jsonPath("$.parametros.tipo_servicio_id").value(2))
                .andExpect(
                        jsonPath("$.resumen_parametros")
                                .value("01/10/2026 – 31/10/2026 · Control fitosanitario"))
                .andExpect(
                        jsonPath("$.archivo.nombre")
                                .value(org.hamcrest.Matchers.endsWith(".xlsx")));

        http.perform(get(RUTA).cookie(sesion("practicante")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].formato").value("Excel"));

        long id =
                jdbc.queryForObject(
                        "SELECT max(id) FROM mod06_reporte_generado",
                        java.util.Map.of(),
                        Long.class);
        byte[] bytes =
                http.perform(get(RUTA + id + "/archivo/").cookie(sesion("jefe")))
                        .andExpect(status().isOk())
                        .andExpect(
                                header().string(
                                                "Content-Disposition",
                                                org.hamcrest.Matchers.startsWith("attachment")))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        // Un .xlsx es un ZIP: empieza por «PK».
        assertEquals("PK", new String(bytes, 0, 2, StandardCharsets.US_ASCII));
    }

    @Test
    void tambienExportaAPdf() throws Exception {
        exportar("jefe", servicio("PDF", 2)).andExpect(status().isCreated());
        long id =
                jdbc.queryForObject(
                        "SELECT max(id) FROM mod06_reporte_generado",
                        java.util.Map.of(),
                        Long.class);
        byte[] bytes =
                http.perform(get(RUTA + id + "/archivo/").cookie(sesion("jefe")))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "application/pdf"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertTrue(new String(bytes, 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }

    @Test
    void soloElJefeExporta() throws Exception {
        exportar("ingeniero", servicio("Excel", 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("sin-permiso"));
    }

    @Test
    void sinDatosEs422() throws Exception {
        exportar("jefe", servicio("Excel", 6))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("sin-datos"));
    }

    @Test
    void cadaTipoValidaSusParametros() throws Exception {
        exportar(
                        "jefe",
                        "{\"tipo\": \"MaterialVegetal\", \"formato\": \"Excel\", \"parametros\":"
                                + " {}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("parametro-invalido"))
                .andExpect(jsonPath("$.errores.mes").exists());
        exportar(
                        "jefe",
                        "{\"tipo\": \"ServicioTercerizado\", \"formato\": \"PDF\", \"parametros\":"
                                + " {\"tipo_servicio_id\": 2}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("periodo-invalido"))
                .andExpect(jsonPath("$.errores.desde").exists());
        exportar(
                        "jefe",
                        "{\"tipo\": \"DistribucionPersonal\", \"formato\": \"PDF\", \"parametros\":"
                                + " {\"desde\": \"2026-10-01\", \"hasta\": \"2026-10-31\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("formato-no-permitido"));
        exportar(
                        "jefe",
                        "{\"tipo\": \"MatrizIncidencias\", \"formato\": \"PDF\", \"parametros\":"
                                + " {}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("tipo-no-disponible"));
    }

    @Test
    void elVolumenExcedidoUsaSeparadorDeMiles() {
        assertEquals("7 250", ExportacionReportesService.miles(7250));
        assertEquals("5 000", ExportacionReportesService.miles(5000));
    }
}
