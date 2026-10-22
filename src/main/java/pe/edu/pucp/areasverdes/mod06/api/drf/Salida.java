package pe.edu.pucp.areasverdes.mod06.api.drf;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import pe.edu.pucp.areasverdes.mod06.comun.Py;

/**
 * Serializadores de salida con la semántica de DRF que usaba el Django ({@code api/serializers.py}).
 *
 * <p>Por cada campo declarado, en el orden de declaración (primero los heredados):
 * <ul>
 *   <li>valor {@code null} → {@code null};</li>
 *   <li>clave ausente → {@code null} si el campo admite nulo, se omite si no es obligatorio, error (HTTP 500) en
 *   otro caso;</li>
 *   <li>conversión por tipo: {@code IntegerField → int()}, {@code FloatField → float()}, {@code CharField → str()},
 *   {@code BooleanField}, {@code DateField}, {@code DateTimeField}, {@code ChoiceField}, {@code JSONField},
 *   {@code DictField}, {@code ListField}, serializadores anidados y {@code SerializerMethodField}.</li>
 * </ul>
 * Las claves no declaradas no se devuelven.
 *
 * <p>También genera los metadatos de {@code OPTIONS} ({@code SimpleMetadata} de DRF).
 */
public final class Salida {

    private Salida() {}

    /** Error de DRF por una clave obligatoria ausente (HTTP 500 en el Django). */
    public static final class ClaveAusente extends RuntimeException {
        public ClaveAusente(String mensaje) {
            super(mensaje);
        }
    }

    // ===================================================================== campos

    /** Campo de un serializador de salida. */
    public abstract static class Campo {
        String nombre = "";
        boolean requerido = true;
        boolean admiteNulo = false;
        boolean soloLectura = false;
        String ayuda;

        /** Representación de un valor no nulo. */
        abstract Object representar(Object valor);

        /** Tipo para los metadatos de OPTIONS. */
        abstract String tipo();

        public Campo nulo() {
            this.admiteNulo = true;
            return this;
        }

        public Campo opcional() {
            this.requerido = false;
            return this;
        }

        public Campo ayuda(String texto) {
            this.ayuda = texto;
            return this;
        }

        Campo nombrar(String n) {
            this.nombre = n;
            return this;
        }

        /** {@code SimpleMetadata.get_field_info}. */
        Map<String, Object> info() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", tipo());
            m.put("required", requerido);
            m.put("read_only", soloLectura);
            String etiqueta = etiqueta(nombre);
            if (!etiqueta.isEmpty()) m.put("label", etiqueta);
            if (ayuda != null && !ayuda.isEmpty()) m.put("help_text", ayuda);
            extraInfo(m);
            return m;
        }

