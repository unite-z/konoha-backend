package pe.edu.pucp.areasverdes.mod06.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;

/**
 * Cliente de DeepSeek (AV-395), alternativa a {@link ClienteGemini} cuando Gemini no responde.
 *
 * <p>Usa la API compatible con OpenAI ({@code POST /chat/completions}) con {@code response_format:
 * json_object}: el esquema va en la instrucción del sistema. Las fotografías van como bloques
 * {@code image_url} en base64 (solo {@code deepseek-flash} las acepta: JPEG, PNG, GIF o WebP).
 * DeepSeek no lee PDF: los informes se envían como texto más una imagen por página ({@link
 * PdfParaIa}). Nunca se registra la clave ({@code DEEPSEEK_API_KEY}) ni el contenido enviado
 * (RNF-008).
 */
public class ClienteDeepSeek implements ClienteIA {

    private static final Logger log = LoggerFactory.getLogger(ClienteDeepSeek.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String PROVEEDOR_IA = "DeepSeek";
    public static final String URL = "https://api.deepseek.com/chat/completions";

    /** Envía el cuerpo JSON y devuelve el cuerpo de la respuesta; en las pruebas, un doble. */
    @FunctionalInterface
    public interface Transporte {
        String enviar(String cuerpo) throws Exception;
    }

    private final String modelo;
    private final Transporte transporte;
    private Uso ultimoUso;

    public ClienteDeepSeek(String apiKey, String modelo, Transporte transporte) {
        String clave = apiKey == null ? "" : apiKey.strip();
        String nombreModelo = modelo == null ? "" : modelo.strip();
        if (clave.isEmpty() || nombreModelo.isEmpty()) {
            log.error(
                    "Análisis con IA no configurado: falta {}",
                    clave.isEmpty() ? "DEEPSEEK_API_KEY" : "DEEPSEEK_MODEL");
            throw new ServicioIANoDisponible(ClienteGemini.MENSAJE_IA_NO_CONFIGURADA);
        }
        this.modelo = nombreModelo;
        this.transporte = transporte != null ? transporte : http(clave);
    }

    private static Transporte http(String clave) {
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return cuerpo -> {
            HttpRequest peticion =
                    HttpRequest.newBuilder(URI.create(URL))
                            .timeout(Duration.ofSeconds(90))
                            .header("Authorization", "Bearer " + clave)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                            .build();
            HttpResponse<String> respuesta =
                    cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() / 100 != 2)
                throw new IllegalStateException("HTTP " + respuesta.statusCode());
            return respuesta.body();
        };
    }

    @Override
    public Uso ultimoUso() {
        return ultimoUso;
    }

    private static Integer entero(com.fasterxml.jackson.databind.JsonNode nodo, String campo) {
        return nodo.path(campo).isNumber() ? nodo.path(campo).intValue() : null;
    }

    @Override
    public String proveedor() {
        return PROVEEDOR_IA;
    }

    @Override
    public String modelo() {
        return modelo;
    }

    @Override
    public Map<String, Object> extraerJson(
            byte[] pdf, Map<String, Object> esquema, String instruccion) {
        PdfParaIa.Contenido contenido;
        try {
            contenido = PdfParaIa.convertir(pdf);
        } catch (IOException | RuntimeException exc) { // PDF dañado o cifrado
            log.error(
                    "No se pudo preparar el PDF para DeepSeek ({})",
                    exc.getClass().getSimpleName());
            throw new ServicioIANoDisponible(ClienteGemini.MENSAJE_IA_FALLO);
        }
        return pedir(cuerpo(modelo, esquema, instruccion, contenido.datos(), contenido.paginas()));
    }

    @Override
    public Map<String, Object> generarJson(
            String datos, List<Imagen> imagenes, Map<String, Object> esquema, String instruccion) {
        return pedir(cuerpo(modelo, esquema, instruccion, datos, imagenes));
    }

    /**
     * Cuerpo de {@code /chat/completions}: instrucción con el esquema, datos (con las fotografías
     * delante, si las hay) y salida JSON.
     */
    static String cuerpo(
            String modelo,
            Map<String, Object> esquema,
            String instruccion,
            String datos,
            List<Imagen> imagenes) {
        try {
            String sistema =
                    (instruccion == null ? "" : instruccion)
                            + "\n\nResponde solo con un objeto JSON válido que siga este esquema"
                            + " JSON:\n"
                            + JSON.writeValueAsString(esquema);
            String texto = datos == null ? "" : datos;
            Object usuario = texto;
            if (imagenes != null && !imagenes.isEmpty()) {
                List<Map<String, Object>> bloques = new ArrayList<>();
                for (Imagen imagen : imagenes) {
                    String url =
                            "data:"
                                    + imagen.tipoMime()
                                    + ";base64,"
                                    + Base64.getEncoder().encodeToString(imagen.datos());
                    bloques.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
                }
                bloques.add(Map.of("type", "text", "text", texto));
                usuario = bloques;
            }
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("model", modelo);
            cuerpo.put(
                    "messages",
                    List.of(
                            Map.of("role", "system", "content", sistema),
                            Map.of("role", "user", "content", usuario)));
            cuerpo.put("response_format", Map.of("type", "json_object"));
            cuerpo.put("stream", false);
            return JSON.writeValueAsString(cuerpo);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Esquema no serializable", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pedir(String cuerpo) {
        String texto;
        try {
            com.fasterxml.jackson.databind.JsonNode raiz = JSON.readTree(transporte.enviar(cuerpo));
            com.fasterxml.jackson.databind.JsonNode usage = raiz.path("usage");
            if (usage.isObject())
                ultimoUso =
                        new Uso(
                                entero(usage, "prompt_tokens"),
                                entero(usage, "completion_tokens"),
                                entero(usage, "prompt_cache_hit_tokens"));
            texto = raiz.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception exc) { // red, HTTP no 2xx o respuesta que no es JSON
            log.error("Fallo en la llamada a DeepSeek ({})", exc.getClass().getSimpleName());
            throw new ServicioIANoDisponible(ClienteGemini.MENSAJE_IA_FALLO);
        }
        if (Py.strip(texto).isEmpty()) {
            log.error("DeepSeek devolvió una respuesta vacía");
            throw new ServicioIANoDisponible(ClienteGemini.MENSAJE_IA_FALLO);
        }
        Object datos;
        try {
            datos = JsonPy.loads(texto);
        } catch (Py.ValueError | Py.TypeError e) {
            log.error("DeepSeek devolvió un JSON no interpretable");
            throw new ServicioIANoDisponible(ClienteGemini.MENSAJE_IA_FALLO);
        }
        if (!(datos instanceof Map<?, ?> mapa)) {
            log.error("DeepSeek devolvió un JSON que no es un objeto");
            throw new ServicioIANoDisponible(ClienteGemini.MENSAJE_IA_FALLO);
        }
        return (Map<String, Object>) mapa;
    }
}
