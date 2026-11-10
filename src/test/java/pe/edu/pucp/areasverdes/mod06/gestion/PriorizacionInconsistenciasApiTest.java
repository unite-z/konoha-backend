package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod06.apoyo.ClienteIAFalso;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;
import pe.edu.pucp.areasverdes.mod06.ia.FabricaClienteIA;

/**
 * RN-IA-002 y RF-IA-002 contra PostgreSQL, con {@link ClienteIAFalso}: nunca se llama a Gemini.
 * Escenario: {@code escenario-gestion.sql} más dos áreas de inventario con el nombre de las de
 * MOD-05 y un segundo proveedor con la misma razón social escrita de otra forma.
 */
@Import(PriorizacionInconsistenciasApiTest.IaFalsa.class)
class PriorizacionInconsistenciasApiTest extends ApiGestion {

    /** Fábrica que devuelve el cliente que fije cada prueba. */
    static class FabricaFalsa extends FabricaClienteIA {
        ClienteIA cliente;

        FabricaFalsa(Mod06Propiedades p) {
            super(p);
        }

        @Override
        public ClienteIA obtenerClienteIa() {
            if (cliente == null)
                throw new ServicioIANoDisponible("El análisis con IA no está configurado");
            return cliente;
        }
    }

    @TestConfiguration
    static class IaFalsa {
        @Bean
        @Primary
        FabricaFalsa fabricaFalsa(Mod06Propiedades p) {
            return new FabricaFalsa(p);
        }
    }

