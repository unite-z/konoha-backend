package pe.edu.pucp.areasverdes.mod06.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod06.api.drf.DespachadorDrf;

/**
 * Cualquier otra ruta bajo {@code /api/v1/}: 404 con la página de Django ({@code config/urls.py} no la resuelve).
 * Las rutas del MOD-06 se resuelven antes porque son más específicas.
 */
@Hidden
@RestController
public class RutaNoEncontradaController {

    @RequestMapping(path = {"/api/v1", "/api/v1/**"}, method = {RequestMethod.GET, RequestMethod.POST,
        RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.TRACE})
    public ResponseEntity<byte[]> noEncontrada(HttpServletRequest peticion) {
        return DespachadorDrf.html(404, DespachadorDrf.PAGINA_404);
    }
}
