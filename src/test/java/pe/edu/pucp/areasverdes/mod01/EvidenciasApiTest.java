package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** RF-T-007, RF-T-008 y RF-T-010: adjuntos, historial y repositorio común. */
class EvidenciasApiTest extends ApiCore {

    static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13};

    long ejemplar;

    @BeforeEach
    void datos() {
        jdbc.update("DELETE FROM mod05_ejemplar WHERE codigo LIKE 'CORE-%'", Map.of());
        ejemplar =
                jdbc.queryForObject(
                        "INSERT INTO mod05_ejemplar (codigo) VALUES (:c) RETURNING id",
                        Map.of("c", "CORE-" + UUID.randomUUID().toString().substring(0, 8)),
                        Long.class);
    }

    ResultActions adjuntar(Cookie sesion, long registro, UUID clave, byte[] contenido)
            throws Exception {
        return http.perform(
                multipart("/api/v1/core/adjuntos/")
                        .file(
                                new MockMultipartFile(
                                        "archivo", "foto árbol.png", "image/png", contenido))
                        .param("entidad", "ejemplar")
                        .param("registro_id", String.valueOf(registro))
                        .header("Idempotency-Key", clave.toString())
                        .cookie(sesion)
                        .with(csrf()));
    }

    @Test
    void unaFotoSeAsociaAlEjemplarYSeConsultaDespues() throws Exception {
        var ingeniero = sesion("ingeniero");
        var r =
                adjuntar(ingeniero, ejemplar, UUID.randomUUID(), PNG)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.nombre").value("foto árbol.png"))
                        .andExpect(jsonPath("$.usuario").value("ingeniero"))
                        .andReturn();
        long id = cuerpo(r).get("id").asLong();
        var capataz = sesion("capataz");
        http.perform(
                        get("/api/v1/core/adjuntos/?entidad=ejemplar&registro_id=" + ejemplar)
                                .cookie(capataz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        http.perform(get("/api/v1/core/adjuntos/" + id + "/contenido/").cookie(capataz))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));
    }

    @Test
    void reintentarLaMismaSubidaNoDuplicaYOtroArchivoIgualSeRechaza() throws Exception {
        var practicante = sesion("practicante");
        UUID clave = UUID.randomUUID();
        adjuntar(practicante, ejemplar, clave, PNG).andExpect(status().isCreated());
        adjuntar(practicante, ejemplar, clave, PNG).andExpect(status().isOk());
        adjuntar(practicante, ejemplar, UUID.randomUUID(), PNG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("archivo-duplicado"));
        assertEquals(
                1,
                jdbc.queryForObject("SELECT count(*) FROM core_adjunto", Map.of(), Integer.class));
    }

    @Test
    void unaCargaInvalidaInformaElErrorSinDejarFilas() throws Exception {
        adjuntar(sesion("jefe"), ejemplar, UUID.randomUUID(), new byte[] {1, 2, 3})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("archivo-invalido"));
        adjuntar(sesion("jefe"), 999_999, UUID.randomUUID(), PNG).andExpect(status().isNotFound());
        assertEquals(
                0,
                jdbc.queryForObject("SELECT count(*) FROM core_archivo", Map.of(), Integer.class));
    }

    @Test
    void elCapatazNoAdjuntaAlInventarioYUnaEntidadSinModuloNoAdmiteAdjuntos() throws Exception {
        adjuntar(sesion("capataz"), ejemplar, UUID.randomUUID(), PNG)
                .andExpect(status().isForbidden());
        http.perform(
                        multipart("/api/v1/core/adjuntos/")
                                .file(new MockMultipartFile("archivo", "a.png", "image/png", PNG))
                                .param("entidad", "incidencia")
                                .param("registro_id", "1")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .cookie(sesion("jefe"))
                                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("entidad-no-disponible"));
    }

    @Test
    void elHistorialDeUnaAtencionUneAmbasAuditoriasYOcultaMontos() throws Exception {
        // La auditoría de MOD-05 no se puede borrar: cada ejecución usa una atención propia.
        long atencion = 1_000_000_000L + ThreadLocalRandom.current().nextInt(1_000_000_000);
        long usuarioMod05 =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario (usuario, nombre, password_hash) VALUES"
                                + " (:u, 'Usuario MOD-05', 'x') RETURNING id",
                        Map.of("u", "core-" + UUID.randomUUID()),
                        Long.class);
        jdbc.update(
                """
                INSERT INTO mod05_registroauditoria (entidad, registro_id, accion, usuario_id,
                    fecha_hora, valores_anteriores, valores_nuevos, motivo)
                VALUES ('AtencionTercerizada', :a, 'Alta', :u, now() - interval '1 day', '{}',
                    '{"estado": "Registrada", "monto_orden": "500.00"}', '')
                """,
                Map.of("u", usuarioMod05, "a", atencion));
        jdbc.update(
                """
                INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id,
                    valores_anteriores, valores_nuevos)
                VALUES ('atencion', :a, 'Modificacion', :u, '{"estado": "Registrada"}',
                    '{"estado": "En ejecución"}')
                """,
                Map.of("u", ids.get("practicante"), "a", atencion));
        String ruta = "/api/v1/core/historial/?entidad=atencion&registro_id=" + atencion;
        http.perform(get(ruta).cookie(sesion("ingeniero")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].usuario").value("practicante"))
                .andExpect(jsonPath("$[1].usuario").value("Usuario MOD-05"))
                .andExpect(jsonPath("$[1].valores_nuevos.monto_orden").doesNotExist());
        http.perform(get(ruta).cookie(sesion("jefe")))
                .andExpect(jsonPath("$[1].valores_nuevos.monto_orden").value("500.00"));
        http.perform(get(ruta).cookie(sesion("capataz"))).andExpect(status().isForbidden());
    }

    @Test
    void elRepositorioCentralizaJardinesYServiciosYAvisaDeLoQueAunNoEsta() throws Exception {
        String nombre = "Jardín Core " + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO mod05_areaverde (nombre, activo) VALUES (:n, true)",
                Map.of("n", nombre));
        var capataz = sesion("capataz");
        http.perform(
                        get("/api/v1/core/repositorio/")
                                .param("tipo", "jardin")
                                .param("q", nombre.substring(7))
                                .cookie(capataz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].nombre").value(nombre))
                .andExpect(jsonPath("$.results[0].modulo").value("MOD-05"));
        http.perform(get("/api/v1/core/repositorio/?tipo=especie").cookie(capataz))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.count").value(0));
        http.perform(get("/api/v1/core/repositorio/?tipo=planeta").cookie(capataz))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/v1/core/repositorio/?tipo=jardin&q=%25").cookie(capataz))
                .andExpect(jsonPath("$.count").value(0));
    }
}
