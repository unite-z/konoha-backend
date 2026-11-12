package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod02.servicios.ReglasCampanaPoda;
import pe.edu.pucp.areasverdes.mod02.servicios.ReglasCampanaPoda.Capacidad;
import pe.edu.pucp.areasverdes.mod02.servicios.ReglasCampanaPoda.EstadoCampana;

class ReglasCampanaPodaTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 12, 20);

    @Test
    void losDiasCuentanElInicioYElFin() {
        assertThat(ReglasCampanaPoda.dias(INICIO, INICIO)).isEqualTo(1);
        assertThat(ReglasCampanaPoda.dias(INICIO, LocalDate.of(2026, 12, 29))).isEqualTo(10);
    }

    @Test
    void losDiasCruzanElCambioDeAnio() {
        assertThat(ReglasCampanaPoda.dias(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 6)))
                .isEqualTo(10);
    }

    @Test
    void laListaPorEncimaDeLaCapacidadSeMarcaComoExcedida() {
        Capacidad capacidad =
                ReglasCampanaPoda.capacidad(INICIO, LocalDate.of(2026, 12, 29), 12, 220);

        assertThat(capacidad.dias()).isEqualTo(10);
        assertThat(capacidad.estimada()).isEqualTo(120);
        assertThat(capacidad.requeridoPorDia()).isEqualByComparingTo(new BigDecimal("22.00"));
        assertThat(capacidad.excedida()).isTrue();
    }

    @Test
    void laListaIgualALaCapacidadNoSeExcede() {
        assertThat(
                        ReglasCampanaPoda.capacidad(INICIO, LocalDate.of(2026, 12, 29), 12, 120)
                                .excedida())
                .isFalse();
    }

    @Test
    void sinEjemplaresElRequeridoEsCero() {
        Capacidad capacidad = ReglasCampanaPoda.capacidad(INICIO, INICIO, 12, 0);

        assertThat(capacidad.requeridoPorDia()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(capacidad.excedida()).isFalse();
    }

    @Test
    void elAvanceEsSobreElTotalDeLaLista() {
        assertThat(ReglasCampanaPoda.avance(180, 1, 220).porcentaje())
                .isEqualByComparingTo(new BigDecimal("81.8"));
    }

    @Test
    void sinEjemplaresElAvanceEsCero() {
        assertThat(ReglasCampanaPoda.avance(0, 0, 0).porcentaje())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void soloBorradorYPlanificadaSonEditables() {
        assertThat(EstadoCampana.Borrador.editable()).isTrue();
        assertThat(EstadoCampana.Planificada.editable()).isTrue();
        assertThat(EstadoCampana.EnEjecucion.editable()).isFalse();
        assertThat(EstadoCampana.Finalizada.editable()).isFalse();
    }
}
