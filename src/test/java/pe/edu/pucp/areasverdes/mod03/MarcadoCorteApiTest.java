package pe.edu.pucp.areasverdes.mod03;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.AreasVerdesApplication;

/**
 * Ruta de hoy y marcado de jardines (AV-260, RF-COR-002). Hoy hay un corte con la zona Sociales; el
 * ingeniero (id 2) lleva ese sector en MOD-02 y el practicante no lleva ninguno.
 */
class MarcadoCorteApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/";
    static final LocalDate HOY = LocalDate.now(AreasVerdesApplication.LIMA);

    @Autowired ObjectMapper json;

    long ciclo;
    long jardin;
    long patio;

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        sql.execute(
                new ClassPathResource("mod03/escenario-programacion.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
        long sociales = sector("Sociales");
        jardin = area("Jardín de Sociales", sociales);
        patio = area("Patio de Sociales", sociales);
        area("Jardín de Ciencias", sector("Ciencias"));
        sql.update(
                "INSERT INTO mod02_historial_capataz_sector (sector_id, capataz_id, usuario_id)"
                        + " VALUES (?, 2, 1)",
                sociales);
        String creado =
                http.perform(
                                post(RUTA + "programaciones/")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                ("{\"tipo_servicio_id\": 1,"
                                                     + " \"fecha_inicio_prevista\": \"%s\","
                                                     + " \"fecha_fin_prevista\": \"%s\", \"zonas\":"
                                                     + " [\"Sociales\"]}")
                                                        .formatted(HOY, HOY)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        ciclo = json.readTree(creado).get("id").asLong();
    }

    long sector(String nombre) {
        return jdbc.getJdbcTemplate()
                .queryForObject(
                        "INSERT INTO mod02_sector (nombre, usuario_creacion_id,"
                                + " usuario_modificacion_id) VALUES (?, 1, 1) RETURNING id",
                        Long.class,
                        nombre);
    }

    long area(String nombre, long sector) {
        var sql = jdbc.getJdbcTemplate();
        long id =
                sql.queryForObject(
                        "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                                + " usuario_creacion_id, usuario_modificacion_id) VALUES (?, ?,"
                                + " 'Campus', 100, 1, 1) RETURNING id",
                        Long.class,
                        nombre,
                        nombre);
        sql.update(
                "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, usuario_id)"
                        + " VALUES (?, ?, 1)",
                id,
                sector);
        return id;
    }

    ResultActions marcar(String usuario, UUID clave, String marcadoEn) throws Exception {
        return http.perform(
                post(RUTA + "ciclos/" + ciclo + "/jardines/" + jardin + "/marca/")
                        .with(httpBasic(usuario, CLAVE))
                        .header("Idempotency-Key", clave.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marcado_en\": \"" + marcadoEn + "\"}"));
    }

    @Test
    void laRutaDeHoyTraeLosJardinesDeSusSectores() throws Exception {
        http.perform(get(RUTA + "ruta-de-hoy/").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruta.programacion_id").value(ciclo))
                // El código guardado del ciclo (AV-364), el mismo que en Ciclos.
                .andExpect(jsonPath("$.ruta.codigo").value("CC-%d-01".formatted(HOY.getYear())))
                .andExpect(jsonPath("$.ruta.jardines.length()").value(2))
                .andExpect(jsonPath("$.ruta.jardines[0].zona").value("Sociales"));
        http.perform(get(RUTA + "ruta-de-hoy/").with(httpBasic("practicante", CLAVE)))
                .andExpect(jsonPath("$.ruta").isEmpty());
        http.perform(get(RUTA + "ruta-de-hoy/").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unReenvioDevuelveLaMarcaYOtroUsuarioRecibe409() throws Exception {
        UUID clave = UUID.randomUUID();
        String hora = HOY + "T10:18:00-05:00";
        marcar("ingeniero", clave, hora)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Cortado"))
                .andExpect(jsonPath("$.marcado_por").value("ingeniero"))
                .andExpect(jsonPath("$.marcado_en").value(startsWith(HOY + "T10:18")));
        marcar("ingeniero", clave, hora).andExpect(status().isOk());
        marcar("practicante", UUID.randomUUID(), hora)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ya-marcado"))
                .andExpect(jsonPath("$.detalle").value(containsString("ingeniero a las 10:18")));
    }

    @Test
    void laObservacionNoTocaLaMarca() throws Exception {
        String ruta = RUTA + "ciclos/" + ciclo + "/jardines/" + jardin + "/observaciones/";
        http.perform(
                        post(ruta)
                                .with(httpBasic("ingeniero", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"texto\": \"Riego encendido\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("Riego encendido"));
        http.perform(
                        post(ruta)
                                .with(httpBasic("ingeniero", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        http.perform(get(RUTA + "ciclos/" + ciclo + "/").with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.jardines[0].estado").value("Pendiente"));
    }

    @Test
    void fueraDeRutaPideNotaYNoSeDuplica() throws Exception {
        String encontrado =
                http.perform(
                                get(RUTA + "ruta-de-hoy/fuera-de-ruta/?q=ciencias")
                                        .with(httpBasic("ingeniero", CLAVE)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.count").value(1))
                        .andExpect(jsonPath("$.results[0].zona").value("Ciencias"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long ciencias = json.readTree(encontrado).get("results").get(0).get("id").asLong();
        String ruta = RUTA + "ciclos/" + ciclo + "/fuera-de-ruta/";
        UUID clave = UUID.randomUUID();
        String cuerpo =
                "{\"jardin_id\": %d, \"marcado_en\": \"%sT11:00:00-05:00\", \"nota\": \"%s\"}";
        http.perform(
                        post(ruta)
                                .with(httpBasic("ingeniero", CLAVE))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo.formatted(ciencias, HOY, " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("nota-requerida"));
        for (int i = 0; i < 2; i++) {
            http.perform(
                            post(ruta)
                                    .with(httpBasic("ingeniero", CLAVE))
                                    .header("Idempotency-Key", clave.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(cuerpo.formatted(ciencias, HOY, "Lo pidió el decano")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.jardin.nombre").value("Jardín de Ciencias"));
        }
        assertEquals(
                1,
                jdbc.getJdbcTemplate()
                        .queryForObject("SELECT count(*) FROM mod03_fuera_de_ruta", Integer.class));
    }

    @Test
    void elAvanceCuentaLosCortadosPorDiaYAvisaSiEstaDesactualizado() throws Exception {
        String avance = RUTA + "ciclos/" + ciclo + "/avance/";
        http.perform(get(avance).with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.porcentaje").value(0))
                .andExpect(jsonPath("$.por_cortar.length()").value(2))
                .andExpect(jsonPath("$.ultima_marca").isEmpty())
                .andExpect(jsonPath("$.fecha_inicio_prevista").value(HOY.toString()));
        marcar("ingeniero", UUID.randomUUID(), HOY.minusDays(2) + "T09:00:00-05:00")
                .andExpect(status().isOk());
        http.perform(get(avance).with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.cortados").value(1))
                .andExpect(jsonPath("$.porcentaje").value(50))
                .andExpect(jsonPath("$.por_dia[0].fecha").value(HOY.minusDays(2).toString()))
                .andExpect(jsonPath("$.por_dia[0].cortados").value(1))
                .andExpect(jsonPath("$.por_cortar.length()").value(1))
                .andExpect(jsonPath("$.desactualizado").value(true));
    }

    ResultActions accion(String usuario, String ruta, String cuerpo) throws Exception {
        return http.perform(
                post(RUTA + ruta)
                        .with(httpBasic(usuario, CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    @Test
    void laLimpiezaPideLaRutaCompletaYElCierreTambien() throws Exception {
        String limpiezas = "ciclos/" + ciclo + "/limpiezas/";
        String hoy = "{\"fecha\": \"" + HOY + "\"}";
        marcar("ingeniero", UUID.randomUUID(), HOY + "T09:00:00-05:00").andExpect(status().isOk());
        accion("ingeniero", limpiezas, hoy)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ruta-incompleta"))
                .andExpect(jsonPath("$.detalle").value(containsString("Faltan 1 jardines")));
        accion("jefe", "programaciones/" + ciclo + "/inicio/", hoy).andExpect(status().isOk());
        accion("jefe", "programaciones/" + ciclo + "/fin/", hoy)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ruta-incompleta"));
        jardin = patio;
        marcar("ingeniero", UUID.randomUUID(), HOY + "T11:00:00-05:00").andExpect(status().isOk());
        accion("ingeniero", limpiezas, hoy)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autor").value("ingeniero"));
        http.perform(get(RUTA + "ciclos/" + ciclo + "/").with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.limpiezas.length()").value(1))
                .andExpect(jsonPath("$.dias_corte").value(1))
                .andExpect(jsonPath("$.maximo_dias").value(4))
                .andExpect(jsonPath("$.cumple").value(true));
        accion("jefe", "programaciones/" + ciclo + "/fin/", hoy)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Completada"));
    }

    @Test
    void laInterrupcionTrasladaElCorteYDescuentaElTiempo() throws Exception {
        area("Jardín de Ciencias Norte", sector("Ciencias Norte"));
        jdbc.getJdbcTemplate()
                .update(
                        "INSERT INTO mod05_areaverde (nombre, activo) SELECT n, true FROM"
                                + " unnest(ARRAY['Jardín de Sociales', 'Patio de Sociales']) n"
                                + " WHERE NOT EXISTS (SELECT 1 FROM mod05_areaverde v WHERE"
                                + " v.nombre = n)");
        jdbc.getJdbcTemplate()
                .update(
                        "DELETE FROM mod03_restriccion_calendario WHERE descripcion = 'Ruido"
                                + " durante el examen'");
        String creado =
                accion(
                                "jefe",
                                "programaciones/",
                                ("{\"tipo_servicio_id\": 1, \"fecha_inicio_prevista\": \"%s\","
                                                + " \"fecha_fin_prevista\": \"%s\", \"zonas\":"
                                                + " [\"Sociales\", \"Ciencias Norte\"]}")
                                        .formatted(HOY, HOY))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long otro = json.readTree(creado).get("id").asLong();
        http.perform(
                        get(RUTA + "programaciones/" + otro + "/zonas-alternativas/")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        String cuerpo =
                "{\"zona_afectada\": \"Sociales\", \"zona_alternativa\": \"%s\", \"hora\":"
                        + " \"%sT13:00:00-05:00\", \"motivo\": \"Ruido durante el examen\","
                        + " \"aviso_proveedor\": true, \"registrar_restriccion\": true}";
        accion(
                        "ingeniero",
                        "programaciones/" + otro + "/interrupciones/",
                        cuerpo.formatted("Ciencias Norte", HOY))
                .andExpect(status().isForbidden());
        accion(
                        "jefe",
                        "programaciones/" + otro + "/interrupciones/",
                        cuerpo.formatted("Letras", HOY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("sin-zona-libre"));
        accion(
                        "jefe",
                        "programaciones/" + otro + "/interrupciones/",
                        cuerpo.formatted("Ciencias Norte", HOY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minutos_descontados").value(240))
                .andExpect(jsonPath("$.registrada_como_restriccion").value(true));
        http.perform(get(RUTA + "ciclos/" + otro + "/").with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.interrupciones.length()").value(1))
                .andExpect(jsonPath("$.dias_interrupcion_descontados").value(0.5))
                .andExpect(
                        jsonPath("$.jardines[?(@.zona == 'Sociales')].detenido_por_interrupcion")
                                .value(
                                        org.hamcrest.Matchers.everyItem(
                                                org.hamcrest.Matchers.is(true))));
        assertEquals(
                2,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_restriccion_calendario WHERE"
                                        + " descripcion = 'Ruido durante el examen'",
                                Integer.class));
    }

    long programarCon(String quien) throws Exception {
        area("Jardín de Letras", sector("Letras"));
        String creado =
                accion(
                                "jefe",
                                "programaciones/",
                                ("{\"tipo_servicio_id\": 2, \"fecha_inicio_prevista\": \"%s\","
                                                + " \"fecha_fin_prevista\": \"%s\", %s"
                                                + " \"zonas\": [\"Sociales\", \"Letras\"]}")
                                        .formatted(HOY, HOY, quien))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(creado).get("id").asLong();
    }

    String interrupcion() {
        return ("{\"zona_afectada\": \"Sociales\", \"zona_alternativa\": \"Letras\", \"hora\":"
                        + " \"%sT10:15:00-05:00\", \"motivo\": \"Examen en el aula\","
                        + " \"aviso_proveedor\": true, \"registrar_restriccion\": false}")
                .formatted(HOY);
    }

    @Test
    void conPersonalPropioElAvisoLlegaAlCapatazDeLaCuadrilla() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        long capataz =
                sql.queryForObject(
                        "INSERT INTO mod04_personal (nombres, apellidos, tipo_personal, usuario_id)"
                                + " VALUES ('Jorge', 'Ramos', 'Capataz', 2) RETURNING id",
                        Long.class);
        long cuadrilla =
                sql.queryForObject(
                        "INSERT INTO mod04_cuadrilla (nombre, capataz_id, usuario_creacion_id,"
                            + " usuario_modificacion_id) VALUES ('Cuadrilla J', ?, 1, 1) RETURNING"
                            + " id",
                        Long.class,
                        capataz);
        long otro = programarCon("\"cuadrilla_id\": " + cuadrilla + ",");
        accion("jefe", "programaciones/" + otro + "/interrupciones/", interrupcion())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aviso_id").isNumber());
        String avisos =
                http.perform(get(RUTA + "avisos/").with(httpBasic("ingeniero", CLAVE)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(1))
                        .andExpect(
                                jsonPath("$[0].texto")
                                        .value(
                                                startsWith(
                                                        "Corte detenido en Sociales a las 10:15")))
                        .andExpect(jsonPath("$[0].leido").value(false))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long aviso = json.readTree(avisos).get(0).get("id").asLong();
        accion("practicante", "avisos/" + aviso + "/leido/", "{}").andExpect(status().isNotFound());
        accion("ingeniero", "avisos/" + aviso + "/leido/", "{}").andExpect(status().isNoContent());
        http.perform(get(RUTA + "avisos/").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(jsonPath("$[0].leido").value(true));
    }

    @Test
    void conProveedorElAvisoQuedaRegistradoParaEl() throws Exception {
        long proveedor =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "INSERT INTO mod05_proveedor(razon_social, activo) VALUES ('Cortes"
                                        + " Demo', true) RETURNING id",
                                Long.class);
        long otro = programarCon("\"proveedor_id\": " + proveedor + ",");
        accion("jefe", "programaciones/" + otro + "/interrupciones/", interrupcion())
                .andExpect(status().isCreated());
        assertEquals(
                1,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_aviso WHERE destinatario_proveedor_id ="
                                        + " ?",
                                Integer.class,
                                proveedor));
    }
}
