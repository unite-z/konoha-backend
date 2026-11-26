package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * AV-433 (RD-15): avance por sector y servicio. Dos sectores (Ciencias y Sociales) y tres
 * servicios: un corte de enero con un jardín cortado, un riego completado en febrero y una poda
 * cancelada que no cuenta.
 */
class ResumenSectoresApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/resumen-sectores/";

    long corte;

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        sql.execute(
                new ClassPathResource("mod03/escenario-programacion.sql")
                                .getContentAsString(StandardCharsets.UTF_8)
                        + "INSERT INTO mod05_tiposervicio (nombre, ejecutor, es_riesgo, activo,"
                        + " es_periodico) VALUES ('Riego de jardines', 'Personal propio', false,"
                        + " true, true);"
                        + " TRUNCATE mod02_sector, mod02_area_verde RESTART IDENTITY CASCADE;");
        long ciencias = sector("Ciencias");
        long sociales = sector("Sociales");
        long c1 = area("Jardín C1", ciencias);
        long c2 = area("Jardín C2", ciencias);
        long s1 = area("Jardín S1", sociales);
        // Servicios del escenario: 1 corte, 2 fitosanitario, 3 poda mayor, 4 riego.
        corte = ciclo(1, "EnCurso", "2027-01-10", "2027-01-12", c1, c2, s1);
        sql.update(
                "INSERT INTO mod03_ruta_corte (programacion_id, area_verde_id, zona, orden,"
                        + " estado, marcado_en, marcado_por_id) VALUES (?, ?, 'Z', 1, 'Cortado',"
                        + " '2027-01-10T15:00:00-05:00', 1), (?, ?, 'Z', 2, 'Pendiente', NULL,"
                        + " NULL), (?, ?, 'Z', 3, 'Pendiente', NULL, NULL)",
                corte,
                c1,
                corte,
                c2,
                corte,
                s1);
        ciclo(4, "Completada", "2027-02-10", "2027-02-12", c1, s1);
        ciclo(3, "Cancelada", "2027-01-10", "2027-01-12", c1);
        // El fitosanitario no es corte, riego ni poda: no entra.
        ciclo(2, "Completada", "2027-01-10", "2027-01-12", c1);
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
                                + " 'Campus', 120, 1, 1) RETURNING id",
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

    long ciclo(int servicio, String estado, String inicio, String fin, long... areas) {
        var sql = jdbc.getJdbcTemplate();
        long id =
                sql.queryForObject(
                        "INSERT INTO mod03_programacion_servicio (tipo_servicio_id, estacion,"
                            + " fecha_inicio_prevista, fecha_fin_prevista, estado, creado_por_id,"
                            + " usuario_creacion_id, usuario_modificacion_id) VALUES (?, 'Verano',"
                            + " ?::date, ?::date, ?, 1, 1, 1) RETURNING id",
                        Long.class,
                        servicio,
                        inicio,
                        fin,
                        estado);
        for (long area : areas) {
            sql.update(
                    "INSERT INTO mod03_programacion_area (programacion_id, area_verde_id) VALUES"
                            + " (?, ?)",
                    id,
                    area);
        }
        return id;
    }

    ResultActions resumen(String filtros) throws Exception {
        return http.perform(get(RUTA + filtros).with(httpBasic("jefe", CLAVE)));
    }

    @Test
    void sinFiltroDaLosDosSectoresConLosTresServicios() throws Exception {
        resumen("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generado_en").isNotEmpty())
                .andExpect(jsonPath("$.sectores.length()").value(2))
                .andExpect(jsonPath("$.sectores[0].sector").value("Ciencias"))
                .andExpect(
                        jsonPath("$.sectores[0].servicios[*].servicio")
                                .value(org.hamcrest.Matchers.contains("corte", "riego", "poda")))
                .andExpect(jsonPath("$.sectores[0].servicios[0].programado").value(2))
                .andExpect(jsonPath("$.sectores[0].servicios[0].ejecutado").value(1))
                .andExpect(jsonPath("$.sectores[0].servicios[0].porcentaje").value(50.0))
                .andExpect(jsonPath("$.sectores[0].servicios[0].actualizado_en").isNotEmpty())
                .andExpect(jsonPath("$.sectores[0].servicios[1].programado").value(1))
                .andExpect(jsonPath("$.sectores[0].servicios[1].ejecutado").value(1))
                .andExpect(jsonPath("$.sectores[0].servicios[1].porcentaje").value(100.0))
                .andExpect(jsonPath("$.sectores[0].servicios[2].programado").value(0))
                .andExpect(jsonPath("$.sectores[0].servicios[2].porcentaje").value(0.0))
                .andExpect(jsonPath("$.sectores[0].servicios[2].actualizado_en").doesNotExist())
                .andExpect(jsonPath("$.sectores[1].sector").value("Sociales"))
                .andExpect(jsonPath("$.sectores[1].servicios[0].programado").value(1))
                .andExpect(jsonPath("$.sectores[1].servicios[0].ejecutado").value(0))
                .andExpect(jsonPath("$.sectores[1].servicios[0].porcentaje").value(0.0))
                .andExpect(jsonPath("$.sectores[1].servicios[1].ejecutado").value(1));
    }

    @Test
    void filtraPorCiclo() throws Exception {
        resumen("?ciclo_id=" + corte)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciclo_id").value(corte))
                .andExpect(jsonPath("$.sectores[0].servicios[0].programado").value(2))
                .andExpect(jsonPath("$.sectores[0].servicios[1].programado").value(0))
                .andExpect(jsonPath("$.sectores[1].servicios[1].programado").value(0));
    }

    @Test
    void filtraPorFechas() throws Exception {
        resumen("?desde=2027-02-01&hasta=2027-02-28")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectores[0].servicios[0].programado").value(0))
                .andExpect(jsonPath("$.sectores[0].servicios[1].programado").value(1))
                .andExpect(jsonPath("$.sectores[1].servicios[1].programado").value(1));
        resumen("?desde=2027-01-01&hasta=2027-01-31")
                .andExpect(jsonPath("$.sectores[0].servicios[0].programado").value(2))
                .andExpect(jsonPath("$.sectores[0].servicios[1].programado").value(0));
    }

    @Test
    void rechazaCicloInexistenteYPeriodoInvertido() throws Exception {
        resumen("?ciclo_id=99999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ciclo-no-encontrado"));
        resumen("?desde=2027-02-01&hasta=2027-01-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("periodo-invalido"));
    }

    @Test
    void exigeSesion() throws Exception {
        http.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }
}
