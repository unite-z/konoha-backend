package pe.edu.pucp.areasverdes.mod03;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Qué incidencias ve cada quien y la búsqueda de la bandeja (AV-407). El jefe ve todas; la capataz
 * ({@code sinrol}, «Marta R.») solo las que tiene asignadas, las de los encargos de su cuadrilla y
 * las que reportó ella.
 */
class IncidenciasPorAlcanceApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/incidencias/";
    static final String ACTIVIDAD = "Retiro de rama AV-407";

    @Autowired ObjectMapper json;

    long asignada;
    long deSuCuadrilla;
    long ajena;
    long marta;
    long usuarioMarta;
    Cookie capataz;

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        sql.update(
                "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                        + " usuario_creacion_id, usuario_modificacion_id) VALUES ('Jardín AV-407',"
                        + " 'Biblioteca', 'Campus', 120, 1, 1)");
        sql.update("INSERT INTO mod05_areaverde (nombre, activo) VALUES ('Jardín AV-407', true)");
        sql.update(
                "INSERT INTO mod04_tipo_actividad (nombre, activo) SELECT ?, true WHERE NOT EXISTS"
                        + " (SELECT 1 FROM mod04_tipo_actividad WHERE nombre = ?)",
                ACTIVIDAD,
                ACTIVIDAD);
        usuarioMarta =
                sql.queryForObject(
                        "SELECT id FROM core_usuario WHERE nombre = 'sinrol'", Long.class);
        marta =
                sql.queryForObject(
                        "INSERT INTO mod04_personal (nombres, apellidos, tipo_personal, usuario_id)"
                                + " VALUES ('Marta', 'Ríos', 'Capataz', ?) RETURNING id",
                        Long.class,
                        usuarioMarta);
        sql.update(
                "INSERT INTO mod04_cuadrilla (nombre, capataz_id, usuario_creacion_id,"
                        + " usuario_modificacion_id) VALUES ('Cuadrilla AV-407', ?, 1, 1)",
                marta);

        asignada = registrar("Rama caída", "Rama sobre el paso");
        sql.update("UPDATE mod03_incidencia SET capataz_id = ? WHERE id = ?", marta, asignada);

        // Un encargo de su cuadrilla, aunque la incidencia ya no figure con ella como responsable.
        deSuCuadrilla = registrar("Rama caída", "Árbol inclinado");
        sql.update(
                "UPDATE mod03_incidencia SET estado = 'Clasificada', via = 'PersonalPropio'"
                        + " WHERE id = ?",
                deSuCuadrilla);
        http.perform(
                        post(RUTA + deSuCuadrilla + "/encargo/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        ("{\"capataz_id\": %d, \"espacio\": \"Jardín AV-407\","
                                             + " \"actividad\": \"%s\", \"plazo\": \"%s\","
                                             + " \"incluye\": [\"Foto\"], \"avisar_capataz\":"
                                             + " false}")
                                                .formatted(
                                                        marta,
                                                        ACTIVIDAD,
                                                        LocalDate.now(LIMA).plusDays(1))))
                .andExpect(status().isOk());
        sql.update("UPDATE mod03_incidencia SET capataz_id = NULL WHERE id = ?", deSuCuadrilla);

        ajena = registrar("Rama caída", "Poste torcido");

        // La capataz entra con su cuenta de MOD-01 (sin copia usable en mod05_usuario).
        sql.update(
                "UPDATE core_usuario SET credencial_hash = ? WHERE id = ?",
                claves.encode(CLAVE),
                usuarioMarta);
        capataz =
                http.perform(
                                post("/api/v1/core/auth/login/")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"identificador\":"
                                                        + " \"mod05-sinrol@prueba.pucp.edu.pe\","
                                                        + " \"contrasena\": \"%s\"}"
                                                                .formatted(CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getCookie(SeguridadMod01.COOKIE_SESION);
    }

    long registrar(String tipo, String descripcion) throws Exception {
        String ficha =
                http.perform(
                                post(RUTA)
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                ("{\"canal\": \"Telefono\", \"tipo\": \"%s\","
                                                                + " \"reportado_por\": {\"nombre\":"
                                                                + " \"Ana\"}, \"espacio\": \"Jardín"
                                                                + " AV-407\", \"descripcion\":"
                                                                + " \"%s\"}")
                                                        .formatted(tipo, descripcion)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(ficha).get("id").asLong();
    }

    ResultActions bandejaJefe(String q) throws Exception {
        var peticion = get(RUTA).with(httpBasic("jefe", CLAVE));
        if (q != null) peticion.queryParam("q", q);
        return http.perform(peticion);
    }

    ResultActions bandejaCapataz(String q) throws Exception {
        var peticion = get(RUTA).cookie(capataz);
        if (q != null) peticion.queryParam("q", q);
        return http.perform(peticion);
    }

    @Test
    void elJefeVeTodasLasIncidencias() throws Exception {
        bandejaJefe(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(
                        jsonPath("$[*].id")
                                .value(
                                        containsInAnyOrder(
                                                (int) asignada, (int) deSuCuadrilla, (int) ajena)));
    }

    @Test
    void laCapatazSoloVeLasAsignadasYLasDeSuCuadrilla() throws Exception {
        bandejaCapataz(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(
                        jsonPath("$[*].id")
                                .value(containsInAnyOrder((int) asignada, (int) deSuCuadrilla)));
    }

    @Test
    void laFichaDeUnaIncidenciaAjenaNoExisteParaLaCapataz() throws Exception {
        http.perform(get(RUTA + ajena + "/").cookie(capataz))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("incidencia-no-encontrada"));
        http.perform(get(RUTA + asignada + "/").cookie(capataz)).andExpect(status().isOk());
        http.perform(get(RUTA + deSuCuadrilla + "/").cookie(capataz)).andExpect(status().isOk());
        http.perform(get(RUTA + ajena + "/").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk());
    }

    @Test
    void laBusquedaFiltraPorDescripcionTipoYCodigo() throws Exception {
        bandejaJefe("poste")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value((int) ajena));
        bandejaJefe("RAMA CAÍDA").andExpect(jsonPath("$", hasSize(3)));
        bandejaJefe("no-existe").andExpect(jsonPath("$", hasSize(0)));
        String codigo =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT codigo FROM mod03_incidencia WHERE id = ?",
                                String.class,
                                asignada);
        bandejaJefe(codigo)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value((int) asignada));
    }

    @Test
    void laBusquedaRespetaElAlcanceYTrataLosComodinesComoTexto() throws Exception {
        bandejaCapataz("poste").andExpect(jsonPath("$", hasSize(0)));
        bandejaCapataz("árbol").andExpect(jsonPath("$", hasSize(1)));
        bandejaJefe("%").andExpect(jsonPath("$", hasSize(0)));
        bandejaJefe("_").andExpect(jsonPath("$", hasSize(0)));
        bandejaJefe("x".repeat(101)).andExpect(status().isBadRequest());
    }
}
