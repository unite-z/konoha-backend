package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * La ruta de MOD-02 sin los jardines retirados (AV-129): un retiro se aplica a los ciclos que
 * empiezan desde su fecha y no a la propuesta de antes. Mismo escenario que {@link CorteApiTest}:
 * Sociales (2 jardines), Ciencias (1) y Biblioteca a demanda (1).
 */
class RutaCorteRetirosApiTest extends CorteApiTest {

    void retirar(String jardin, String desde) {
        jdbc.update(
                """
                INSERT INTO mod02_ruta_corte_retiro (area_verde_id, desde, motivo, usuario_id)
                SELECT id, CAST(:desde AS date), 'Obra', 1 FROM mod02_area_verde WHERE nombre = :n
                """,
                Map.of("n", jardin, "desde", desde));
    }

    @Test
    void laPropuestaDeHoyNoCuentaLosJardinesRetirados() throws Exception {
        retirar("Patio de Sociales", LocalDate.now(ZoneId.of("America/Lima")).toString());
        http.perform(get(RUTA + "servicios/1/ruta/").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonas[2].zona").value("Sociales"))
                .andExpect(jsonPath("$.zonas[2].jardines").value(1));
    }

    @Test
    void elCicloQueEmpiezaDesdeElRetiroYaNoCopiaElJardin() throws Exception {
        retirar("Patio de Sociales", "2027-01-11");
        programar("[\"Sociales\"]", "2027-01-04", "2027-01-05")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areas.length()").value(2));
        programar("[\"Sociales\"]", "2027-01-11", "2027-01-12")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.areas.length()").value(1));
    }

    @Test
    void reincorporadoVuelveALaRutaDesdeSuFecha() throws Exception {
        retirar("Patio de Sociales", "2027-01-04");
        jdbc.update(
                "UPDATE mod02_ruta_corte_retiro SET reincorporado_desde = '2027-01-11',"
                    + " motivo_reincorporacion = 'Fin de la obra', usuario_reincorporacion_id = 1",
                Map.of());
        programar("[\"Sociales\"]", "2027-01-04", "2027-01-05")
                .andExpect(jsonPath("$.areas.length()").value(1));
        programar("[\"Sociales\"]", "2027-01-11", "2027-01-12")
                .andExpect(jsonPath("$.areas.length()").value(2));
    }
}
