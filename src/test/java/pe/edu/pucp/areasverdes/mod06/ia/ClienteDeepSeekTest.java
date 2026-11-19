package pe.edu.pucp.areasverdes.mod06.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;
import pe.edu.pucp.areasverdes.mod06.config.ProveedorIaPropiedades;

/** Cliente de DeepSeek y elección del proveedor (AV-395), sin llamadas reales. */
class ClienteDeepSeekTest {

    private static final Map<String, Object> ESQUEMA = Map.of("type", "object");

    private static String respuesta(String contenido) throws Exception {
        return new ObjectMapper()
                .writeValueAsString(
                        Map.of(
                                "choices",
                                List.of(Map.of("message", Map.of("content", contenido)))));
    }

    @Test
    void enviaInstruccionConEsquemaDatosYSalidaJson() throws Exception {
        List<String> enviados = new ArrayList<>();
        ClienteDeepSeek cliente =
                new ClienteDeepSeek(
                        "clave",
                        "deepseek-flash",
                        cuerpo -> {
                            enviados.add(cuerpo);
                            return respuesta("{\"prioridad\": \"Alta\"}");
                        });

        assertEquals(
                Map.of("prioridad", "Alta"),
                cliente.generarJson("{\"x\": 1}", List.of(), ESQUEMA, "Clasifica"));

        JsonNode cuerpo = new ObjectMapper().readTree(enviados.get(0));
        assertEquals("deepseek-flash", cuerpo.path("model").asText());
        assertEquals("json_object", cuerpo.path("response_format").path("type").asText());
        String sistema = cuerpo.path("messages").path(0).path("content").asText();
        assertTrue(sistema.startsWith("Clasifica") && sistema.contains("{\"type\":\"object\"}"));
        assertEquals("{\"x\": 1}", cuerpo.path("messages").path(1).path("content").asText());
        assertEquals("DeepSeek", cliente.proveedor());
    }

    @Test
    void lasFotografiasVanComoBloquesImageUrlDelanteDelTexto() throws Exception {
        List<String> enviados = new ArrayList<>();
        ClienteDeepSeek cliente =
                new ClienteDeepSeek(
                        "clave",
                        "deepseek-flash",
                        cuerpo -> {
                            enviados.add(cuerpo);
                            return respuesta("{\"hipotesis\": []}");
                        });
        cliente.generarJson(
                "Síntomas",
                List.of(new ClienteIA.Imagen(new byte[] {1, 2}, "image/png")),
                ESQUEMA,
                "x");

        JsonNode contenido =
                new ObjectMapper()
                        .readTree(enviados.get(0))
                        .path("messages")
                        .path(1)
                        .path("content");
        assertEquals("image_url", contenido.path(0).path("type").asText());
        assertEquals(
                "data:image/png;base64,AQI=",
                contenido.path(0).path("image_url").path("url").asText());
        assertEquals("Síntomas", contenido.path(1).path("text").asText());
    }

    @Test
    void fallosDeRedRespuestasVaciasYJsonQueNoEsObjetoDan503() {
        for (ClienteDeepSeek.Transporte t :
                List.<ClienteDeepSeek.Transporte>of(
                        c -> {
                            throw new IllegalStateException("HTTP 503");
                        },
                        c -> respuesta(" "),
                        c -> respuesta("[1, 2]"),
                        c -> "no es json")) {
            ClienteDeepSeek cliente = new ClienteDeepSeek("clave", "deepseek-flash", t);
            ServicioIANoDisponible e =
                    assertThrows(
                            ServicioIANoDisponible.class,
                            () -> cliente.generarJson("", null, ESQUEMA, "x"));
            assertEquals(ClienteGemini.MENSAJE_IA_FALLO, e.getMessage());
        }
    }

    @Test
    void sinClaveNoEstaConfigurado() {
        ServicioIANoDisponible e =
                assertThrows(
                        ServicioIANoDisponible.class,
                        () -> new ClienteDeepSeek(" ", "deepseek-flash", null));
        assertEquals(ClienteGemini.MENSAJE_IA_NO_CONFIGURADA, e.getMessage());
    }

    @Test
    void laFabricaEligeElProveedorDeIaProveedor() {
        Mod06Propiedades mod06 =
                new Mod06Propiedades("db", null, "clave-gemini", "gemini-3.6-flash");
        FabricaClienteIA deepseek =
                new FabricaClienteIA(mod06, new ProveedorIaPropiedades("DeepSeek", "clave", null));
        ClienteIA cliente = deepseek.obtenerClienteIa();
        assertInstanceOf(ClienteDeepSeek.class, cliente);
        assertEquals("deepseek-flash", cliente.modelo());

        assertInstanceOf(ClienteGemini.class, new FabricaClienteIA(mod06).obtenerClienteIa());
        FabricaClienteIA otro =
                new FabricaClienteIA(mod06, new ProveedorIaPropiedades("openai", "", ""));
        assertThrows(ServicioIANoDisponible.class, otro::obtenerClienteIa);
        assertTrue(
                !new ProveedorIaPropiedades("deepseek", "secreta", null)
                        .toString()
                        .contains("secreta"));
    }
}
