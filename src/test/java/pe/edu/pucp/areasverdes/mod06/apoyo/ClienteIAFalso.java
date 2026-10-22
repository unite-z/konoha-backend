package pe.edu.pucp.areasverdes.mod06.apoyo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteGemini;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;

/**
 * Doble del cliente de IA ({@code ClienteIAFalso} de {@code conftest.py}): {@code propuesta} fija o {@code error}
 * para simular un fallo. <b>Ninguna prueba llama a Gemini.</b>
 */
public class ClienteIAFalso implements ClienteIA {

    /** Llamada registrada. */
    public record Llamada(byte[] pdf, Map<String, Object> esquema, String instruccion) {}

    private final Map<String, Object> propuesta;
    private final RuntimeException error;
    private final String modelo;
    public final List<Llamada> llamadas = new ArrayList<>();

    public ClienteIAFalso(Map<String, Object> propuesta) {
        this(propuesta, null);
    }

    public ClienteIAFalso(Map<String, Object> propuesta, RuntimeException error) {
        this.propuesta = propuesta != null ? propuesta : new LinkedHashMap<>();
        this.error = error;
        this.modelo = "modelo-de-prueba";
    }

    public static ClienteIAFalso conError(RuntimeException error) {
        return new ClienteIAFalso(null, error);
    }

    @Override
    public String proveedor() {
        return ClienteGemini.PROVEEDOR_IA;
    }

    @Override
    public String modelo() {
        return modelo;
    }

    @Override
    public Map<String, Object> extraerJson(byte[] pdf, Map<String, Object> esquema, String instruccion) {
        llamadas.add(new Llamada(pdf, esquema, instruccion));
        if (error != null) throw error;
        return Py.copia(propuesta);
    }
}
