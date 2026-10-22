package pe.edu.pucp.areasverdes.mod06.api.drf;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.Py;

/**
 * Serializadores de entrada con la semántica de DRF que usaba el Django (parte de entrada de
 * {@code api/serializers.py}): mismas reglas de vacío y nulo, mismas conversiones y mismos mensajes en español.
 *
 * <p>Los datos llegan como JSON ya leído ({@code Map}, {@code List}, texto, números...) o como un
 * {@link MultiValor} (parámetros de consulta o formulario, el {@code QueryDict} de Django).
 */
public final class Entrada {

    private Entrada() {}

    // ---------------------------------------------------------------- mensajes por defecto de DRF (es)

    public static final String MSG_REQUERIDO = "Este campo es requerido.";
    public static final String MSG_NULO = "Este campo no puede ser nulo.";
    public static final String MSG_EN_BLANCO = "Este campo no puede estar en blanco.";
    public static final String MSG_CADENA_LARGA = "Cadena demasiado larga.";
    public static final String MSG_CADENA_INVALIDA = "No es una cadena válida.";
    public static final String MSG_LARGO_MAXIMO = "Asegúrese de que este campo no tenga más de %d caracteres.";
    public static final String MSG_CARACTERES_NULOS = "Los caracteres nulos no están permitidos.";
    public static final String MSG_SUSTITUTOS = "Surrogate characters are not allowed: U+%X.";
    public static final String MSG_NO_ES_DICCIONARIO = "Se esperaba un diccionario de elementos en vez del tipo \"%s\".";
    public static final String MSG_DATOS_INVALIDOS = "Datos inválidos. Se esperaba un diccionario pero es un %s.";
    public static final String MSG_SIN_DATOS = "No data provided";
    public static final String NON_FIELD_ERRORS = "non_field_errors";

    /** Valor ausente ({@code empty} de DRF). */
    public static final Object VACIO = new Object() {
        @Override
        public String toString() {
            return "<empty>";
        }
    };

    /** Campo que se omite del resultado ({@code SkipField}). */
    private static final class Omitir extends RuntimeException {
        Omitir() {
            super(null, null, false, false);
        }
    }

    /** {@code ValidationError} de DRF: {@code detalle} es una lista de mensajes o un diccionario anidado. */
    public static final class ErrorValidacion extends RuntimeException {
        private final Object detalle;
        private final String codigo;

        public ErrorValidacion(Object detalle, String codigo) {
            super(String.valueOf(detalle), null, false, false);
            this.detalle = detalle;
            this.codigo = codigo;
        }

        public ErrorValidacion(Object detalle) {
            this(detalle, null);
        }

        public Object detalle() {
            return detalle;
        }

        public String codigo() {
            return codigo;
        }
    }

    private static ErrorValidacion fallo(String mensaje, String codigo) {
        return new ErrorValidacion(new ArrayList<>(List.of(mensaje)), codigo);
    }

    /** {@code QueryDict} / {@code MultiValueDict} de Django: varios valores por clave; {@code get} devuelve el último. */
    public static final class MultiValor {
        private final Map<String, List<Object>> valores = new LinkedHashMap<>();

        public void agregar(String clave, Object valor) {
            valores.computeIfAbsent(clave, k -> new ArrayList<>()).add(valor);
        }

        public void fijarLista(String clave, List<Object> lista) {
            valores.put(clave, new ArrayList<>(lista));
        }

        public boolean contiene(String clave) {
            return valores.containsKey(clave);
        }

        /** Último valor (o {@code []} si la lista está vacía, como Django). */
        public Object get(String clave) {
            List<Object> l = valores.get(clave);
            if (l == null) return null;
            return l.isEmpty() ? List.of() : l.get(l.size() - 1);
        }

        public List<Object> lista(String clave) {
            return valores.getOrDefault(clave, List.of());
        }

        public java.util.Set<String> claves() {
            return valores.keySet();
        }

        public int tamano() {
            return valores.size();
        }
    }

    // ===================================================================== campos

