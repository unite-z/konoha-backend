package pe.edu.pucp.areasverdes.mod06.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.edu.pucp.areasverdes.mod06.api.drf.DespachadorDrf;
import pe.edu.pucp.areasverdes.mod06.api.drf.Entrada;
import pe.edu.pucp.areasverdes.mod06.api.drf.ExcepcionApi;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ConfiguracionFaltante;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ErrorDominio;
import pe.edu.pucp.areasverdes.mod06.comun.errores.FuenteDatosNoDisponible;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ParametroInvalido;
import pe.edu.pucp.areasverdes.mod06.comun.errores.RecursoNoEncontrado;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;
import pe.edu.pucp.areasverdes.mod06.comun.errores.TipoArchivoNoAdmitido;
import pe.edu.pucp.areasverdes.mod06.comun.errores.TransicionNoPermitida;

/**
 * Traducción de las excepciones del MOD-06 a respuestas HTTP ({@code api/manejador_excepciones.py}).
 *
 * <ul>
 *   <li>Excepciones de dominio: {@code {"codigo": "<NombreExcepcion>", "detalle": "<mensaje que dice qué hacer>"}}
 *   con el código HTTP de la tabla.</li>
 *   <li>Errores propios de DRF (validación, 403, 404, 405, 406, 415, lectura del cuerpo): formato por defecto de
 *   DRF ({@code {"campo": ["mensaje"]}} o {@code {"detail": "..."}}).</li>
 *   <li>Cualquier otro error: 500 con la página de error de Django.</li>
 * </ul>
 *
 * <p>Limitado al paquete {@code mod06} para no interferir con el MOD-05 en la fusión. Va primero porque el
 * manejador del MOD-05 es global y, si no, atendería también los errores del MOD-06.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "pe.edu.pucp.areasverdes.mod06")
public class ManejadorExcepciones {

    private static final Logger log = LoggerFactory.getLogger(ManejadorExcepciones.class);

    static final Map<Class<? extends ErrorDominio>, Integer> ESTADO_HTTP_POR_EXCEPCION = new LinkedHashMap<>();

    static {
        ESTADO_HTTP_POR_EXCEPCION.put(ParametroInvalido.class, 400);
        ESTADO_HTTP_POR_EXCEPCION.put(RecursoNoEncontrado.class, 404);
        ESTADO_HTTP_POR_EXCEPCION.put(TransicionNoPermitida.class, 409);
        ESTADO_HTTP_POR_EXCEPCION.put(ConfiguracionFaltante.class, 422);
        ESTADO_HTTP_POR_EXCEPCION.put(TipoArchivoNoAdmitido.class, 415);
        ESTADO_HTTP_POR_EXCEPCION.put(FuenteDatosNoDisponible.class, 503);
        ESTADO_HTTP_POR_EXCEPCION.put(ServicioIANoDisponible.class, 503);
    }

    private static String allow(HttpServletRequest peticion) {
        Object a = peticion.getAttribute(DespachadorDrf.ATRIBUTO_ALLOW);
        return a == null ? null : a.toString();
    }

    @ExceptionHandler(ErrorDominio.class)
    ResponseEntity<byte[]> dominio(ErrorDominio exc, HttpServletRequest peticion) {
        int codigoHttp = 500;
        for (Map.Entry<Class<? extends ErrorDominio>, Integer> e : ESTADO_HTTP_POR_EXCEPCION.entrySet()) {
            if (e.getKey().isInstance(exc)) {
                codigoHttp = e.getValue();
                break;
            }
        }
        String nombre = exc.getClass().getSimpleName();
        if (codigoHttp >= 500) log.warn("{}: {}", nombre, exc.mensaje());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", nombre);
        cuerpo.put("detalle", exc.mensaje());
        return DespachadorDrf.json(codigoHttp, cuerpo, allow(peticion));
    }

    @ExceptionHandler(Entrada.ErrorValidacion.class)
    ResponseEntity<byte[]> validacion(Entrada.ErrorValidacion exc, HttpServletRequest peticion) {
        return DespachadorDrf.json(400, exc.detalle(), allow(peticion));
    }

    @ExceptionHandler(ExcepcionApi.class)
    ResponseEntity<byte[]> api(ExcepcionApi exc, HttpServletRequest peticion) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("detail", exc.detalle());
        return DespachadorDrf.json(exc.estado(), cuerpo, allow(peticion));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<byte[]> inesperado(Exception exc, HttpServletRequest peticion) {
        log.error("Internal Server Error: {}", peticion.getRequestURI(), exc);
        return DespachadorDrf.html(500, DespachadorDrf.PAGINA_500);
    }
}
