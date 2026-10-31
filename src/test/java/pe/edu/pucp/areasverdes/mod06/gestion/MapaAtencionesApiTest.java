package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampoNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteTiposUsoNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteUbicacionesNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.MapaAtencionesService;

/**
 * RF-REP-007 · HU-MOD06-003 · CU-MOD06-003 con los adaptadores reales (sin ubicaciones ni tipo de
 * uso en {@code main}) sobre {@code mod06/escenario-gestion.sql}.
 *
 * <p>Filas esperadas en octubre de 2026, en el orden de la respuesta (fecha descendente):
 *
 * <pre>
 * | Fila | Atención | Destino                   | Tipo                  |
 * |------|----------|---------------------------|-----------------------|
 * | e    | AT-006   | área 3                    | Mantenimiento (corte) |
 * | f    | AT-007   | sin alcance               | Mantenimiento (corte) |
 * | c    | AT-002   | área 2                    | Poda                  |
 * | d    | AT-002   | ejemplar EJ-001 (área 2)  | Poda                  |
 * | a    | AT-001   | área 1                    | Fitosanitario         |
 * | b    | AT-001   | ejemplar EJ-001 (área 2)  | Fitosanitario         |
 * </pre>
 */
class MapaAtencionesApiTest extends ApiGestion {