    /** Campo de entrada ({@code serializers.Field}). */
    public abstract static class Campo {
        String nombre = "";
        boolean requerido = true;
        boolean admiteNulo = false;
        final Map<String, String> mensajes = new LinkedHashMap<>();

        Campo() {
            mensajes.put("required", MSG_REQUERIDO);
            mensajes.put("null", MSG_NULO);
        }

        public Campo opcional() {
            requerido = false;
            return this;
        }

        public Campo nulo() {
            admiteNulo = true;
            return this;
        }

        public Campo mensaje(String codigo, String texto) {
            mensajes.put(codigo, texto);
            return this;
        }

        ErrorValidacion fallar(String codigo) {
            return fallo(mensajes.get(codigo), codigo);
        }

        boolean admiteVacioTexto() {
            return false;
        }

        /** {@code Field.get_value}. */
        Object obtenerValor(Object datos) {
            if (datos instanceof MultiValor mv) {
                if (!mv.contiene(nombre)) return VACIO;
                Object ret = mv.get(nombre);
                if ("".equals(ret) && admiteNulo) return admiteVacioTexto() ? "" : null;
                if ("".equals(ret) && !requerido) return admiteVacioTexto() ? "" : VACIO;
                return ret;
            }
            Map<?, ?> m = (Map<?, ?>) datos;
            return m.containsKey(nombre) ? m.get(nombre) : VACIO;
        }

        /** {@code Field.run_validation}. */
        Object validar(Object dato) {
            if (dato == VACIO) {
                if (requerido) throw fallar("required");
                throw new Omitir();
            }
            if (dato == null) {
                if (!admiteNulo) throw fallar("null");
                return null;
            }
            Object valor = aInterno(dato);
            List<String> errores = new ArrayList<>();
            validadores(valor, errores::add);
            if (!errores.isEmpty()) throw new ErrorValidacion(errores);
            return valor;
        }

        /** {@code to_internal_value}. */
        abstract Object aInterno(Object dato);

        /** {@code run_validators}: se acumulan todos los mensajes. */
        void validadores(Object valor, Consumer<String> error) {}
    }

    /** Fecha {@code AAAA-MM-DD} obligatoria; vacía cuenta como no indicada ({@code FechaPeriodoField}). */
    public static final class FechaPeriodo extends Campo {
        public FechaPeriodo(String mensajeRequerido, String mensajeFormato) {
            mensajes.put("required", mensajeRequerido);
            mensajes.put("null", mensajeRequerido);
            mensajes.put("invalid", mensajeFormato);
            mensajes.put("datetime", mensajeFormato);
        }

        @Override
        Object aInterno(Object dato) {
            if ("".equals(dato) || dato == null) throw fallar("required");
            if (dato instanceof LocalDate d) return d;
            if (dato instanceof String s) {
                LocalDate fecha = Fechas.strptimeAaaaMmDd(s);
                if (fecha != null) return fecha;
            }
            throw fallar("invalid");
        }
    }

    /** {@code IntegerField} (con {@code min_value} opcional). */
    public static final class Entero extends Campo {
        private static final Pattern RE_DECIMAL = Pattern.compile("\\.0*\\s*\\z", Pattern.UNICODE_CHARACTER_CLASS);
        private Object minimo;

        public Entero() {
            mensajes.put("invalid", "Introduzca un número entero válido.");
            mensajes.put("max_string_length", MSG_CADENA_LARGA);
            mensajes.put("min_value", "Asegúrese de que este valor es mayor o igual a %s.");
        }

        public Entero minimo(long valor) {
            this.minimo = valor;
            return this;
        }

        @Override
        Object aInterno(Object dato) {
            if (dato instanceof String s && Py.len(s) > 1000) throw fallar("max_string_length");
            try {
                return Py.parseInt(RE_DECIMAL.matcher(Py.str(dato)).replaceAll(""));
            } catch (Py.ValueError e) {
                throw fallar("invalid");
            }
        }

        @Override
        void validadores(Object valor, Consumer<String> error) {
            if (minimo != null && Py.compare(valor, minimo) < 0) error.accept(String.format(mensajes.get("min_value"), Py.str(minimo)));
        }
    }

