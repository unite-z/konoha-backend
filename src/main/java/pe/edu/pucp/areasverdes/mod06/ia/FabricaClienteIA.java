package pe.edu.pucp.areasverdes.mod06.ia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;
import pe.edu.pucp.areasverdes.mod06.config.ProveedorIaPropiedades;

/**
 * Único punto de creación del cliente de IA ({@code obtener_cliente_ia()} en el Django). Se
 * sustituye por un doble en las pruebas.
 *
 * <p>Como en el Django, el cliente se crea en cada solicitud: si falta la clave o el modelo del
 * proveedor activo, la solicitud responde 503 y el servidor sigue arrancando sin ellas. El
 * proveedor sale de {@code IA_PROVEEDOR} ({@code gemini} o {@code deepseek}, AV-395).
 */
@Component
public class FabricaClienteIA {

    private static final Logger log = LoggerFactory.getLogger(FabricaClienteIA.class);

    private final Mod06Propiedades propiedades;
    private final ProveedorIaPropiedades proveedorIa;

    public FabricaClienteIA(Mod06Propiedades propiedades) {
        this(propiedades, ProveedorIaPropiedades.GEMINI);
    }

    @Autowired
    public FabricaClienteIA(Mod06Propiedades propiedades, ProveedorIaPropiedades proveedorIa) {
        this.propiedades = propiedades;
        this.proveedorIa = proveedorIa;
    }

    public ClienteIA obtenerClienteIa() {
        return switch (proveedorIa.nombre()) {
            case "gemini" ->
                    new ClienteGemini(
                            propiedades.geminiApiKey(),
                            propiedades.geminiModel(),
                            propiedades.geminiThinkingLevel(),
                            null);
            case "deepseek" ->
                    new ClienteDeepSeek(
                            proveedorIa.deepseekApiKey(), proveedorIa.deepseekModel(), null);
            default -> {
                log.error(
                        "Análisis con IA mal configurado: IA_PROVEEDOR debe ser gemini o deepseek");
                throw new ServicioIANoDisponible(ClienteGemini.MENSAJE_IA_NO_CONFIGURADA);
            }
        };
    }
}
