package pe.edu.pucp.areasverdes.mod03;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ServiciosApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/servicios/";

    @Autowired ValoresIniciales valoresIniciales;

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

    @Test
    void traeLaViaYElProveedorDelContratoVigente() throws Exception {
        LocalDate hoy = LocalDate.now();
        long antiguo =
                jdbc.queryForObject(
                        "INSERT INTO mod05_proveedor(razon_social, activo) VALUES ('Antiguo Demo',"
                                + " true) RETURNING id",
                        Map.of(),
                        Long.class);
        long vigente =
                jdbc.queryForObject(
                        "INSERT INTO mod05_proveedor(razon_social, activo) VALUES ('Servicios"
                                + " Verdes Demo S.A.C.', true) RETURNING id",
                        Map.of(),
                        Long.class);
        String contrato =
                "INSERT INTO mod05_contrato(numero, proveedor_id, tipo_servicio_id,"
                        + " vigencia_inicio, vigencia_fin) VALUES (:numero, :proveedor, 1,"
                        + " :inicio, :fin)";
        jdbc.update(
                contrato,
                Map.of(
                        "numero",
                        "C-ANT",
                        "proveedor",
                        antiguo,
                        "inicio",
                        hoy.minusYears(2),
                        "fin",
                        hoy.minusYears(1)));
        jdbc.update(
                contrato,
                Map.of(
                        "numero",
                        "C-VIG",
                        "proveedor",
                        vigente,
                        "inicio",
                        hoy.minusMonths(1),
                        "fin",
                        hoy.plusMonths(11)));
        http.perform(get(RUTA).with(httpBasic("practicante", CLAVE)))
                .andExpect(jsonPath("$[0].via").value("Tercerizado"))
                .andExpect(jsonPath("$[0].proveedor").value("Servicios Verdes Demo S.A.C."));
        jdbc.update("UPDATE mod05_tiposervicio SET ejecutor = 'PersonalPropio'", Map.of());
        http.perform(get(RUTA).with(httpBasic("practicante", CLAVE)))
                .andExpect(jsonPath("$[0].via").value("PersonalPropio"))
                .andExpect(jsonPath("$[0].proveedor").value(nullValue()));
    }

    @Test
    void losValoresInicialesSalenDeLaPrecargaYSinEllosResponde404() throws Exception {
        http.perform(get(RUTA + "1/valores-iniciales/").with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("sin-valores-iniciales"));
        valoresIniciales.precargar();
        http.perform(get(RUTA + "1/valores-iniciales/").with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuencias.length()").value(4))
                .andExpect(jsonPath("$.frecuencias[0].estacion").value("Verano"))
                .andExpect(jsonPath("$.frecuencias[0].frecuencia_teorica_dias").value(21))
                .andExpect(jsonPath("$.frecuencias[0].frecuencia_practica_dias").value(35))
                .andExpect(jsonPath("$.frecuencias[0].frecuencia_maxima_dias").value(45))
                .andExpect(jsonPath("$.duracion.duracion_maxima_dias").value(3))
                .andExpect(jsonPath("$.duracion.dias_limpieza").value(1));
        // La poda de altura no es periódica: no tiene valores iniciales.
        http.perform(get(RUTA + "2/valores-iniciales/").with(httpBasic("practicante", CLAVE)))
                .andExpect(status().isNotFound());
    }
}
