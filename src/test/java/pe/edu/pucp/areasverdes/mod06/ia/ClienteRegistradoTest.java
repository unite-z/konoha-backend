package pe.edu.pucp.areasverdes.mod06.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;

/** Registro del uso de la IA (AV-396): cada llamada, correcta o fallida, con sus tokens. */
class ClienteRegistradoTest {

    record Fila(
            String asistente, String proveedor, String modelo, String error, ClienteIA.Uso uso) {}

    static class RegistroFalso extends UsoIaRepository {
        final List<Fila> filas = new ArrayList<>();

        RegistroFalso() {
            super(null);
        }

        @Override
        public void registrar(
                String asistente,
                String proveedor,
                String modelo,
                long duracionMs,
                String error,
                ClienteIA.Uso uso) {
            filas.add(new Fila(asistente, proveedor, modelo, error, uso));
        }
    }

    private static String respuesta(String contenido, String usage) {
        return "{\"choices\": [{\"message\": {\"content\": \"" + contenido + "\"}}]" + usage + "}";
    }

    @Test
    void registraLaLlamadaCorrectaConSusTokens() {
        RegistroFalso registro = new RegistroFalso();
        ClienteDeepSeek deepseek =
                new ClienteDeepSeek(
                        "clave",
                        "deepseek-flash",
                        c ->
                                respuesta(
                                        "{\\\"ok\\\": true}",
                                        ", \"usage\": {\"prompt_tokens\": 1200,"
                                                + " \"completion_tokens\": 80,"
                                                + " \"prompt_cache_hit_tokens\": 1024}"));
        ClienteIA cliente = new ClienteRegistrado(deepseek, "priorizacion", registro);

        cliente.generarJson("datos", List.of(), Map.of(), "x");

        Fila fila = registro.filas.get(0);
        assertEquals("priorizacion", fila.asistente());
        assertEquals("DeepSeek", fila.proveedor());
        assertEquals("deepseek-flash", fila.modelo());
        assertNull(fila.error());
        assertEquals(new ClienteIA.Uso(1200, 80, 1024), fila.uso());
    }

    @Test
    void registraElFalloYLoRelanzaSinCambios() {
        RegistroFalso registro = new RegistroFalso();
        ClienteDeepSeek deepseek =
                new ClienteDeepSeek(
                        "clave",
                        "deepseek-flash",
                        c -> {
                            throw new IllegalStateException("HTTP 503");
                        });
        ClienteIA cliente = new ClienteRegistrado(deepseek, "cronograma", registro);

        ServicioIANoDisponible e =
                assertThrows(
                        ServicioIANoDisponible.class,
                        () -> cliente.generarJson("", List.of(), Map.of(), "x"));

        assertEquals(ClienteGemini.MENSAJE_IA_FALLO, e.getMessage());
        assertEquals(ClienteGemini.MENSAJE_IA_FALLO, registro.filas.get(0).error());
        assertNull(registro.filas.get(0).uso());
    }
}