        void extraInfo(Map<String, Object> m) {}
    }

    /** {@code field_name.replace('_', ' ').capitalize()}. */
    static String etiqueta(String nombre) {
        if (nombre.isEmpty()) return "";
        String t = nombre.replace('_', ' ');
        return t.substring(0, 1).toUpperCase() + t.substring(1).toLowerCase();
    }

    static final class Entero extends Campo {
        Object representar(Object v) { return Py.toInt(v); }
        String tipo() { return "integer"; }
    }

    static final class Decimal extends Campo {
        Object representar(Object v) { return Py.toFloat(v); }
        String tipo() { return "float"; }
    }

    static final class Texto extends Campo {
        Object representar(Object v) { return Py.str(v); }
        String tipo() { return "string"; }
    }

    static final class Booleano extends Campo {
        private static final Set<Object> VERDADEROS = Set.of("t", "T", "y", "Y", "yes", "Yes", "YES", "true", "True", "TRUE",
            "on", "On", "ON", "1");
        private static final Set<Object> FALSOS = Set.of("f", "F", "n", "N", "no", "No", "NO", "false", "False", "FALSE",
            "off", "Off", "OFF", "0");
        private static final Set<Object> NULOS = Set.of("null", "Null", "NULL", "");

        Object representar(Object v) {
            if (v instanceof String s) {
                if (VERDADEROS.contains(s)) return true;
                if (FALSOS.contains(s)) return false;
                if (NULOS.contains(s) && admiteNulo) return null;
                return Py.truthy(s);
            }
            if (Py.esNumero(v)) {
                if (Py.eq(v, 1L)) return true;
                if (Py.eq(v, 0L)) return false;
            }
            return Py.truthy(v);
        }

        String tipo() { return "boolean"; }
    }

    static final class Fecha extends Campo {
        Object representar(Object v) {
            if (!Py.truthy(v)) return null;
            if (v instanceof String) return v;
            if (v instanceof LocalDate d) return d.toString();
            throw new Py.TypeError("'" + Py.nombreTipo(v) + "' object has no attribute 'isoformat'");
        }

        String tipo() { return "date"; }
    }

    static final class FechaHora extends Campo {
        Object representar(Object v) {
            if (!Py.truthy(v)) return null;
            if (v instanceof String) return v;
            if (v instanceof java.time.ZonedDateTime z) {
                String iso = pe.edu.pucp.areasverdes.mod06.comun.Fechas.isoformat(
                    z.withZoneSameInstant(pe.edu.pucp.areasverdes.mod06.comun.Fechas.LIMA));
                return iso.endsWith("+00:00") ? iso.substring(0, iso.length() - 6) + "Z" : iso;
            }
            throw new Py.TypeError("'" + Py.nombreTipo(v) + "' object has no attribute 'isoformat'");
        }

        String tipo() { return "datetime"; }
    }

    static final class Opcion extends Campo {
        final List<String> opciones;

        Opcion(List<String> opciones) { this.opciones = opciones; }

        Object representar(Object v) {
            if ("".equals(v)) return v;
            String s = Py.str(v);
            return opciones.contains(s) ? s : v;
        }

        String tipo() { return "choice"; }

        @Override
        void extraInfo(Map<String, Object> m) {
            if (soloLectura) return;
            List<Object> lista = new ArrayList<>();
            for (String o : opciones) lista.add(Py.dict("value", o, "display_name", o));
            m.put("choices", lista);
        }
    }

    static final class Json extends Campo {
        Object representar(Object v) { return v; }
        String tipo() { return "field"; }
    }

    /** {@code SectorFiltroField}: entero o {@code sin_sector}, devuelto tal cual. */
    static final class Tal extends Campo {
        Object representar(Object v) { return v; }
        String tipo() { return "field"; }
    }

    static final class Dicc extends Campo {
        final Campo hijo;

        Dicc(Campo hijo) { this.hijo = hijo; }

        Object representar(Object v) {
            if (!(v instanceof Map<?, ?> m)) throw new Py.TypeError("'" + Py.nombreTipo(v) + "' object has no attribute 'items'");
            Map<String, Object> r = new LinkedHashMap<>();
            m.forEach((k, x) -> r.put(Py.str(k), x == null ? null : hijo.representar(x)));
            return r;
        }

        String tipo() { return "nested object"; }

        @Override
        void extraInfo(Map<String, Object> m) { m.put("child", hijo.info()); }
    }

    static final class Lista extends Campo {
        final Campo hijo;

        Lista(Campo hijo) { this.hijo = hijo; }

        Object representar(Object v) {
            List<Object> r = new ArrayList<>();
            for (Object x : iterar(v)) r.add(x == null ? null : hijo.representar(x));
            return r;
        }

        String tipo() { return "list"; }

        @Override
        void extraInfo(Map<String, Object> m) { m.put("child", hijo.info()); }
    }

    /** Iteración de Python: listas, texto (caracteres) y dict (claves). */
    static List<Object> iterar(Object v) {
        if (v instanceof List<?> l) return new ArrayList<>(l);
        if (v instanceof Map<?, ?> m) return new ArrayList<>(m.keySet());
        if (v instanceof String s) {
            List<Object> r = new ArrayList<>();
            s.codePoints().forEach(cp -> r.add(new String(Character.toChars(cp))));
            return r;
        }
        throw new Py.TypeError("'" + Py.nombreTipo(v) + "' object is not iterable");
    }

    static final class Anidado extends Campo {
        final Serializador serializador;

        Anidado(Serializador s) { this.serializador = s; }

        Object representar(Object v) { return serializador.representar(v); }

        String tipo() { return "nested object"; }

        @Override
        void extraInfo(Map<String, Object> m) { m.put("children", serializador.info()); }
    }

    /** {@code ListSerializer} ({@code many=True}). */
    static final class Muchos extends Campo {
        final Serializador serializador;

        Muchos(Serializador s) { this.serializador = s; }

        Object representar(Object v) {
            List<Object> r = new ArrayList<>();
            for (Object x : iterar(v)) r.add(serializador.representar(x));
            return r;
        }

        String tipo() { return "field"; }

        @Override
        void extraInfo(Map<String, Object> m) {
            Campo hijo = new Anidado(serializador);
            m.put("child", hijo.info());
        }
    }

    static final class Metodo extends Campo {
        final Function<Object, Object> funcion;

        Metodo(Function<Object, Object> funcion) {
            this.funcion = funcion;
            this.soloLectura = true;
            this.requerido = false;
        }

        Object representar(Object v) { return funcion.apply(v); }

        String tipo() { return "field"; }
    }

    // ===================================================================== fábricas

    public static Campo entero() { return new Entero(); }
    public static Campo decimal() { return new Decimal(); }
    public static Campo texto() { return new Texto(); }
    public static Campo booleano() { return new Booleano(); }
    public static Campo fecha() { return new Fecha(); }
    public static Campo fechaHora() { return new FechaHora(); }
    public static Campo opcion(List<String> opciones) { return new Opcion(opciones); }
    public static Campo json() { return new Json(); }
    public static Campo talCual() { return new Tal(); }
    public static Campo dicc(Campo hijo) { return new Dicc(hijo); }
    public static Campo lista(Campo hijo) { return new Lista(hijo); }
    public static Campo anidado(Serializador s) { return new Anidado(s); }
    public static Campo muchos(Serializador s) { return new Muchos(s); }
    public static Campo metodo(Function<Object, Object> f) { return new Metodo(f); }

    // ===================================================================== serializador

    /** Serializador de salida: campos en orden de declaración. */
    public static final class Serializador {
        private final List<Campo> campos = new ArrayList<>();

        /** Hereda los campos de {@code base} (van primero, como en DRF). */
        public Serializador hereda(Serializador base) {
            campos.addAll(base.campos);
            return this;
        }

        public Serializador campo(String nombre, Campo campo) {
            campos.add(campo.nombrar(nombre));
            return this;
        }

        /** {@code Serializer.to_representation(instancia)}. */
        public Map<String, Object> representar(Object instancia) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Campo c : campos) {
                Object valor;
                if (c instanceof Metodo) {
                    valor = instancia;
                } else if (instancia instanceof Map<?, ?> m && m.containsKey(c.nombre)) {
                    valor = m.get(c.nombre);
                } else {
                    if (c.admiteNulo) {
                        r.put(c.nombre, null);
                        continue;
                    }
                    if (!c.requerido) continue;
                    throw new ClaveAusente("Got KeyError when attempting to get a value for field `" + c.nombre + "`");
                }
                r.put(c.nombre, valor == null ? null : c.representar(valor));
            }
            return r;
        }

        /** {@code many=True}. */
        public List<Object> representarMuchos(List<?> instancias) {
            List<Object> r = new ArrayList<>();
            for (Object x : instancias) r.add(representar(x));
            return r;
        }

        /** {@code SimpleMetadata.get_serializer_info}. */
        public Map<String, Object> info() {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Campo c : campos) r.put(c.nombre, c.info());
            return r;
        }
    }

    public static Serializador serializador() {
        return new Serializador();
    }
}
