package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.Optional;
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
 * proveedor y el modelo son los elegidos en Configuración ({@link ConfiguracionIaRepository}) o, si
 * no hay, los de {@code IA_PROVEEDOR} ({@code gemini} o {@code deepseek}, AV-395).
 */
@Component
public class FabricaClienteIA {

    private static final Logger log = LoggerFactory.getLogger(FabricaClienteIA.class);

    /** Proveedor y modelo activos, y de dónde salen ({@code configuracion} o {@code entorno}). */
    public record Activo(String proveedor, String modelo, String origen) {}

    private final Mod06Propiedades propiedades;
    private final ProveedorIaPropiedades proveedorIa;
    private final ConfiguracionIaRepository configuracion;

    public FabricaClienteIA(Mod06Propiedades propiedades) {
        this(propiedades, ProveedorIaPropiedades.GEMINI);
    }

    public FabricaClienteIA(Mod06Propiedades propiedades, ProveedorIaPropiedades proveedorIa) {
        this(propiedades, proveedorIa, null);
    }

    @Autowired
    public FabricaClienteIA(
            Mod06Propiedades propiedades,
            ProveedorIaPropiedades proveedorIa,
            ConfiguracionIaRepository configuracion) {
        this.propiedades = propiedades;
        this.proveedorIa = proveedorIa;
        this.configuracion = configuracion;
    }

    public Activo activo() {
        return Optional.ofNullable(configuracion)
                .flatMap(ConfiguracionIaRepository::leer)
                .map(e -> new Activo(e.proveedor(), e.modelo(), "configuracion"))
                .orElseGet(
                        () ->
                                new Activo(
                                        proveedorIa.nombre(),
                                        modeloDelEntorno(proveedorIa.nombre()),
                                        "entorno"));
    }

    public ClienteIA obtenerClienteIa() {
        Activo activo = activo();
        return crear(activo.proveedor(), activo.modelo());
    }

    /** Modelo del {@code api.env} para un proveedor. */
    public String modeloDelEntorno(String proveedor) {
        return "deepseek".equals(proveedor)
                ? proveedorIa.deepseekModel()
                : propiedades.geminiModel();
    }

    /** Si el {@code api.env} tiene clave para el proveedor (nunca se devuelve su valor). */
    public boolean tieneClave(String proveedor) {
        String clave =
                "deepseek".equals(proveedor)
                        ? proveedorIa.deepseekApiKey()
                        : propiedades.geminiApiKey();
        return clave != null && !clave.isBlank();
    }

    /** Cliente de un proveedor y un modelo concretos (también lo usa «Probar conexión»). */
    public ClienteIA crear(String proveedor, String modelo) {
        return switch (proveedor) {
            case "gemini" ->
                    new ClienteGemini(
                            propiedades.geminiApiKey(),
                            modelo,
                            propiedades.geminiThinkingLevel(),
                            null);
            case "deepseek" -> new ClienteDeepSeek(proveedorIa.deepseekApiKey(), modelo, null);
            default -> {
                log.error(
                        "Análisis con IA mal configurado: IA_PROVEEDOR debe ser gemini o deepseek");
                throw new ServicioIANoDisponible(ClienteGemini.MENSAJE_IA_NO_CONFIGURADA);
            }
        };
    }
}
