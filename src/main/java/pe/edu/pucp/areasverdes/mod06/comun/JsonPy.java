package pe.edu.pucp.areasverdes.mod06.comun;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON con el comportamiento del módulo {@code json} de CPython, que usaba el Django:
 *
 * <ul>
 *   <li>{@link #loads}: {@code json.loads} (mismos mensajes de error, posición en puntos de código, enteros sin
 *   límite, {@code NaN}/{@code Infinity}, claves repetidas: gana la última y conserva su posición).</li>
 *   <li>{@link #dumpsArchivo}: {@code json.dump(..., indent=2, ensure_ascii=False)}, usado en los JSON de
 *   {@code mock-data}.</li>
 *   <li>{@link #dumpsRespuesta}: el {@code JSONRenderer} de DRF (compacto, sin escapar tildes, sin NaN).</li>
 * </ul>
 */
public final class JsonPy {

    private JsonPy() {}

    /** {@code json.JSONDecodeError}: mensaje {@code "<msg>: line L column C (char P)"}. */
    public static final class JsonDecodeError extends Py.ValueError {
        public JsonDecodeError(String mensaje, int[] doc, int pos) {
            super(formatear(mensaje, doc, pos));
        }

        private static String formatear(String mensaje, int[] doc, int pos) {
            int linea = 1, ultimoSalto = -1;
            for (int i = 0; i < pos && i < doc.length; i++) {
                if (doc[i] == '\n') {
                    linea++;
                    ultimoSalto = i;
                }
            }
            return mensaje + ": line " + linea + " column " + (pos - ultimoSalto) + " (char " + pos + ")";
        }
    }

    /** Qué hacer con {@code NaN}, {@code Infinity} y {@code -Infinity}. */
    public enum Constantes {
        /** {@code json.loads} por defecto: se aceptan como float. */
        PYTHON,
        /** {@code parse_constant} estricto de DRF: {@code ValueError}. */
        ESTRICTAS
    }

    // =================================================================== lectura

    public static Object loads(String texto) {
        return loads(texto, Constantes.PYTHON);
    }

    public static Object loads(String texto, Constantes constantes) {
        int[] s = texto.codePoints().toArray();
        if (s.length > 0 && s[0] == 0xFEFF) {
            throw new JsonDecodeError("Unexpected UTF-8 BOM (decode using utf-8-sig)", s, 0);
        }
        Lector lector = new Lector(s, constantes);
        int idx = lector.espacios(0);
        Object[] r = lector.valorRaiz(idx);
        int fin = lector.espacios((Integer) r[1]);
        if (fin != s.length) throw new JsonDecodeError("Extra data", s, fin);
        return r[0];
    }

    /** Señal interna equivalente a {@code StopIteration(idx)} del escáner de CPython. */
    private static final class Parada extends RuntimeException {
        final int idx;
        Parada(int idx) {
            super(null, null, false, false);
            this.idx = idx;
        }
    }

    private static final class Lector {
        final int[] s;
        final int n;
        final Constantes constantes;

        Lector(int[] s, Constantes constantes) {
            this.s = s;
            this.n = s.length;
            this.constantes = constantes;
        }

        int espacios(int i) {
            while (i < n && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++;
            return i;
        }

        Object[] valorRaiz(int idx) {
            try {
                return valor(idx);
            } catch (Parada p) {
                throw new JsonDecodeError("Expecting value", s, p.idx);
            }
        }

        boolean empieza(int idx, String palabra) {
            if (idx + palabra.length() > n) return false;
            for (int i = 0; i < palabra.length(); i++) if (s[idx + i] != palabra.charAt(i)) return false;
            return true;
        }

        Object[] valor(int idx) {
            if (idx >= n) throw new Parada(idx);
            int c = s[idx];
            switch (c) {
                case '"': return texto(idx + 1);
                case '{': return objeto(idx + 1);
                case '[': return arreglo(idx + 1);
                case 'n': if (empieza(idx, "null")) return new Object[] {null, idx + 4}; break;
                case 't': if (empieza(idx, "true")) return new Object[] {Boolean.TRUE, idx + 4}; break;
                case 'f': if (empieza(idx, "false")) return new Object[] {Boolean.FALSE, idx + 5}; break;
                case 'N': if (empieza(idx, "NaN")) return new Object[] {constante("NaN"), idx + 3}; break;
                case 'I': if (empieza(idx, "Infinity")) return new Object[] {constante("Infinity"), idx + 8}; break;
                case '-': if (empieza(idx, "-Infinity")) return new Object[] {constante("-Infinity"), idx + 9}; break;
                default: break;
            }
            return numero(idx);
        }

        Object constante(String nombre) {
            if (constantes == Constantes.ESTRICTAS) {
                throw new Py.ValueError("Out of range float values are not JSON compliant: " + Py.repr(nombre));
            }
            return switch (nombre) {
                case "NaN" -> Double.NaN;
                case "Infinity" -> Double.POSITIVE_INFINITY;
                default -> Double.NEGATIVE_INFINITY;
            };
        }

        boolean digito(int i) {
            return i < n && s[i] >= '0' && s[i] <= '9';
        }

        Object[] numero(int inicio) {
            int idx = inicio;
            if (idx < n && s[idx] == '-') {
                idx++;
                if (idx >= n) throw new Parada(inicio);
            }
            if (idx < n && s[idx] >= '1' && s[idx] <= '9') {
                idx++;
                while (digito(idx)) idx++;
            } else if (idx < n && s[idx] == '0') {
                idx++;
            } else {
                throw new Parada(inicio);
            }
            boolean esFloat = false;
            if (idx < n - 1 && s[idx] == '.' && s[idx + 1] >= '0' && s[idx + 1] <= '9') {
                esFloat = true;
                idx += 2;
                while (digito(idx)) idx++;
            }
            if (idx < n - 1 && (s[idx] == 'e' || s[idx] == 'E')) {
                int e = idx;
                idx++;
                if (idx < n - 1 && (s[idx] == '-' || s[idx] == '+')) idx++;
                while (digito(idx)) idx++;
                if (s[idx - 1] >= '0' && s[idx - 1] <= '9') esFloat = true;
                else idx = e;
            }
            String literal = new String(s, inicio, idx - inicio);
            if (esFloat) return new Object[] {Double.parseDouble(literal), idx};
            return new Object[] {Py.parseInt(literal), idx};
        }

        Object[] texto(int fin) {
            int inicio = fin - 1;
            StringBuilder sb = new StringBuilder();
            while (true) {
                int c = 0;
                int sig = fin;
                for (; sig < n; sig++) {
                    c = s[sig];
                    if (c == '"' || c == '\\') break;
                    if (c <= 0x1f) throw new JsonDecodeError("Invalid control character at", s, sig);
                }
                if (sig >= n) throw new JsonDecodeError("Unterminated string starting at", s, inicio);
                for (int i = fin; i < sig; i++) sb.appendCodePoint(s[i]);
                sig++;
                if (c == '"') {
                    fin = sig;
                    break;
                }
                if (sig == n) throw new JsonDecodeError("Unterminated string starting at", s, inicio);
                c = s[sig];
                if (c != 'u') {
                    fin = sig + 1;
                    int r = switch (c) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case '/' -> '/';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> -1;
                    };
                    if (r < 0) throw new JsonDecodeError("Invalid \\escape", s, fin - 2);
                    sb.appendCodePoint(r);
                } else {
                    sig++;
                    fin = sig + 4;
                    if (fin >= n) throw new JsonDecodeError("Invalid \\uXXXX escape", s, sig - 1);
                    int u = hex4(sig, fin);
                    sig = fin;
                    if (u >= 0xD800 && u <= 0xDBFF && fin + 6 < n && s[sig] == '\\' && s[sig + 1] == 'u') {
                        int u2 = hex4(sig + 2, fin + 6);
                        if (u2 >= 0xDC00 && u2 <= 0xDFFF) {
                            u = Character.toCodePoint((char) u, (char) u2);
                            fin += 6;
                        }
                    }
                    sb.appendCodePoint(u);
                }
            }
            return new Object[] {sb.toString(), fin};
        }

        int hex4(int desde, int fin) {
            int v = 0;
            for (int i = desde; i < fin; i++) {
                int d = Character.digit(s[i], 16);
                if (d < 0 || s[i] > 'f') throw new JsonDecodeError("Invalid \\uXXXX escape", s, fin - 5);
                v = (v << 4) | d;
            }
            return v;
        }

        Object[] objeto(int idx) {
            Map<String, Object> r = new LinkedHashMap<>();
            idx = espacios(idx);
            if (idx >= n || s[idx] != '}') {
                while (true) {
                    if (idx >= n || s[idx] != '"') {
                        throw new JsonDecodeError("Expecting property name enclosed in double quotes", s, idx);
                    }
                    Object[] clave = texto(idx + 1);
                    idx = espacios((Integer) clave[1]);
                    if (idx >= n || s[idx] != ':') throw new JsonDecodeError("Expecting ':' delimiter", s, idx);
                    idx = espacios(idx + 1);
                    Object[] v = valor(idx);
                    r.put((String) clave[0], v[0]);
                    idx = espacios((Integer) v[1]);
                    if (idx < n && s[idx] == '}') break;
                    if (idx >= n || s[idx] != ',') throw new JsonDecodeError("Expecting ',' delimiter", s, idx);
                    int coma = idx;
                    idx = espacios(idx + 1);
                    if (idx < n && s[idx] == '}') {
                        throw new JsonDecodeError("Illegal trailing comma before end of object", s, coma);
                    }
                }
            }
            return new Object[] {r, idx + 1};
        }

        Object[] arreglo(int idx) {
            List<Object> r = new ArrayList<>();
            idx = espacios(idx);
            if (idx >= n || s[idx] != ']') {
                while (true) {
                    Object[] v = valor(idx);
                    r.add(v[0]);
                    idx = espacios((Integer) v[1]);
                    if (idx < n && s[idx] == ']') break;
                    if (idx >= n || s[idx] != ',') throw new JsonDecodeError("Expecting ',' delimiter", s, idx);
                    int coma = idx;
                    idx = espacios(idx + 1);
                    if (idx < n && s[idx] == ']') {
                        throw new JsonDecodeError("Illegal trailing comma before end of array", s, coma);
                    }
                }
            }
            return new Object[] {r, idx + 1};
        }
    }

    // =================================================================== escritura

    /** {@code json.dump(valor, f, indent=2, ensure_ascii=False)}. */
    public static String dumpsArchivo(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor, 2, 0, true);
        return sb.toString();
    }

    /** Salida del {@code JSONRenderer} de DRF: compacta, UTF-8 sin escapar y sin NaN. */
    public static String dumpsRespuesta(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor, -1, 0, false);
        return sb.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    private static void escribir(StringBuilder sb, Object v, int indent, int nivel, boolean permitirNan) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (Py.esFloat(v)) {
            double d = Py.aDouble(v);
            if (!Double.isFinite(d)) {
                if (!permitirNan) throw new Py.ValueError("Out of range float values are not JSON compliant: " + Py.reprFloat(d));
                sb.append(Double.isNaN(d) ? "NaN" : d > 0 ? "Infinity" : "-Infinity");
            } else {
                sb.append(Py.reprFloat(d));
            }
        } else if (Py.esEntero(v)) {
            sb.append(Py.aBigInteger(v).toString());
        } else if (v instanceof String s) {
            texto(sb, s);
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                salto(sb, indent, nivel + 1);
                texto(sb, clave(e.getKey()));
                sb.append(indent >= 0 ? ": " : ":");
                escribir(sb, e.getValue(), indent, nivel + 1, permitirNan);
            }
            salto(sb, indent, nivel);
            sb.append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                salto(sb, indent, nivel + 1);
                escribir(sb, l.get(i), indent, nivel + 1, permitirNan);
            }
            salto(sb, indent, nivel);
            sb.append(']');
        } else if (v instanceof BigInteger b) {
            sb.append(b);
        } else {
            throw new Py.TypeError("Object of type " + v.getClass().getSimpleName() + " is not JSON serializable");
        }
    }

    private static String clave(Object k) {
        if (k instanceof String s) return s;
        if (k == null) return "null";
        if (k instanceof Boolean b) return b ? "true" : "false";
        return Py.repr(k);
    }

    private static void salto(StringBuilder sb, int indent, int nivel) {
        if (indent < 0) return;
        sb.append('\n').append(" ".repeat(indent * nivel));
    }

    private static void texto(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
