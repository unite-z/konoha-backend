package pe.edu.pucp.areasverdes.mod03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Requerimiento de MOD-05 al programar un corte con proveedor (AV-263, CU-010 paso 7). Zonas: Norte
 * (dos jardines, los dos en el catálogo de MOD-05) y Sur (uno que MOD-05 no tiene). «Corte Demo»
 * tiene contrato vigente para el corte; «Sin Contrato Demo», no.
 */
class RequerimientoCorteApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/";

    @Autowired ObjectMapper json;

    long conContrato;
    long sinContrato;
    long contrato;

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        sql.execute(
                new ClassPathResource("mod03/escenario-programacion.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
        sql.update("DELETE FROM mod05_areaverde WHERE nombre LIKE 'AV-263 %'");
        sql.update(
                "INSERT INTO mod05_areaverde (nombre, activo) VALUES ('AV-263 Jardín Norte', true),"
                        + " ('AV-263 Patio Norte', true)");
        jardines("Norte", "AV-263 Jardín Norte", "AV-263 Patio Norte");
        jardines("Sur", "AV-263 Jardín Sur");
        conContrato = proveedor("Corte Demo");
        sinContrato = proveedor("Sin Contrato Demo");
        contrato =
                jdbc.queryForObject(
                        "INSERT INTO mod05_contrato (numero, proveedor_id, tipo_servicio_id,"
                                + " vigencia_inicio, vigencia_fin) VALUES ('C-AV263', :p, 1,"
                                + " current_date - 30, current_date + 365) RETURNING id",
                        Map.of("p", conContrato),
                        Long.class);
    }

    long proveedor(String nombre) {
        return jdbc.queryForObject(
                "INSERT INTO mod05_proveedor (razon_social, activo) VALUES (:n, true) RETURNING id",
                Map.of("n", nombre),
                Long.class);
    }

    void jardines(String zona, String... nombres) {
        var sql = jdbc.getJdbcTemplate();
        long sector =
                sql.queryForObject(
                        "INSERT INTO mod02_sector (nombre, atencion_a_demanda, usuario_creacion_id,"
                                + " usuario_modificacion_id) VALUES (?, false, 1, 1) RETURNING id",
                        Long.class,
                        zona);
        for (String nombre : nombres) {
            long area =
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
                    area,
                    sector);
        }
    }

    ResultActions programar(long proveedor, String zona) throws Exception {
        return http.perform(
                post(RUTA + "programaciones/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                ("{\"tipo_servicio_id\": 1, \"proveedor_id\": %d,"
                                                + " \"fecha_inicio_prevista\": \"2027-01-11\","
                                                + " \"fecha_fin_prevista\": \"2027-01-12\","
                                                + " \"zonas\": [\"%s\"]}")
                                        .formatted(proveedor, zona)));
    }

    @Test
    void programarElCorteCreaSuRequerimientoConLosJardinesDeLaRuta() throws Exception {
        String respuesta =
                programar(conContrato, "Norte")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.requerimiento.codigo").value(startsWith("AT-")))
                        .andExpect(
                                jsonPath("$.requerimiento.estado")
                                        .value("Requerimiento registrado"))
                        .andExpect(jsonPath("$.motivo_sin_requerimiento").value(nullValue()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var ciclo = json.readTree(respuesta);
        long atencion = ciclo.get("requerimiento").get("id").asLong();
        var guardada =
                jdbc.queryForMap(
                        "SELECT origen, clase, contrato_id, descripcion,"
                                + " (SELECT count(*) FROM mod05_alcanceatencion al"
                                + " WHERE al.atencion_id = a.id) alcances"
                                + " FROM mod05_atenciontercerizada a WHERE id = :id",
                        Map.of("id", atencion));
        assertThat(guardada.get("origen")).isEqualTo("ProgramacionEstacional");
        assertThat(guardada.get("clase")).isEqualTo("ProgramadaContrato");
        assertThat(((Number) guardada.get("contrato_id")).longValue()).isEqualTo(contrato);
        assertThat(((Number) guardada.get("alcances")).intValue()).isEqualTo(2);
        assertThat((String) guardada.get("descripcion"))
                .startsWith("Corte de césped " + ciclo.get("codigo").asText())
                .contains("del 11/01/2027 al 12/01/2027, con 2 jardines");
        http.perform(
                        get(RUTA + "programaciones/" + ciclo.get("id").asLong() + "/")
                                .with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requerimiento.id").value(atencion));
    }

    @Test
    void sinContratoVigenteElCorteSeProgramaYDiceElMotivo() throws Exception {
        programar(sinContrato, "Norte")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requerimiento").value(nullValue()))
                .andExpect(
                        jsonPath("$.motivo_sin_requerimiento")
                                .value(RequerimientoCorte.SIN_CONTRATO));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM mod05_atenciontercerizada",
                                Map.of(),
                                Integer.class))
                .isZero();
    }

    @Test
    void sinJardinesEnElCatalogoDeMod05NoHayRequerimiento() throws Exception {
        programar(conContrato, "Sur")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requerimiento").value(nullValue()))
                .andExpect(
                        jsonPath("$.motivo_sin_requerimiento")
                                .value(RequerimientoCorte.SIN_JARDINES));
    }
}
