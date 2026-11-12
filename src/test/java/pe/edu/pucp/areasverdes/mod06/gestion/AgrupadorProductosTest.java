package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.edu.pucp.areasverdes.mod06.gestion.FuenteProductosProveedorEnMemoria.aplicacion;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.AgrupadorProductos;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.TotalProductoProveedor;

/** RF-REP-005, flujo 3a: un total por producto y proveedor. */
class AgrupadorProductosTest {

    @Test
    void sinAplicacionesNoHayTotales() {
        assertTrue(AgrupadorProductos.porProductoYProveedor(List.of()).isEmpty());
    }

    @Test
    void agrupaPorProductoYProveedorYCuentaLasAtenciones() {
        List<TotalProductoProveedor> t =
                AgrupadorProductos.porProductoYProveedor(
                        List.of(
                                aplicacion(
                                        "2026-10-02", 1, "Cupravit", "Oxicloruro", "2 g/L", "Agro"),
                                aplicacion(
                                        "2026-10-09", 2, "Cupravit", "Oxicloruro", "3 g/L", "Agro"),
                                aplicacion(
                                        "2026-10-10",
                                        3,
                                        "Cupravit",
                                        "Oxicloruro",
                                        "2 g/L",
                                        "Verde"),
                                aplicacion("2026-10-11", 4, "Abamectina", "", "", "Agro")));
        assertEquals(3, t.size());
        TotalProductoProveedor abamectina = t.get(0);
        assertEquals("Abamectina", abamectina.producto());
        assertNull(abamectina.principioActivo(), "Sin principio activo va nulo, no vacío");
        assertTrue(abamectina.dosis().isEmpty(), "Las dosis vacías no se listan");
        TotalProductoProveedor agro = t.get(1);
        assertEquals("Agro", agro.proveedor());
        assertEquals(2, agro.aplicaciones());
        assertEquals(List.of("2 g/L", "3 g/L"), agro.dosis());
        assertEquals(List.of("AT-1", "AT-2"), agro.atenciones());
        assertEquals("Verde", t.get(2).proveedor());
    }

    @Test
    void lasDosisRepetidasSeListanUnaVez() {
        List<TotalProductoProveedor> t =
                AgrupadorProductos.porProductoYProveedor(
                        List.of(
                                aplicacion(
                                        "2026-10-02", 1, "Cupravit", "Oxicloruro", "2 g/L", "Agro"),
                                aplicacion(
                                        "2026-10-09",
                                        2,
                                        "Cupravit",
                                        "Oxicloruro",
                                        " 2 g/L ",
                                        "Agro")));
        assertEquals(List.of("2 g/L"), t.getFirst().dosis());
    }

    @Test
    void ordenaPorProductoConLasReglasDelEspanol() {
        List<TotalProductoProveedor> t =
                AgrupadorProductos.porProductoYProveedor(
                        List.of(
                                aplicacion("2026-10-02", 1, "Óxido", "", "", "Agro"),
                                aplicacion("2026-10-02", 1, "Nitrato", "", "", "Agro"),
                                aplicacion("2026-10-02", 1, "Abono", "", "", "Agro")));
        assertEquals(
                List.of("Abono", "Nitrato", "Óxido"),
                t.stream().map(TotalProductoProveedor::producto).toList());
    }
}