    static final String MAPA = "/api/v1/reportes/mapa-atenciones/";
    static final String PERIODO = "?desde=2026-10-01&hasta=2026-10-31";

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
    }

    ResultActions como(String usuario, String consulta) throws Exception {
        return http.perform(get(MAPA + consulta).cookie(sesion(usuario)));
    }

    ResultActions mapa(String filtros) throws Exception {
        return como("jefe", PERIODO + filtros);
    }

    @Test
    void sinUbicacionesTodasVanALaLista() throws Exception {
        mapa("").andExpect(status().isOk())
                .andExpect(jsonPath("$.mapa.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.mapa.features", hasSize(0)))
                .andExpect(jsonPath("$.sin_coordenadas.length()").value(6))
                .andExpect(jsonPath("$.sin_datos").value(false))
                .andExpect(jsonPath("$.notas", hasItem(FuenteUbicacionesNoDisponible.MOTIVO)))
                .andExpect(jsonPath("$.indicador.codigo").value("RF-REP-007"))
                .andExpect(
                        jsonPath(
                                "$.totales_por_tipo[*].tipo_atencion",
                                contains("Mantenimiento", "Poda", "Fitosanitario")))
                .andExpect(jsonPath("$.totales_por_tipo[*].total", contains(2, 1, 1)));
    }

    @Test
    void elResumenDeLaAtencion() throws Exception {
        mapa("").andExpect(jsonPath("$.sin_coordenadas[0].codigo").value("AT-006"))
                .andExpect(jsonPath("$.sin_coordenadas[0].fecha").value("2026-10-31"))
                .andExpect(jsonPath("$.sin_coordenadas[0].proveedor", notNullValue()))
                .andExpect(jsonPath("$.sin_coordenadas[0].estado").value("Reporte adjunto"))
                .andExpect(jsonPath("$.sin_coordenadas[0].tipo_servicio").value("Corte de césped"))
                .andExpect(jsonPath("$.sin_coordenadas[0].archivos_asociados").value(0))
                .andExpect(
                        jsonPath(
                                "$.sin_coordenadas[?(@.codigo == 'AT-001')].archivos_asociados",
                                everyItem(is(2))))
                .andExpect(jsonPath("$.sin_coordenadas[1].codigo").value("AT-007"))
                .andExpect(jsonPath("$.sin_coordenadas[1].espacio").value("Sin espacio"))
                .andExpect(jsonPath("$.sin_coordenadas[1].area_verde_id").value(nullValue()));
    }

    // --- Filtros
    // ----------------------------------------------------------------------------------

    @Test
    void filtroPorTipoDeAtencion() throws Exception {
        mapa("&tipo_atencion=Poda")
                .andExpect(jsonPath("$.sin_coordenadas[*].codigo", contains("AT-002", "AT-002")))
                .andExpect(jsonPath("$.totales_por_tipo[*].total", contains(0, 1, 0)));
    }

    @Test
    void filtroPorRiesgo() throws Exception {
        mapa("&es_riesgo=true")
                .andExpect(jsonPath("$.sin_coordenadas[*].codigo", contains("AT-002", "AT-002")));
    }

    @Test
    void historialDelEspacio() throws Exception {
        mapa("&area_verde_id=2")
                .andExpect(
                        jsonPath(
                                "$.sin_coordenadas[*].codigo",
                                contains("AT-002", "AT-002", "AT-001")));
    }

    @Test
    void filtroPorTipoDeServicio() throws Exception {
        mapa("&tipo_servicio_id=2")
                .andExpect(jsonPath("$.sin_coordenadas[*].codigo", contains("AT-001", "AT-001")));
    }

    @Test
    void filtroPorEstado() throws Exception {
        http.perform(
                        get(MAPA)
                                .param("desde", "2026-10-01")
                                .param("hasta", "2026-10-31")
                                .param("estado", "Reporte adjunto")
                                .cookie(sesion("jefe")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.sin_coordenadas[*].codigo",
                                contains("AT-006", "AT-001", "AT-001")));
    }

    @Test
    void elTipoDeUsoTodaviaNoEstaDisponible() throws Exception {
        mapa("&tipo_uso_id=1")
                .andExpect(jsonPath("$.sin_datos").value(true))
                .andExpect(jsonPath("$.mensaje_sin_datos").value(FuenteTiposUsoNoDisponible.MOTIVO))
                .andExpect(jsonPath("$.sin_coordenadas", hasSize(0)))
                .andExpect(jsonPath("$.totales_por_tipo", hasSize(0)));
    }

    @Test
    void conPersonalPropioNoHayDatos() throws Exception {
        mapa("&modalidad=PersonalPropio")
                .andExpect(jsonPath("$.sin_datos").value(true))
                .andExpect(
                        jsonPath("$.mensaje_sin_datos")
                                .value(FuenteOperacionCampoNoDisponible.MOTIVO));
    }

    @Test
    void unPeriodoSinAtencionesDejaElMapaVisible() throws Exception {
        como("jefe", "?desde=2025-01-01&hasta=2025-01-31")
                .andExpect(jsonPath("$.sin_datos").value(true))
                .andExpect(
                        jsonPath("$.mensaje_sin_datos")
                                .value(MapaAtencionesService.MENSAJE_SIN_DATOS))
                .andExpect(jsonPath("$.mapa.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.mapa.features", hasSize(0)))
                .andExpect(jsonPath("$.totales_por_tipo[*].total", contains(0, 0, 0)));
    }

    // --- Validación
    // -------------------------------------------------------------------------------

    @Test
    void unTipoDeAtencionInventado() throws Exception {
        mapa("&tipo_atencion=Riego")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tipo_atencion", notNullValue()));
    }

    @Test
    void unEstadoQueNoEsDeAtencionRealizada() throws Exception {
        http.perform(
                        get(MAPA)
                                .param("desde", "2026-10-01")
                                .param("hasta", "2026-10-31")
                                .param("estado", "En ejecución")
                                .cookie(sesion("jefe")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.estado", notNullValue()));
    }

    @Test
    void elRiesgoEsTrueOFalse() throws Exception {
        mapa("&es_riesgo=quizas")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.es_riesgo[0]").value("Usa true o false"));
    }

    @Test
    void elTipoDeUsoEsUnNumero() throws Exception {
        mapa("&tipo_uso_id=x").andExpect(status().isBadRequest());
    }

    @Test
    void sinHastaPideLaFechaDeFin() throws Exception {
        como("jefe", "?desde=2026-10-01").andExpect(status().isBadRequest());
    }

    // --- Clasificación, permisos y solo lectura
    // ---------------------------------------------------

    @Test
    void unServicioSinClasificarNoSeMuestraYSeAvisa() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        INSERT INTO mod05_tiposervicio (nombre, ejecutor, es_riesgo, activo)
                        VALUES ('Riego tecnificado', 'Tercerizado', false, true);
                        INSERT INTO mod05_atenciontercerizada (fecha_creacion, fecha_modificacion,
                            codigo, clase, descripcion, origen, numero_orden_compra,
                            fecha_hora_inicio, estado, es_riesgo, comentarios_acuerdo_unidad,
                            solicitado_por_id, usuario_creacion_id, usuario_modificacion_id,
                            contrato_id, tipo_servicio_id)
                        SELECT '2026-10-01 08:00-05', '2026-10-01 08:00-05', 'AT-RIEGO',
                            'Programada', 'Prueba', 'Demanda', '', '2026-10-08 09:00-05',
                            'Reporte adjunto', false, '', 1, 1, 1, 1, id
                        FROM mod05_tiposervicio WHERE nombre = 'Riego tecnificado';
                        """);
        mapa("").andExpect(jsonPath("$.sin_coordenadas.length()").value(6))
                .andExpect(
                        jsonPath(
                                "$.notas",
                                hasItem(MapaAtencionesService.NOTA_SIN_CLASIFICAR.formatted(1))));
    }

    @Test
    void ingenieroYPracticantePuedenConsultar() throws Exception {
        como("ingeniero", PERIODO).andExpect(status().isOk());
        como("practicante", PERIODO).andExpect(status().isOk());
    }

    @Test
    void elCapatazNoTienePermiso() throws Exception {
        como("capataz", PERIODO)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("sin-permiso"));
    }

    @Test
    void sinSesionNoSeAutentica() throws Exception {
        http.perform(get(MAPA + PERIODO)).andExpect(status().isUnauthorized());
    }

    @Test
    void laConsultaNoModificaNada() throws Exception {
        List<String> tablas =
                List.of(
                        "mod05_atenciontercerizada",
                        "mod05_alcanceatencion",
                        "mod05_historialestado",
                        "mod05_reporteproveedor",
                        "mod05_archivoasociado");
        List<Long> antes = tablas.stream().map(this::contar).toList();
        mapa("").andExpect(status().isOk());
        assertEquals(antes, tablas.stream().map(this::contar).toList());
    }

    /** {@code tabla} es siempre una constante de esta prueba, nunca una entrada externa. */
    private long contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Map.of(), Long.class);
    }
}
