package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Paginacion;

class PaginacionTest {

    @Test
    void siempreHayAlMenosUnaPagina() {
        assertEquals(1, Paginacion.paginas(0));
        assertEquals(1, Paginacion.paginas(25));
        assertEquals(2, Paginacion.paginas(26));
    }

    @Test
    void resuelveLaPaginaPedida() {
        assertEquals(1, Paginacion.resolver(null, 0));
        assertEquals(1, Paginacion.resolver("", 30));
        assertEquals(3, Paginacion.resolver("last", 51));
        assertEquals(2, Paginacion.resolver("2", 30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "abc"})
    void unaPaginaFueraDeRangoOMalEscritaNoExiste(String pagina) {
        var e = assertThrows(ErrorGestion.class, () -> Paginacion.resolver(pagina, 30));
        assertEquals(404, e.estado());
        assertEquals("pagina-inexistente", e.codigo());
    }

    @Test
    void elDesplazamientoEsDe25PorPagina() {
        assertEquals(50, Paginacion.desplazamiento(3));
    }
}
