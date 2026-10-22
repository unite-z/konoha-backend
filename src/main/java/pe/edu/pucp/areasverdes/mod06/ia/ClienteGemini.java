package pe.edu.pucp.areasverdes.mod06.ia;

import com.google.genai.Client;
import com.google.genai.gaos.models.interactions.Content;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.CreateModelInteractionResponseFormat;
import com.google.genai.gaos.models.interactions.DocumentContent;
import com.google.genai.gaos.models.interactions.DocumentContentMimeType;
import com.google.genai.gaos.models.interactions.GenerationConfig;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.ModelOutputStep;
import com.google.genai.gaos.models.interactions.ResponseFormat;
import com.google.genai.gaos.models.interactions.Step;
import com.google.genai.gaos.models.interactions.TextContent;
import com.google.genai.gaos.models.interactions.ThinkingLevel;
import com.google.genai.gaos.models.interactions.TextResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormatMimeType;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;

/**
 * Adaptador de Gemini (componente de IA desacoplado, ADR-001 figura 1).
 *
 * <p>Es la <b>única</b> clase que usa el SDK {@code com.google.genai:google-genai}. Expone un método del dominio,
 * {@link #extraerJson}, y oculta el SDK: si mañana cambia el proveedor (por ejemplo Ollama), solo cambia esta clase.
 *
 * <p>API usada (documentación oficial de la Gemini API, SDK Java {@code google-genai} 1.78.0), la misma que el
 * Django con el SDK de Python 2.32.0:
 * <ul>
 *   <li>PDF en línea (https://ai.google.dev/gemini-api/docs/document-processing):
 *   {@code client.interactions.create(...)} con {@code DocumentContent} (base64, {@code application/pdf}) y
 *   {@code TextContent}. Límite: 50 MB o 1 000 páginas, igual en línea que con el Files API.</li>
 *   <li>Salida JSON estructurada (https://ai.google.dev/gemini-api/docs/structured-output):
 *   {@code TextResponseFormat} con {@code mime_type=application/json} y el esquema JSON, y lectura de
 *   {@code interaction.outputText()}. Los campos que admiten nulo usan {@code {"type": [<tipo>, "null"]}}.</li>
 *   <li>Tiempo de espera: el valor por defecto del SDK (no se fija ninguno).</li>
 * </ul>
 *
 * <p>Seguridad: nunca se registra ni se devuelve el valor de {@code GEMINI_API_KEY} ni el contenido del PDF
 * (RNF-008). RN-IA-001: la salida es una propuesta; si el servicio falla, el flujo manual continúa (HU-MOD06-010).
 */
public class ClienteGemini implements ClienteIA {

    private static final Logger log = LoggerFactory.getLogger(ClienteGemini.class);

    public static final String PROVEEDOR_IA = "Gemini (Google AI Studio)";
    public static final String MENSAJE_IA_NO_CONFIGURADA = "El análisis con IA no está configurado; registra los datos manualmente";
    public static final String MENSAJE_IA_FALLO =
        "No se pudo analizar el informe con IA; registra los datos manualmente o inténtalo más tarde";
    public static final String TEXTO_SOLICITUD = "Extrae los datos del informe adjunto según el esquema indicado.";

    /** Llamada al SDK ({@code client.interactions.create}); en las pruebas se sustituye por un doble. */
    @FunctionalInterface
    public interface Sdk {
        /** Devuelve el texto de la respuesta ({@link #textoDeSalida}); vacío si no trae texto. */
        Optional<String> crearInteraccion(CreateModelInteraction parametros) throws Exception;
    }

    private final String modelo;
    private final ThinkingLevel nivelRazonamiento;
    private final Sdk sdk;

    /**
     * Crea el cliente del SDK con la clave <b>explícita</b> ({@code GEMINI_API_KEY}) y el modelo
     * ({@code GEMINI_MODEL}), sin fijar el nivel de razonamiento. {@code sdk} permite inyectar un doble en las pruebas.
     */
    public ClienteGemini(String apiKey, String modelo, Sdk sdk) {
        this(apiKey, modelo, null, sdk);
    }

    /**
     * Igual que el anterior, con el nivel de razonamiento ({@code GEMINI_THINKING_LEVEL}: {@code minimal},
     * {@code low}, {@code medium} o {@code high}). Vacío o {@code null}: no se envía y decide el modelo.
     */
    public ClienteGemini(String apiKey, String modelo, String nivelRazonamiento, Sdk sdk) {
        String clave = apiKey == null ? "" : apiKey;
        String nombreModelo = modelo == null ? "" : modelo;
        if (clave.isEmpty() || nombreModelo.isEmpty()) {
            log.error("Análisis con IA no configurado: falta {} en backend/.env", clave.isEmpty() ? "GEMINI_API_KEY" : "GEMINI_MODEL");
            throw new ServicioIANoDisponible(MENSAJE_IA_NO_CONFIGURADA);
        }
        String nivel = nivelRazonamiento == null ? "" : Py.strip(nivelRazonamiento).toLowerCase(Locale.ROOT);
        if (!nivel.isEmpty() && !ThinkingLevel.of(nivel).isKnown()) {
            log.error("Análisis con IA mal configurado: GEMINI_THINKING_LEVEL debe ser minimal, low, medium o high");
            throw new ServicioIANoDisponible(MENSAJE_IA_NO_CONFIGURADA);
        }
        this.modelo = nombreModelo;
        this.nivelRazonamiento = nivel.isEmpty() ? null : ThinkingLevel.of(nivel);
        if (sdk != null) {
            this.sdk = sdk;
        } else {
            Client cliente = Client.builder().apiKey(clave).build();
            this.sdk = parametros -> cliente.interactions.create(CreateInteractionRequestBody.of(parametros))
                .interaction().flatMap(ClienteGemini::textoDeSalida);
        }
    }

