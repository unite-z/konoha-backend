package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.IncidenciasRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.IncidenciasRepository.Conteo;
import pe.edu.pucp.areasverdes.mod06.gestion.exportaciones.MatrizIncidencias;

/** RF-REP-009: matriz de incidencias por espacio, tipo y estado. */
class MatrizIncidenciasTest {

    @Test
    void sinIncidenciasNoHayFilasNiSiquieraLaDeTotales() {
        assertTrue(MatrizIncidencias.filas(List.of()).isEmpty());
    }

    @Test
    void lasColumnasSonEspacioTipoLosEstadosEnOrdenYElTotal() {
        List<String> columnas = MatrizIncidencias.columnas();
        assertEquals(
                List.of("Espacio", "Tipo de incidencia", "Registrada"), columnas.subList(0, 3));
        assertEquals("Descartada", columnas.get(columnas.size() - 2));
        assertEquals("Total", columnas.getLast());
        assertEquals(2 + MatrizIncidencias.ESTADOS.size() + 1, columnas.size());
    }

    @Test
    void cuentaPorEspacioYTipoConLaFilaDeTotalesAlFinal() {
        List<List<String>> filas =
                MatrizIncidencias.filas(
                        List.of(
                                new Conteo("Jardín", "Rama caída", "Registrada", 2),
                                new Conteo("Jardín", "Rama caída", "Cerrada", 1),
                                new Conteo(
                                        IncidenciasRepository.SIN_ESPACIO, "Basura", "Cerrada", 1),
                                new Conteo("Biblioteca", "Basura", "EnAtencion", 3)));
        assertEquals(
                List.of("Biblioteca", "Jardín", IncidenciasRepository.SIN_ESPACIO, "Total"),
                filas.stream().map(List::getFirst).toList(),
                "Por espacio con las reglas del español; «Sin espacio» y el total, al final");
        List<String> jardin = filas.get(1);
        int registrada = 2, cerrada = 2 + 6;
        assertEquals("2", jardin.get(registrada));
        assertEquals("1", jardin.get(cerrada));
        assertEquals("3", jardin.getLast());
        assertEquals("7", filas.getLast().getLast());
    }

    @Test
    void unEstadoDesconocidoNoSeCuenta() {
        List<List<String>> filas =
                MatrizIncidencias.filas(List.of(new Conteo("Jardín", "Poda", "Inventado", 4)));
        assertTrue(filas.isEmpty());
    }
}
