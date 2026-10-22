package pe.edu.pucp.areasverdes.mod06.api.drf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.permisos.Permisos;
import pe.edu.pucp.areasverdes.mod06.permisos.ProveedorUsuario;
import pe.edu.pucp.areasverdes.mod06.permisos.Usuario;

/**
 * Ciclo de una petición como en una {@code APIView} de DRF (más el {@code CommonMiddleware} de Django), para que las
 * respuestas sean idénticas a las del Django:
 *
 * <ol>
 *   <li>negociación de contenido: {@code ?format=} (404 si no es {@code json} ni {@code api}) y cabecera
 *   {@code Accept} (406);</li>
 *   <li>permisos por rol (403);</li>
 *   <li>método: {@code HEAD} usa el de {@code GET}, {@code OPTIONS} devuelve los metadatos y el resto, 405;</li>
 *   <li>cabeceras {@code Allow} y {@code Vary: Accept} en todas las respuestas de la vista.</li>
 * </ol>
 *
 * <p>También lee el cuerpo como {@code request.data} (JSON, formulario o multipart; 415 si el tipo no se admite;
 * 400 si el JSON no se puede leer), los parámetros de consulta como un {@code QueryDict} y responde la redirección
 * 301 de las rutas sin barra final ({@code APPEND_SLASH}).
 */
@Component
public class DespachadorDrf {

    /** Atributo de la petición con la cabecera {@code Allow} de la vista (lo usa el manejador de excepciones). */
    public static final String ATRIBUTO_ALLOW = "mod06.drf.allow";

    public static final String TIPO_JSON = "application/json";
    public static final String TIPO_HTML = "text/html; charset=utf-8";

    private static final List<String> METODOS_HTTP = List.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    /**
     * Configuración de una vista.
     *
     * @param nombre          {@code get_view_name()} (metadatos de OPTIONS)
     * @param metodos         métodos permitidos en el orden de DRF (cabecera {@code Allow})
     * @param roles           roles permitidos ({@code PermisoPorRol})
     * @param serializadorPost serializador de las acciones POST en OPTIONS ({@code GenericAPIView}), o {@code null}
     */
    public record Vista(String nombre, List<String> metodos, Set<String> roles, Salida.Serializador serializadorPost) {
        public String allow() {
            return String.join(", ", metodos);
        }
    }

    /** Respuesta de un manejador: código HTTP y datos ya serializados. */
    public record Respuesta(int estado, Object datos) {
        public static Respuesta ok(Object datos) {
            return new Respuesta(200, datos);
        }
    }

    private final Permisos permisos;
    private final ProveedorUsuario proveedorUsuario;

    public DespachadorDrf(Permisos permisos, ProveedorUsuario proveedorUsuario) {
        this.permisos = permisos;
        this.proveedorUsuario = proveedorUsuario;
    }

    /** Usuario de la petición ({@code request.user}). */
    public Usuario usuario(HttpServletRequest peticion) {
        return proveedorUsuario.usuario(peticion);
    }

    /**
     * {@code APIView.dispatch}: negociación, permisos y método. {@code manejadores} se indexa por método en
     * mayúsculas ({@code GET}, {@code POST}).
     */
    public ResponseEntity<byte[]> despachar(HttpServletRequest peticion, Vista vista, Map<String, Supplier<Respuesta>> manejadores) {
        peticion.setAttribute(ATRIBUTO_ALLOW, vista.allow());
        negociar(peticion);
        Usuario usuario = usuario(peticion);
        if (!permisos.tienePermiso(usuario, vista.roles())) {
            throw new ExcepcionApi(403, Permisos.MENSAJE_ROL_SIN_ACCESO);
        }
        String metodo = peticion.getMethod();
        String bajo = metodo.toLowerCase(Locale.ROOT);
        if (!METODOS_HTTP.contains(bajo)) throw ExcepcionApi.metodoNoPermitido(metodo);
        if (bajo.equals("options")) return json(200, metadatos(vista), vista.allow());
        String clave = bajo.equals("head") ? "GET" : bajo.toUpperCase(Locale.ROOT);
        Supplier<Respuesta> manejador = manejadores.get(clave);
        if (manejador == null) throw ExcepcionApi.metodoNoPermitido(metodo);
        Respuesta r = manejador.get();
        return json(r.estado(), r.datos(), vista.allow());
    }

