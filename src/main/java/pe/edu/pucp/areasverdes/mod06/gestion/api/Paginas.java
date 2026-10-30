package pe.edu.pucp.areasverdes.mod06.gestion.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Paginacion;

/**
 * Enlaces absolutos {@code next} y {@code previous} a partir de la petición actual, como {@code
 * Paginacion25} y MOD-05 ({@code docs/estandares/api.md}, «Paginación»). Con {@code
 * forward-headers-strategy: framework}, el host y el esquema salen bien detrás del proxy.
 */
public final class Paginas {

    private Paginas() {}

    public static Paginacion.EnlacePagina desde(HttpServletRequest peticion) {
        return pagina ->
                ServletUriComponentsBuilder.fromRequest(peticion)
                        .replaceQueryParam("page", pagina)
                        .build()
                        .toUriString();
    }
}
