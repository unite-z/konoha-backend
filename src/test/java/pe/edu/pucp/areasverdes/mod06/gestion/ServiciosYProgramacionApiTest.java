package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioService;

/**
 * RF-REP-008 y RF-REP-010 sobre {@code escenario-gestion.sql} y {@code
 * escenario-servicio-programado.sql}; los totales esperados están en la cabecera del segundo.
 */
class ServiciosYProgramacionApiTest extends ApiGestion {

    static final String PERIODO = "?desde=2026-10-01&hasta=2026-10-31";

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod06/escenario-servicio-programado.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
    }

    ResultActions como(String usuario, String ruta) throws Exception {
        return http.perform(get("/api/v1/reportes/" + ruta).cookie(sesion(usuario)));
    }

    // --- RF-REP-008
    // -------------------------------------------------------------------------------

    @Test
    void elReporteDelServicioTraeAvanceConformidadYPlagas() throws Exception {
        como("jefe", "servicios-tercerizados/" + PERIODO + "&tipo_servicio_id=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sin_datos").value(false))
                .andExpect(jsonPath("$.servicio.nombre").value("Control fitosanitario"))
                .andExpect(jsonPath("$.avance.programadas").value(2))
                .andExpect(jsonPath("$.avance.ejecutadas").value(1))
                .andExpect(jsonPath("$.avance.pendientes").value(1))
                .andExpect(jsonPath("$.avance.porcentaje_avance").value("50.00"))
                .andExpect(jsonPath("$.conformidad.conformes").value(1))
                .andExpect(jsonPath("$.conformidad.observadas").value(0))
                .andExpect(
                        jsonPath("$.especies_plagas.elementos[0].nombre").value("Pulgón de prueba"))
                .andExpect(
                        jsonPath("$.atenciones[*].codigo", containsInAnyOrder("AT-001", "AT-004")))
                .andExpect(
                        jsonPath("$.atenciones[?(@.codigo == 'AT-001')].conformidad")
                                .value("Conforme"))
                .andExpect(
                        jsonPath("$.atenciones[?(@.codigo == 'AT-001')].con_evidencia").value(true))
                .andExpect(jsonPath("$.rendimiento.sin_datos").value(true));
    }

    /**
     * Metrado: AT-001 (ejecutada) y AT-004 (pendiente) prevén 100 m2 cada una; el informe de AT-001
     * declara 80. Plazo: AT-001 viene de la programación 901 (fin previsto 10/10); AT-004 no tiene.
     * Al final, AT-004 se queda sin metrado: una atención sin metrado no es «otra unidad».
     */
    @Test
    void elMetradoEjecutadoSaleDelInformeYElPlazoDeLaProgramacion() throws Exception {
        String[] sql = {
            "INSERT INTO mod05_unidadmedida (id, nombre, simbolo) VALUES (9401, 'Metro cuadrado',"
                    + " 'm2')",
            "UPDATE mod05_alcanceatencion SET metrado = 100, unidad_medida_id = 9401 WHERE id IN"
                    + " (1, 6)",
            "INSERT INTO mod05_formato_reporte_servicio (id, tipo_servicio_id, version) VALUES"
                    + " (9401, 2, 1)",
            "UPDATE mod05_reporteproveedor SET formato_id = 9401, datos_formato = '{\"Metrado\":"
                    + " 80, \"Ruta recorrida\": \"Norte\"}' WHERE id = 1",
        };
        for (String s : sql) jdbc.getJdbcTemplate().update(s);

        // Sin fin real y con el 10/10 ya pasado, AT-001 va fuera de plazo; AT-004 no tiene plazo.
        como("jefe", "servicios-tercerizados/" + PERIODO + "&tipo_servicio_id=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avance.metrado_previsto").value("200"))
                .andExpect(jsonPath("$.avance.metrado_ejecutado").value("80"))
                .andExpect(jsonPath("$.avance.unidad_metrado").value("m2"))
                .andExpect(jsonPath("$.atenciones[?(@.codigo == 'AT-001')].metrado").value("80"))
                .andExpect(jsonPath("$.rendimiento.atenciones_fuera_de_plazo").value(1))
                .andExpect(jsonPath("$.notas", hasItem(ReporteServicioService.NOTA_PLAZO)));

        // Terminada antes del fin previsto: dentro de plazo.
        jdbc.getJdbcTemplate()
                .update(
                        "UPDATE mod05_atenciontercerizada SET fecha_hora_fin = '2026-10-08"
                                + " 12:00-05' WHERE id = 1");
        como("jefe", "servicios-tercerizados/" + PERIODO + "&tipo_servicio_id=2")
                .andExpect(jsonPath("$.rendimiento.atenciones_fuera_de_plazo").value(0))
                .andExpect(jsonPath("$.rendimiento.rendimiento_por_hora").exists());

        jdbc.getJdbcTemplate()
                .update(
                        "UPDATE mod05_alcanceatencion SET metrado = NULL, unidad_medida_id = NULL"
                                + " WHERE id = 6");
        como("jefe", "servicios-tercerizados/" + PERIODO + "&tipo_servicio_id=2")
                .andExpect(jsonPath("$.avance.metrado_previsto").value("100"))
                .andExpect(jsonPath("$.avance.metrado_ejecutado").value("80"))
                .andExpect(jsonPath("$.avance.unidad_metrado").value("m2"))
                .andExpect(
                        jsonPath(
                                "$.notas",
                                org.hamcrest.Matchers.not(
                                        hasItem(ReporteServicioService.NOTA_UNIDADES))));
    }

    @Test
    void unServicioSinAtencionesDiceSinDatosNuncaCeros() throws Exception {
        como("ingeniero", "servicios-tercerizados/" + PERIODO + "&tipo_servicio_id=6")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sin_datos").value(true))
                .andExpect(jsonPath("$.avance.sin_datos").value(true))
                .andExpect(jsonPath("$.avance.porcentaje_avance").doesNotExist());
    }

    @Test
    void elTipoDeServicioEsObligatorioYDebeExistir() throws Exception {
        como("jefe", "servicios-tercerizados/" + PERIODO).andExpect(status().isBadRequest());
        como("jefe", "servicios-tercerizados/" + PERIODO + "&tipo_servicio_id=999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("validacion"))
                .andExpect(jsonPath("$.errores.tipo_servicio_id").exists());
    }

    // --- RF-REP-010
    // -------------------------------------------------------------------------------

    @Test
    void laComparacionClasificaCadaProgramacion() throws Exception {
        como("jefe", "programado-ejecutado/" + PERIODO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.programados").value(4))
                .andExpect(jsonPath("$.resumen.en_curso").value(1))
                .andExpect(jsonPath("$.resumen.ejecutados_con_desviacion").value(1))
                .andExpect(jsonPath("$.resumen.pendientes").value(1))
                .andExpect(jsonPath("$.resumen.cancelados").value(1))
                .andExpect(jsonPath("$.resumen.porcentaje_cumplimiento").value("33.33"))
                .andExpect(jsonPath("$.resumen.ejecuciones_sin_programacion").value(4))
                .andExpect(
                        jsonPath("$.filas[?(@.programacion_id == 902)].desviacion_dias").value(2))
                .andExpect(
                        jsonPath("$.filas[?(@.programacion_id == 901)].ejecuciones[0].codigo")
                                .value("AT-001"))
                .andExpect(jsonPath("$.personal_propio.sin_datos").value(true));
    }

    @Test
    void losFiltrosDeSituacionYServicioSeAplicanEnElServidor() throws Exception {
        como("jefe", "programado-ejecutado/" + PERIODO + "&situacion=Pendiente")
                .andExpect(jsonPath("$.filas.length()").value(1))
                .andExpect(jsonPath("$.filas[0].programacion_id").value(903));
        como("jefe", "programado-ejecutado/" + PERIODO + "&tipo_servicio_id=1")
                .andExpect(jsonPath("$.resumen.programados").value(2))
                .andExpect(jsonPath("$.por_servicio[0].tipo_servicio_id").value(1));
    }

    @Test
    void unaSituacionDesconocidaEs400() throws Exception {
        como("jefe", "programado-ejecutado/" + PERIODO + "&situacion=Otra")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.situacion").exists());
    }
}