    /** Entero ({@code SectorMantenimiento.id}) o el valor especial {@code sin_sector} ({@code SectorFiltroField}). */
    public static final class SectorFiltro extends Campo {
        private final String sinSector;

        public SectorFiltro(String sinSector) {
            this.sinSector = sinSector;
            mensajes.put("invalid", "Usa el id del sector (número entero) o '" + sinSector + "'");
        }

        @Override
        Object aInterno(Object dato) {
            if (sinSector.equals(dato)) return sinSector;
            Object valor;
            try {
                valor = Py.parseInt(Py.str(dato));
            } catch (Py.ValueError e) {
                throw fallar("invalid");
            }
            if (Py.compare(valor, 1L) < 0) throw fallar("invalid");
            return valor;
        }
    }

    /** {@code ChoiceField}. */
    public static final class Opcion extends Campo {
        private final List<String> opciones;

        public Opcion(List<String> opciones) {
            this.opciones = opciones;
            mensajes.put("invalid_choice", "\"%s\" no es una elección válida.");
        }

        @Override
        Object aInterno(Object dato) {
            String s = Py.str(dato);
            if (opciones.contains(s)) return s;
            throw fallar("invalid_choice");
        }
    }

    /** {@code JSONField} ({@code valor_final}). */
    public static final class Json extends Campo {
        @Override
        Object aInterno(Object dato) {
            if (dato instanceof Double d && !Double.isFinite(d)) {
                throw fallo("Valor JSON no válido.", "invalid");
            }
            return dato;
        }
    }

    /** {@code CharField}. */
    public static final class Texto extends Campo {
        private boolean admiteVacio = false;
        private Integer largoMaximo;

        public Texto() {
            mensajes.put("invalid", MSG_CADENA_INVALIDA);
            mensajes.put("blank", MSG_EN_BLANCO);
        }

        public Texto vacioPermitido() {
            admiteVacio = true;
            return this;
        }

        public Texto largoMaximo(int n) {
            largoMaximo = n;
            return this;
        }

        @Override
        boolean admiteVacioTexto() {
            return admiteVacio;
        }

        @Override
        Object validar(Object dato) {
            if ("".equals(dato) || (dato != VACIO && Py.strip(Py.str(dato)).isEmpty())) {
                if (!admiteVacio) throw fallar("blank");
                return "";
            }
            return super.validar(dato);
        }

        @Override
        Object aInterno(Object dato) {
            if (dato instanceof Boolean || !(dato instanceof String || Py.esNumero(dato))) throw fallar("invalid");
            return Py.strip(Py.str(dato));
        }

