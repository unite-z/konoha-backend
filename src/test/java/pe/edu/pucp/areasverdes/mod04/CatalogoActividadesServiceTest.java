package pe.edu.pucp.areasverdes.mod04;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod04.api.Entradas;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod04.servicios.CatalogoActividadesService;
import pe.edu.pucp.areasverdes.mod05.ApiError;
import pe.edu.pucp.areasverdes.mod05.Usuario;

class CatalogoActividadesServiceTest {
    final Mod04Repository repo = mock(Mod04Repository.class);
    final CatalogoActividadesService service = new CatalogoActividadesService(repo);
    final Entradas.TipoActividad entrada = new Entradas.TipoActividad("Poda", 4L, true);

    @Test
    void soloElJefePuedeModificarElCatalogo() {
        var capataz = new Usuario(1, "capataz", "Capataz", "", true, List.of("Capataz"));
        assertThrows(ApiError.class, () -> service.guardar(null, entrada, capataz));
        verifyNoInteractions(repo);
    }

    @Test
    void rechazaUnaClaseInactiva() {
        var jefe = new Usuario(1, "jefe", "Jefe", "", true, List.of(Usuario.JEFE));
        when(repo.get("clase_actividad", 4L, true)).thenReturn(Map.of("activo", false));
        assertEquals(
                "La clase de actividad debe estar activa",
                assertThrows(ApiError.class, () -> service.guardar(null, entrada, jefe))
                        .getMessage());
    }

    @Test
    void rechazaNombresDuplicadosDentroDeLaClase() {
        var jefe = new Usuario(1, "jefe", "Jefe", "", true, List.of(Usuario.JEFE));
        when(repo.get("clase_actividad", 4L, true)).thenReturn(Map.of("activo", true));
        when(repo.rows(any(), any())).thenReturn(List.of(Map.of("id", 12L)));
        assertEquals(
                "Ya existe un tipo con ese nombre en la clase",
                assertThrows(ApiError.class, () -> service.guardar(null, entrada, jefe))
                        .getMessage());
    }
}
