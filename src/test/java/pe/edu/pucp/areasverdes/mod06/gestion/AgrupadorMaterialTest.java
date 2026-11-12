package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static pe.edu.pucp.areasverdes.mod06.gestion.FuenteMovimientosMaterialEnMemoria.consumo;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.MovimientoMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.TipoMovimiento;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.AgrupadorMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.TotalMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.TotalMes;

class AgrupadorMaterialTest {

    static final List<MovimientoMaterial> DATOS =
            List.of(
                    consumo("2026-10-01", null, null, "Abono", "kg", "1"),
                    consumo("2026-10-01", 2L, "Bambú", "Abono", "kg", "1"),
                    consumo("2026-10-01", 1L, "Árbol", "Abono", "kg", "5"));

    @Test
    void ordenaConTildesYSinEspacioAlFinal() {
        assertEquals(
                List.of("Árbol", "Bambú", "Sin espacio"),
                AgrupadorMaterial.porEspacio(DATOS, TipoMovimiento.CONSUMO).stream()
                        .map(TotalMaterial::espacio)
                        .toList());
    }

    @Test
    void lasCantidadesSalenConDosDecimales() {
        assertEquals(
                "5.00",
                AgrupadorMaterial.porEspacio(DATOS, TipoMovimiento.CONSUMO).get(0).cantidad());
        assertEquals(
                List.of(new TotalMes("Abono", "kg", "7.00")),
                AgrupadorMaterial.porMaterial(DATOS, TipoMovimiento.CONSUMO));
    }

    @Test
    void lasListasSonInmutablesYNuncaNulas() {
        var lista = AgrupadorMaterial.porEspacio(DATOS, TipoMovimiento.CONSUMO);
        assertThrows(UnsupportedOperationException.class, () -> lista.add(lista.get(0)));
        assertEquals(List.of(), AgrupadorMaterial.traslados(DATOS));
        assertEquals(List.of(), AgrupadorMaterial.porMaterial(List.of(), TipoMovimiento.CONSUMO));
    }
}
