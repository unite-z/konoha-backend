package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.util.List;

/**
 * Valor de un indicador del panel. Sin datos: {@code valor} nulo, {@code desglose} vacío y el
 * motivo en {@code mensajeSinDatos}; nunca un {@code "0"} inventado (Guía de Estilo Web 5.7).
 *
 * @param valor texto; {@code null} si {@code sinDatos}
 */
public record ResultadoIndicador(
        String clave,
        Indicador indicador,
        String valor,
        boolean sinDatos,
        String mensajeSinDatos,
        List<ElementoDesglose> desglose,
        List<String> notas) {

    /** Sin datos, con su motivo. */
    static ResultadoIndicador sinDatos(IndicadorPanel i, String motivo, List<String> notas) {
        return new ResultadoIndicador(
                i.clave(), i.definicion(), null, true, motivo, List.of(), notas);
    }
}
