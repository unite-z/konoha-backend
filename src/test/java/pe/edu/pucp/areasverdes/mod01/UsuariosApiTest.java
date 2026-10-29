package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.edu.pucp.areasverdes.mod01.servicios.ErrorCore;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuariosService;

/** HU-MOD01-004, 005 y 008; CU-MOD01-003, 004 y 007. */
class UsuariosApiTest extends ApiCore {

    static final String USUARIOS = "/api/v1/core/usuarios/";

    @Autowired UsuariosService servicio;

    @Test
    void elAltaDeUnCapatazCreaLaCuentaActivaConContrasenaTemporal() throws Exception {
        var jefe = sesion("jefe");
        var r =
                http.perform(
                                escribir(
                                        post(USUARIOS),
                                        jefe,
                                        "{\"correo\": \"Nuevo.Capataz@pucp.edu.pe\", \"nombre\":"
                                                + " \"Capataz nuevo\", \"rol\": \"capataz\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.usuario.estado").value("activa"))
                        .andExpect(jsonPath("$.usuario.correo").value("nuevo.capataz@pucp.edu.pe"))
                        .andExpect(jsonPath("$.usuario.debe_cambiar_contrasena").value(true))
                        .andExpect(jsonPath("$.avisos.length()").value(1))
                        .andReturn();
        String temporal = cuerpo(r).get("contrasena_temporal").asText();
        assertEquals(12, temporal.length());
        http.perform(login("nuevo.capataz@pucp.edu.pe", temporal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debe_cambiar_contrasena").value(true));
        assertEquals(1, cambios("Alta"));
    }

    @Test
    void unCorreoDuplicadoNoCreaOtraCuentaYOfreceReactivar() throws Exception {
        jdbc.update(
                "UPDATE core_usuario SET activo = false WHERE id = :id",
                Map.of("id", ids.get("practicante")));
        http.perform(
                        escribir(
                                post(USUARIOS),
                                sesion("jefe"),
                                "{\"correo\": \"practicante"
                                        + DOMINIO
                                        + "\", \"nombre\": \"Otra\", \"rol\": \"practicante\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("correo-en-uso"))
                .andExpect(jsonPath("$.cuenta_id").value(ids.get("practicante")))
                .andExpect(jsonPath("$.puede_reactivar").value(true));
    }

    @Test
    void elOperarioNoSePuedeDarDeAltaEnLaPrimeraFase() throws Exception {
        http.perform(
                        escribir(
                                post(USUARIOS),
                                sesion("jefe"),
                                "{\"correo\": \"operario@pucp.edu.pe\", \"nombre\": \"Operario\","
                                        + " \"rol\": \"operario\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("rol-no-habilitado"));
    }

    @Test
    void desactivarCierraDeInmediatoLasSesionesYConservaLaFirma() throws Exception {
        var movilPerdido = sesion("capataz");
        jdbc.update(
                "INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id)"
                        + " VALUES ('actividad', 1, 'Alta', :id)",
                Map.of("id", ids.get("capataz")));
        http.perform(
                        escribir(
                                post(USUARIOS + ids.get("capataz") + "/desactivar/"),
                                sesion("jefe"),
                                "{\"motivo\": \"Móvil perdido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("inactiva"));
        http.perform(get("/api/v1/core/auth/sesion/").cookie(movilPerdido))
                .andExpect(status().isUnauthorized());
        assertEquals(
                ids.get("capataz"),
                jdbc.queryForObject(
                        "SELECT usuario_id FROM core_registro_auditoria WHERE entidad ="
                                + " 'actividad'",
                        Map.of(),
                        Long.class));
    }

    @Test
    void unJefeNoPuedeDesactivarseASiMismo() throws Exception {
        http.perform(
                        escribir(
                                post(USUARIOS + ids.get("jefe") + "/desactivar/"),
                                sesion("jefe"),
                                "{\"motivo\": \"Prueba\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("no-autodesactivar"));
    }

    @Test
    void elUnicoJefeActivoNoSePuedeDesactivar() {
        // Defensa en profundidad: aunque otro actor llegara al servicio, la regla se mantiene.
        var otro =
                new UsuarioSesion(
                        ids.get("ingeniero"),
                        "ingeniero",
                        "ingeniero",
                        "jefe-seccion",
                        "Jefe",
                        Set.of(),
                        false,
                        0);
        var error =
                assertThrows(
                        ErrorCore.class,
                        () -> servicio.desactivar(ids.get("jefe"), "Prueba", otro));
        assertEquals("ultimo-jefe-seccion", error.codigo());
    }

    @Test
    void noExisteEliminarCuentas() throws Exception {
        http.perform(
                        escribir(
                                delete(USUARIOS + ids.get("practicante") + "/"),
                                sesion("jefe"),
                                null))
                .andExpect(status().is4xxClientError());
        assertEquals(4, servicio.listar(new UsuariosService.Filtros(null, null, false)).size());
    }

    @Test
    void laListaMuestraEstadoVigenciaUltimoAccesoYDestacados() throws Exception {
        var jefe = sesion("jefe");
        jdbc.update(
                "UPDATE core_usuario SET ultimo_acceso = now() - interval '61 days' WHERE id = :id",
                Map.of("id", ids.get("ingeniero")));
        jdbc.update(
                "UPDATE core_usuario SET fecha_fin_vigencia = current_date + 5 WHERE id = :id",
                Map.of("id", ids.get("practicante")));
        jdbc.update(
                "UPDATE core_usuario SET bloqueado_hasta = now() + interval '10 minutes'"
                        + " WHERE id = :id",
                Map.of("id", ids.get("capataz")));
        var lista =
                cuerpo(
                        http.perform(get(USUARIOS).cookie(jefe))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.count").value(4))
                                .andReturn());
        for (var u : lista.get("results")) {
            String nombre = u.get("nombre").asText();
            String destacados = u.get("destacados").toString();
            switch (nombre) {
                case "ingeniero" -> assertTrue(destacados.contains("sin-uso"));
                case "practicante" -> assertTrue(destacados.contains("vence-pronto"));
                case "capataz" -> assertEquals("bloqueada", u.get("estado").asText());
                default -> assertFalse(destacados.contains("sin-uso"));
            }
        }
        http.perform(get(USUARIOS + "?estado=bloqueada").cookie(jefe))
                .andExpect(jsonPath("$.count").value(1));
        http.perform(get(USUARIOS + "?vigencia_proxima=true").cookie(jefe))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void lasCuentasVencidasSeDesactivanSolas() throws Exception {
        var sesionPracticante = sesion("practicante");
        jdbc.update(
                "UPDATE core_usuario SET fecha_fin_vigencia = current_date - 1 WHERE id = :id",
                Map.of("id", ids.get("practicante")));
        assertEquals(1, servicio.desactivarVencidas());
        assertEquals(0, servicio.desactivarVencidas());
        http.perform(get("/api/v1/core/auth/sesion/").cookie(sesionPracticante))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editarDesbloquearYConsultarElHistorialDeLaCuenta() throws Exception {
        var jefe = sesion("jefe");
        long id = ids.get("capataz");
        jdbc.update(
                "UPDATE core_usuario SET bloqueado_hasta = now() + interval '10 minutes'"
                        + " WHERE id = :id",
                Map.of("id", id));
        http.perform(
                        escribir(
                                patch(USUARIOS + id + "/"),
                                jefe,
                                "{\"nombre\": \"Capataz Pérez\", \"telefono\": \"999111222\","
                                        + " \"fecha_fin_vigencia\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Capataz Pérez"));
        http.perform(escribir(post(USUARIOS + id + "/desbloquear/"), jefe, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("activa"));
        http.perform(get(USUARIOS + id + "/historial/").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].valores_nuevos.nombre").value("Capataz Pérez"))
                .andExpect(jsonPath("$[1].usuario_nombre").value("jefe"));
    }

    @Test
    void elRegistroDeAccesosListaExitosFallosYRechazos() throws Exception {
        http.perform(login("ingeniero" + DOMINIO, "mala-clave-1"));
        var practicante = sesion("practicante");
        http.perform(get(USUARIOS).cookie(practicante)).andExpect(status().isForbidden());
        var jefe = sesion("jefe");
        http.perform(get("/api/v1/core/accesos/").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
        http.perform(get("/api/v1/core/accesos/?resultado=SinPermiso").cookie(jefe))
                .andExpect(jsonPath("$.results[0].usuario_id").value(ids.get("practicante")))
                .andExpect(jsonPath("$.results[0].ruta").value("GET " + USUARIOS));
    }

    private int cambios(String accion) {
        Integer n =
                jdbc.queryForObject(
                        "SELECT count(*) FROM core_registro_auditoria WHERE accion = :a",
                        Map.of("a", accion),
                        Integer.class);
        return n == null ? 0 : n;
    }
}
