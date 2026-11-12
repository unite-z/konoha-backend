package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;

/**
 * Paginación de 25 elementos (Guía de Estilo Web 5.5 y {@code docs/estandares/api.md}), con la
 * misma semántica que {@code mod05.ConsultasService}: {@code page} ausente o vacío es la 1; {@code
 * last} es la última; una página fuera de rango o que no es un número responde 404.
 */
public final class Paginacion {

    public static final int TAMANO_PAGINA = 25;

    private Paginacion() {}

    /** Número de páginas: al menos 1, aunque no haya elementos. */
    public static long paginas(long total) {
        return Math.max(1, (total + TAMANO_PAGINA - 1) / TAMANO_PAGINA);
    }

    /** Página pedida (1..paginas) o 404 {@code pagina-inexistente}. */
    public static long resolver(String page, long total) {
        long ultima = paginas(total);
        if (page == null || page.isBlank()) {
            return 1;
        }
        if (page.equals("last")) {
            return ultima;
        }
        long pagina;
        try {
            pagina = Long.parseLong(page);
        } catch (NumberFormatException e) {
            throw paginaInexistente();
        }
        if (pagina < 1 || pagina > ultima) {
            throw paginaInexistente();
        }
        return pagina;
    }

    public static long desplazamiento(long pagina) {
        return (pagina - 1) * TAMANO_PAGINA;
    }

    private static ErrorGestion paginaInexistente() {
        return new ErrorGestion(404, "pagina-inexistente", "Página inexistente");
    }

    /**
     * Construye el enlace absoluto a otra página conservando los demás parámetros (lo implementa el
     * controlador).
     */
    @FunctionalInterface
    public interface EnlacePagina {
        String a(long pagina);
    }
}
