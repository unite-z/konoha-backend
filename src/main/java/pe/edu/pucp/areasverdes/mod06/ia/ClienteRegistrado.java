package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Envoltorio de un {@link ClienteIA} que registra cada llamada en {@link UsoIaRepository} (AV-396):
 * asistente, proveedor, modelo, duración, resultado y tokens. Los errores se registran y se
 * relanzan sin cambios.
 */
final class ClienteRegistrado implements ClienteIA {

    private final ClienteIA cliente;
    private final String asistente;
    private final UsoIaRepository uso;

    ClienteRegistrado(ClienteIA cliente, String asistente, UsoIaRepository uso) {
        this.cliente = cliente;
        this.asistente = asistente;
        this.uso = uso;
    }

    @Override
    public String proveedor() {
        return cliente.proveedor();
    }

    @Override
    public String modelo() {
        return cliente.modelo();
    }

    @Override
    public Uso ultimoUso() {
        return cliente.ultimoUso();
    }

    @Override
    public Map<String, Object> extraerJson(
            byte[] pdf, Map<String, Object> esquema, String instruccion) {
        return medir(() -> cliente.extraerJson(pdf, esquema, instruccion));
    }

    @Override
    public Map<String, Object> generarJson(
            String datos, List<Imagen> imagenes, Map<String, Object> esquema, String instruccion) {
        return medir(() -> cliente.generarJson(datos, imagenes, esquema, instruccion));
    }

    private Map<String, Object> medir(Supplier<Map<String, Object>> llamada) {
        long inicio = System.nanoTime();
        try {
            Map<String, Object> respuesta = llamada.get();
            registrar(inicio, null);
            return respuesta;
        } catch (RuntimeException e) {
            registrar(
                    inicio, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            throw e;
        }
    }

    private void registrar(long inicio, String error) {
        uso.registrar(
                asistente,
                cliente.proveedor(),
                cliente.modelo(),
                (System.nanoTime() - inicio) / 1_000_000,
                error,
                cliente.ultimoUso());
    }
}
