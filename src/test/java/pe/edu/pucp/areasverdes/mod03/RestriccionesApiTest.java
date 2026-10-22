package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestriccionesApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/restricciones/";

    @BeforeEach
    void restricciones() {
        jdbc.getJdbcTemplate()
                .execute(
                        "INSERT INTO mod03_restriccion_calendario(tipo_restriccion_id,"
                            + " fecha_inicio, fecha_fin, descripcion, usuario_creacion_id,"
                            + " usuario_modificacion_id) SELECT id, d::date, d::date, nombre, 1, 1"
                            + " FROM mod03_tipo_restriccion, (VALUES ('2026-11-08'),"
                            + " ('2026-12-01')) f(d) WHERE nombre = 'Examen de admisión'");
    }

    @Test
    void listaLasRestriccionesDelPeriodoConSuEfecto() throws Exception {
        http.perform(
                        get(RUTA)
                                .param("desde", "2026-11-01")
                                .param("hasta", "2026-11-30")
                                .with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("Examen de admisión"))
                .andExpect(jsonPath("$[0].efecto").value("Prohibicion"))
                .andExpect(jsonPath("$[0].fecha_inicio").value("2026-11-08"));
    }

    @Test
    void rechazaUnPeriodoInvertido() throws Exception {
        http.perform(
                        get(RUTA)
                                .param("desde", "2026-11-30")
                                .param("hasta", "2026-11-01")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("periodo-invalido"));
    }

    @Test
    void elCatalogoDeTiposEstaDisponible() throws Exception {
        http.perform(get("/api/v1/planificacion/tipos-restriccion/").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.nombre == 'Actividad extracurricular')].efecto")
                                .value("Advertencia"));
    }
}
