package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.DefinicionesIndicador;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Indicador;

class DefinicionesIndicadorTest {

    @Test
    void losCodigosSonUnicos() {
        Set<String> codigos = new HashSet<>();
        for (Indicador i : DefinicionesIndicador.TODAS) {
            assertTrue(codigos.add(i.codigo()), "Código repetido: " + i.codigo());
        }
    }

    @Test
    void ningunTextoEstaEnBlanco() {
        for (Indicador i : DefinicionesIndicador.TODAS) {
            assertFalse(i.nombre().isBlank(), i.codigo());
            assertFalse(i.descripcionCalculo().isBlank(), i.codigo());
            assertFalse(i.unidad().isBlank(), i.codigo());
        }
    }

    @Test
    void seBuscaPorCodigo() {
        assertTrue(DefinicionesIndicador.porCodigo("RF-REP-006").isPresent());
        assertTrue(DefinicionesIndicador.porCodigo("X").isEmpty());
    }

    /** Si alguien cambia el texto en un sitio y no en el otro, esta prueba avisa. */
    @Test
    void laDescripcionDeAtencionesPorEspacioEsLaDelMockData() throws Exception {
        JsonNode definiciones =
                new ObjectMapper()
                        .readTree(Path.of("mock-data/mod06/DefinicionIndicador.json").toFile());
        String esperado = null;
        for (JsonNode d : definiciones) {
            if (d.get("codigo").asText().equals("RF-REP-006")) {
                esperado = d.get("descripcion_calculo").asText();
            }
        }
        assertEquals(esperado, DefinicionesIndicador.ATENCIONES_POR_ESPACIO.descripcionCalculo());
    }
}
