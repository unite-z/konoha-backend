package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.edu.pucp.areasverdes.mod06.gestion.FuenteMovimientosMaterialEnMemoria.consumo;
import static pe.edu.pucp.areasverdes.mod06.gestion.FuenteMovimientosMaterialEnMemoria.propagacion;
import static pe.edu.pucp.areasverdes.mod06.gestion.FuenteMovimientosMaterialEnMemoria.traslado;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteMovimientosMaterialNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ComparacionMes;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.DefinicionesIndicador;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.FiltrosMaterialVegetal;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.MaterialVegetalService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteMaterialVegetal;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.TotalMaterial;

/** RF-REP-005 · CU-MOD05-029: reglas del reporte mensual de material vegetal. */
class MaterialVegetalServiceTest {

    static final Clock RELOJ =
            Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneId.of("America/Lima"));
    static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    static ReporteMaterialVegetal generar(FuenteMovimientosMaterialEnMemoria fuente) {
        return generar(fuente, null);
    }

    static ReporteMaterialVegetal generar(FuenteMovimientosMaterialEnMemoria fuente, Long area) {
        return new MaterialVegetalService(fuente, RELOJ, 3).generar(OCTUBRE, area);
    }

    @Test
    void sinFuenteDisponibleNoHayDatosYDiceElMotivo() {
        var fuente =
                new FuenteMovimientosMaterialEnMemoria(
                        new FuenteMovimientosMaterialNoDisponible().disponibilidad(), List.of());
        var r = generar(fuente);
        assertTrue(r.sinDatos());
        assertEquals(FuenteMovimientosMaterialNoDisponible.MOTIVO, r.mensajeSinDatos());
        assertTrue(r.consumido().isEmpty());
        assertTrue(r.propagado().isEmpty());
        assertTrue(r.trasladado().isEmpty());
        assertTrue(r.comparacion().isEmpty());
        assertTrue(r.productosProveedor().sinDatos());
        assertTrue(fuente.periodosPedidos.isEmpty(), "No se piden movimientos");
    }

    @Test
    void unMesSinMovimientosComparaIgualConLosAnteriores() {
        var r =
                generar(
                        FuenteMovimientosMaterialEnMemoria.con(
                                consumo("2026-08-10", 1L, "Jardín", "Grass", "m²", "3")));
        assertTrue(r.sinDatos());
        assertEquals(MaterialVegetalService.MENSAJE_SIN_DATOS_MES, r.mensajeSinDatos());
        assertEquals(3, r.comparacion().size());
        ComparacionMes agosto = r.comparacion().get(1);
        assertEquals("2026-08", agosto.mes());
        assertFalse(agosto.sinDatos());
    }

    @Test
    void separaLoConsumidoLoPropagadoYLoTrasladado() {
        var r =
                generar(
                        FuenteMovimientosMaterialEnMemoria.con(
                                consumo("2026-10-02", 1L, "Jardín", "Grass", "m²", "1"),
                                propagacion("2026-10-03", 1L, "Jardín", "Esqueje", "u", "4"),
                                traslado(
                                        "2026-10-04",
                                        1L,
                                        "Jardín",
                                        2L,
                                        "Bosque",
                                        "Palmera",
                                        "u",
                                        "2")));
        assertFalse(r.sinDatos());
        assertNull(r.mensajeSinDatos());
        assertEquals(
                List.of("Grass"), r.consumido().stream().map(TotalMaterial::tipoMaterial).toList());
        assertEquals(
                List.of("Esqueje"),
                r.propagado().stream().map(TotalMaterial::tipoMaterial).toList());
        assertEquals(1, r.trasladado().size());
        assertEquals("Palmera", r.trasladado().get(0).tipoMaterial());
    }

    @Test
    void sumaDecimalExacta() {
        var r =
                generar(
                        FuenteMovimientosMaterialEnMemoria.con(
                                consumo("2026-10-02", 1L, "Jardín", "Grass", "m²", "0.10"),
                                consumo("2026-10-05", 1L, "Jardín", "Grass", "m²", "0.20")));
        assertEquals(1, r.consumido().size());
        assertEquals("0.30", r.consumido().get(0).cantidad());
        assertEquals(2, r.consumido().get(0).movimientos());
    }

    @Test
    void lasUnidadesDistintasNoSeSuman() {
        var r =
                generar(
                        FuenteMovimientosMaterialEnMemoria.con(
                                consumo("2026-10-02", 1L, "Jardín", "Grass", "m²", "1"),
                                consumo("2026-10-02", 1L, "Jardín", "Grass", "sacos", "2")));
        assertEquals(2, r.consumido().size());
    }

    @Test
    void sinEspacioVaAlFinal() {
        var r =
                generar(
                        FuenteMovimientosMaterialEnMemoria.con(
                                consumo("2026-10-02", null, null, "Abono", "kg", "1"),
                                consumo("2026-10-02", 1L, "Zona sur", "Abono", "kg", "1")));
        TotalMaterial ultimo = r.consumido().get(r.consumido().size() - 1);
        assertEquals("Sin espacio", ultimo.espacio());
        assertNull(ultimo.areaVerdeId());
        assertEquals("Zona sur", r.consumido().get(0).espacio());
    }

    @Test
    void elTrasladoConservaOrigenYDestino() {
        var r =
                generar(
                        FuenteMovimientosMaterialEnMemoria.con(
                                traslado(
                                        "2026-10-04",
                                        1L,
                                        "Jardín",
                                        2L,
                                        "Bosque",
                                        "Palmera",
                                        "u",
                                        "2"),
                                traslado(
                                        "2026-10-04",
                                        null,
                                        null,
                                        2L,
                                        "Bosque",
                                        "Palmera",
                                        "u",
                                        "1")));
        var conOrigen = r.trasladado().get(0);
        assertEquals("Jardín", conOrigen.origen());
        assertEquals("Bosque", conOrigen.destino());
        var sinOrigen = r.trasladado().get(1);
        assertNull(sinOrigen.origenAreaId());
        assertEquals("Sin espacio", sinOrigen.origen());
    }

    @Test
    void comparaLosTresMesesAnterioresSinCeros() {
        var fuente =
                FuenteMovimientosMaterialEnMemoria.con(
                        consumo("2026-07-15", 1L, "Jardín", "Grass", "m²", "1"),
                        consumo("2026-09-15", 1L, "Jardín", "Grass", "m²", "2"));
        var r = generar(fuente);
        assertEquals(
                List.of("2026-07", "2026-08", "2026-09"),
                r.comparacion().stream().map(ComparacionMes::mes).toList());
        ComparacionMes agosto = r.comparacion().get(1);
        assertTrue(agosto.sinDatos());
        assertTrue(agosto.consumido().isEmpty());
        assertTrue(agosto.propagado().isEmpty());
        assertTrue(agosto.trasladado().isEmpty());
        assertEquals("2.00", r.comparacion().get(2).consumido().get(0).cantidad());
        assertEquals(
                List.of(new RangoFechas(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 31))),
                fuente.periodosPedidos);
    }

    @Test
    void elFiltroDeEspacioLlegaTalCualALaFuente() {
        var fuente = FuenteMovimientosMaterialEnMemoria.con();
        var r = generar(fuente, 7L);
        assertEquals(List.of(7L), fuente.areasPedidas);
        assertEquals(new FiltrosMaterialVegetal("2026-10", 7L), r.filtrosAplicados());
    }

    @Test
    void periodoIndicadorYNotas() {
        var r = generar(FuenteMovimientosMaterialEnMemoria.con());
        assertEquals(
                new RangoFechas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
                r.periodo());
        assertEquals(OffsetDateTime.now(RELOJ), r.generadoEn());
        assertEquals(DefinicionesIndicador.CONSUMO_MATERIALES, r.indicador());
        assertTrue(r.notas().contains(MaterialVegetalService.NOTA_SEPARACION));
        assertTrue(r.notas().contains(MaterialVegetalService.NOTA_UNIDADES));
    }

    @Test
    void conCeroMesesDeComparacionNoArranca() {
        var fuente = new FuenteMovimientosMaterialEnMemoria(Disponibilidad.DISPONIBLE, List.of());
        assertThrows(
                IllegalStateException.class, () -> new MaterialVegetalService(fuente, RELOJ, 0));
    }
}
