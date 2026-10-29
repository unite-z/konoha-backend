package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.util.List;

/**
 * Un mes anterior de la comparación (CU-MOD05-029, paso 4). Un mes sin movimientos va con {@code
 * sinDatos} y listas vacías, nunca con totales en 0 (Guía de Estilo Web 5.7).
 *
 * @param mes AAAA-MM
 */
public record ComparacionMes(
        String mes,
        boolean sinDatos,
        List<TotalMes> consumido,
        List<TotalMes> propagado,
        List<TotalMes> trasladado) {}