    /**
     * Texto de la respuesta. Los modelos con razonamiento (por ejemplo {@code gemini-3.x}) dejan
     * {@code output_text} vacío y ponen la respuesta en los pasos {@code model_output} de {@code steps}; en ese caso se
     * unen los textos de esos pasos (los pasos {@code thought} se ignoran).
     */
    static Optional<String> textoDeSalida(Interaction interaccion) {
        Optional<String> directo = interaccion.outputText().filter(t -> !t.isBlank());
        if (directo.isPresent()) return directo;
        StringBuilder texto = new StringBuilder();
        for (Step paso : interaccion.steps().orElse(List.of())) {
            if (!(paso instanceof ModelOutputStep salida)) continue;
            for (Content contenido : salida.content().orElse(List.of())) {
                if (contenido instanceof TextContent t) t.text().ifPresent(texto::append);
            }
        }
        return texto.isEmpty() ? Optional.empty() : Optional.of(texto.toString());
    }

    @Override
    public String proveedor() {
        return PROVEEDOR_IA;
    }

    @Override
    public String modelo() {
        return modelo;
    }

    /** Parámetros de la interacción: documento PDF en línea, texto de la solicitud y salida JSON con esquema. */
    static CreateModelInteraction parametros(String modelo, byte[] pdf, Map<String, Object> esquema, String instruccion) {
        return parametros(modelo, null, pdf, esquema, instruccion);
    }

    /** Igual que el anterior; con {@code nivel} no nulo se envía {@code generation_config.thinking_level}. */
    static CreateModelInteraction parametros(String modelo, ThinkingLevel nivel, byte[] pdf, Map<String, Object> esquema,
                                             String instruccion) {
        List<Content> entrada = List.of(
            DocumentContent.builder()
                .data(Base64.getEncoder().encodeToString(pdf))
                .mimeType(DocumentContentMimeType.APPLICATION_PDF)
                .build(),
            TextContent.builder().text(TEXTO_SOLICITUD).build());
        CreateModelInteraction.Builder parametros = CreateModelInteraction.builder()
            .model(modelo)
            .systemInstruction(instruccion)
            .input(InteractionsInput.ofContent(entrada))
            .responseFormat(CreateModelInteractionResponseFormat.of(ResponseFormat.of(
                TextResponseFormat.builder()
                    .mimeType(TextResponseFormatMimeType.APPLICATION_JSON)
                    .schema(esquema)
                    .build())));
        if (nivel != null) parametros.generationConfig(GenerationConfig.builder().thinkingLevel(nivel).build());
        return parametros.build();
    }

    /**
     * Envía el PDF a Gemini con la instrucción del sistema y pide salida JSON con {@code esquema}.
     *
     * <p>Cualquier excepción del SDK, respuesta vacía o JSON no interpretable → {@link ServicioIANoDisponible}.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> extraerJson(byte[] pdf, Map<String, Object> esquema, String instruccion) {
        Optional<String> respuesta;
        try {
            respuesta = sdk.crearInteraccion(parametros(modelo, nivelRazonamiento, pdf, esquema, instruccion));
        } catch (Exception exc) { // cualquier error del SDK o de red
            log.error("Fallo en la llamada a Gemini ({})", exc.getClass().getSimpleName());
            throw new ServicioIANoDisponible(MENSAJE_IA_FALLO);
        }

        String texto = respuesta == null ? null : respuesta.orElse(null);
        if (texto == null || texto.isEmpty() || Py.strip(texto).isEmpty()) {
            log.error("Gemini devolvió una respuesta vacía");
            throw new ServicioIANoDisponible(MENSAJE_IA_FALLO);
        }
        Object datos;
        try {
            datos = JsonPy.loads(texto);
        } catch (Py.ValueError | Py.TypeError e) {
            log.error("Gemini devolvió un JSON no interpretable");
            throw new ServicioIANoDisponible(MENSAJE_IA_FALLO);
        }
        if (!(datos instanceof Map<?, ?> mapa)) {
            log.error("Gemini devolvió un JSON que no es un objeto");
            throw new ServicioIANoDisponible(MENSAJE_IA_FALLO);
        }
        return (Map<String, Object>) mapa;
    }
}
