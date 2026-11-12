package pe.edu.pucp.areasverdes.mod01.api;

import java.util.List;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Página de 25 elementos con el formato de {@code api.md}: {@code count}, {@code next}, {@code
 * previous} y {@code results}.
 */
public record Pagina<T>(long count, String next, String previous, List<T> results) {

    public static final int TAMANO = 25;

    /** Pagina una lista ya filtrada en memoria. */
    public static <T> Pagina<T> de(List<T> todos, int pagina) {
        int desde = Math.min((pagina - 1) * TAMANO, todos.size());
        int hasta = Math.min(desde + TAMANO, todos.size());
        return conTotal(todos.subList(desde, hasta), todos.size(), pagina);
    }

    /** Página ya recortada en la base, con el total de elementos. */
    public static <T> Pagina<T> conTotal(List<T> elementos, long total, int pagina) {
        boolean hayMas = (long) pagina * TAMANO < total;
        return new Pagina<>(
                total,
                hayMas ? enlace(pagina + 1) : null,
                pagina > 1 ? enlace(pagina - 1) : null,
                List.copyOf(elementos));
    }

    private static String enlace(int pagina) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", pagina)
                .build()
                .toUriString()
                .replaceFirst("^https?://[^/]+", "");
    }
}
