package pe.edu.pucp.areasverdes.mod06.gestion.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Validación de los parámetros de los reportes de gestión (RF-REP-001, RF-REP-005 y RF-REP-007) con
 * mensajes que dicen qué hacer. Los controladores la llaman antes del servicio. Los mensajes del
 * periodo son los de {@link Periodo}, comunes a todos los reportes del MOD-06.
 */
public final class ParametrosGestion {

    /** Patrón de mes AAAA-MM: el mismo CHECK que mod03_programacion_servicio.ventana_mes (V4). */
    static final Pattern MES = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");

    static final String MENSAJE_FORMATO_MES = "Usa el formato AAAA-MM";

    private ParametrosGestion() {}

    /** Periodo con desde ≤ hasta; si no, 400 periodo-invalido con el mensaje en el campo hasta. */
    public static RangoFechas periodo(LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new ErrorGestion(
                    400,
                    "periodo-invalido",
                    Periodo.MENSAJE_ORDEN_FECHAS,
                    Map.of("hasta", List.of(Periodo.MENSAJE_ORDEN_FECHAS)));
        }
        return new RangoFechas(desde, hasta);
    }

    /** Mes AAAA-MM; si no, 400 validacion {mes: ["Usa el formato AAAA-MM"]}. */
    public static YearMonth mes(String texto) {
        if (texto == null || !MES.matcher(texto).matches()) {
            throw ErrorGestion.validacion("mes", MENSAJE_FORMATO_MES);
        }
        return YearMonth.parse(texto);
    }

    /**
     * {@code null} pasa; si no, tiene que estar en la lista blanca: 400 validacion {campo: ["Usa
     * uno de estos valores: a, b, c"]}.
     */
    public static String unoDe(String campo, String valor, List<String> permitidos) {
        if (valor == null || permitidos.contains(valor)) {
            return valor;
        }
        throw ErrorGestion.validacion(
                campo, "Usa uno de estos valores: " + String.join(", ", permitidos));
    }
}
