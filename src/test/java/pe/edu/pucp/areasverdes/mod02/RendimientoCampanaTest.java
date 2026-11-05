package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod02.servicios.RendimientoCampana;
import pe.edu.pucp.areasverdes.mod02.servicios.RendimientoCampana.Registro;
import pe.edu.pucp.areasverdes.mod02.servicios.RendimientoCampana.Resultado;

class RendimientoCampanaTest {

    private static final LocalDate INICIO = LocalDate.of(2025, 12, 20);
    private static final int UMBRAL = 18;

    /** {@code porDia} ejemplares nuevos en cada uno de {@code dias} días seguidos. */
    private static List<Registro> uniformes(int dias, int porDia) {
        List<Registro> registros = new ArrayList<>();
        long ejemplar = 1;
        for (int dia = 0; dia < dias; dia++) {
            for (int i = 0; i < porDia; i++) {
                registros.add(new Registro(ejemplar++, INICIO.plusDays(dia)));
            }
        }
        return registros;
    }

    @Test
    void cientoOchentaEjemplaresEnQuinceDiasSonDocePorDia() {
        Resultado resultado = RendimientoCampana.calcular(uniformes(15, 12), UMBRAL);

        assertThat(resultado.verificados()).isEqualTo(180);
        assertThat(resultado.diasConEjecucion()).isEqualTo(15);
        assertThat(resultado.promedio()).isEqualByComparingTo("12");
        assertThat(resultado.destacado()).isFalse();
    }

    @Test
    void laEvolucionVaPorTramosDeCincoDiasTrabajados() {
        Resultado resultado = RendimientoCampana.calcular(uniformes(15, 12), UMBRAL);

        assertThat(resultado.tramos()).hasSize(3);
        assertThat(resultado.tramos().get(0).diaDesde()).isEqualTo(1);
        assertThat(resultado.tramos().get(0).diaHasta()).isEqualTo(5);
        assertThat(resultado.tramos().get(0).atendidos()).isEqualTo(60);
        assertThat(resultado.tramos().get(0).promedio()).isEqualByComparingTo("12");
        assertThat(resultado.tramos().get(2).fechaHasta()).isEqualTo(INICIO.plusDays(14));
    }

    @Test
    void sinEjecucionNoHayPromedioNiSeDivideEntreCero() {
        Resultado resultado = RendimientoCampana.calcular(List.of(), UMBRAL);

        assertThat(resultado.verificados()).isZero();
        assertThat(resultado.diasConEjecucion()).isZero();
        assertThat(resultado.promedio()).isNull();
        assertThat(resultado.destacado()).isFalse();
        assertThat(resultado.tramos()).isEmpty();
    }

    @Test
    void unEjemplarRepetidoCuentaUnaVezEnSuPrimeraFecha() {
        Resultado resultado =
                RendimientoCampana.calcular(
                        List.of(
                                new Registro(1, INICIO.plusDays(1)),
                                new Registro(1, INICIO),
                                new Registro(1, INICIO),
                                new Registro(2, INICIO)),
                        UMBRAL);

        assertThat(resultado.verificados()).isEqualTo(2);
        assertThat(resultado.diasConEjecucion()).isEqualTo(2);
        assertThat(resultado.promedio()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(resultado.tramos().get(0).atendidos()).isEqualTo(2);
    }

    @Test
    void elDenominadorNoCuentaLosDiasSinPoda() {
        Resultado resultado =
                RendimientoCampana.calcular(
                        List.of(new Registro(1, INICIO), new Registro(2, INICIO.plusDays(9))),
                        UMBRAL);

        assertThat(resultado.diasConEjecucion()).isEqualTo(2);
        assertThat(resultado.promedio()).isEqualByComparingTo("1");
    }

    @Test
    void alcanzarElUmbralDestacaElResultado() {
        assertThat(RendimientoCampana.calcular(uniformes(5, 18), UMBRAL).destacado()).isTrue();
        assertThat(RendimientoCampana.calcular(uniformes(5, 17), UMBRAL).destacado()).isFalse();
    }

    @Test
    void elPromedioSeRedondeaADosDecimales() {
        Resultado resultado =
                RendimientoCampana.calcular(
                        List.of(
                                new Registro(1, INICIO),
                                new Registro(2, INICIO),
                                new Registro(3, INICIO.plusDays(1)),
                                new Registro(4, INICIO.plusDays(1)),
                                new Registro(5, INICIO.plusDays(2))),
                        UMBRAL);

        assertThat(resultado.promedio()).isEqualByComparingTo(new BigDecimal("1.67"));
    }
}
