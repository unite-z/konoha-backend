package pe.edu.pucp.areasverdes.mod02.servicios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Qué fuentes del resumen del área (AV-431) ve cada usuario según sus permisos. */
class ResumenAreaFuentesTest {

    private static Usuario usuario(boolean activo, String... permisos) {
        return new Usuario(7, "u", "U", "x", activo, List.of(), Set.of(permisos));
    }

    @Test
    void quienTieneTodosLosPermisosVeTodasLasFuentes() {
        ResumenAreaService.Fuentes f =
                ResumenAreaService.Fuentes.de(
                        usuario(
                                true,
                                "CAM.PARTE.CONSULTAR",
                                "CAM.PARTE.REGISTRAR",
                                "CAM.CUADRILLA.GESTIONAR",
                                "PLA.INCIDENCIA.REGISTRAR",
                                "PLA.INCIDENCIA.CONSULTAR_TODAS",
                                "SER.ATENCION.REGISTRAR",
                                "PLA.PLANIFICACION.CONSULTAR",
                                "REP.INDICADOR.CONSULTAR"));

        assertThat(f.visibles())
                .containsExactly(
                        "actividades",
                        "incidencias",
                        "atenciones",
                        "programacion",
                        "prioridad",
                        "cambios_sector");
        assertThat(f.actividadesPropias()).isFalse();
        assertThat(f.veTodasIncidencias()).isTrue();
    }

    @Test
    void elCapatazSoloVeLoSuyoYNoVeAtencionesNiPrioridad() {
        ResumenAreaService.Fuentes f =
                ResumenAreaService.Fuentes.de(
                        usuario(
                                true,
                                "CAM.PARTE.CONSULTAR",
                                "CAM.PARTE.REGISTRAR",
                                "PLA.INCIDENCIA.REGISTRAR",
                                "PLA.PLANIFICACION.CONSULTAR",
                                // Proponer a la IA no basta para leer sus sugerencias.
                                "IA.PROPUESTA.PROPONER"));

        assertThat(f.visibles())
                .containsExactly("actividades", "incidencias", "programacion", "cambios_sector");
        assertThat(f.actividadesPropias()).isTrue();
        assertThat(f.veTodasIncidencias()).isFalse();
        assertThat(f.parametros(3, usuario(true)).getValues())
                .containsEntry("ver_atenciones", false)
                .containsEntry("actividades_propias", true)
                .containsEntry("area", 3L)
                .containsEntry("usuario", 7L);
    }

    @Test
    void unUsuarioInactivoSoloVeLosCambiosDeSector() {
        ResumenAreaService.Fuentes f =
                ResumenAreaService.Fuentes.de(
                        usuario(false, "CAM.PARTE.CONSULTAR", "SER.ATENCION.REGISTRAR"));

        assertThat(f.visibles()).containsExactly("cambios_sector");
    }
}
