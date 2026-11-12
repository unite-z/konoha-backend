package pe.edu.pucp.areasverdes.mod05;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos del informe final según el formato estándar del tipo de servicio (RF-SER-009, CU-MOD05-006
 * pasos 2 a 4 y A1). Valida lo que envía el cliente y lo normaliza para guardarlo en {@code
 * mod05_reporteproveedor.datos_formato}.
 *
 * <p>Reglas, por tipo de dato del campo (o del subcampo, en los campos JSON):
 *
 * <ul>
 *   <li>{@code Texto} y {@code Texto largo}: texto, sin espacios a los extremos.
 *   <li>{@code Entero} y {@code Decimal}: número o texto numérico, nunca negativo. El decimal se
 *       guarda como texto («120.50»), igual que el resto de decimales de MOD-05.
 *   <li>{@code Booleano}: {@code true} o {@code false}.
 *   <li>{@code Fecha}: {@code AAAA-MM-DD}.
 *   <li>{@code JSON}: lista de filas; cada fila es un objeto con los subcampos del formato. Las
 *       filas sin ningún valor se descartan.
 * </ul>
 *
 * <p>Un valor nulo, un texto vacío o una lista sin filas cuentan como «sin dato». Si falta un campo
 * obligatorio, o llega un campo que el formato no define, el envío se rechaza entero (400) con un
 * mensaje que nombra los campos.
 *
 * <p>Clase pura, sin Spring ni base, para probarla en segundos.
 */
public final class DatosFormatoInforme {

    public static final String TEXTO = "Texto";
    public static final String TEXTO_LARGO = "Texto largo";
    public static final String ENTERO = "Entero";
    public static final String DECIMAL = "Decimal";
    public static final String BOOLEANO = "Booleano";
    public static final String FECHA = "Fecha";
    public static final String JSON = "JSON";

    static final int LARGO_TEXTO = 500;
    static final int LARGO_TEXTO_LARGO = 10_000;
    static final int MAXIMO_FILAS = 200;
    static final int MAXIMO_DECIMALES = 4;
    static final BigDecimal MAXIMO_NUMERO = new BigDecimal("999999999999");

    /** Subcampo de un campo JSON ({@code subcampos} de {@code mod05_campo_formato_reporte}). */
    public record Subcampo(String nombre, String tipoDato, String descripcion) {}

    /** Campo del formato, en su orden. {@code subcampos} solo en los campos JSON. */
    public record Campo(
            String nombreCampo,
            String tipoDato,
            boolean obligatorio,
            int orden,
            List<Subcampo> subcampos) {
        public Campo {
            subcampos = subcampos == null ? null : List.copyOf(subcampos);
        }
    }

    /** Formato estándar vigente de un tipo de servicio. */
    public record Formato(
            long id,
            int version,
            long tipoServicioId,
            String tipoServicioNombre,
            List<Campo> campos) {
        public Formato {
            campos = List.copyOf(campos);
        }
    }

    private DatosFormatoInforme() {}

    /**
     * Valida {@code datos} contra los campos del formato y devuelve los valores normalizados, en el
     * orden del formato y sin los campos vacíos.
     *
     * @throws ApiError 400 si {@code datos} no es un objeto, trae campos ajenos al formato, algún
     *     valor no corresponde a su tipo o falta algún obligatorio.
     */
    public static Map<String, Object> normalizar(List<Campo> campos, JsonNode datos) {
        if (datos == null || !datos.isObject())
            throw ApiError.invalid("Los datos del informe final deben ser un objeto JSON");
        var porNombre = new LinkedHashMap<String, Campo>();
        campos.forEach(c -> porNombre.put(c.nombreCampo(), c));
        var ajenos = new ArrayList<String>();
        datos.fieldNames()
                .forEachRemaining(
                        nombre -> {
                            if (!porNombre.containsKey(nombre)) ajenos.add("«" + nombre + "»");
                        });
        if (!ajenos.isEmpty())
            throw ApiError.invalid(
                    "El formato estándar vigente no define "
                            + (ajenos.size() == 1 ? "el campo " : "los campos ")
                            + String.join(", ", ajenos)
                            + ". Vuelve a abrir el formulario para usar el formato actual");

        var resultado = new LinkedHashMap<String, Object>();
        var faltantes = new ArrayList<String>();
        var invalidos = new ArrayList<String>();
        for (Campo campo : porNombre.values()) {
            JsonNode valor = datos.get(campo.nombreCampo());
            try {
                Object normalizado =
                        JSON.equals(campo.tipoDato())
                                ? filas(campo, valor)
                                : valor(campo.tipoDato(), valor, campo.nombreCampo());
                if (normalizado != null) resultado.put(campo.nombreCampo(), normalizado);
                else if (campo.obligatorio()) faltantes.add(campo.nombreCampo());
            } catch (IllegalArgumentException e) {
                invalidos.add(e.getMessage());
            }
        }
        if (!invalidos.isEmpty())
            throw ApiError.invalid(
                    "Revisa los datos del informe final: " + String.join("; ", invalidos));
        if (!faltantes.isEmpty())
            throw ApiError.invalid(
                    "Faltan datos obligatorios del informe final: " + String.join(", ", faltantes));
        return resultado;
    }

