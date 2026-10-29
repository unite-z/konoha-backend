package pe.edu.pucp.areasverdes.mod01.servicios;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod01.servicios.HistorialService.Entrada;

/** CU-MOD01-006: permiso, alcance de cuadrilla y campos económicos, con dobles y sin Spring. */
class AutorizacionTest {

    /** Registro de accesos falso: guarda los rechazos en memoria. */
    static final class AccesosEnMemoria extends RegistroAccesos {
        final List<String> rechazos = new ArrayList<>();

        AccesosEnMemoria() {
            super(null, null);
        }

        @Override
        public void registrar(
                Long usuarioId,
                String identificador,
                String canal,
                String resultado,
                String motivo,
                String ruta) {
            rechazos.add(resultado + ":" + motivo);
        }
    }

    static final ConsultaCampo CUADRILLA_7 =
            new ConsultaCampo() {
                @Override
                public Set<Long> cuadrillasDelCapataz(long usuarioId) {
                    return Set.of(7L);
                }

                @Override
                public List<EncargoAbierto> encargosAbiertos(long usuarioId) {
                    return List.of();
                }
            };

    final AccesosEnMemoria accesos = new AccesosEnMemoria();
    final Autorizacion autorizacion = new Autorizacion(accesos, CUADRILLA_7);

    static UsuarioSesion usuario(String rol, String... permisos) {
        return new UsuarioSesion(1, rol, rol, rol, rol, Set.of(permisos), false, 1);
    }

    @Test
    void sinElPermisoSeRechazaYSeRegistraElIntento() {
        var practicante = usuario("practicante", "CORE.REPOSITORIO.CONSULTAR");
        var error =
                assertThrows(
                        ErrorCore.class,
                        () -> autorizacion.exigir(practicante, "CORE.USUARIO.GESTIONAR"));
        assertEquals(403, error.estado());
        assertEquals("No tiene permiso para esta acción", error.getMessage());
        assertEquals(List.of("SinPermiso:CORE.USUARIO.GESTIONAR"), accesos.rechazos);
        assertDoesNotThrow(() -> autorizacion.exigir(practicante, "CORE.REPOSITORIO.CONSULTAR"));
    }

    @Test
    void sinSesionEsNoAutenticado() {
        assertEquals(
                401,
                assertThrows(ErrorCore.class, () -> autorizacion.exigir(null, "X.Y.Z")).estado());
    }

    @Test
    void elCapatazNoRegistraEnLaCuadrillaDeOtro() {
        var capataz = usuario("capataz", "CAM.PARTE.REGISTRAR");
        assertDoesNotThrow(() -> autorizacion.exigirCuadrilla(capataz, 7));
        assertThrows(ErrorCore.class, () -> autorizacion.exigirCuadrilla(capataz, 8));
        assertTrue(autorizacion.alcanceCuadrillas(usuario("ingeniero-campo")).isEmpty());
    }

    @Test
    void soloQuienPuedeVerMontosLosVe() {
        assertTrue(autorizacion.puedeVerMontos(usuario("jefe-seccion", Autorizacion.VER_MONTOS)));
        assertFalse(autorizacion.puedeVerMontos(usuario("ingeniero-campo")));
        var entrada =
                new Entrada(
                        "Alta",
                        "x",
                        OffsetDateTime.now(),
                        Map.of(),
                        Map.of("monto_fijo", "10.00", "Importe", "1", "estado", "Registrada"),
                        null);
        assertEquals(
                Map.of("estado", "Registrada"),
                HistorialService.sinMontos(entrada).valoresNuevos());
    }

    @Test
    void lasBusquedasEscapanLosComodinesDeLike() {
        assertEquals("100\\%\\_a\\\\b", RepositorioService.escaparLike("100%_a\\b"));
        assertEquals("core-foto-arbol.png", AdjuntosService.saneado("Core Foto Árbol.png"));
    }
}
