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
import java.time.OffsetDateTime;
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
 * RF-PLA-001/002: HTTP real de Spring, persistencia y auditoría común (core_registro_auditoria) en
 * PostGIS exclusivo de pruebas.
 */
@Import(ConfiguracionServiciosApiTest.Tiempo.class)
class ConfiguracionServiciosApiTest extends ApiPlanificacion {

    /** 28/10/2026 a las 10:00 en Lima: primavera. */
    static final Instant INSTANTE = Instant.parse("2026-10-28T15:00:00Z");

    @TestConfiguration
    static class Tiempo {
        @Bean
        @Primary
        Clock relojAuditoria() {
            return Clock.fixed(INSTANTE, LIMA);
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

    /** Cambios del servicio en la auditoría común de MOD-01 (AV-256). */
    private int auditorias() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM core_registro_auditoria WHERE entidad = :entidad"
                        + " AND registro_id = :id",
                Map.of("entidad", ConfiguracionServicios.ENTIDAD, "id", servicio),
                Integer.class);
    }

    /**
     * Otro jefe de sección, como los de {@link ApiPlanificacion}: usuario de MOD-05 para HTTP Basic
     * y cuenta de MOD-01 con el mismo id, porque desde V43 (AV-287) el autor de la configuración y
     * de la auditoría apunta a {@code core_usuario}.
     */
    private long otroJefe() {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario,nombre,password_hash,activo)"
                                + " VALUES ('jefe2','Segundo jefe',:clave,true) RETURNING id",
                        Map.of("clave", claves.encode(CLAVE)),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod05_usuario_rol(usuario_id,rol) VALUES (:id,'Jefe de sección')",
                Map.of("id", id));
        jdbc.update(
                "INSERT INTO core_usuario (id, nombre_usuario, correo, nombre, credencial_hash,"
                        + " rol_id) SELECT :id, 'mod05-jefe2', 'mod05-jefe2@prueba.pucp.edu.pe',"
                        + " 'Segundo jefe', 'sin-clave', r.id FROM core_rol r"
                        + " WHERE r.codigo = 'jefe-seccion'",
                Map.of("id", id));
        return id;
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
        long otroJefe = otroJefe();
        guardar("jefe2", recurso, "{\"" + campo + "\":12}").andExpect(status().isOk());
        var historial =
                jdbc.queryForList(
                        "SELECT accion, usuario_id, valores_anteriores->>:campo anterior,"
                                + " valores_nuevos->>:campo nuevo,"
                                + " fecha_hora = :instante en_hora FROM core_registro_auditoria"
                                + " WHERE entidad = :entidad AND registro_id = :id ORDER BY id",
                        Map.of(
                                "campo",
                                campo,
                                "instante",
                                OffsetDateTime.ofInstant(INSTANTE, LIMA),
                                "entidad",
                                ConfiguracionServicios.ENTIDAD,
                                "id",
                                servicio));
        assertEquals(2, historial.size());
        var alta = historial.getFirst();
        var cambio = historial.getLast();
        assertEquals("Alta", alta.get("accion"));
        assertEquals(1L, ((Number) alta.get("usuario_id")).longValue());
        assertEquals("10", alta.get("nuevo"));
        assertEquals("Modificacion", cambio.get("accion"));
        assertEquals(otroJefe, ((Number) cambio.get("usuario_id")).longValue());
        assertEquals("10", cambio.get("anterior"));
        assertEquals("12", cambio.get("nuevo"));
        // El instante de la auditoría sale del reloj de la aplicación, fijado en esta prueba.
        assertEquals(true, alta.get("en_hora"));
        assertEquals(true, cambio.get("en_hora"));
        var registro =
                jdbc.queryForMap(
                        "SELECT * FROM " + tabla + " WHERE tipo_servicio_id=:id",
                        Map.of("id", servicio));
        assertEquals(1L, ((Number) registro.get("usuario_creacion_id")).longValue());
        assertEquals(otroJefe, ((Number) registro.get("usuario_modificacion_id")).longValue());
        assertNotNull(registro.get("fecha_creacion"));
        assertNotNull(registro.get("fecha_modificacion"));
        http.perform(get(ruta("historial")).with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].campo").value(campo))
                .andExpect(jsonPath("$[0].anterior").value("10"))
                .andExpect(jsonPath("$[0].nuevo").value("12"))
                .andExpect(jsonPath("$[0].autor").value("Segundo jefe"))
                .andExpect(jsonPath("$[1].campo").value("inicial"));
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
