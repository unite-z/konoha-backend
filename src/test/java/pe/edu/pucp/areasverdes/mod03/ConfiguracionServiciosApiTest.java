package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * RF-PLA-001/002: HTTP real de Spring, persistencia y auditoría en PostGIS exclusivo de pruebas.
 */
@Import(ConfiguracionServiciosApiTest.Tiempo.class)
class ConfiguracionServiciosApiTest extends ApiPlanificacion {

    @TestConfiguration
    static class Tiempo {
        @Bean
        @Primary
        Clock relojAuditoria() {
            return Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), LIMA);
        }
    }

    private long servicio;
    private static final String FRECUENCIA =
            "{\"frecuencia_teorica_dias\":21,\"frecuencia_practica_dias\":30,\"frecuencia_maxima_dias\":45}";

    @BeforeEach
    void catalogoControlado() {
        servicio =
                jdbc.queryForObject(
                        "INSERT INTO mod05_tiposervicio(nombre, ejecutor, es_riesgo, activo,"
                                + " es_periodico) VALUES ('Auditoría RF-PLA', 'Tercerizado', false,"
                                + " true, true) RETURNING id",
                        Map.of(),
                        Long.class);
    }

    private String ruta(String recurso) {
        return "/api/v1/planificacion/servicios/" + servicio + "/" + recurso + "/";
    }

    private ResultActions guardar(String usuario, String recurso, String cuerpo) throws Exception {
        var solicitud = put(ruta(recurso)).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (!"anonimo".equals(usuario)) solicitud.with(httpBasic(usuario, CLAVE));
        return http.perform(solicitud);
    }

    private String fila(String tabla) {
        return jdbc.queryForObject(
                "SELECT to_jsonb(t)::text FROM " + tabla + " t WHERE tipo_servicio_id = :id",
                Map.of("id", servicio),
                String.class);
    }

    private int auditorias() {
        return jdbc.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM mod05_registroauditoria", Integer.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Verano", "Otoño", "Invierno", "Primavera"})
    void conservaLasCuatroEstacionesIndependientes(String estacion) throws Exception {
        for (String e : ConfiguracionServicios.ESTACIONES) {
            guardar("jefe", "frecuencias/" + e, FRECUENCIA).andExpect(status().isOk());
        }
        guardar("jefe", "frecuencias/" + estacion, "{\"frecuencia_teorica_dias\":50}")
                .andExpect(status().isOk());
        assertEquals(
                4,
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_frecuencia_servicio WHERE tipo_servicio_id=:id",
                        Map.of("id", servicio),
                        Integer.class));
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_frecuencia_servicio WHERE tipo_servicio_id=:id"
                                + " AND frecuencia_teorica_dias=21",
                        Map.of("id", servicio),
                        Integer.class));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"frecuencia_teorica_dias\":21,\"frecuencia_maxima_dias\":20}",
                "{\"frecuencia_teorica_dias\":21,\"frecuencia_practica_dias\":30,\"frecuencia_maxima_dias\":29}",
                "{\"frecuencia_teorica_dias\":null}",
                "{\"frecuencia_teorica_dias\":0}",
                "{\"frecuencia_teorica_dias\":-1}",
                "{\"frecuencia_teorica_dias\":21,\"frecuencia_practica_dias\":0}",
                "{\"frecuencia_teorica_dias\":21,\"frecuencia_maxima_dias\":-1}"
            })
    void frecuenciaInvalidaConservaFilaCompletaYAuditoria(String cuerpo) throws Exception {
        guardar("jefe", "frecuencias/Verano", FRECUENCIA).andExpect(status().isOk());
        String anterior = fila("mod03_frecuencia_servicio");
        int cantidad = auditorias();
        guardar("jefe", "frecuencias/Verano", cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").exists());
        assertEquals(anterior, fila("mod03_frecuencia_servicio"));
        assertEquals(cantidad, auditorias());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"duracion_maxima_dias\":null}",
                "{\"duracion_maxima_dias\":0}",
                "{\"duracion_maxima_dias\":-1}"
            })
    void duracionInvalidaConservaFilaCompletaYAuditoria(String cuerpo) throws Exception {
        guardar("jefe", "duracion", "{\"duracion_maxima_dias\":10}").andExpect(status().isOk());
        String anterior = fila("mod03_duracion_servicio");
        int cantidad = auditorias();
        guardar("jefe", "duracion", cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("validacion"))
                .andExpect(jsonPath("$.errores.duracion_maxima_dias[0]").exists());
        assertEquals(anterior, fila("mod03_duracion_servicio"));
        assertEquals(cantidad, auditorias());
    }

    @ParameterizedTest
    @CsvSource({"anonimo,401", "ingeniero,403", "practicante,403", "sinrol,403"})
    void permisosDeAmbasEscriturasConservanLosDatos(String usuario, int estado) throws Exception {
        guardar("jefe", "frecuencias/Verano", FRECUENCIA).andExpect(status().isOk());
        guardar("jefe", "duracion", "{\"duracion_maxima_dias\":10}").andExpect(status().isOk());
        String frecuencia = fila("mod03_frecuencia_servicio");
        String duracion = fila("mod03_duracion_servicio");
        int cantidad = auditorias();
        guardar(usuario, "frecuencias/Verano", "{\"frecuencia_teorica_dias\":50}")
                .andExpect(status().is(estado));
        guardar(usuario, "duracion", "{\"duracion_maxima_dias\":20}")
                .andExpect(status().is(estado));
        assertEquals(frecuencia, fila("mod03_frecuencia_servicio"));
        assertEquals(duracion, fila("mod03_duracion_servicio"));
        assertEquals(cantidad, auditorias());
    }

    @ParameterizedTest
    @CsvSource({
        "frecuencias/Verano,mod03_frecuencia_servicio,frecuencia_teorica_dias",
        "duracion,mod03_duracion_servicio,duracion_maxima_dias"
    })
    void cadaCambioConservaAutorInstanteYValores(String recurso, String tabla, String campo)
            throws Exception {
        guardar("jefe", recurso, "{\"" + campo + "\":10}").andExpect(status().isOk());
        long otroJefe =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario,nombre,password_hash,activo)"
                                + " VALUES ('jefe2','Segundo jefe',:clave,true) RETURNING id",
                        Map.of("clave", claves.encode(CLAVE)),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod05_usuario_rol(usuario_id,rol) VALUES (:id,'Jefe de sección')",
                Map.of("id", otroJefe));
        guardar("jefe2", recurso, "{\"" + campo + "\":12}").andExpect(status().isOk());
        var historial =
                jdbc.queryForList(
                        "SELECT * FROM mod05_registroauditoria WHERE entidad=:tabla ORDER BY id",
                        Map.of("tabla", tabla));
        assertEquals(2, historial.size());
        assertNotNull(historial.getFirst().get("fecha_hora"));
        assertNotNull(historial.getLast().get("fecha_hora"));
        assertEquals(otroJefe, ((Number) historial.getLast().get("usuario_id")).longValue());
        assertEquals(
                10,
                jdbc.queryForObject(
                        "SELECT (valores_anteriores->>:campo)::int FROM mod05_registroauditoria"
                                + " WHERE id=:id",
                        Map.of("campo", campo, "id", historial.getLast().get("id")),
                        Integer.class));
        assertEquals(
                12,
                jdbc.queryForObject(
                        "SELECT (valores_nuevos->>:campo)::int FROM mod05_registroauditoria WHERE"
                                + " id=:id",
                        Map.of("campo", campo, "id", historial.getLast().get("id")),
                        Integer.class));
        var registro =
                jdbc.queryForMap(
                        "SELECT * FROM " + tabla + " WHERE tipo_servicio_id=:id",
                        Map.of("id", servicio));
        assertEquals(1L, ((Number) registro.get("usuario_creacion_id")).longValue());
        assertEquals(otroJefe, ((Number) registro.get("usuario_modificacion_id")).longValue());
        assertNotNull(registro.get("fecha_creacion"));
        assertNotNull(registro.get("fecha_modificacion"));
        assertEquals(1L, ((Number) historial.getFirst().get("usuario_id")).longValue());
        assertEquals(
                true,
                jdbc.queryForObject(
                        "SELECT fecha_hora = (valores_nuevos->>'fecha_modificacion')::timestamptz"
                                + " FROM mod05_registroauditoria WHERE id=:id",
                        Map.of("id", historial.getLast().get("id")),
                        Boolean.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jefe", "ingeniero", "practicante"})
    void consultaRecuperaAmbasConfiguracionesPersistidas(String usuario) throws Exception {
        guardar("jefe", "frecuencias/Verano", FRECUENCIA).andExpect(status().isOk());
        guardar("jefe", "duracion", "{\"duracion_maxima_dias\":10}").andExpect(status().isOk());
        http.perform(get("/api/v1/planificacion/servicios/").with(httpBasic(usuario, CLAVE)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$[?(@.id == "
                                                + servicio
                                                + ")].frecuencias[0].frecuencia_practica_dias")
                                .value(30))
                .andExpect(
                        jsonPath("$[?(@.id == " + servicio + ")].duracion.duracion_maxima_dias")
                                .value(10))
                .andExpect(
                        jsonPath("$[?(@.id == " + servicio + ")].duracion.modificado_por")
                                .value("jefe"));
    }

    @Test
    void programacionConsumeLasConfiguracionesGuardadasPorHttp() throws Exception {
        guardar("jefe", "frecuencias/Primavera", FRECUENCIA).andExpect(status().isOk());
        guardar("jefe", "duracion", "{\"duracion_maxima_dias\":2}").andExpect(status().isOk());
        http.perform(get(ruta("propuesta")).with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estacion").value("Primavera"))
                .andExpect(jsonPath("$.fecha_inicio").value("2026-10-28"))
                .andExpect(jsonPath("$.fecha_fin").value("2026-10-29"));
        String cuerpo =
                "{\"tipo_servicio_id\":"
                        + servicio
                        + ",\"fecha_inicio_prevista\":\"2026-10-28\",\"fecha_fin_prevista\":\"2026-10-30\"}";
        http.perform(
                        post("/api/v1/planificacion/programaciones/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("duracion-excedida"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_programacion_servicio WHERE"
                                + " tipo_servicio_id=:id",
                        Map.of("id", servicio),
                        Integer.class));
        guardar("jefe", "duracion", "{\"duracion_maxima_dias\":3}").andExpect(status().isOk());
        http.perform(
                        post("/api/v1/planificacion/programaciones/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo))
                .andExpect(status().isCreated());
        jdbc.update(
                "UPDATE mod03_programacion_servicio SET estado='Completada',"
                        + " fecha_inicio_real='2026-10-18',fecha_fin_real='2026-10-20'"
                        + " WHERE tipo_servicio_id=:id",
                Map.of("id", servicio));
        http.perform(get(ruta("propuesta")).with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha_inicio").value("2026-11-19"));
        guardar(
                        "jefe",
                        "frecuencias/Primavera",
                        "{\"frecuencia_teorica_dias\":21,\"frecuencia_practica_dias\":40,\"frecuencia_maxima_dias\":45}")
                .andExpect(status().isOk());
        http.perform(get(ruta("propuesta")).with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha_inicio").value("2026-11-29"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"duracion", "frecuencias/Verano"})
    void rechazaServiciosInactivosNoPeriodicosEInexistentes(String recurso) throws Exception {
        String cuerpo = recurso.equals("duracion") ? "{\"duracion_maxima_dias\":10}" : FRECUENCIA;
        jdbc.update(
                "UPDATE mod05_tiposervicio SET es_periodico=false WHERE id=:id",
                Map.of("id", servicio));
        guardar("jefe", recurso, cuerpo)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("servicio-no-periodico"));
        jdbc.update(
                "UPDATE mod05_tiposervicio SET activo=false WHERE id=:id", Map.of("id", servicio));
        guardar("jefe", recurso, cuerpo).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM mod05_tiposervicio WHERE id=:id", Map.of("id", servicio));
        guardar("jefe", recurso, cuerpo).andExpect(status().isNotFound());
        assertEquals(0, auditorias());
    }
}