        @Override
        void validadores(Object valor, Consumer<String> error) {
            String s = (String) valor;
            if (largoMaximo != null && Py.len(s) > largoMaximo) error.accept(String.format(MSG_LARGO_MAXIMO, largoMaximo));
            if (s.indexOf('\u0000') >= 0) error.accept(MSG_CARACTERES_NULOS);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (Character.isSurrogate(c)) {
                    boolean par = Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1));
                    if (!par) {
                        error.accept(String.format(MSG_SUSTITUTOS, (int) c));
                        break;
                    }
                    i++;
                }
            }
        }
    }

    /** {@code DictField(child=<serializador>)}. */
    public static final class Dicc extends Campo {
        private final Serializador hijo;
        private boolean admiteVacio = true;

        public Dicc(Serializador hijo) {
            this.hijo = hijo;
            mensajes.put("not_a_dict", MSG_NO_ES_DICCIONARIO);
            mensajes.put("empty", "Este diccionario no puede estar vacío.");
        }

        public Dicc noVacio() {
            admiteVacio = false;
            return this;
        }

        @Override
        Object obtenerValor(Object datos) {
            if (datos instanceof MultiValor mv) return parseHtmlDict(mv, nombre);
            return super.obtenerValor(datos);
        }

        @Override
        Object aInterno(Object dato) {
            if (dato instanceof MultiValor mv) dato = parseHtmlDict(mv, "");
            Map<String, Object> entradas = new LinkedHashMap<>();
            if (dato instanceof MultiValor mv) {
                for (String k : mv.claves()) entradas.put(k, mv.get(k));
            } else if (dato instanceof Map<?, ?> m) {
                m.forEach((k, v) -> entradas.put(Py.str(k), v));
            } else {
                throw fallo(String.format(mensajes.get("not_a_dict"), Py.nombreTipo(dato)), "not_a_dict");
            }
            if (!admiteVacio && entradas.isEmpty()) throw fallar("empty");
            Map<String, Object> resultado = new LinkedHashMap<>();
            Map<String, Object> errores = new LinkedHashMap<>();
            entradas.forEach((k, v) -> {
                try {
                    resultado.put(k, hijo.validarAnidado(v));
                } catch (ErrorValidacion e) {
                    errores.put(k, e.detalle());
                }
            });
            if (!errores.isEmpty()) throw new ErrorValidacion(errores);
            return resultado;
        }
    }

    /** {@code html.parse_html_dict}. */
    static MultiValor parseHtmlDict(MultiValor datos, String prefijo) {
        MultiValor r = new MultiValor();
        Pattern patron = Pattern.compile("^" + Pattern.quote(prefijo) + "\\.(.+)$", Pattern.DOTALL);
        for (String campo : datos.claves()) {
            var m = patron.matcher(campo);
            if (!m.matches()) continue;
            r.fijarLista(m.group(1), datos.lista(campo));
        }
        return r;
    }

    // ===================================================================== serializador

    /** Serializador de entrada ({@code serializers.Serializer}). */
    public static final class Serializador {
        private final List<Campo> campos = new ArrayList<>();
        private java.util.function.UnaryOperator<Map<String, Object>> validacion = m -> m;

        public Serializador hereda(Serializador base) {
            campos.addAll(base.campos);
            validacion = base.validacion;
            return this;
        }

        public Serializador campo(String nombre, Campo campo) {
            campo.nombre = nombre;
            campos.add(campo);
            return this;
        }

        /** {@code Serializer.validate(attrs)}. */
        public Serializador validacion(java.util.function.UnaryOperator<Map<String, Object>> v) {
            this.validacion = v;
            return this;
        }

        /** {@code Serializer.to_internal_value}. */
        Map<String, Object> aInterno(Object datos) {
            if (!(datos instanceof Map<?, ?>) && !(datos instanceof MultiValor)) {
                throw new ErrorValidacion(Py.dict(NON_FIELD_ERRORS,
                    new ArrayList<>(List.of(String.format(MSG_DATOS_INVALIDOS, Py.nombreTipo(datos))))), "invalid");
            }
            Map<String, Object> resultado = new LinkedHashMap<>();
            Map<String, Object> errores = new LinkedHashMap<>();
            for (Campo c : campos) {
                Object primitivo = c.obtenerValor(datos);
                try {
                    resultado.put(c.nombre, c.validar(primitivo));
                } catch (ErrorValidacion e) {
                    errores.put(c.nombre, e.detalle());
                } catch (Omitir e) {
                    // SkipField
                }
            }
            if (!errores.isEmpty()) throw new ErrorValidacion(errores);
            return resultado;
        }

        /** {@code Serializer.run_validation} de un serializador anidado. */
        Map<String, Object> validarAnidado(Object datos) {
            if (datos == null) throw fallo(MSG_NULO, "null");
            return validacion.apply(aInterno(datos));
        }

        /**
         * {@code serializer.is_valid(raise_exception=True)} sobre los datos de la petición: devuelve
         * {@code validated_data} o lanza {@link ErrorValidacion} con el diccionario de errores.
         */
        public Map<String, Object> validarRaiz(Object datos) {
            try {
                return validarAnidado(datos);
            } catch (ErrorValidacion e) {
                if (e.detalle() instanceof List<?> lista && lista.size() == 1 && "null".equals(e.codigo())) {
                    throw new ErrorValidacion(Py.dict(NON_FIELD_ERRORS, new ArrayList<>(List.of(MSG_SIN_DATOS))));
                }
                throw e;
            }
        }
    }

    public static Serializador serializador() {
        return new Serializador();
    }
}
