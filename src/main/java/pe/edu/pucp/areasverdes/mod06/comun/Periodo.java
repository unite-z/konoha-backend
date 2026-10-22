package pe.edu.pucp.areasverdes.mod06.comun;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ParametroInvalido;

/**
 * Periodo de consulta de los reportes del MOD-06: rango {@code [desde, hasta]}, ambos extremos incluidos.
 *
 * <p>CU-MOD06-001: «el actor selecciona un rango de fechas». Todos los endpoints de reporte reciben
 * {@code desde} y {@code hasta} (ambos obligatorios, formato AAAA-MM-DD, ambos extremos incluidos).
 */
public record Periodo(LocalDate desde, LocalDate hasta) {

    public static final String MENSAJE_FALTA_DESDE = "Indica la fecha de inicio del periodo (desde)";
    public static final String MENSAJE_FALTA_HASTA = "Indica la fecha de fin del periodo (hasta)";
    public static final String MENSAJE_FORMATO_FECHA = "Usa el formato AAAA-MM-DD";
    public static final String MENSAJE_ORDEN_FECHAS = "La fecha de fin debe ser igual o posterior a la fecha de inicio";

    public Periodo {
        if (hasta.isBefore(desde)) throw new ParametroInvalido(MENSAJE_ORDEN_FECHAS);
    }

    /** {@code true} si {@code fecha} está entre {@code desde} y {@code hasta} (ambos incluidos). Una fecha nula no pertenece. */
    public boolean contiene(LocalDate fecha) {
        if (fecha == null) return false;
        return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }

    /** Representación {@code {desde, hasta}} usada en las respuestas. */
    public Map<String, Object> comoDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("desde", desde);
        m.put("hasta", hasta);
        return m;
    }
}
