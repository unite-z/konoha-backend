package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.mod06.gestion.FuenteMovimientosMaterialEnMemoria.consumo;
import static pe.edu.pucp.areasverdes.mod06.gestion.FuenteMovimientosMaterialEnMemoria.propagacion;
import static pe.edu.pucp.areasverdes.mod06.gestion.FuenteMovimientosMaterialEnMemoria.traslado;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteMovimientosMaterial;

/**
 * Contrato JSON de RF-REP-005 con datos: el adaptador se sustituye por el doble en memoria, con un
 * consumo, una propagación y un traslado de octubre de 2026.
 */
@Import(MaterialVegetalConDatosApiTest.ConDatos.class)
class MaterialVegetalConDatosApiTest extends ApiGestion {

    @TestConfiguration
    static class ConDatos {
        @Bean
        @Primary
        FuenteMovimientosMaterial fuenteMovimientosEnMemoria() {
            return FuenteMovimientosMaterialEnMemoria.con(
                    consumo("2026-10-02", 1L, "Jardín central", "Grass", "m²", "12.5"),
                    propagacion("2026-10-03", 1L, "Jardín central", "Esqueje", "u", "4"),
                    traslado(
                            "2026-10-04", 1L, "Jardín central", 2L, "Bosque", "Palmera", "u", "2"));
        }
    }

    @Test
    void elContratoJsonLlevaLasClavesEnSnakeCase() throws Exception {
        http.perform(
                        get("/api/v1/reportes/material-vegetal/?mes=2026-10")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sin_datos").value(false))
                .andExpect(jsonPath("$.mensaje_sin_datos").value(nullValue()))
                .andExpect(jsonPath("$.consumido[0].area_verde_id").value(1))
                .andExpect(jsonPath("$.consumido[0].espacio").value("Jardín central"))
                .andExpect(jsonPath("$.consumido[0].tipo_material").value("Grass"))
                .andExpect(jsonPath("$.consumido[0].categoria").value("Material vegetal"))
                .andExpect(jsonPath("$.consumido[0].unidad").value("m²"))
                .andExpect(jsonPath("$.consumido[0].cantidad").value("12.50"))
                .andExpect(jsonPath("$.consumido[0].movimientos").value(1))
                .andExpect(jsonPath("$.propagado[0].tipo_material").value("Esqueje"))
                .andExpect(jsonPath("$.trasladado[0].origen_area_id").value(1))
                .andExpect(jsonPath("$.trasladado[0].origen").value("Jardín central"))
                .andExpect(jsonPath("$.trasladado[0].destino_area_id").value(2))
                .andExpect(jsonPath("$.trasladado[0].destino").value("Bosque"))
                .andExpect(jsonPath("$.comparacion.length()").value(3));
    }
}
