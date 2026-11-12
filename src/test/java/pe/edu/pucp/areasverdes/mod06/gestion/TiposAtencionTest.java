package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.MapaAtencionesService;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.TiposAtencion;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/** RF-REP-007 v2: clasificación de los tipos de servicio en mantenimiento, poda y fitosanitario. */
class TiposAtencionTest {

    @ParameterizedTest
    @CsvSource({
        "Corte de césped, Mantenimiento",
        "Control fitosanitario, Fitosanitario",
        "Poda mayor programada, Poda",
        "Poda de altura a demanda, Poda",
        "Mantenimiento de jardines, Mantenimiento",
        "Poda menor, Poda"
    })
    void clasificaLosSeisTiposDeServicio(String servicio, String tipo) {
        assertEquals(Optional.of(tipo), TiposAtencion.deTipoServicio(servicio));
    }

    @Test
    void unNombreDesconocidoONuloNoSeClasifica() {
        assertTrue(TiposAtencion.deTipoServicio("Riego tecnificado").isEmpty());
        assertTrue(TiposAtencion.deTipoServicio(null).isEmpty());
    }

    @Test
    void laCoincidenciaEsExacta() {
        assertTrue(TiposAtencion.deTipoServicio("control fitosanitario").isEmpty());
    }

    @Test
    void todosRespetaElOrden() {
        assertEquals(List.of("Mantenimiento", "Poda", "Fitosanitario"), TiposAtencion.TODOS);
    }

    @Test
    void losEstadosDelMapaSonLosEjecutadosDeRfRep006() {
        assertEquals(
                AtencionesEspacioSectorService.ESTADOS_ATENCION_EJECUTADA,
                new HashSet<>(MapaAtencionesService.ESTADOS_REALIZADOS));
        assertEquals(
                MapaAtencionesService.ESTADOS_REALIZADOS.size(),
                AtencionesEspacioSectorService.ESTADOS_ATENCION_EJECUTADA.size());
    }
}
