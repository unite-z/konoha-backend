package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Matriz de permisos de las funciones de IA de S12 ({@code mod06.gestion.ia}): consultar exige
 * REP.INDICADOR.CONSULTAR; solicitar, IA.PROPUESTA.PROPONER; validar y rechazar,
 * IA.PROPUESTA.VALIDAR (V9). Sin Gemini configurado, quien puede solicitar recibe 503; las
 * sugerencias inexistentes dan 404 a quien pasa el permiso.
 */
public class MatrizPermisosIaApiTest extends ApiGestion {

    static final List<String> USUARIOS =
            List.of("anónimo", "jefe", "ingeniero", "practicante", "capataz", "inactivo");

    static final List<Integer> CONSULTA = List.of(401, 200, 200, 200, 403, 401);
    static final List<Integer> DETALLE = List.of(401, 404, 404, 404, 403, 401);
    static final List<Integer> PROPONER = List.of(401, 503, 503, 503, 403, 401);
    static final List<Integer> VALIDAR = List.of(401, 404, 403, 403, 403, 401);

    /** Fila: método, patrón registrado, URL de ejemplo, cuerpo y esperados por usuario. */
    record Fila(String metodo, String patron, String url, String cuerpo, List<Integer> esperados) {}

    /** Marca de cuerpo multipart (solo la parte «datos», sin fotos). */
    static final String MULTIPART = "multipart";

    static final List<Fila> MATRIZ = new ArrayList<>();

    /** Cuerpo de validar bien formado para todos los recursos: la prueba mide permiso y 404. */
    static final String CUERPO_VALIDAR =
            "{\"por_elemento\": {}, \"por_propuesta\": {}, \"por_campo\": {}, \"acciones\": {},"
                    + " \"confirmado\": {}}";

    static {
        recurso("priorizaciones", "{\"alcance\": \"Areas\", \"horizonte_dias\": 7}", true);
        recurso("inconsistencias", null, false);
        // Evidencia: el alta es multipart; sin fotos, quien puede proponer recibe 400 antes de la
        // IA.
        recurso("evidencias-fitosanitarias", null, false);
        MATRIZ.add(
                new Fila(
                        "POST",
                        "/api/v1/ia/evidencias-fitosanitarias",
                        "/api/v1/ia/evidencias-fitosanitarias/",
                        MULTIPART,
                        List.of(401, 400, 400, 400, 403, 401)));
        MATRIZ.add(
                new Fila(
                        "POST",
                        "/api/v1/ia/evidencias-fitosanitarias/manual",
                        "/api/v1/ia/evidencias-fitosanitarias/manual/",
                        "{\"ejemplar_id\": 999999, \"resultado\": \"NoConcluyente\"}",
                        VALIDAR));
        MATRIZ.add(
                new Fila(
                        "GET",
                        "/api/v1/ia/evidencias-fitosanitarias/{id:[0-9]+}/fotografias/{archivo:[0-9]+}",
                        "/api/v1/ia/evidencias-fitosanitarias/999999/fotografias/1/",
                        null,
                        DETALLE));
        // Sin Gemini, quien puede proponer recibe 503 (la IA se pide antes de leer).
        recurso("cronogramas", "{\"desde\": \"2026-11-01\", \"hasta\": \"2026-11-30\"}", true);
        // Clasificación y estimación: la incidencia o actividad 999999 no existe, así que quien
        // puede solicitar recibe 404 antes de llamar a la IA.
        recurso("clasificaciones-incidencia", null, false);
        alta("clasificaciones-incidencia", "", "{\"incidencia_id\": 999999}", DETALLE);
        alta(
                "clasificaciones-incidencia",
                "/manual",
                "{\"incidencia_id\": 999999, \"valores\": {}}",
                VALIDAR);
        MATRIZ.add(
                new Fila(
                        "GET",
                        "/api/v1/ia/clasificaciones-incidencia/pendientes",
                        "/api/v1/ia/clasificaciones-incidencia/pendientes/",
                        null,
                        CONSULTA));
        recurso("estimaciones", null, false);
        alta("estimaciones", "", "{\"actividad_id\": 999999}", DETALLE);
        alta("estimaciones", "/manual", "{\"actividad_id\": 999999, \"confirmado\": {}}", DETALLE);
        MATRIZ.add(
                new Fila(
                        "GET",
                        "/api/v1/ia/estimaciones/actividades-pendientes",
                        "/api/v1/ia/estimaciones/actividades-pendientes/",
                        null,
                        CONSULTA));
        MATRIZ.add(
                new Fila(
                        "POST",
                        "/api/v1/ia/inconsistencias/analisis",
                        "/api/v1/ia/inconsistencias/analisis/",
                        "{\"conjunto\": \"Proveedores\"}",
                        PROPONER));
        // Configuración de la IA (AV-395): solo el jefe (IA.PROPUESTA.VALIDAR). Sin clave, la
        // prueba de conexión responde 200 con ok=false.
        List<Integer> soloJefe = List.of(401, 200, 403, 403, 403, 401);
        MATRIZ.add(
                new Fila(
                        "GET",
                        "/api/v1/ia/configuracion",
                        "/api/v1/ia/configuracion/",
                        null,
                        soloJefe));
        MATRIZ.add(
                new Fila(
                        "PUT",
                        "/api/v1/ia/configuracion",
                        "/api/v1/ia/configuracion/",
                        "{\"proveedor\": \"gemini\", \"modelo\": \"gemini-3.6-flash\"}",
                        soloJefe));
        MATRIZ.add(
                new Fila(
                        "POST",
                        "/api/v1/ia/configuracion/probar",
                        "/api/v1/ia/configuracion/probar/",
                        "{}",
                        soloJefe));
        // Uso y costes de la IA (AV-396): solo el jefe.
        MATRIZ.add(new Fila("GET", "/api/v1/ia/uso", "/api/v1/ia/uso/", null, soloJefe));
        MATRIZ.add(new Fila("GET", "/api/v1/ia/precios", "/api/v1/ia/precios/", null, soloJefe));
        MATRIZ.add(
                new Fila(
                        "PUT",
                        "/api/v1/ia/precios",
                        "/api/v1/ia/precios/",
                        "{\"modelo\": \"deepseek-flash\", \"vigente_desde\": \"2099-01-01\","
                                + " \"usd_millon_entrada\": 0.3, \"usd_millon_salida\": 1.2}",
                        soloJefe));
    }

