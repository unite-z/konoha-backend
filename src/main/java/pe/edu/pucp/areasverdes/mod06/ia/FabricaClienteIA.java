package pe.edu.pucp.areasverdes.mod06.ia;

import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;

/**
 * Único punto de creación del cliente de IA ({@code obtener_cliente_ia()} en el Django). Se sustituye por un doble
 * en las pruebas.
 *
 * <p>Como en el Django, el cliente se crea en cada solicitud: si falta {@code GEMINI_API_KEY} o
 * {@code GEMINI_MODEL}, la solicitud responde 503 y el servidor sigue arrancando sin ellas.
 */
@Component
public class FabricaClienteIA {

    private final Mod06Propiedades propiedades;

    public FabricaClienteIA(Mod06Propiedades propiedades) {
        this.propiedades = propiedades;
    }

    public ClienteIA obtenerClienteIa() {
        return new ClienteGemini(propiedades.geminiApiKey(), propiedades.geminiModel(), propiedades.geminiThinkingLevel(), null);
    }
}