    /** Filas de un campo JSON: descarta las vacías y valida cada subcampo. */
    private static List<Map<String, Object>> filas(Campo campo, JsonNode valor) {
        if (vacio(valor)) return null;
        if (!valor.isArray())
            throw new IllegalArgumentException(campo.nombreCampo() + " debe ser una lista");
        if (valor.size() > MAXIMO_FILAS)
            throw new IllegalArgumentException(
                    campo.nombreCampo() + " admite hasta " + MAXIMO_FILAS + " filas");
        List<Subcampo> subcampos = campo.subcampos() == null ? List.of() : campo.subcampos();
        var nombres = subcampos.stream().map(Subcampo::nombre).toList();
        var resultado = new ArrayList<Map<String, Object>>();
        int numero = 0;
        for (JsonNode fila : valor) {
            numero++;
            String donde = campo.nombreCampo() + ", fila " + numero;
            if (!fila.isObject()) throw new IllegalArgumentException(donde + ": no es un objeto");
            fila.fieldNames()
                    .forEachRemaining(
                            nombre -> {
                                if (!nombres.contains(nombre))
                                    throw new IllegalArgumentException(
                                            donde + ": «" + nombre + "» no es un dato de la lista");
                            });
            var normalizada = new LinkedHashMap<String, Object>();
            for (Subcampo sub : subcampos) {
                Object v =
                        valor(sub.tipoDato(), fila.get(sub.nombre()), donde + ", " + sub.nombre());
                if (v != null) normalizada.put(sub.nombre(), v);
            }
            if (!normalizada.isEmpty()) resultado.add(normalizada);
        }
        return resultado.isEmpty() ? null : resultado;
    }

    /** Valor simple normalizado, o {@code null} si no hay dato. */
    private static Object valor(String tipo, JsonNode valor, String donde) {
        if (vacio(valor)) return null;
        return switch (tipo) {
            case TEXTO -> texto(valor, donde, LARGO_TEXTO);
            case TEXTO_LARGO -> texto(valor, donde, LARGO_TEXTO_LARGO);
            case ENTERO -> {
                BigDecimal n = numero(valor, donde);
                if (n.stripTrailingZeros().scale() > 0)
                    throw new IllegalArgumentException(donde + " debe ser un número entero");
                yield n.longValueExact();
            }
            case DECIMAL -> {
                BigDecimal n = numero(valor, donde);
                if (Math.max(0, n.stripTrailingZeros().scale()) > MAXIMO_DECIMALES)
                    throw new IllegalArgumentException(
                            donde + " admite hasta " + MAXIMO_DECIMALES + " decimales");
                yield n.toPlainString();
            }
            case BOOLEANO -> {
                if (!valor.isBoolean())
                    throw new IllegalArgumentException(donde + " debe ser sí o no");
                yield valor.booleanValue();
            }
            case FECHA -> {
                String texto = texto(valor, donde, 10);
                try {
                    yield LocalDate.parse(texto).toString();
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(
                            donde + " debe ser una fecha válida (AAAA-MM-DD)");
                }
            }
            // Un campo JSON dentro de una fila no existe en el formato: se guarda como texto.
            default -> texto(valor, donde, LARGO_TEXTO);
        };
    }

    private static String texto(JsonNode valor, String donde, int largo) {
        if (!valor.isTextual() && !valor.isNumber())
            throw new IllegalArgumentException(donde + " debe ser un texto");
        String texto = valor.asText().strip();
        if (texto.length() > largo)
            throw new IllegalArgumentException(donde + " admite hasta " + largo + " caracteres");
        return texto;
    }

    private static BigDecimal numero(JsonNode valor, String donde) {
        BigDecimal n;
        try {
            if (valor.isNumber()) n = valor.decimalValue();
            else if (valor.isTextual())
                n = new BigDecimal(valor.asText().strip().replace(',', '.'));
            else throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(donde + " debe ser un número");
        }
        if (n.signum() < 0) throw new IllegalArgumentException(donde + " no puede ser negativo");
        if (n.compareTo(MAXIMO_NUMERO) > 0)
            throw new IllegalArgumentException(donde + " es demasiado grande");
        return n;
    }

    private static boolean vacio(JsonNode valor) {
        return valor == null
                || valor.isNull()
                || valor.isMissingNode()
                || (valor.isTextual() && valor.asText().isBlank())
                || (valor.isArray() && valor.isEmpty());
    }
}
