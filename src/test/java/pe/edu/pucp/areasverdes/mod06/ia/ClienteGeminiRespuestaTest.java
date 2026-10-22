package pe.edu.pucp.areasverdes.mod06.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionStatus;
import com.google.genai.gaos.models.interactions.ModelOutputStep;
import com.google.genai.gaos.models.interactions.TextContent;
import com.google.genai.gaos.models.interactions.ThinkingLevel;
import com.google.genai.gaos.models.interactions.ThoughtStep;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;

/** Lectura de la respuesta de Gemini y nivel de razonamiento (sin llamadas reales). */
class ClienteGeminiRespuestaTest {

    private static final byte[] PDF = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);

    /** Interacción mínima válida: el SDK exige el estado. */
    private static Interaction.Builder interaccion() {
        return Interaction.builder().status(InteractionStatus.COMPLETED);
    }

    private static ModelOutputStep salida(String... textos) {
        return ModelOutputStep.builder()
            .content(java.util.Arrays.stream(textos).<com.google.genai.gaos.models.interactions.Content>map(TextContent::new).toList())
            .build();
    }

    @Test
    void usaOutputTextSiViene() {
        Interaction i = interaccion().outputText("{\"campo_1\": 1}").steps(List.of(salida("otro"))).build();
        assertEquals(Optional.of("{\"campo_1\": 1}"), ClienteGemini.textoDeSalida(i));
    }

    /** Caso real de gemini-3.x: output_text vacío, la respuesta va en un paso model_output después del thought. */
    @Test
    void sinOutputTextLeeLosPasosDeSalidaEIgnoraElRazonamiento() {
        Interaction i = interaccion()
            .steps(List.of(new ThoughtStep(), salida("{\"campo_1\": ", "7152.0}")))
            .build();
        assertEquals(Optional.of("{\"campo_1\": 7152.0}"), ClienteGemini.textoDeSalida(i));
    }

    @Test
    void sinTextoDevuelveVacio() {
        assertTrue(ClienteGemini.textoDeSalida(interaccion().build()).isEmpty());
        assertTrue(ClienteGemini.textoDeSalida(interaccion().outputText(" ").steps(List.of(new ThoughtStep())).build()).isEmpty());
    }

    @Test
    void enviaElNivelDeRazonamientoConfigurado() {
        CreateModelInteraction[] enviado = new CreateModelInteraction[1];
        ClienteGemini cliente = new ClienteGemini("clave-falsa", "modelo-x", " LOW ", p -> {
            enviado[0] = p;
            return Optional.of("{}");
        });
        assertEquals(Map.of(), cliente.extraerJson(PDF, Map.of(), "instrucción"));
        assertEquals(ThinkingLevel.LOW, enviado[0].generationConfig().orElseThrow().thinkingLevel().orElseThrow());
    }

    @Test
    void sinNivelNoEnviaGenerationConfig() {
        CreateModelInteraction[] enviado = new CreateModelInteraction[1];
        for (String nivel : new String[] {null, ""}) {
            new ClienteGemini("clave-falsa", "modelo-x", nivel, p -> {
                enviado[0] = p;
                return Optional.of("{}");
            }).extraerJson(PDF, Map.of(), "instrucción");
            assertFalse(enviado[0].generationConfig().isPresent());
        }
    }

    @Test
    void nivelInvalidoSeInformaComoIaNoConfigurada() {
        ServicioIANoDisponible e = assertThrows(ServicioIANoDisponible.class,
            () -> new ClienteGemini("clave-falsa", "modelo-x", "altisimo", p -> Optional.of("{}")));
        assertEquals(ClienteGemini.MENSAJE_IA_NO_CONFIGURADA, e.mensaje());
    }
}