    @Autowired FabricaFalsa fabrica;

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
        jdbc.getJdbcTemplate().execute("TRUNCATE mod06_sugerencia_ia RESTART IDENTITY");
        jdbc.getJdbcTemplate()
                .execute(
                        "DELETE FROM mod02_area_verde WHERE nombre IN ('Jardín Central', 'Plaza de"
                                + " Armas')");
        jdbc.getJdbcTemplate()
                .execute(
                        """
                        INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2, usuario_creacion_id, usuario_modificacion_id)
                        VALUES ('Jardín Central', 'Campus', 'Centro', 500, 1, 1), ('Plaza de Armas', 'Campus', 'Norte', 300, 1, 1)
                        """);
        jdbc.getJdbcTemplate()
                .execute(
                        "INSERT INTO mod05_proveedor (id, razon_social, ruc, activo) VALUES (99,"
                                + " ' JARDINES  del Sur S.A.C. (prueba)', '20100000099', true)");
        fabrica.cliente = null;
    }

    ResultActions publicar(String usuario, String ruta, String cuerpo) throws Exception {
        return http.perform(
                post("/api/v1/ia/" + ruta)
                        .cookie(sesion(usuario))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private long idArea(String nombre) {
        return jdbc.queryForObject(
                "SELECT id FROM mod02_area_verde WHERE nombre = :n",
                Map.of("n", nombre),
                Long.class);
    }

    // --- RN-IA-002
    // --------------------------------------------------------------------------------

    private long priorizacion() throws Exception {
        String central = "area-" + idArea("Jardín Central");
        fabrica.cliente =
                new ClienteIAFalso(
                        Map.of(
                                "elementos",
                                List.of(
                                        Map.of(
                                                "clave",
                                                central,
                                                "prioridad_sugerida",
                                                "Alta",
                                                "puntuacion",
                                                90,
                                                "motivos",
                                                List.of("Atención atrasada"))),
                                "explicacion",
                                "Prueba",
                                "limitaciones",
                                List.of()));
        String respuesta =
                publicar(
                                "ingeniero",
                                "priorizaciones/",
                                "{\"alcance\": \"Areas\", \"horizonte_dias\": 15}")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.estado").value("Propuesta"))
                        .andExpect(jsonPath("$.etiqueta_estado").value("Pendiente de revisión"))
                        .andExpect(jsonPath("$.contenido.elementos[0].clave").value(central))
                        .andExpect(
                                jsonPath("$.contenido.elementos[0].prioridad_sugerida")
                                        .value("Alta"))
                        .andExpect(
                                jsonPath("$.contenido.elementos[0].sustento.incidencias_abiertas")
                                        .value(0))
                        .andExpect(jsonPath("$.contenido.suficiencia").value("parcial"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return Long.parseLong(respuesta.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    @Test
    void laPriorizacionSeValidaElementoPorElementoUnaSolaVez() throws Exception {
        long id = priorizacion();
        String central = "area-" + idArea("Jardín Central");
        String plaza = "area-" + idArea("Plaza de Armas");

        publicar(
                        "jefe",
                        "priorizaciones/" + id + "/validar/",
                        "{\"por_elemento\": {\"" + central + "\": {\"accion\": \"aceptar\"}}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("decisiones-incompletas"))
                .andExpect(jsonPath("$.errores['" + plaza + "']").exists());

        String todas =
                "{\"por_elemento\": {\""
                        + central
                        + "\": {\"accion\": \"aceptar\"}, \""
                        + plaza
                        + "\": {\"accion\": \"corregir\", \"prioridad_final\": \"Alta\"}}}";
        publicar("jefe", "priorizaciones/" + id + "/validar/", todas)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Validada"))
                .andExpect(jsonPath("$.validador_nombre").isNotEmpty())
                .andExpect(jsonPath("$.contenido.decision.resumen.corregidos").value(1));
        publicar("jefe", "priorizaciones/" + id + "/validar/", todas)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("transicion-no-permitida"));
    }

    @Test
    void soloElJefeValidaYLaBandejaSePagina() throws Exception {
        long id = priorizacion();
        publicar("ingeniero", "priorizaciones/" + id + "/rechazar/", "{\"motivo\": \"x\"}")
                .andExpect(status().isForbidden());
        publicar("jefe", "priorizaciones/" + id + "/rechazar/", "{\"motivo\": \"No aplica\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Rechazada"))
                .andExpect(jsonPath("$.contenido.decision.motivo_rechazo").value("No aplica"));
        http.perform(
                        get("/api/v1/ia/priorizaciones/?estado=Rechazada")
                                .cookie(sesion("practicante")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void sinIaResponde503YNoGuardaNada() throws Exception {
        publicar("jefe", "priorizaciones/", "{\"alcance\": \"Ambos\", \"horizonte_dias\": 7}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("ia-no-disponible"));
        http.perform(get("/api/v1/ia/priorizaciones/").cookie(sesion("jefe")))
                .andExpect(jsonPath("$.count").value(0));
    }

    // --- RF-IA-002
    // --------------------------------------------------------------------------------

    @Test
    void elAnalisisDeProveedoresDetectaLaRazonSocialDuplicadaYSeAcepta() throws Exception {
        fabrica.cliente =
                new ClienteIAFalso(
                        Map.of(
                                "hallazgos",
                                List.of(
                                        Map.of(
                                                "indice",
                                                0,
                                                "descripcion",
                                                "Conserva el primero",
                                                "confianza",
                                                "Alta",
                                                "explicacion",
                                                "Misma razón social")),
                                "limitaciones",
                                List.of()));
        String respuesta =
                publicar(
                                "ingeniero",
                                "inconsistencias/analisis/",
                                "{\"conjunto\": \"Proveedores\"}")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.hallazgos").value(1))
                        .andExpect(
                                jsonPath("$.sugerencias[0].contenido.tipo_hallazgo")
                                        .value("Duplicado"))
                        .andExpect(jsonPath("$.sugerencias[0].contenido.confianza").value("Alta"))
                        .andExpect(
                                jsonPath(
                                                "$.sugerencias[0].contenido.propuesta.registro_conservar_id")
                                        .value(1))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id =
                Long.parseLong(
                        respuesta.replaceAll("(?s).*?\"sugerencias\":\\[\\{\"id\":(\\d+).*", "$1"));
        publicar(
                        "jefe",
                        "inconsistencias/" + id + "/validar/",
                        "{\"comentario\": \"Se unificará en MOD-05\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.decision.accion").value("aceptar"))
                .andExpect(
                        jsonPath("$.contenido.decision.comentario")
                                .value("Se unificará en MOD-05"));
        // Aceptar solo registra la decisión: los dos proveedores siguen.
        org.junit.jupiter.api.Assertions.assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod05_proveedor", Map.of(), Integer.class));
        http.perform(get("/api/v1/ia/inconsistencias/?conjunto=Proveedores").cookie(sesion("jefe")))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void unConjuntoDesconocidoEs400() throws Exception {
        fabrica.cliente = new ClienteIAFalso(Map.of());
        publicar("jefe", "inconsistencias/analisis/", "{\"conjunto\": \"Otro\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.conjunto").exists());
    }
}
