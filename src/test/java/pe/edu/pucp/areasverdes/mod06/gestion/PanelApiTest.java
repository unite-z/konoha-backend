package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteMovimientosMaterialNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampoNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.PanelService;

/**
 * RF-REP-001 · HU-MOD06-001 · CU-MOD06-001 sobre {@code mod06/escenario-gestion.sql} (los
 * resultados esperados están en su cabecera).
 */
class PanelApiTest extends ApiGestion {

    static final String PANEL = "/api/v1/reportes/panel/";
    static final String PERIODO = "desde=2026-10-01&hasta=2026-10-31";

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
    }

    ResultActions como(String usuario, String ruta) throws Exception {
        return http.perform(get(ruta).with(httpBasic(usuario, CLAVE)));
    }

    ResultActions panel(String filtros) throws Exception {
        return como("jefe", PANEL + "?" + PERIODO + filtros);
    }

    ResultActions detalle(String indicador, String filtros) throws Exception {
        return como("jefe", PANEL + indicador + "/?" + PERIODO + filtros);
    }

    // --- Panel
    // ------------------------------------------------------------------------------------

    @Test
    void elJefeVeLosCincoIndicadoresEnElOrdenDelCu() throws Exception {
        var r =
                panel("")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.indicadores.length()").value(5))
                        .andExpect(jsonPath("$.periodo.desde").value("2026-10-01"))
                        .andExpect(jsonPath("$.generado_en", notNullValue()))
                        .andExpect(
                                jsonPath("$.notas[0]").value(PanelService.NOTA_FILTROS_PENDIENTES));
        for (int i = 0; i < PanelService.ORDEN.size(); i++) {
            r.andExpect(
                    jsonPath("$.indicadores[" + i + "].clave").value(PanelService.ORDEN.get(i)));
        }
        for (String filtro :
                List.of(
                        "desde",
                        "hasta",
                        "area_verde_id",
                        "tipo_servicio_id",
                        "estado",
                        "modalidad")) {
            r.andExpect(jsonPath("$.filtros_aplicados", hasKey(filtro)));
        }
        r.andExpect(jsonPath("$.filtros_aplicados.area_verde_id").value(nullValue()));
    }

    @Test
    void atencionesPorEspacio() throws Exception {
        panel("")
                .andExpect(jsonPath("$.indicadores[2].clave").value("atenciones-por-espacio"))
                .andExpect(jsonPath("$.indicadores[2].indicador.codigo").value("RF-REP-006"))
                .andExpect(jsonPath("$.indicadores[2].valor").value("5"))
                .andExpect(jsonPath("$.indicadores[2].sin_datos").value(false))
                .andExpect(
                        jsonPath("$.indicadores[2].desglose[0].etiqueta").value("Plaza de Armas"))
                .andExpect(jsonPath("$.indicadores[2].desglose[0].valor").value("2"))
                .andExpect(
                        jsonPath("$.indicadores[2].desglose[1].etiqueta").value("Campo Deportivo"))
                .andExpect(
                        jsonPath("$.indicadores[2].desglose[2].etiqueta").value("Jardín Central"))
                .andExpect(jsonPath("$.indicadores[2].desglose[3].etiqueta").value("Sin espacio"));
    }

    @Test
    void cumplimientoDeTercerizados() throws Exception {
        panel("")
                .andExpect(jsonPath("$.indicadores[4].valor").value("66.67"))
                .andExpect(jsonPath("$.indicadores[4].desglose.length()").value(4))
                .andExpect(
                        jsonPath("$.indicadores[4].desglose[0].etiqueta")
                                .value("Requerimiento registrado"))
                .andExpect(jsonPath("$.indicadores[4].desglose[1].etiqueta").value("En ejecución"))
                .andExpect(
                        jsonPath("$.indicadores[4].desglose[2].etiqueta")
                                .value("Ejecutado pendiente de reporte"))
                .andExpect(jsonPath("$.indicadores[4].desglose[2].valor").value("2"))
                .andExpect(
                        jsonPath("$.indicadores[4].desglose[3].etiqueta").value("Reporte adjunto"));
    }

    @Test
    void losIndicadoresSinFuenteDicenSuMotivo() throws Exception {
        var r = panel("");
        Map<Integer, String> motivos =
                Map.of(
                        0, FuenteOperacionCampoNoDisponible.MOTIVO,
                        1, FuenteOperacionCampoNoDisponible.MOTIVO,
                        3, FuenteMovimientosMaterialNoDisponible.MOTIVO);
        for (var m : motivos.entrySet()) {
            String i = "$.indicadores[" + m.getKey() + "]";
            r.andExpect(jsonPath(i + ".sin_datos").value(true))
                    .andExpect(jsonPath(i + ".valor").value(nullValue()))
                    .andExpect(jsonPath(i + ".mensaje_sin_datos").value(m.getValue()));
        }
    }

    // --- Filtros
    // ----------------------------------------------------------------------------------

    @Test
    void filtroPorEspacio() throws Exception {
        panel("&area_verde_id=2")
                .andExpect(jsonPath("$.indicadores[2].valor").value("2"))
                .andExpect(jsonPath("$.indicadores[4].valor").value("100.00"));
    }

    @Test
    void filtroPorTipoDeServicio() throws Exception {
        panel("&tipo_servicio_id=1").andExpect(jsonPath("$.indicadores[2].valor").value("2"));
    }

    @Test
    void filtroPorEstado() throws Exception {
        http.perform(
                        get(PANEL)
                                .param("desde", "2026-10-01")
                                .param("hasta", "2026-10-31")
                                .param("estado", "Reporte adjunto")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[2].valor").value("3"));
    }

    @Test
    void conPersonalPropioNoHayAtencionesNiCumplimiento() throws Exception {
        panel("&modalidad=PersonalPropio")
                .andExpect(jsonPath("$.indicadores[2].sin_datos").value(true))
                .andExpect(jsonPath("$.indicadores[4].sin_datos").value(true));
    }

    @Test
    void conTercerizadoLosMismosValoresQueSinFiltro() throws Exception {
        panel("&modalidad=Tercerizado")
                .andExpect(jsonPath("$.indicadores[2].valor").value("5"))
                .andExpect(jsonPath("$.indicadores[4].valor").value("66.67"));
    }

    // --- Validación
    // -------------------------------------------------------------------------------

    @Test
    void unEstadoInventadoSeRechaza() throws Exception {
        panel("&estado=Inventado")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath(
                                "$.errores.estado[0]", containsString("Usa uno de estos valores")));
    }

    @Test
    void unaModalidadInventadaSeRechaza() throws Exception {
        panel("&modalidad=x").andExpect(status().isBadRequest());
    }

    @Test
    void sinDesdePideLaFechaDeInicio() throws Exception {
        como("jefe", PANEL + "?hasta=2026-10-31")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.desde[0]").value(Periodo.MENSAJE_FALTA_DESDE));
    }

    @Test
    void unPeriodoInvertidoEsInvalido() throws Exception {
        como("jefe", PANEL + "?desde=2026-10-31&hasta=2026-10-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("periodo-invalido"))
                .andExpect(jsonPath("$.errores.hasta[0]").value(Periodo.MENSAJE_ORDEN_FECHAS));
    }

    @Test
    void unaFechaMalEscritaPideElFormato() throws Exception {
        como("jefe", PANEL + "?desde=31/10/2026&hasta=2026-10-31")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.desde[0]").value(Periodo.MENSAJE_FORMATO_FECHA));
    }

    // --- Detalle
    // ----------------------------------------------------------------------------------

    @Test
    void detalleDeAtencionesPorEspacio() throws Exception {
        detalle("atenciones-por-espacio", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5))
                .andExpect(jsonPath("$.results.length()").value(5))
                .andExpect(jsonPath("$.next").value(nullValue()))
                .andExpect(jsonPath("$.results[0].codigo").value("AT-006"));
    }

    @Test
    void elDetalleMantieneLosFiltros() throws Exception {
        detalle("atenciones-por-espacio", "&area_verde_id=2")
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.filtros_aplicados.area_verde_id").value(2));
    }

    @Test
    void detalleDelCumplimiento() throws Exception {
        detalle("cumplimiento-tercerizados", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(6))
                .andExpect(jsonPath("$.results[0].proveedor", notNullValue()));
    }

    @Test
    void unIndicadorQueNoExiste() throws Exception {
        detalle("no-existe", "")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("indicador-no-encontrado"));
    }

    @Test
    void unaPaginaQueNoExiste() throws Exception {
        detalle("atenciones-por-espacio", "&page=9")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("pagina-inexistente"));
        detalle("atenciones-por-espacio", "&page=last").andExpect(status().isOk());
    }

    @Test
    void paginaDe25ConEnlaces() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        INSERT INTO mod05_atenciontercerizada (fecha_creacion, fecha_modificacion,
                            codigo, clase, descripcion, origen, numero_orden_compra,
                            fecha_hora_inicio, estado, es_riesgo, comentarios_acuerdo_unidad,
                            solicitado_por_id, usuario_creacion_id, usuario_modificacion_id,
                            contrato_id, tipo_servicio_id)
                        SELECT '2026-10-01 08:00-05', '2026-10-01 08:00-05',
                            'AT-X' || lpad(n::text, 2, '0'), 'Programada', 'Prueba', 'Demanda', '',
                            timestamptz '2026-10-02 09:00-05' + n * interval '1 hour',
                            'Reporte adjunto', false, '', 1, 1, 1, 1, 1
                        FROM generate_series(1, 30) n;
                        INSERT INTO mod05_alcanceatencion (atencion_id, area_verde_id)
                        SELECT id, 1 FROM mod05_atenciontercerizada WHERE codigo LIKE 'AT-X%';
                        """);
        detalle("atenciones-por-espacio", "")
                .andExpect(jsonPath("$.count").value(35))
                .andExpect(jsonPath("$.results.length()").value(25))
                .andExpect(jsonPath("$.next", endsWith("page=2")))
                .andExpect(jsonPath("$.next", containsString("desde=2026-10-01")))
                .andExpect(jsonPath("$.next", containsString("hasta=2026-10-31")))
                .andExpect(jsonPath("$.previous").value(nullValue()));
        detalle("atenciones-por-espacio", "&page=2")
                .andExpect(jsonPath("$.results.length()").value(10))
                .andExpect(jsonPath("$.next").value(nullValue()))
                .andExpect(jsonPath("$.previous", notNullValue()));
    }

    // --- Permisos y solo lectura
    // ------------------------------------------------------------------

    @Test
    void ingenieroYPracticantePuedenConsultar() throws Exception {
        como("ingeniero", PANEL + "?" + PERIODO).andExpect(status().isOk());
        como("practicante", PANEL + "?" + PERIODO).andExpect(status().isOk());
    }

    @Test
    void sinRolNoTienePermiso() throws Exception {
        como("sinrol", PANEL + "?" + PERIODO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("sin-permiso"));
    }

    @Test
    void sinCredencialesNoSeAutentica() throws Exception {
        http.perform(get(PANEL + "?" + PERIODO)).andExpect(status().isUnauthorized());
    }

    /** HU-MOD06-001, criterio 5: la consulta no modifica ningún registro operativo. */
    @Test
    void laConsultaNoModificaNada() throws Exception {
        List<String> tablas =
                List.of(
                        "mod05_atenciontercerizada",
                        "mod05_alcanceatencion",
                        "mod05_historialestado");
        List<Long> antes = tablas.stream().map(this::contar).toList();
        panel("").andExpect(status().isOk());
        for (String clave : PanelService.ORDEN) {
            detalle(clave, "").andExpect(status().isOk());
        }
        assertEquals(antes, tablas.stream().map(this::contar).toList());
    }

    /** {@code tabla} es siempre una constante de esta prueba, nunca una entrada externa. */
    private long contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Map.of(), Long.class);
    }
}
