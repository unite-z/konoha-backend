package pe.edu.pucp.areasverdes.mod01.servicios;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.edu.pucp.areasverdes.mod05.ArchivoStorage;

class AdjuntosActividadTest {
    final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    final Autorizacion autorizacion = mock(Autorizacion.class);
    final AdjuntosService servicio =
            new AdjuntosService(
                    jdbc,
                    mock(ArchivoStorage.class),
                    autorizacion,
                    mock(RegistradorAuditoria.class));
    final UsuarioSesion capataz =
            new UsuarioSesion(
                    1,
                    "capataz",
                    "Capataz",
                    "capataz",
                    "Capataz",
                    Set.of("CAM.PARTE.REGISTRAR"),
                    false,
                    1);

    @Test
    void capatazNoConsultaFotografiasDeActividadAjena() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Boolean.class))).thenReturn(false);
        assertThrows(ErrorCore.class, () -> servicio.listar("actividad", 42, capataz));
        verify(autorizacion).exigir(capataz, "CAM.PARTE.REGISTRAR");
    }
}
