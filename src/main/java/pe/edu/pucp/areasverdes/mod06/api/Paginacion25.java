package pe.edu.pucp.areasverdes.mod06.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.api.drf.DespachadorDrf;
import pe.edu.pucp.areasverdes.mod06.api.drf.ExcepcionApi;
import pe.edu.pucp.areasverdes.mod06.api.drf.Salida;
import pe.edu.pucp.areasverdes.mod06.comun.Py;

/**
 * Paginación de 25 elementos por página (Guía Web 5.5): {@code PageNumberPagination} de DRF con el
 * {@code Paginator} de Django.
 *
 * <p>Parámetro {@code page} (vacío → 1; {@code last} → última página); página inválida o inexistente → 404
 * {@code {"detail": "Página inválida."}}. Respuesta {@code {count, next, previous, results}} con los enlaces absolutos
 * construidos a partir de la URL de la petición.
 */
public final class Paginacion25 {

    private Paginacion25() {}

    public static final int TAMANO_PAGINA = 25;

    public static Map<String, Object> paginar(List<Map<String, Object>> elementos, HttpServletRequest peticion,
                                              Salida.Serializador serializador) {
        int total = elementos.size();
        long paginas = total == 0 ? 1 : (total + TAMANO_PAGINA - 1) / TAMANO_PAGINA;
        Object numero = Py.or(DespachadorDrf.parametrosConsulta(peticion).get("page"), 1L);
        if ("last".equals(numero)) numero = paginas;
        Object pagina;
        try {
            pagina = Py.toInt(numero);
        } catch (Py.ValueError | Py.TypeError e) {
            throw new ExcepcionApi(404, ExcepcionApi.PAGINA_INVALIDA);
        }
        if (Py.compare(pagina, 1L) < 0 || Py.compare(pagina, paginas) > 0) {
            throw new ExcepcionApi(404, ExcepcionApi.PAGINA_INVALIDA);
        }
        long n = (Long) pagina;
        int desde = (int) ((n - 1) * TAMANO_PAGINA);
        int hasta = Math.min(desde + TAMANO_PAGINA, total);
        List<Map<String, Object>> pagina25 = elementos.subList(desde, hasta);

        String url = DespachadorDrf.urlAbsoluta(peticion);
        String siguiente = n < paginas ? DespachadorDrf.reemplazarParametro(url, "page", String.valueOf(n + 1)) : null;
        String anterior = null;
        if (n > 1) {
            anterior = n - 1 == 1
                ? DespachadorDrf.reemplazarParametro(url, "page", null)
                : DespachadorDrf.reemplazarParametro(url, "page", String.valueOf(n - 1));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("count", (long) total);
        r.put("next", siguiente);
        r.put("previous", anterior);
        r.put("results", serializador.representarMuchos(pagina25));
        return r;
    }
}