    private static void alta(String nombre, String sufijo, String cuerpo, List<Integer> esperados) {
        String ruta = "/api/v1/ia/" + nombre + sufijo;
        MATRIZ.add(new Fila("POST", ruta, ruta + "/", cuerpo, esperados));
    }

    private static void recurso(String nombre, String solicitud, boolean conPostDeAlta) {
        String base = "/api/v1/ia/" + nombre;
        MATRIZ.add(new Fila("GET", base, base + "/", null, CONSULTA));
        if (conPostDeAlta) MATRIZ.add(new Fila("POST", base, base + "/", solicitud, PROPONER));
        MATRIZ.add(new Fila("GET", base + "/{id:[0-9]+}", base + "/999999/", null, DETALLE));
        MATRIZ.add(
                new Fila(
                        "POST",
                        base + "/{id:[0-9]+}/validar",
                        base + "/999999/validar/",
                        CUERPO_VALIDAR,
                        VALIDAR));
        MATRIZ.add(
                new Fila(
                        "POST",
                        base + "/{id:[0-9]+}/rechazar",
                        base + "/999999/rechazar/",
                        "{}",
                        VALIDAR));
    }

    /** Endpoints de la matriz («MÉTODO url»), para la prueba global de contrato de rutas. */
    public static List<String> endpointsCubiertos() {
        return MATRIZ.stream().map(f -> f.metodo() + " " + f.url()).toList();
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping rutas;

    static Stream<Arguments> combinaciones() {
        List<Arguments> casos = new ArrayList<>();
        for (Fila f : MATRIZ) {
            for (int i = 0; i < USUARIOS.size(); i++) {
                casos.add(
                        Arguments.of(
                                f.metodo() + " " + f.patron(),
                                f,
                                USUARIOS.get(i),
                                f.esperados().get(i)));
            }
        }
        return casos.stream();
    }

    @ParameterizedTest(name = "{0} como {2} → {3}")
    @MethodSource("combinaciones")
    void cadaRolRecibeLoQueDiceLaMatriz(String endpoint, Fila fila, String usuario, int esperado)
            throws Exception {
        MockHttpServletRequestBuilder peticion =
                MULTIPART.equals(fila.cuerpo())
                        ? multipart(fila.url())
                                .part(new MockPart("datos", "{\"ejemplar_id\": 1}".getBytes()))
                                .with(csrf())
                        : fila.metodo().equals("POST") || fila.metodo().equals("PUT")
                                ? (fila.metodo().equals("PUT") ? put(fila.url()) : post(fila.url()))
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(fila.cuerpo())
                                : get(fila.url());
        if (!usuario.equals("anónimo")) peticion = peticion.cookie(sesion(usuario));
        int estado = http.perform(peticion).andReturn().getResponse().getStatus();
        assertEquals(esperado, estado, endpoint + " como " + usuario);
    }

    @Test
    void todasLasRutasDeIaTienenFila() {
        Set<String> filas = new LinkedHashSet<>();
        MATRIZ.forEach(f -> filas.add(f.metodo() + " " + f.patron()));
        rutas.getHandlerMethods()
                .forEach(
                        (info, metodo) -> {
                            if (!metodo.getBeanType().getPackageName().endsWith(".gestion.ia"))
                                return;
                            for (String patron : info.getPatternValues()) {
                                String sinBarra =
                                        patron.endsWith("/")
                                                ? patron.substring(0, patron.length() - 1)
                                                : patron;
                                info.getMethodsCondition()
                                        .getMethods()
                                        .forEach(
                                                m -> {
                                                    if (!filas.contains(m + " " + sinBarra)) {
                                                        fail(
                                                                "Ruta de IA sin fila en la matriz: "
                                                                        + m
                                                                        + " "
                                                                        + sinBarra);
                                                    }
                                                });
                            }
                        });
    }
}
