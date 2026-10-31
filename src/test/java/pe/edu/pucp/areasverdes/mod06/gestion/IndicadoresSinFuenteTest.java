package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteMovimientosMaterialNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteOperacionCampoNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.IndicadorActividadesPorEstado;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.IndicadorConsumoMateriales;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.IndicadorDistribucionOperarios;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.IndicadorPanel;

/**
 * Los indicadores cuyas fuentes aún no están en el sistema responden «Sin datos» con su motivo,
 * nunca un 0 (Guía de Estilo Web 5.7).
 */
class IndicadoresSinFuenteTest {

    static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneId.of("America/Lima"));

    static Stream<Arguments> indicadores() {
        var operacion = new FuenteOperacionCampoNoDisponible();
        return Stream.of(
                Arguments.of(
                        new IndicadorActividadesPorEstado(operacion, RELOJ),
                        FuenteOperacionCampoNoDisponible.MOTIVO),
                Arguments.of(
                        new IndicadorDistribucionOperarios(operacion, RELOJ),
                        FuenteOperacionCampoNoDisponible.MOTIVO),
                Arguments.of(
                        new IndicadorConsumoMateriales(
                                new FuenteMovimientosMaterialNoDisponible(), RELOJ),
                        FuenteMovimientosMaterialNoDisponible.MOTIVO));
    }

    @ParameterizedTest
    @MethodSource("indicadores")
    void calculaSinDatosConSuMotivo(IndicadorPanel indicador, String motivo) {
        var r = indicador.calcular(PanelServiceTest.CONSULTA);
        assertTrue(r.sinDatos());
        assertNull(r.valor());
        assertTrue(r.desglose().isEmpty());
        assertEquals(motivo, r.mensajeSinDatos());
    }

    @ParameterizedTest
    @MethodSource("indicadores")
    void elDetalleEstaVacio(IndicadorPanel indicador, String motivo) {
        var d = indicador.detalle(PanelServiceTest.CONSULTA, null, p -> "");
        assertEquals(0, d.count());
        assertTrue(d.results().isEmpty());
        assertTrue(d.sinDatos());
        assertEquals(motivo, d.mensajeSinDatos());
        assertTrue(indicador.detalle(PanelServiceTest.CONSULTA, "1", p -> "").sinDatos());
    }

    @ParameterizedTest
    @MethodSource("indicadores")
    void laPagina2NoExiste(IndicadorPanel indicador, String motivo) {
        var e =
                assertThrows(
                        ErrorGestion.class,
                        () -> indicador.detalle(PanelServiceTest.CONSULTA, "2", p -> ""));
        assertEquals(404, e.estado());
        assertEquals("pagina-inexistente", e.codigo());
    }
}
