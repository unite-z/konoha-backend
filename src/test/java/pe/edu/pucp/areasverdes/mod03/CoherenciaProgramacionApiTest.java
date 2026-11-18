package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class CoherenciaProgramacionApiTest extends ApiPlanificacion {
    final LocalDate hoy = LocalDate.now(pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA);
    long a, b;

    @BeforeEach
    void escenario() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        new ClassPathResource("mod03/escenario-programacion.sql")
                                .getContentAsString(StandardCharsets.UTF_8));
        jdbc.getJdbcTemplate()
                .update(
                        "UPDATE mod03_duracion_servicio SET duracion_maxima_dias=4 WHERE"
                                + " tipo_servicio_id=1");
        a = area("Área A");
        b = area("Área B");
    }

    long area(String nombre) {
        return jdbc.getJdbcTemplate()
                .queryForObject(
                        "INSERT INTO"
                            + " mod02_area_verde(nombre,lugar,ubicacion,superficie_m2,usuario_creacion_id,usuario_modificacion_id)"
                            + " VALUES (?,'Prueba','Prueba',100,1,1) RETURNING id",
                        Long.class,
                        nombre);
    }

    long cuadrilla() {
        long capataz =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "INSERT INTO mod04_personal(nombres,apellidos,tipo_personal) VALUES"
                                        + " ('Demo','Prueba','Capataz') RETURNING id",
                                Long.class);
        return jdbc.getJdbcTemplate()
                .queryForObject(
                        "INSERT INTO"
                            + " mod04_cuadrilla(nombre,capataz_id,usuario_creacion_id,usuario_modificacion_id)"
                            + " VALUES (?,?,1,1) RETURNING id",
                        Long.class,
                        "Prueba " + capataz,
                        capataz);
    }

    ResultActions crear(int servicio, LocalDate inicio, LocalDate fin, String asignaciones)
            throws Exception {
        return http.perform(
                post("/api/v1/planificacion/programaciones/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_servicio_id\":"
                                        + servicio
                                        + ",\"fecha_inicio_prevista\":\""
                                        + inicio
                                        + "\",\"fecha_fin_prevista\":\""
                                        + fin
                                        + "\""
                                        + asignaciones
                                        + "}"));
    }

    long id(ResultActions resultado) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(
                        resultado
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .get("id")
                .asLong();
    }

    ResultActions accion(long id, String accion, LocalDate fecha) throws Exception {
        return http.perform(
                post("/api/v1/planificacion/" + accion.replace("{id}", "" + id))
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"" + fecha + "\"}"));
    }

    String areas(long area) {
        return ",\"area_verde_ids\":[" + area + "]";
    }

    @Test
    void permiteDosDiasPeroNoUnoNiSeisParaCorteCuatroMasUno() throws Exception {
        crear(1, hoy.minusDays(1), hoy, areas(a)).andExpect(status().isCreated());
        crear(1, hoy, hoy, areas(b))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("periodo-sin-limpieza"));
        crear(1, hoy.minusDays(5), hoy, areas(b))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("duracion-excedida"));
    }

    @Test
    void exigeRutaYJornadaPosteriorNoFuturaAntesDeCerrar() throws Exception {
        long ciclo = id(crear(1, hoy.minusDays(1), hoy, areas(a)));
        jdbc.getJdbcTemplate()
                .update(
                        "INSERT INTO"
                            + " mod03_ruta_corte(programacion_id,area_verde_id,zona,orden,estado)"
                            + " VALUES (?,?,'Prueba',1,'Pendiente')",
                        ciclo,
                        a);
        accion(ciclo, "programaciones/{id}/inicio/", hoy.minusDays(1)).andExpect(status().isOk());
        accion(ciclo, "programaciones/{id}/fin/", hoy)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ruta-incompleta"));
        jdbc.getJdbcTemplate()
                .update(
                        "UPDATE mod03_ruta_corte SET estado='Cortado',marcado_en=(?::date+time"
                                + " '10:00') AT TIME ZONE 'America/Lima',marcado_por_id=1 WHERE"
                                + " programacion_id=?",
                        hoy.minusDays(1),
                        ciclo);
        accion(ciclo, "programaciones/{id}/fin/", hoy)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("limpieza-pendiente"));
        accion(ciclo, "ciclos/{id}/limpiezas/", hoy.minusDays(1))
                .andExpect(status().isBadRequest());
        accion(ciclo, "ciclos/{id}/limpiezas/", hoy.plusDays(1)).andExpect(status().isBadRequest());
        accion(ciclo, "ciclos/{id}/limpiezas/", hoy).andExpect(status().isCreated());
        accion(ciclo, "ciclos/{id}/limpiezas/", hoy).andExpect(status().isCreated());
        assertEquals(
                1,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_limpieza_corte WHERE programacion_id=?",
                                Integer.class,
                                ciclo));
        http.perform(
                        get("/api/v1/planificacion/ciclos/" + ciclo + "/")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(jsonPath("$.cumple_limpieza").value(true));
        accion(ciclo, "programaciones/{id}/fin/", hoy.minusDays(1))
                .andExpect(status().isBadRequest());
        accion(ciclo, "programaciones/{id}/fin/", hoy).andExpect(status().isOk());
        accion(ciclo, "ciclos/{id}/limpiezas/", hoy).andExpect(status().isConflict());
    }

    @Test
    void bloqueaAreaCompartidaYCuadrillaPeroPermiteRecursosIndependientes() throws Exception {
        long q = cuadrilla();
        long q2 = cuadrilla();
        id(crear(2, hoy.minusDays(1), hoy, areas(a) + ",\"cuadrilla_id\":" + q));
        crear(2, hoy.minusDays(1), hoy, areas(b) + ",\"cuadrilla_id\":" + q)
                .andExpect(status().isConflict());
        crear(2, hoy.minusDays(1), hoy, areas(a) + ",\"cuadrilla_id\":" + q2)
                .andExpect(status().isConflict());
        crear(2, hoy.minusDays(1), hoy, areas(b) + ",\"cuadrilla_id\":" + q2)
                .andExpect(status().isCreated());
    }

    @Test
    void bloqueaDuplicadoSinAsignacionYNoDejaFilasParciales() throws Exception {
        id(crear(2, hoy.minusDays(1), hoy, ""));
        int antes =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_programacion_servicio", Integer.class);
        crear(2, hoy.minusDays(1), hoy, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("conflicto-disponibilidad"));
        assertEquals(
                antes,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_programacion_servicio", Integer.class));
    }

    @Test
    void reprogramarTambienCompruebaDisponibilidad() throws Exception {
        id(crear(2, hoy.minusDays(1), hoy, areas(a)));
        long segundo = id(crear(2, hoy.minusDays(4), hoy.minusDays(3), areas(a)));
        http.perform(
                        patch("/api/v1/planificacion/programaciones/" + segundo + "/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fecha_inicio_prevista\":\""
                                                + hoy.minusDays(1)
                                                + "\",\"fecha_fin_prevista\":\""
                                                + hoy
                                                + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void dosAltasConcurrentesNoDuplicanLaReserva() throws Exception {
        try (var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var salida = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> alta =
                    () -> {
                        salida.await();
                        return crear(2, hoy.minusDays(1), hoy, "")
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    };
            var uno = ejecutor.submit(alta);
            var dos = ejecutor.submit(alta);
            salida.countDown();
            assertEquals(
                    java.util.List.of(201, 409),
                    java.util.stream.Stream.of(
                                    uno.get(30, java.util.concurrent.TimeUnit.SECONDS),
                                    dos.get(30, java.util.concurrent.TimeUnit.SECONDS))
                            .sorted()
                            .toList());
        }
    }

    @Test
    void reemplazoEsAtomicoYLaConfirmacionDeCancelacionEsIdempotente() throws Exception {
        long original = id(crear(2, hoy.minusDays(1), hoy, areas(a)));
        long nuevo =
                id(
                        crear(
                                1,
                                hoy.minusDays(1),
                                hoy,
                                areas(a) + ",\"reemplaza_programacion_id\":" + original));
        assertEquals(
                "Cancelada",
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT estado FROM mod03_programacion_servicio WHERE id=?",
                                String.class,
                                original));
        String motivo =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT motivo_cancelacion FROM mod03_programacion_servicio WHERE"
                                        + " id=?",
                                String.class,
                                original);
        http.perform(
                        post("/api/v1/planificacion/programaciones/" + original + "/cancelacion/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        new com.fasterxml.jackson.databind.ObjectMapper()
                                                .writeValueAsString(
                                                        java.util.Map.of("motivo", motivo))))
                .andExpect(status().isOk());
        assertEquals(
                1,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_historial_programacion WHERE"
                                        + " programacion_id=? AND estado='Cancelada'",
                                Integer.class,
                                original));
        assertNotEquals(original, nuevo);
    }

    @Test
    void conflictoAlReemplazarConservaElOriginalYRevierteLaCreacion() throws Exception {
        long original = id(crear(2, hoy.minusDays(1), hoy, areas(a)));
        id(crear(2, hoy.minusDays(1), hoy, areas(b)));
        int antes =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_programacion_servicio", Integer.class);
        crear(
                        1,
                        hoy.minusDays(1),
                        hoy,
                        ",\"area_verde_ids\":["
                                + a
                                + ","
                                + b
                                + "],\"reemplaza_programacion_id\":"
                                + original)
                .andExpect(status().isConflict());
        assertEquals(
                "Programada",
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT estado FROM mod03_programacion_servicio WHERE id=?",
                                String.class,
                                original));
        assertEquals(
                antes,
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                "SELECT count(*) FROM mod03_programacion_servicio", Integer.class));
    }
}
