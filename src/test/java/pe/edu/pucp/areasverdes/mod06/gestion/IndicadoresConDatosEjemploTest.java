package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteMovimientosMaterialEjemplo;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampoEjemplo;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ConsultaPanel;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ElementoDesglose;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.FiltrosPanel;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.IndicadorActividadesPorEstado;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.IndicadorConsumoMateriales;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.IndicadorDistribucionOperarios;

/**
 * Con las fuentes de EJEMPLO ({@code MOD06_DATOS_EJEMPLO=true}, solo desarrollo) los tres
 * indicadores de MOD-04 tienen datos, siempre los mismos para el mismo periodo.
 */
class IndicadoresConDatosEjemploTest {

    static final ConsultaPanel CONSULTA = PanelServiceTest.CONSULTA;

    final FuenteOperacionCampoEjemplo operacion =
            new FuenteOperacionCampoEjemplo(PanelServiceTest.RELOJ);

    @Test
    void lasActividadesSeCuentanPorEstadoYTipo() {
        var r =
                new IndicadorActividadesPorEstado(operacion, PanelServiceTest.RELOJ)
                        .calcular(CONSULTA);
        assertFalse(r.sinDatos());
        int total = Integer.parseInt(r.valor());
        assertTrue(total > 0);
        List<ElementoDesglose> estados =
                r.desglose().stream().filter(e -> e.etiqueta().startsWith("Estado: ")).toList();
        assertEquals(
                List.of("Estado: Abierto", "Estado: Cerrado"),
                estados.stream().map(ElementoDesglose::etiqueta).toList());
        assertEquals(total, estados.stream().mapToInt(e -> Integer.parseInt(e.valor())).sum());
        var otraVez =
                new IndicadorActividadesPorEstado(operacion, PanelServiceTest.RELOJ)
                        .calcular(CONSULTA);
        assertEquals(r, otraVez);
    }

    @Test
    void elDetalleDeActividadesPagina() {
        var d =
                new IndicadorActividadesPorEstado(operacion, PanelServiceTest.RELOJ)
                        .detalle(CONSULTA, "1", p -> "?page=" + p);
        assertFalse(d.sinDatos());
        assertTrue(d.count() > 0);
        assertFalse(d.results().isEmpty());
    }

    @Test
    void losOperariosSeCuentanUnaVez() {
        var r =
                new IndicadorDistribucionOperarios(operacion, PanelServiceTest.RELOJ)
                        .calcular(CONSULTA);
        assertFalse(r.sinDatos());
        int operarios = Integer.parseInt(r.valor());
        assertTrue(operarios > 0 && operarios <= 8);
        assertTrue(r.desglose().stream().anyMatch(e -> e.etiqueta().startsWith("Cuadrilla: ")));
    }

    @Test
    void elConsumoDeMaterialesTieneDatos() {
        var r =
                new IndicadorConsumoMateriales(
                                new FuenteMovimientosMaterialEjemplo(PanelServiceTest.RELOJ),
                                PanelServiceTest.RELOJ)
                        .calcular(CONSULTA);
        assertFalse(r.sinDatos());
        assertFalse(r.desglose().isEmpty());
    }

    @Test
    void elFiltroDeEspacioYLaModalidadTercerizadaSeRespetan() {
        var soloEspacio1 =
                new ConsultaPanel(
                        CONSULTA.periodo(),
                        new FiltrosPanel(
                                CONSULTA.periodo().desde(),
                                CONSULTA.periodo().hasta(),
                                1L,
                                null,
                                null,
                                null));
        assertTrue(
                operacion.actividades(soloEspacio1.periodo(), 1L).stream()
                        .allMatch(a -> a.areaVerdeId() == 1L));
        var tercerizado =
                new ConsultaPanel(
                        CONSULTA.periodo(),
                        new FiltrosPanel(
                                CONSULTA.periodo().desde(),
                                CONSULTA.periodo().hasta(),
                                null,
                                null,
                                null,
                                "Tercerizado"));
        var r =
                new IndicadorActividadesPorEstado(operacion, PanelServiceTest.RELOJ)
                        .calcular(tercerizado);
        assertTrue(r.sinDatos());
        assertEquals(
                IndicadorActividadesPorEstado.MENSAJE_NO_APLICA_TERCERIZADOS, r.mensajeSinDatos());
    }
}
