package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Matriz de permisos de MOD-01 (gate de {@code docs/estandares/pruebas.md}). Cada fila es un
 * endpoint con el resultado esperado por rol: {@code ok} (pasa la autorización), {@code 403} o
 * {@code 401}. Sin sesión, todo responde 401 salvo el inicio de sesión. La segunda prueba falla si
 * se añade un endpoint a {@code /api/v1/core/**} sin fila en esta tabla.
 */
class Mod01PermisosApiTest extends ApiCore {

    record Fila(
            String metodo,
            String patron,
            String url,
            String jefe,
            String ingeniero,
            String practicante,
            String capataz) {}

    static final String U = "/api/v1/core/usuarios/999999";

    // spotless:off
    static final List<Fila> MATRIZ =
            List.of(
                    //        método   patrón                                          url
                    //                                  jefe   ing    prac   capataz
                    new Fila(
                            "GET",
                            "/auth/sesion",
                            "/api/v1/core/auth/sesion/",
                            "ok",
                            "ok",
                            "ok",
                            "ok"),
                    new Fila(
                            "POST",
                            "/auth/contrasena",
                            "/api/v1/core/auth/contrasena/",
                            "ok",
                            "ok",
                            "ok",
                            "ok"),
                    new Fila(
                            "POST",
                            "/auth/logout",
                            "/api/v1/core/auth/logout/",
                            "ok",
                            "ok",
                            "ok",
                            "ok"),
                    new Fila(
                            "GET",
                            "/usuarios",
                            "/api/v1/core/usuarios/",
                            "ok",
                            "403",
                            "403",
                            "403"),
                    new Fila(
                            "POST",
                            "/usuarios",
                            "/api/v1/core/usuarios/",
                            "ok",
                            "403",
                            "403",
                            "403"),
                    new Fila("GET", "/usuarios/{id}", U + "/", "ok", "403", "403", "403"),
                    new Fila("PATCH", "/usuarios/{id}", U + "/", "ok", "403", "403", "403"),
                    new Fila(
                            "POST",
                            "/usuarios/{id}/desactivar",
                            U + "/desactivar/",
                            "ok",
                            "403",
                            "403",
                            "403"),
                    new Fila(
                            "POST",
                            "/usuarios/{id}/activar",
                            U + "/activar/",
                            "ok",
                            "403",
                            "403",
                            "403"),
                    new Fila(
                            "POST",
                            "/usuarios/{id}/desbloquear",
                            U + "/desbloquear/",
                            "ok",
                            "403",
                            "403",
                            "403"),
                    new Fila(
                            "POST",
                            "/usuarios/{id}/cerrar-sesiones",
                            U + "/cerrar-sesiones/",
                            "ok",
                            "403",
                            "403",
                            "403"),
                    new Fila(
                            "POST",
                            "/usuarios/{id}/contrasena-temporal",
                            U + "/contrasena-temporal/",
                            "ok",
                            "403",
                            "403",
                            "403"),
                    new Fila(
                            "GET",
                            "/usuarios/{id}/historial",
                            U + "/historial/",
                            "ok",
                            "403",
                            "403",
                            "403"),
                    new Fila("GET", "/usuarios/{id}/rol", U + "/rol/", "ok", "403", "403", "403"),
                    new Fila("PUT", "/usuarios/{id}/rol", U + "/rol/", "ok", "403", "403", "403"),
                    new Fila("GET", "/roles", "/api/v1/core/roles/", "ok", "403", "403", "403"),
                    new Fila("GET", "/accesos", "/api/v1/core/accesos/", "ok", "403", "403", "403"),
                    new Fila(
                            "GET",
                            "/historial",
                            "/api/v1/core/historial/?entidad=ejemplar&registro_id=1",
                            "ok",
                            "ok",
                            "ok",
                            "403"),
                    new Fila(
                            "POST",
                            "/adjuntos",
                            "/api/v1/core/adjuntos/?entidad=ejemplar&registro_id=999999",
                            "ok",
                            "ok",
                            "ok",
                            "403"),
                    new Fila(
                            "GET",
                            "/adjuntos",
                            "/api/v1/core/adjuntos/?entidad=ejemplar&registro_id=1",
                            "ok",
                            "ok",
                            "ok",
                            "ok"),
                    new Fila(
                            "GET",
                            "/adjuntos/{id}/contenido",
                            "/api/v1/core/adjuntos/999999/contenido/",
                            "ok",
                            "ok",
                            "ok",
                            "ok"),
                    new Fila(
                            "GET",
                            "/repositorio",
                            "/api/v1/core/repositorio/?tipo=jardin",
                            "ok",
                            "ok",
                            "ok",
                            "ok"));

    // spotless:on

    /** Endpoints públicos: solo el inicio de sesión. */
    static final Set<String> PUBLICOS = Set.of("POST /auth/login");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping rutas;

    @Test
    void cadaRolObtieneLoQueDiceLaMatriz() throws Exception {
        for (Fila f : MATRIZ) {
            assertEquals(401, ejecutar(f, null), "anónimo en " + f.metodo() + " " + f.patron());
            comprobar(f, "jefe", f.jefe());
            comprobar(f, "ingeniero", f.ingeniero());
            comprobar(f, "practicante", f.practicante());
            comprobar(f, "capataz", f.capataz());
        }
        assertTrue(contarAccesos("SinPermiso") > 0, "Los rechazos se registran");
    }

    @Test
    void todoEndpointDeMod01EstaEnLaMatriz() {
        Set<String> registrados = new TreeSet<>();
        rutas.getHandlerMethods()
                .forEach(
                        (info, metodo) -> {
                            for (String patron : info.getPatternValues()) {
                                if (!patron.startsWith("/api/v1/core/")) {
                                    continue;
                                }
                                String relativo =
                                        patron.substring("/api/v1/core".length())
                                                .replaceAll("/$", "");
                                info.getMethodsCondition()
                                        .getMethods()
                                        .forEach(m -> registrados.add(m.name() + " " + relativo));
                            }
                        });
        Set<String> enMatriz =
                MATRIZ.stream()
                        .map(f -> f.metodo() + " " + f.patron())
                        .collect(Collectors.toCollection(TreeSet::new));
        enMatriz.addAll(PUBLICOS);
        assertEquals(enMatriz, registrados, "Añade cada endpoint nuevo a la matriz de permisos");
    }

    private void comprobar(Fila f, String rol, String esperado) throws Exception {
        int estado = ejecutar(f, sesion(rol));
        String contexto = rol + " en " + f.metodo() + " " + f.patron() + " respondió " + estado;
        if ("ok".equals(esperado)) {
            assertTrue(estado != 401 && estado != 403, contexto);
        } else {
            assertEquals(Integer.parseInt(esperado), estado, contexto);
        }
    }

    private int ejecutar(Fila f, Cookie sesion) throws Exception {
        MockHttpServletRequestBuilder peticion;
        if (f.patron().equals("/adjuntos") && f.metodo().equals("POST")) {
            peticion =
                    multipart(f.url())
                            .file(
                                    new MockMultipartFile(
                                            "archivo", "a.png", "image/png", new byte[] {1}))
                            .header("Idempotency-Key", UUID.randomUUID().toString());
        } else {
            peticion =
                    request(HttpMethod.valueOf(f.metodo()), f.url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}");
        }
        peticion = sesion == null ? peticion.with(csrf()) : escribir(peticion, sesion, null);
        return http.perform(peticion).andReturn().getResponse().getStatus();
    }
}
