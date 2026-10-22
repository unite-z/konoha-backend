package pe.edu.pucp.areasverdes.mod06.comun;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semántica de Python que el back-end Django usaba de forma implícita y que hay que conservar para que la salida
 * sea idéntica.
 *
 * <p>Los registros circulan como en Python: {@code Map<String, Object>} (dict), {@code List<Object>} (list),
 * {@code String}, {@code Long} o {@code BigInteger} (int), {@code Double} (float), {@code Boolean} (bool) y
 * {@code null} (None).
 *
 * <ul>
 *   <li>{@link #eq}: {@code ==} de Python ({@code 1 == 1.0 == True}).</li>
 *   <li>{@link #k}: clave de diccionario o de conjunto equivalente al hash de Python.</li>
 *   <li>{@link #truthy}, {@link #or}: verdad de Python y operador {@code or}.</li>
 *   <li>{@link #str}, {@link #repr}: {@code str()} y {@code repr()}.</li>
 *   <li>{@link #sum}, {@link #add}, {@link #mul}, {@link #truediv}, {@link #round}: aritmética de Python.</li>
 *   <li>{@link #toInt}, {@link #toFloat}, {@link #parseInt}: {@code int()} y {@code float()}.</li>
 *   <li>{@link #compare}: orden de Python para claves de ordenación.</li>
 * </ul>
 */
public final class Py {

    private Py() {}

    /** Error de tipo de Python ({@code TypeError}): en el Django producía un HTTP 500. */
    public static final class TypeError extends RuntimeException {
        public TypeError(String mensaje) { super(mensaje); }
    }

    /** Error de valor de Python ({@code ValueError}): en el Django producía un HTTP 500 si no se capturaba. */
    public static class ValueError extends IllegalArgumentException {
        public ValueError(String mensaje) { super(mensaje); }
    }

    // ------------------------------------------------------------------ números

    public static boolean esNumero(Object o) {
        return o instanceof Long || o instanceof Integer || o instanceof BigInteger || o instanceof Double
            || o instanceof Boolean || o instanceof Short || o instanceof Byte || o instanceof Float;
    }

    /** {@code isinstance(o, int)}: incluye bool. */
    public static boolean esEntero(Object o) {
        return o instanceof Long || o instanceof Integer || o instanceof BigInteger || o instanceof Boolean
            || o instanceof Short || o instanceof Byte;
    }

    public static boolean esFloat(Object o) {
        return o instanceof Double || o instanceof Float;
    }

    /** Entero de Python normalizado: {@code Long} si cabe, si no {@code BigInteger}. */
    public static Object entero(BigInteger valor) {
        return valor.bitLength() < 64 ? (Object) valor.longValue() : valor;
    }

    public static BigInteger aBigInteger(Object o) {
        if (o instanceof BigInteger b) return b;
        if (o instanceof Boolean b) return b ? BigInteger.ONE : BigInteger.ZERO;
        return BigInteger.valueOf(((Number) o).longValue());
    }

    public static double aDouble(Object o) {
        if (o instanceof Boolean b) return b ? 1.0 : 0.0;
        if (o instanceof BigInteger b) return b.doubleValue();
        return ((Number) o).doubleValue();
    }

    /** Normaliza un número de Java al tipo usado aquí para los int de Python. */
    public static Object normalizar(Object o) {
        if (o instanceof Integer || o instanceof Short || o instanceof Byte) return ((Number) o).longValue();
        if (o instanceof Float f) return f.doubleValue();
        if (o instanceof BigInteger b) return entero(b);
        return o;
    }

    private static BigDecimal exacto(Object o) {
        if (esFloat(o)) return new BigDecimal(aDouble(o));
        return new BigDecimal(aBigInteger(o));
    }

    private static boolean noFinito(Object o) {
        return esFloat(o) && !Double.isFinite(aDouble(o));
    }

    // ------------------------------------------------------------------ igualdad y hash

    /** {@code a == b} de Python. */
    public static boolean eq(Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (esNumero(a) && esNumero(b)) {
            if (noFinito(a) || noFinito(b)) {
                return esFloat(a) && esFloat(b) && aDouble(a) == aDouble(b);
            }
            return exacto(a).compareTo(exacto(b)) == 0;
        }
        if (a instanceof String sa) return b instanceof String && sa.equals(b);
        if (a instanceof java.time.ZonedDateTime za) {
            return b instanceof java.time.ZonedDateTime zb && za.toInstant().equals(zb.toInstant());
        }
        if (a instanceof List<?> la) {
            if (!(b instanceof List<?> lb) || la.size() != lb.size()) return false;
            for (int i = 0; i < la.size(); i++) if (!eq(la.get(i), lb.get(i))) return false;
            return true;
        }
        if (a instanceof Map<?, ?> ma) {
            if (!(b instanceof Map<?, ?> mb) || ma.size() != mb.size()) return false;
            for (Map.Entry<?, ?> e : ma.entrySet()) {
                if (!mb.containsKey(e.getKey()) || !eq(e.getValue(), mb.get(e.getKey()))) return false;
            }
            return true;
        }
        return a.equals(b);
    }

    /**
     * Clave equivalente al hash de Python: {@code 1}, {@code 1.0} y {@code True} son la misma clave.
     * Las listas se convierten en listas de claves (equivalen a tuplas).
     */
    public static Object k(Object o) {
        if (o == null) return null;
        if (esNumero(o)) {
            if (esFloat(o)) {
                double d = aDouble(o);
                if (!Double.isFinite(d) || d != Math.rint(d)) return d;
                return entero(new BigDecimal(d).toBigIntegerExact());
            }
            return entero(aBigInteger(o));
        }
        if (o instanceof List<?> l) {
            List<Object> clave = new ArrayList<>(l.size());
            for (Object x : l) clave.add(k(x));
            return clave;
        }
        if (o instanceof Map<?, ?>) throw new TypeError("unhashable type: 'dict'");
        return o;
    }

    /** Tupla de Python como clave (los elementos pueden ser nulos). */
    public static List<Object> tupla(Object... elementos) {
        List<Object> t = new ArrayList<>(elementos.length);
        for (Object e : elementos) t.add(k(e));
        return t;
    }

    /** {@code x in lista} de Python. */
    public static boolean en(Object x, Collection<?> lista) {
        for (Object y : lista) if (eq(x, y)) return true;
        return false;
    }

    // ------------------------------------------------------------------ verdad

    public static boolean truthy(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean b) return b;
        if (esNumero(o)) return esFloat(o) ? aDouble(o) != 0.0 : aBigInteger(o).signum() != 0;
        if (o instanceof String s) return !s.isEmpty();
        if (o instanceof Collection<?> c) return !c.isEmpty();
        if (o instanceof Map<?, ?> m) return !m.isEmpty();
        return true;
    }

    /** {@code a or b} de Python. */
    public static Object or(Object a, Object b) {
        return truthy(a) ? a : b;
    }

    // ------------------------------------------------------------------ texto

    /** {@code type(o).__name__} de Python. */
    public static String nombreTipo(Object o) {
        if (o == null) return "NoneType";
        if (o instanceof Boolean) return "bool";
        if (esEntero(o)) return "int";
        if (esFloat(o)) return "float";
        if (o instanceof String) return "str";
        if (o instanceof List<?>) return "list";
        if (o instanceof Map<?, ?>) return "dict";
        return o.getClass().getSimpleName();
    }

    /** {@code str(o)} de Python. */
    public static String str(Object o) {
        if (o instanceof String s) return s;
        if (o == null || o instanceof Boolean || esNumero(o) || o instanceof List<?> || o instanceof Map<?, ?>) {
            return repr(o);
        }
        if (o instanceof LocalDate d) return d.toString();
        return String.valueOf(o);
    }

    /** {@code repr(o)} de Python. */
    public static String repr(Object o) {
        if (o == null) return "None";
        if (o instanceof Boolean b) return b ? "True" : "False";
        if (esFloat(o)) return reprFloat(aDouble(o));
        if (esEntero(o)) return aBigInteger(o).toString();
        if (o instanceof String s) return reprTexto(s);
        if (o instanceof List<?> l) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(repr(l.get(i)));
            }
            return sb.append(']').toString();
        }
        if (o instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean primero = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primero) sb.append(", ");
                primero = false;
                sb.append(repr(e.getKey())).append(": ").append(repr(e.getValue()));
            }
            return sb.append('}').toString();
        }
        if (o instanceof LocalDate d) return "datetime.date(" + d.getYear() + ", " + d.getMonthValue() + ", " + d.getDayOfMonth() + ")";
        return String.valueOf(o);
    }

    private static String reprTexto(String s) {
        char comilla = s.indexOf('\'') >= 0 && s.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder sb = new StringBuilder().append(comilla);
        s.codePoints().forEach(cp -> {
            if (cp == comilla || cp == '\\') sb.append('\\').appendCodePoint(cp);
            else if (cp == '\n') sb.append("\\n");
            else if (cp == '\r') sb.append("\\r");
            else if (cp == '\t') sb.append("\\t");
            else if (cp < 0x20 || cp == 0x7f) sb.append(String.format("\\x%02x", cp));
            else if (!esImprimible(cp)) {
                if (cp <= 0xff) sb.append(String.format("\\x%02x", cp));
                else if (cp <= 0xffff) sb.append(String.format("\\u%04x", cp));
                else sb.append(String.format("\\U%08x", cp));
            } else sb.appendCodePoint(cp);
        });
        return sb.append(comilla).toString();
    }

    private static boolean esImprimible(int cp) {
        int tipo = Character.getType(cp);
        if (cp == ' ') return true;
        return !(tipo == Character.CONTROL || tipo == Character.FORMAT || tipo == Character.SURROGATE
            || tipo == Character.PRIVATE_USE || tipo == Character.UNASSIGNED || tipo == Character.LINE_SEPARATOR
            || tipo == Character.PARAGRAPH_SEPARATOR || tipo == Character.SPACE_SEPARATOR);
    }

    /** {@code repr(float)} de Python (dígitos más cortos; notación científica si el exponente es < -4 o >= 16). */
    public static String reprFloat(double d) {
        if (Double.isNaN(d)) return "nan";
        if (Double.isInfinite(d)) return d > 0 ? "inf" : "-inf";
        if (d == 0.0) return (1 / d < 0) ? "-0.0" : "0.0";
        BigDecimal corto = new BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
        String digitos = corto.unscaledValue().toString();
        int exponente = digitos.length() - 1 - corto.scale(); // posición decimal del primer dígito
        String signo = d < 0 ? "-" : "";
        if (exponente < -4 || exponente >= 16) {
            String mantisa = digitos.length() == 1 ? digitos : digitos.charAt(0) + "." + digitos.substring(1);
            String exp = String.format("%02d", Math.abs(exponente));
            return signo + mantisa + "e" + (exponente < 0 ? "-" : "+") + exp;
        }
        String texto;
        if (exponente < 0) {
            texto = "0." + "0".repeat(-exponente - 1) + digitos;
        } else if (digitos.length() <= exponente + 1) {
            texto = digitos + "0".repeat(exponente + 1 - digitos.length()) + ".0";
        } else {
            texto = digitos.substring(0, exponente + 1) + "." + digitos.substring(exponente + 1);
        }
        return signo + texto;
    }

    /** {@code str.isspace()} de Python para un carácter. */
    public static boolean esEspacio(int cp) {
        return Character.isWhitespace(cp) || cp == 0x85 || cp == 0xa0 || cp == 0x2007 || cp == 0x202f
            || Character.getType(cp) == Character.SPACE_SEPARATOR;
    }

    /** {@code str.strip()} de Python. */
    public static String strip(String s) {
        int[] cps = s.codePoints().toArray();
        int i = 0, j = cps.length;
        while (i < j && esEspacio(cps[i])) i++;
        while (j > i && esEspacio(cps[j - 1])) j--;
        return new String(cps, i, j - i);
    }

    /** {@code len(str)} de Python (puntos de código). */
    public static int len(String s) {
        return s.codePointCount(0, s.length());
    }

    // ------------------------------------------------------------------ int() y float()

    /**
     * {@code int(texto)} de Python en base 10: espacios alrededor, signo, dígitos Unicode y guiones bajos entre
     * dígitos. Lanza {@link ValueError} si el texto no es un entero.
     */
    public static Object parseInt(String texto) {
        int[] cps = texto.codePoints().toArray();
        int i = 0, j = cps.length;
        while (i < j && esEspacio(cps[i])) i++;
        while (j > i && esEspacio(cps[j - 1])) j--;
        if (i == j) throw new ValueError("invalid literal for int() with base 10: " + repr(texto));
        boolean negativo = false;
        if (cps[i] == '+' || cps[i] == '-') {
            negativo = cps[i] == '-';
            i++;
        }
        StringBuilder digitos = new StringBuilder();
        boolean anteriorDigito = false;
        for (int p = i; p < j; p++) {
            int cp = cps[p];
            if (cp == '_') {
                if (!anteriorDigito || p + 1 >= j) throw new ValueError("invalid literal for int() with base 10: " + repr(texto));
                anteriorDigito = false;
                continue;
            }
            int valor = Character.getType(cp) == Character.DECIMAL_DIGIT_NUMBER ? Character.digit(cp, 10) : -1;
            if (valor < 0) throw new ValueError("invalid literal for int() with base 10: " + repr(texto));
            digitos.append((char) ('0' + valor));
            anteriorDigito = true;
        }
        if (digitos.isEmpty() || !anteriorDigito) throw new ValueError("invalid literal for int() with base 10: " + repr(texto));
        if (digitos.length() > 4300) {
            throw new ValueError("Exceeds the limit (4300 digits) for integer string conversion: value has "
                + digitos.length() + " digits; use sys.set_int_max_str_digits() to increase the limit");
        }
        BigInteger valor = new BigInteger(digitos.toString());
        return entero(negativo ? valor.negate() : valor);
    }

    /** {@code int(o)} de Python (trunca los float; texto según {@link #parseInt}). */
    public static Object toInt(Object o) {
        if (esEntero(o)) return entero(aBigInteger(o));
        if (esFloat(o)) {
            double d = aDouble(o);
            if (Double.isNaN(d)) throw new ValueError("cannot convert float NaN to integer");
            if (Double.isInfinite(d)) throw new Py.OverflowError("cannot convert float infinity to integer");
            return entero(new BigDecimal(d).setScale(0, RoundingMode.DOWN).toBigIntegerExact());
        }
        if (o instanceof String s) return parseInt(s);
        throw new TypeError("int() argument must be a string, a bytes-like object or a real number, not '" + nombreTipo(o) + "'");
    }

    /** {@code OverflowError} de Python. */
    public static final class OverflowError extends RuntimeException {
        public OverflowError(String mensaje) { super(mensaje); }
    }

    /** {@code float(o)} de Python. */
    public static double toFloat(Object o) {
        if (esNumero(o)) {
            if (o instanceof BigInteger b && b.bitLength() > 1024) throw new OverflowError("int too large to convert to float");
            return aDouble(o);
        }
        if (o instanceof String s) {
            String t = strip(s).replace("_", "");
            String bajo = t.toLowerCase();
            switch (bajo) {
                case "nan", "+nan", "-nan" -> { return Double.NaN; }
                case "inf", "+inf", "infinity", "+infinity" -> { return Double.POSITIVE_INFINITY; }
                case "-inf", "-infinity" -> { return Double.NEGATIVE_INFINITY; }
                default -> { }
            }
            if (!t.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?") || strip(s).matches(".*(^|[^0-9])_.*|.*_($|[^0-9]).*")) {
                throw new ValueError("could not convert string to float: " + repr(s));
            }
            return Double.parseDouble(t);
        }
        throw new TypeError("float() argument must be a string or a real number, not '" + nombreTipo(o) + "'");
    }

    // ------------------------------------------------------------------ aritmética

    /** {@code a + b} de Python para números. */
    public static Object add(Object a, Object b) {
        if (esNumero(a) && esNumero(b)) {
            if (esFloat(a) || esFloat(b)) return aDouble(a) + aDouble(b);
            return entero(aBigInteger(a).add(aBigInteger(b)));
        }
        if (a instanceof String sa && b instanceof String sb) return sa + sb;
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            List<Object> r = new ArrayList<>(la);
            r.addAll(lb);
            return r;
        }
        throw new TypeError("unsupported operand type(s) for +: '" + nombreTipo(a) + "' and '" + nombreTipo(b) + "'");
    }

    /** {@code a * b} de Python (números, o texto/lista por entero). */
    public static Object mul(Object a, Object b) {
        if (esNumero(a) && esNumero(b)) {
            if (esFloat(a) || esFloat(b)) return aDouble(a) * aDouble(b);
            return entero(aBigInteger(a).multiply(aBigInteger(b)));
        }
        if (a instanceof String s && esEntero(b)) return s.repeat(Math.max(0, aBigInteger(b).intValue()));
        if (a instanceof List<?> l && esEntero(b)) {
            List<Object> r = new ArrayList<>();
            for (int i = 0; i < aBigInteger(b).intValue(); i++) r.addAll(l);
            return r;
        }
        throw new TypeError("unsupported operand type(s) for *: '" + nombreTipo(a) + "' and '" + nombreTipo(b) + "'");
    }

    /** {@code a / b} de Python (división real, correctamente redondeada). */
    public static double truediv(Object a, Object b) {
        if (!esNumero(a) || !esNumero(b)) {
            throw new TypeError("unsupported operand type(s) for /: '" + nombreTipo(a) + "' and '" + nombreTipo(b) + "'");
        }
        if (esFloat(a) || esFloat(b)) {
            double y = aDouble(b);
            if (y == 0.0) throw new ArithmeticException("float division by zero");
            return aDouble(a) / y;
        }
        BigInteger x = aBigInteger(a), y = aBigInteger(b);
        if (y.signum() == 0) throw new ArithmeticException("division by zero");
        if (x.bitLength() <= 53 && y.bitLength() <= 53) return x.doubleValue() / y.doubleValue();
        return new BigDecimal(x).divide(new BigDecimal(y), new java.math.MathContext(40, RoundingMode.HALF_EVEN)).doubleValue();
    }

    /**
     * {@code sum(valores)} de Python 3.12 o posterior: enteros exactos y, desde el primer float, suma compensada de
     * Neumaier (igual que CPython).
     */
    public static Object sum(Iterable<?> valores) {
        java.util.Iterator<?> it = valores.iterator();
        Object resultado = 0L;
        // Fase entera: suma exacta hasta el primer float.
        while (it.hasNext() && !esFloat(resultado)) resultado = add(resultado, it.next());
        if (!esFloat(resultado)) return resultado;
        // Fase float: suma compensada de Neumaier; los int pequeños se suman sin compensación.
        double f = aDouble(resultado);
        double c = 0.0;
        while (it.hasNext()) {
            Object item = it.next();
            if (esFloat(item)) {
                double x = aDouble(item);
                double t = f + x;
                if (Math.abs(f) >= Math.abs(x)) c += (f - t) + x;
                else c += (x - t) + f;
                f = t;
            } else if (esEntero(item) && aBigInteger(item).bitLength() < 64) {
                f += aDouble(item);
            } else {
                // Fase genérica (sin compensación), como CPython.
                if (c != 0.0 && Double.isFinite(c)) f += c;
                Object r = add(f, item);
                while (it.hasNext()) r = add(r, it.next());
                return r;
            }
        }
        if (c != 0.0 && Double.isFinite(c)) f += c;
        return f;
    }

    /** {@code round(x, n)} de Python para float: redondeo al par sobre el valor binario exacto. */
    public static double round(double x, int n) {
        if (!Double.isFinite(x) || x == 0.0) return x;
        double r = new BigDecimal(x).setScale(n, RoundingMode.HALF_EVEN).doubleValue();
        return r == 0.0 && x < 0 ? -0.0 : r;
    }

    /** {@code int(round(x))} de Python: redondeo al par. */
    public static long roundEntero(double x) {
        return (long) Math.rint(x);
    }

    // ------------------------------------------------------------------ orden

    /** Comparación de Python para claves de ordenación (números, texto, fechas, instantes y tuplas). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(Object a, Object b) {
        if (esNumero(a) && esNumero(b)) {
            if (noFinito(a) || noFinito(b)) return Double.compare(aDouble(a), aDouble(b));
            return exacto(a).compareTo(exacto(b));
        }
        if (a instanceof String sa && b instanceof String sb) return compararTexto(sa, sb);
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            for (int i = 0; i < Math.min(la.size(), lb.size()); i++) {
                if (!eq(la.get(i), lb.get(i))) return compare(la.get(i), lb.get(i));
            }
            return Integer.compare(la.size(), lb.size());
        }
        if (a instanceof java.time.ZonedDateTime za && b instanceof java.time.ZonedDateTime zb) {
            return za.toInstant().compareTo(zb.toInstant());
        }
        if (a != null && b != null && a.getClass() == b.getClass() && a instanceof Comparable ca) return ca.compareTo(b);
        throw new TypeError("'<' not supported between instances of '" + nombreTipo(a) + "' and '" + nombreTipo(b) + "'");
    }

    /** Orden de texto de Python: por punto de código. */
    public static int compararTexto(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i), cb = b.codePointAt(j);
            if (ca != cb) return Integer.compare(ca, cb);
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    // ------------------------------------------------------------------ copias y diccionarios

    /** {@code copy.deepcopy} de los valores JSON. */
    @SuppressWarnings("unchecked")
    public static <T> T copia(T valor) {
        if (valor instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            m.forEach((clave, v) -> r.put((String) clave, copia(v)));
            return (T) r;
        }
        if (valor instanceof List<?> l) {
            List<Object> r = new ArrayList<>(l.size());
            for (Object v : l) r.add(copia(v));
            return (T) r;
        }
        return valor;
    }

    /** {@code dict.get(clave)} sobre un registro: lanza {@link TypeError} si el registro no es un dict. */
    public static Object get(Object registro, String clave) {
        if (registro instanceof Map<?, ?> m) return m.get(clave);
        throw new TypeError("'" + nombreTipo(registro) + "' object has no attribute 'get'");
    }

    /** {@code registro[clave]}: lanza {@link KeyError} si falta la clave. */
    public static Object item(Object registro, String clave) {
        if (registro instanceof Map<?, ?> m) {
            if (!m.containsKey(clave)) throw new KeyError(clave);
            return m.get(clave);
        }
        throw new TypeError("'" + nombreTipo(registro) + "' object is not subscriptable");
    }

    /** {@code KeyError} de Python. */
    public static final class KeyError extends RuntimeException {
        public KeyError(String clave) { super("'" + clave + "'"); }
    }

    /** Diccionario ordenado con pares clave, valor. */
    public static Map<String, Object> dict(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) m.put((String) pares[i], pares[i + 1]);
        return m;
    }

    /** {@code {r[campo]: r for r in registros}}: el último registro gana y la clave conserva su posición. */
    public static Map<Object, Map<String, Object>> indexar(List<Map<String, Object>> registros, String campo) {
        Map<Object, Map<String, Object>> r = new LinkedHashMap<>();
        for (Map<String, Object> registro : registros) {
            r.put(k(item(registro, campo)), registro);
        }
        return r;
    }
}
