package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.pucp.areasverdes.mod06.ia.ContextoExtracciones;

/**
 * AV-355: atención e informe de MOD-05 de las propuestas de extracción, sobre {@code
 * escenario-gestion.sql} (informe 1 de AT-001 con el archivo 1, «informe.pdf», InformeFinal).
 */
class ContextoExtraccionesApiTest extends ApiGestion {

    @Autowired ContextoExtracciones contexto;

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
    }

    @Test
    void devuelveLaAtencionYElInformeConElDocumentoAnalizado() {
        var r = contexto.porReporte(List.of(1L), Map.of(1L, 1L)).get(1L);
        assertEquals("AT-001", r.atencion().get("codigo"));
        assertEquals("Control fitosanitario", r.atencion().get("tipo_servicio"));
        assertEquals("Jardines del Sur S.A.C. (prueba)", r.atencion().get("proveedor"));
        assertEquals(0, r.informe().get("revision"));
        assertEquals("2026-10-06", r.informe().get("fecha_recepcion"));
        Map<?, ?> documento = (Map<?, ?>) r.informe().get("documento");
        assertEquals("informe.pdf", documento.get("nombre"));
        assertEquals(1L, documento.get("id"));
        assertEquals("InformeFinal", documento.get("categoria"));
    }

    @Test
    void sinElArchivoDeLaPropuestaNoHayDocumentoYUnInformeInexistenteNoAparece() {
        var r = contexto.porReporte(List.of(1L, 999L), Map.of(1L, 999L));
        assertNull(r.get(1L).informe().get("documento"));
        assertNull(r.get(999L));
    }
}