    /** {@code SimpleMetadata.determine_metadata}. */
    private static Map<String, Object> metadatos(Vista vista) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", vista.nombre());
        m.put("description", "");
        m.put("renders", List.of("application/json", "text/html"));
        m.put("parses", List.of("application/json", "application/x-www-form-urlencoded", "multipart/form-data"));
        if (vista.serializadorPost() != null && vista.metodos().contains("POST")) {
            m.put("actions", Py.dict("POST", vista.serializadorPost().info()));
        }
        return m;
    }

    // ================================================================== respuestas

    /** Respuesta JSON del {@code JSONRenderer} de DRF con las cabeceras de la vista. */
    public static ResponseEntity<byte[]> json(int estado, Object datos, String allow) {
        HttpHeaders h = cabecerasComunes();
        h.set(HttpHeaders.CONTENT_TYPE, TIPO_JSON);
        if (allow != null) h.set(HttpHeaders.ALLOW, allow);
        h.set(HttpHeaders.VARY, "Accept");
        return new ResponseEntity<>(JsonPy.dumpsRespuesta(datos).getBytes(StandardCharsets.UTF_8), h, estado);
    }

    /** Cabeceras del {@code SecurityMiddleware} de Django. */
    public static HttpHeaders cabecerasComunes() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "same-origin");
        h.set("Cross-Origin-Opener-Policy", "same-origin");
        return h;
    }

    public static ResponseEntity<byte[]> html(int estado, String cuerpo) {
        HttpHeaders h = cabecerasComunes();
        h.set(HttpHeaders.CONTENT_TYPE, TIPO_HTML);
        return new ResponseEntity<>(cuerpo.getBytes(StandardCharsets.UTF_8), h, estado);
    }

    /** Página 404 de Django ({@code page_not_found} sin plantilla). */
    public static final String PAGINA_404 = "\n<!doctype html>\n<html lang=\"en\">\n<head>\n  <title>Not Found</title>\n</head>\n"
        + "<body>\n  <h1>Not Found</h1><p>The requested resource was not found on this server.</p>\n</body>\n</html>\n";

    /** Página 500 de Django ({@code server_error} sin plantilla). */
    public static final String PAGINA_500 = "\n<!doctype html>\n<html lang=\"en\">\n<head>\n  <title>Server Error (500)</title>\n</head>\n"
        + "<body>\n  <h1>Server Error (500)</h1><p></p>\n</body>\n</html>\n";

    /**
     * Ruta sin barra final cuya versión con barra existe ({@code CommonMiddleware} con {@code APPEND_SLASH}):
     * 301 a la ruta con barra, conservando la consulta. Como el Django en modo {@code DEBUG} (el del {@code .env}),
     * los métodos POST, PUT y PATCH responden 500 en vez de redirigir, porque se perdería el cuerpo.
     */
    public static ResponseEntity<byte[]> sinBarraFinal(HttpServletRequest peticion) {
        String metodo = peticion.getMethod();
        if (metodo.equals("POST") || metodo.equals("PUT") || metodo.equals("PATCH")) {
            return html(500, PAGINA_500);
        }
        String ruta = peticion.getRequestURI() + "/";
        if (ruta.startsWith("//")) ruta = "/%2F" + ruta.substring(2);
        String consulta = peticion.getQueryString();
        if (consulta != null && !consulta.isEmpty()) ruta += "?" + consulta;
        HttpHeaders h = cabecerasComunes();
        h.set(HttpHeaders.CONTENT_TYPE, TIPO_HTML);
        h.set(HttpHeaders.LOCATION, ruta);
        return new ResponseEntity<>(new byte[0], h, 301);
    }

    // ================================================================== negociación

    /** Tipo de medio de DRF ({@code _MediaType}). */
    record TipoMedio(String principal, String sub, Map<String, String> parametros) {
        static TipoMedio de(String texto) {
            String[] partes = (texto == null ? "" : texto).split(";", -1);
            String completo = partes[0].strip().toLowerCase(Locale.ROOT);
            Map<String, String> params = new LinkedHashMap<>();
            for (int i = 1; i < partes.length; i++) {
                String p = partes[i].strip();
                int eq = p.indexOf('=');
                if (eq < 0) continue;
                String nombre = p.substring(0, eq).strip().toLowerCase(Locale.ROOT);
                String valor = p.substring(eq + 1).strip();
                if (valor.length() >= 2 && valor.charAt(0) == '"' && valor.charAt(valor.length() - 1) == '"') {
                    valor = valor.substring(1, valor.length() - 1).replace("\\\\", "\\").replace("\\\"", "\"");
                }
                params.put(nombre, valor);
            }
            int barra = completo.indexOf('/');
            String principal = barra < 0 ? completo : completo.substring(0, barra);
            String sub = barra < 0 ? "" : completo.substring(barra + 1);
            return new TipoMedio(principal, sub, params);
        }

        /** {@code _MediaType.match(otro)}. */
        boolean coincide(TipoMedio otro) {
            for (String clave : parametros.keySet()) {
                if (!clave.equals("q") && !java.util.Objects.equals(otro.parametros.get(clave), parametros.get(clave))) return false;
            }
            if (!sub.equals("*") && !otro.sub.equals("*") && !otro.sub.equals(sub)) return false;
            return principal.equals("*") || otro.principal.equals("*") || otro.principal.equals(principal);
        }

        int precedencia() {
            if (principal.equals("*")) return 0;
            if (sub.equals("*")) return 1;
            if (parametros.isEmpty() || (parametros.size() == 1 && parametros.containsKey("q"))) return 2;
            return 3;
        }
    }

    private static final List<TipoMedio> RENDERIZADORES = List.of(TipoMedio.de("application/json"), TipoMedio.de("text/html"));

    /**
     * {@code DefaultContentNegotiation.select_renderer}. Si se elige la API navegable de DRF ({@code text/html}),
     * el Java responde igualmente en JSON.
     */
    static void negociar(HttpServletRequest peticion) {
        Object formato = parametrosConsulta(peticion).get("format");
        if (Py.truthy(formato)) {
            if (!formato.equals("json") && !formato.equals("api")) throw new ExcepcionApi(404, ExcepcionApi.NO_ENCONTRADO);
            return;
        }
        String cabecera = peticion.getHeader("Accept");
        if (cabecera == null) cabecera = "*/*";
        List<List<TipoMedio>> grupos = new ArrayList<>();
        for (int i = 0; i < 4; i++) grupos.add(new ArrayList<>());
        for (String token : cabecera.split(",", -1)) {
            TipoMedio t = TipoMedio.de(token.strip());
            grupos.get(3 - t.precedencia()).add(t);
        }
        for (List<TipoMedio> grupo : grupos) {
            if (grupo.isEmpty()) continue;
            for (TipoMedio renderizador : RENDERIZADORES) {
                for (TipoMedio aceptado : grupo) {
                    if (renderizador.coincide(aceptado)) return;
                }
            }
        }
        throw new ExcepcionApi(406, ExcepcionApi.NO_ACEPTABLE);
    }

    // ================================================================== parámetros de consulta

    /** {@code request.query_params}: {@code QueryDict} de la consulta sin decodificar. */
    public static Entrada.MultiValor parametrosConsulta(HttpServletRequest peticion) {
        String crudo = peticion.getQueryString();
        return parseQsl(crudo == null ? "" : bytesATexto(crudo.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private static String bytesATexto(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /** {@code urllib.parse.parse_qsl(texto, keep_blank_values=True)} → {@code QueryDict}. */
    public static Entrada.MultiValor parseQsl(String texto) {
        Entrada.MultiValor r = new Entrada.MultiValor();
        for (String par : texto.split("&", -1)) {
            if (par.isEmpty()) continue;
            int eq = par.indexOf('=');
            String nombre = eq < 0 ? par : par.substring(0, eq);
            String valor = eq < 0 ? "" : par.substring(eq + 1);
            r.agregar(unquote(nombre.replace('+', ' ')), unquote(valor.replace('+', ' ')));
        }
        return r;
    }

    private static final Pattern HEX2 = Pattern.compile("[0-9A-Fa-f]{2}");

    /** {@code urllib.parse.unquote(texto, errors='replace')}. */
    public static String unquote(String texto) {
        if (texto.indexOf('%') < 0) return texto;
        StringBuilder sb = new StringBuilder();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < texto.length()) {
            char c = texto.charAt(i);
            if (c == '%' && i + 3 <= texto.length() && HEX2.matcher(texto.substring(i + 1, i + 3)).matches()) {
                bytes.write(Integer.parseInt(texto.substring(i + 1, i + 3), 16));
                i += 3;
                continue;
            }
            if (bytes.size() > 0) {
                sb.append(decodificarReemplazando(bytes.toByteArray()));
                bytes.reset();
            }
            sb.append(c);
            i++;
        }
        if (bytes.size() > 0) sb.append(decodificarReemplazando(bytes.toByteArray()));
        return sb.toString();
    }

    private static String decodificarReemplazando(byte[] b) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE).decode(ByteBuffer.wrap(b)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ================================================================== cuerpo (request.data)

    private static final List<String> TIPOS_ANALIZADORES = List.of("application/json", "application/x-www-form-urlencoded",
        "multipart/form-data");

    /**
     * {@code request.data} de DRF: {@code Map}/{@code List}/... para JSON, {@link Entrada.MultiValor} para
     * formularios. Sin cuerpo ({@code Content-Length} 0 o ausente) → diccionario vacío. Lanza {@link ExcepcionApi}
     * 415 si el tipo no se admite y 400 si no se puede leer.
     */
    public static Object datos(HttpServletRequest peticion) {
        String tipoContenido = peticion.getContentType();
        // El servidor de desarrollo de Django (wsgiref) pone text/plain si falta la cabecera.
        if (tipoContenido == null) tipoContenido = "text/plain";
        TipoMedio tipo = TipoMedio.de(tipoContenido);
        boolean formulario = TipoMedio.de("application/x-www-form-urlencoded").coincide(tipo)
            || TipoMedio.de("multipart/form-data").coincide(tipo);
        if (peticion.getContentLengthLong() <= 0) {
            return formulario ? new Entrada.MultiValor() : new LinkedHashMap<String, Object>();
        }
        String analizador = null;
        for (String a : TIPOS_ANALIZADORES) {
            if (TipoMedio.de(a).coincide(tipo)) {
                analizador = a;
                break;
            }
        }
        if (analizador == null) throw ExcepcionApi.tipoNoSoportado(tipoContenido);
        String charset = tipo.parametros().get("charset");
        Charset codificacion = StandardCharsets.UTF_8;
        if (charset != null) {
            try {
                if (Charset.isSupported(charset)) codificacion = Charset.forName(charset);
            } catch (IllegalArgumentException ignorada) {
                // codecs.lookup falla: se usa DEFAULT_CHARSET (utf-8)
            }
        }
        return switch (analizador) {
            case "application/json" -> {
                try {
                    String texto = decodificarComoPython(leer(peticion), codificacion);
                    yield JsonPy.loads(texto, JsonPy.Constantes.ESTRICTAS);
                } catch (Py.ValueError e) {
                    throw ExcepcionApi.errorLectura("JSON parse error - " + e.getMessage());
                }
            }
            case "application/x-www-form-urlencoded" -> {
                byte[] b = leer(peticion);
                String texto;
                try {
                    texto = codificacion.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
                } catch (java.nio.charset.CharacterCodingException e) {
                    texto = new String(b, StandardCharsets.ISO_8859_1);
                }
                yield parseQsl(texto);
            }
            default -> multipart(peticion);
        };
    }

    private static byte[] leer(HttpServletRequest peticion) {
        try (InputStream entrada = peticion.getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Entrada.MultiValor multipart(HttpServletRequest peticion) {
        Entrada.MultiValor r = new Entrada.MultiValor();
        try {
            for (Part parte : peticion.getParts()) {
                if (parte.getSubmittedFileName() == null) {
                    r.agregar(parte.getName(), new String(parte.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                } else {
                    r.agregar(parte.getName(), parte.getSubmittedFileName());
                }
            }
        } catch (IOException | jakarta.servlet.ServletException | IllegalStateException e) {
            throw ExcepcionApi.errorLectura("Multipart form parse error - " + e.getMessage());
        }
        return r;
    }

    /**
     * {@code codecs.getreader(codificacion)(flujo).read()}: decodificación estricta con el mensaje de Python; una
     * secuencia UTF-8 incompleta al final se descarta (el lector incremental no la marca como error).
     */
    static String decodificarComoPython(byte[] b, Charset codificacion) {
        if (!codificacion.equals(StandardCharsets.UTF_8)) {
            try {
                return codificacion.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
            } catch (java.nio.charset.CharacterCodingException e) {
                throw new Py.ValueError("'" + codificacion.name().toLowerCase(Locale.ROOT) + "' codec can't decode bytes");
            }
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < b.length) {
            int c = b[i] & 0xff;
            if (c < 0x80) {
                sb.append((char) c);
                i++;
                continue;
            }
            int n;
            int min2 = 0x80, max2 = 0xbf;
            if (c >= 0xc2 && c <= 0xdf) n = 2;
            else if (c >= 0xe0 && c <= 0xef) {
                n = 3;
                if (c == 0xe0) min2 = 0xa0;
                if (c == 0xed) max2 = 0x9f;
            } else if (c >= 0xf0 && c <= 0xf4) {
                n = 4;
                if (c == 0xf0) min2 = 0x90;
                if (c == 0xf4) max2 = 0x8f;
            } else {
                throw errorUtf8(c, i, i + 1, "invalid start byte");
            }
            int k = 1;
            for (; k < n; k++) {
                if (i + k >= b.length) return sb.toString(); // secuencia incompleta al final: se descarta
                int x = b[i + k] & 0xff;
                int lo = k == 1 ? min2 : 0x80, hi = k == 1 ? max2 : 0xbf;
                if (x < lo || x > hi) throw errorUtf8(c, i, i + k, "invalid continuation byte");
            }
            sb.append(new String(b, i, n, StandardCharsets.UTF_8));
            i += n;
        }
        return sb.toString();
    }

    private static Py.ValueError errorUtf8(int primero, int inicio, int fin, String razon) {
        if (fin - inicio == 1) {
            return new Py.ValueError(String.format("'utf-8' codec can't decode byte 0x%02x in position %d: %s", primero, inicio, razon));
        }
        return new Py.ValueError(String.format("'utf-8' codec can't decode bytes in position %d-%d: %s", inicio, fin - 1, razon));
    }

    // ================================================================== URLs

    /** {@code request.build_absolute_uri()}. */
    public static String urlAbsoluta(HttpServletRequest peticion) {
        String host = peticion.getHeader("Host");
        if (host == null) {
            // HttpRequest.get_host() de Django: SERVER_NAME y el puerto solo si no es el estándar.
            int puerto = peticion.getServerPort();
            boolean estandar = puerto == (peticion.isSecure() ? 443 : 80);
            host = peticion.getServerName() + (estandar ? "" : ":" + puerto);
        }
        String consulta = peticion.getQueryString();
        return peticion.getScheme() + "://" + host + peticion.getRequestURI()
            + (consulta != null && !consulta.isEmpty() ? "?" + consulta : "");
    }

    /** {@code rest_framework.utils.urls.replace_query_param} ({@code valor == null}: {@code remove_query_param}). */
    public static String reemplazarParametro(String url, String clave, String valor) {
        int signo = url.indexOf('?');
        int almohadilla = url.indexOf('#');
        String fragmento = "";
        if (almohadilla >= 0) {
            fragmento = url.substring(almohadilla);
            url = url.substring(0, almohadilla);
        }
        String base = signo >= 0 && (almohadilla < 0 || signo < almohadilla) ? url.substring(0, signo) : url;
        String consulta = signo >= 0 && (almohadilla < 0 || signo < almohadilla) ? url.substring(signo + 1) : "";
        Map<String, List<String>> parametros = new LinkedHashMap<>();
        for (String par : consulta.split("&", -1)) {
            if (par.isEmpty()) continue;
            int eq = par.indexOf('=');
            String n = unquote((eq < 0 ? par : par.substring(0, eq)).replace('+', ' '));
            String v = unquote((eq < 0 ? "" : par.substring(eq + 1)).replace('+', ' '));
            parametros.computeIfAbsent(n, k -> new ArrayList<>()).add(v);
        }
        if (valor == null) parametros.remove(clave);
        else parametros.put(clave, List.of(valor));
        TreeMap<String, List<String>> ordenados = new TreeMap<>(Py::compararTexto);
        ordenados.putAll(parametros);
        List<String> partes = new ArrayList<>();
        ordenados.forEach((n, vs) -> vs.forEach(v -> partes.add(quotePlus(n) + "=" + quotePlus(v))));
        String q = String.join("&", partes);
        return base + (q.isEmpty() ? "" : "?" + q) + fragmento;
    }

    /** {@code urllib.parse.quote_plus}. */
    static String quotePlus(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' || c == '~') {
                sb.append((char) c);
            } else if (c == ' ') {
                sb.append('+');
            } else {
                sb.append(String.format("%%%02X", c));
            }
        }
        return sb.toString();
    }
}
