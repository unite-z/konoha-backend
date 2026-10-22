package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiciosApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/servicios/";

    @BeforeEach
    void servicios() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_tiposervicio RESTART IDENTITY CASCADE; INSERT INTO"
                            + " mod05_tiposervicio(nombre, ejecutor, es_riesgo, activo,"
                            + " es_periodico, es_ruidoso) VALUES ('Corte de césped', 'Tercerizado',"
                            + " false, true, true, true), ('Poda de altura a demanda',"
                            + " 'Tercerizado', true, true, false, false); INSERT INTO"
                            + " mod03_frecuencia_servicio(tipo_servicio_id, estacion,"
                            + " frecuencia_teorica_dias, frecuencia_practica_dias,"
                            + " frecuencia_maxima_dias, usuario_creacion_id,"
                            + " usuario_modificacion_id) VALUES (1, 'Invierno', 30, 40, 45, 1, 1),"
                            + " (1, 'Verano', 21, 30, 45, 1, 1)");
    }

    @Test
    void listaLosServiciosPeriodicosConSuFrecuenciaPorEstacion() throws Exception {
        http.perform(get(RUTA).with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Corte de césped"))
                .andExpect(jsonPath("$[0].frecuencias[0].estacion").value("Verano"))
                .andExpect(jsonPath("$[0].frecuencias[0].frecuencia_maxima_dias").value(45))
                .andExpect(jsonPath("$[0].frecuencias[0].modificado_por").value("jefe"))
                .andExpect(jsonPath("$[0].frecuencias[1].estacion").value("Invierno"));
    }

    @Test
    void usuarioSinRolResponde403() throws Exception {
        http.perform(get(RUTA).with(httpBasic("sinrol", CLAVE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("sin-permiso"));
    }
}
