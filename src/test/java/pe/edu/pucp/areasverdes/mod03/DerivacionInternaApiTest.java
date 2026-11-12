package pe.edu.pucp.areasverdes.mod03;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Derivar una incidencia clasificada a un encargo directo de un capataz (AV-277, RF-INC-003): crea
 * el encargo de MOD-04 con el plazo, lo vincula y deja la incidencia Derivada · interna. «Marta R.»
 * tiene la «Cuadrilla AV-277» con un operario y cuenta de capataz ({@code sinrol}).
 */
class DerivacionInternaApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/incidencias/";
    static final String ACTIVIDAD = "Retiro de rama AV-277";

    @Autowired ObjectMapper json;

    long incidencia;
    long marta;
    long cuadrilla;

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        sql.update(
                "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                        + " usuario_creacion_id, usuario_modificacion_id) VALUES ('Jardín AV-277',"
                        + " 'Biblioteca', 'Campus', 120, 1, 1)");
        sql.update("INSERT INTO mod05_areaverde (nombre, activo) VALUES ('Jardín AV-277', true)");
        sql.update(
                "INSERT INTO mod04_tipo_actividad (nombre, activo) SELECT ?, true WHERE NOT EXISTS"
                        + " (SELECT 1 FROM mod04_tipo_actividad WHERE nombre = ?)",
                ACTIVIDAD,
                ACTIVIDAD);
        long sinrol =
                sql.queryForObject(
                        "SELECT id FROM core_usuario WHERE nombre = 'sinrol'", Long.class);
        marta =
                sql.queryForObject(
                        "INSERT INTO mod04_personal (nombres, apellidos, tipo_personal, usuario_id)"
                                + " VALUES ('Marta', 'Ríos', 'Capataz', ?) RETURNING id",
                        Long.class,
                        sinrol);
        cuadrilla =
                sql.queryForObject(
                        "INSERT INTO mod04_cuadrilla (nombre, capataz_id, usuario_creacion_id,"
                                + " usuario_modificacion_id) VALUES ('Cuadrilla AV-277', ?, 1, 1)"
                                + " RETURNING id",
                        Long.class,
                        marta);
        long operario =
                sql.queryForObject(
                        "INSERT INTO mod04_personal (nombres, apellidos, tipo_personal) VALUES"
                                + " ('Luis', 'Paz', 'Operario') RETURNING id",
                        Long.class);
        sql.update(
                "INSERT INTO mod04_asignacion_cuadrilla (cuadrilla_id, operario_id, fecha_inicio)"
                        + " VALUES (?, ?, current_date - 1)",
                cuadrilla,
                operario);
        String ficha =
                http.perform(
                                post(RUTA)
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"canal\": \"Telefono\", \"tipo\": \"Rama"
                                                    + " caída\", \"reportado_por\": {\"nombre\":"
                                                    + " \"Ana\"}, \"espacio\": \"Jardín AV-277\","
                                                    + " \"descripcion\": \"Rama sobre el paso\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        incidencia = json.readTree(ficha).get("id").asLong();
        sql.update(
                "UPDATE mod03_incidencia SET estado = 'Clasificada', via = 'PersonalPropio'"
                        + " WHERE id = ?",
                incidencia);
    }

    String manana() {
        return LocalDate.now(LIMA).plusDays(1).toString();
    }

    ResultActions encargar(String usuario, String cuerpo) throws Exception {
        return http.perform(
                post(RUTA + incidencia + "/encargo/")
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    String cuerpo(long capataz, String actividad, String plazo) {
        return ("{\"capataz_id\": %d, \"espacio\": \"Jardín AV-277\", \"actividad\": \"%s\","
                        + " \"plazo\": \"%s\", \"incluye\": [\"Foto\", \"Descripción\"],"
                        + " \"avisar_capataz\": true}")
                .formatted(capataz, actividad, plazo);
    }

    @Test
    void elEncargoQuedaVinculadoYLaIncidenciaDerivadaInterna() throws Exception {
        http.perform(get(RUTA + "actividades/").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasItem(ACTIVIDAD)));
        encargar("jefe", cuerpo(marta, ACTIVIDAD, manana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DerivadaInterna"))
                .andExpect(jsonPath("$.via").value("PersonalPropio"))
                .andExpect(jsonPath("$.responsable").value("Marta R."))
                .andExpect(
                        jsonPath("$.historial[0].texto").value(startsWith("Derivada · interna")));
        Map<String, Object> encargo =
                jdbc.queryForMap(
                        """
                        SELECT e.periodicidad, e.plazo_previsto, e.fecha_reunion, e.estado,
                            e.cuadrilla_id, e.descripcion
                        FROM mod03_encargo_incidencia v JOIN mod04_encargo e ON e.id = v.encargo_id
                        WHERE v.incidencia_id = :id
                        """,
                        Map.of("id", incidencia));
        assertEquals("Directo", encargo.get("periodicidad"));
        assertEquals(manana(), encargo.get("plazo_previsto").toString());
        assertEquals(null, encargo.get("fecha_reunion"));
        assertEquals("Por completar", encargo.get("estado"));
        assertEquals(cuadrilla, ((Number) encargo.get("cuadrilla_id")).longValue());
        assertEquals(
                true,
                ((String) encargo.get("descripcion")).contains("Incluye: Foto, Descripción."));
        // El capataz recibe el aviso (avisar_capataz) y la cuadrilla queda con un encargo ese día.
        http.perform(get("/api/v1/planificacion/avisos/").with(httpBasic("sinrol", CLAVE)))
                .andExpect(jsonPath("$[0].texto").value(containsString("encargo ENC-")));
        http.perform(
                        get(RUTA + incidencia + "/capataces/?fecha=" + manana())
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$[0].carga.asignados").value(1))
                .andExpect(jsonPath("$[0].carga.capacidad").value(1));
        // Ya derivada: otro encargo es una transición inválida.
        encargar("jefe", cuerpo(marta, ACTIVIDAD, manana()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("transicion-invalida"));
    }

    @Test
    void validaCapatazActividadPlazoYPermiso() throws Exception {
        encargar("ingeniero", cuerpo(marta, ACTIVIDAD, manana())).andExpect(status().isForbidden());
        encargar("jefe", cuerpo(999999, ACTIVIDAD, manana()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("capataz-invalido"));
        encargar("jefe", cuerpo(marta, "No existe", manana()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("actividad-invalida"));
        encargar("jefe", cuerpo(marta, ACTIVIDAD, "2020-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("plazo-pasado"));
        jdbc.getJdbcTemplate()
                .update("UPDATE mod05_areaverde SET activo = false WHERE nombre = 'Jardín AV-277'");
        encargar("jefe", cuerpo(marta, ACTIVIDAD, manana()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("espacio-sin-equivalente"));
        assertEquals(
                0,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_encargo_incidencia WHERE incidencia_id"
                                        + " = ?",
                                Integer.class,
                                incidencia));
        http.perform(get(RUTA + incidencia + "/").with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.estado").value("Clasificada"));
    }

    /** Deriva la incidencia a Marta y devuelve el id del encargo. */
    long derivada() throws Exception {
        encargar("jefe", cuerpo(marta, ACTIVIDAD, manana())).andExpect(status().isOk());
        return jdbc.getJdbcTemplate()
                .queryForObject(
                        "SELECT max(encargo_id) FROM mod03_encargo_incidencia WHERE incidencia_id ="
                                + " ?",
                        Long.class,
                        incidencia);
    }

    /** Lo que hace MOD-04 al cambiar el estado del encargo (AV-247). */
    void encargoA(long encargo, String estado, String observaciones) {
        var sql = jdbc.getJdbcTemplate();
        String anterior =
                sql.queryForObject(
                        "SELECT estado FROM mod04_encargo WHERE id = ?", String.class, encargo);
        sql.update("UPDATE mod04_encargo SET estado = ? WHERE id = ?", estado, encargo);
        sql.update(
                "INSERT INTO mod04_encargo_cambio_estado (encargo_id, estado_anterior,"
                        + " estado_nuevo, observaciones, usuario_id, fecha) VALUES (?, ?, ?, ?, 1,"
                        + " now())",
                encargo,
                anterior,
                estado,
                observaciones);
    }

    ResultActions ficha() throws Exception {
        return http.perform(get(RUTA + incidencia + "/").with(httpBasic("jefe", CLAVE)));
    }

    @Test
    void alCompletarseElEncargoLaIncidenciaQuedaAtendidaPorSistema() throws Exception {
        long encargo = derivada();
        encargoA(encargo, "En curso", "");
        ficha().andExpect(jsonPath("$.estado").value("EnAtencion"));
        encargoA(encargo, "Completado", "Rama retirada");
        ficha().andExpect(jsonPath("$.estado").value("Atendida"))
                .andExpect(jsonPath("$.atencion.atendida_por").value("Sistema"))
                .andExpect(jsonPath("$.atencion.encargo_codigo").value("ENC-" + encargo))
                .andExpect(jsonPath("$.encargo_cancelado").isEmpty());
        // Consultar otra vez no repite la transición.
        ficha().andExpect(jsonPath("$.estado").value("Atendida"));
        assertEquals(
                1,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_historial_incidencia WHERE"
                                        + " incidencia_id = ? AND estado = 'Atendida'",
                                Integer.class,
                                incidencia));
    }

    @Test
    void alCancelarseVuelveAClasificadaConElMotivoYSePuedeDerivarDeNuevo() throws Exception {
        long encargo = derivada();
        encargoA(encargo, "Cancelado", "Sin acceso al jardín");
        ficha().andExpect(jsonPath("$.estado").value("Clasificada"))
                .andExpect(jsonPath("$.encargo_cancelado.codigo").value("ENC-" + encargo))
                .andExpect(jsonPath("$.encargo_cancelado.motivo").value("Sin acceso al jardín"));
        long otro = derivada();
        ficha().andExpect(jsonPath("$.estado").value("DerivadaInterna"))
                .andExpect(jsonPath("$.encargo_cancelado").isEmpty());
        assertEquals(
                2,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_encargo_incidencia WHERE incidencia_id"
                                        + " = ?",
                                Integer.class,
                                incidencia));
        assertEquals(true, otro != encargo);
    }
}
