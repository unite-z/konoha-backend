package pe.edu.pucp.areasverdes.mod01.servicios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Los únicos fragmentos de SQL que MOD-01 concatena son constantes: nombres de tabla simples y
 * selecciones de una sola tabla (AV-235; {@code estilo-codigo-java.md}).
 */
class ConsultasFijasTest {

    private static final String NOMBRE_DE_TABLA = "mod0[0-9]_[a-z_]+";

    @Test
    void lasTablasDeLosAdjuntosSonNombresSimples() {
        List<String> tablas =
                AdjuntosService.DESTINOS.values().stream()
                        .flatMap(destino -> destino.tablas().stream())
                        .toList();

        assertEquals(5, AdjuntosService.DESTINOS.size());
        assertTrue(tablas.stream().allMatch(t -> t.matches(NOMBRE_DE_TABLA)), tablas.toString());
    }

    @Test
    void cadaFuenteDelRepositorioLeeSoloSuTabla() {
        RepositorioService.FUENTES.values().stream()
                .flatMap(List::stream)
                .forEach(
                        fuente -> {
                            assertTrue(fuente.tabla().matches(NOMBRE_DE_TABLA), fuente.tabla());
                            assertTrue(
                                    fuente.seleccion().contains(" FROM " + fuente.tabla()),
                                    fuente.seleccion());
                            assertEquals(
                                    1,
                                    fuente.seleccion().split(" FROM ", -1).length - 1,
                                    fuente.seleccion());
                            assertFalse(fuente.seleccion().contains(";"), fuente.seleccion());
                        });
    }
}
