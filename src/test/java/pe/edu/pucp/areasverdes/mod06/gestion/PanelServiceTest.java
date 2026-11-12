package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ConsultaPanel;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.DefinicionesIndicador;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.DetalleIndicador;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.FiltrosPanel;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Indicador;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.IndicadorPanel;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Paginacion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.PanelService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ResultadoIndicador;

/** RF-REP-001: orden, validación de las estrategias y agregado del panel. */
class PanelServiceTest {

    static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneId.of("America/Lima"));
    static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    static final LocalDate HASTA = LocalDate.of(2026, 10, 31);
    static final ConsultaPanel CONSULTA =
            new ConsultaPanel(
                    new RangoFechas(DESDE, HASTA),
                    new FiltrosPanel(DESDE, HASTA, null, null, null, null));

    /** Indicador falso con o sin datos. */
    record Falso(String clave, boolean sinDatos) implements IndicadorPanel {
        @Override
        public Indicador definicion() {
            return DefinicionesIndicador.ATENCIONES_POR_ESPACIO;
        }

        @Override
        public ResultadoIndicador calcular(ConsultaPanel consulta) {
            return new ResultadoIndicador(
                    clave,
                    definicion(),
                    sinDatos ? null : "1",
                    sinDatos,
                    sinDatos ? "motivo" : null,
                    List.of(),
                    List.of());
        }

        @Override
        public DetalleIndicador detalle(
                ConsultaPanel consulta, String page, Paginacion.EnlacePagina enlace) {
            throw new UnsupportedOperationException();
        }
    }

    static List<IndicadorPanel> todos(boolean sinDatos) {
        List<IndicadorPanel> lista = new ArrayList<>();
        for (String clave : PanelService.ORDEN) {
            lista.add(new Falso(clave, sinDatos));
        }
        return lista;
    }

    @Test
    void devuelveLosIndicadoresEnElOrdenDelCu() {
        List<IndicadorPanel> desordenados = new ArrayList<>(todos(false));
        Collections.reverse(desordenados);
        var panel = new PanelService(desordenados, RELOJ).panel(CONSULTA);
        assertEquals(
                PanelService.ORDEN,
                panel.indicadores().stream().map(ResultadoIndicador::clave).toList());
    }

    @Test
    void faltaUnIndicador() {
        List<IndicadorPanel> lista = todos(false);
        lista.remove(0);
        assertThrows(IllegalStateException.class, () -> new PanelService(lista, RELOJ));
    }

    @Test
    void sobraUnIndicadorSinPosicion() {
        List<IndicadorPanel> lista = todos(false);
        lista.add(new Falso("otro", false));
        assertThrows(IllegalStateException.class, () -> new PanelService(lista, RELOJ));
    }

    @Test
    void unaClaveRepetida() {
        List<IndicadorPanel> lista = todos(false);
        lista.add(new Falso(PanelService.ORDEN.get(0), false));
        assertThrows(IllegalStateException.class, () -> new PanelService(lista, RELOJ));
    }

    @Test
    void elPanelSoloEstaSinDatosSiLosCincoLoEstan() {
        var vacio = new PanelService(todos(true), RELOJ).panel(CONSULTA);
        assertTrue(vacio.sinDatos());
        assertEquals(PanelService.MENSAJE_SIN_DATOS, vacio.mensajeSinDatos());

        List<IndicadorPanel> uno = todos(true);
        uno.set(2, new Falso(PanelService.ORDEN.get(2), false));
        assertFalse(new PanelService(uno, RELOJ).panel(CONSULTA).sinDatos());
    }

    @Test
    void unIndicadorQueNoExisteDa404() {
        var e =
                assertThrows(
                        ErrorGestion.class,
                        () ->
                                new PanelService(todos(false), RELOJ)
                                        .detalle("no-existe", CONSULTA, null, p -> ""));
        assertEquals(404, e.estado());
        assertEquals("indicador-no-encontrado", e.codigo());
    }

    @Test
    void lasNotasAvisanDeLosFiltrosPendientes() {
        var panel = new PanelService(todos(false), RELOJ).panel(CONSULTA);
        assertTrue(panel.notas().contains(PanelService.NOTA_FILTROS_PENDIENTES));
    }
}
