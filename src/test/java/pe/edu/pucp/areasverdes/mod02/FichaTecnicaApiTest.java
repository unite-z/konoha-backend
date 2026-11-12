package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.comun.BasePruebaGlobal;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Ficha técnica y evaluación del ejemplar (AV-133, HU-MOD02-011 y HU-MOD02-012). Usa el contexto
 * compartido de {@link BasePruebaGlobal} y es la matriz de permisos de su controlador.
 */
public class FichaTecnicaApiTest extends BasePruebaGlobal {

    private static final String CLAVE = "prueba-integration-local";
    private static final String DOMINIO = "@ficha.prueba.pucp.edu.pe";
    private static final String BASE = "/api/v1/inventario/ejemplares/";
    private static final List<String> ROLES =
            List.of("jefe-seccion", "ingeniero-campo", "practicante", "capataz");

    private long ejemplar;
    private Cookie ingeniero;

    /** Matriz de permisos de {@code FichaTecnicaController} para el contrato de rutas (AV-244). */
    public static List<String> endpointsCubiertos() {
        return List.of(
                "GET " + BASE + "1/ficha-tecnica/",
                "PATCH " + BASE + "1/ficha-tecnica/",
                "POST " + BASE + "1/evaluaciones/");
    }

    @BeforeEach
    void preparar() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        jdbc.update(
                "DELETE FROM mod02_evaluacion_ejemplar WHERE ejemplar_id IN"
                        + " (SELECT id FROM mod02_ejemplar WHERE codigo LIKE 'FICHA-%')",
                Map.of());
        jdbc.update("DELETE FROM mod02_ejemplar WHERE codigo LIKE 'FICHA-%'", Map.of());
        for (String rol : ROLES) {
            jdbc.update(
                    """
                    INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                    SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                    """,
                    Map.of("correo", rol + DOMINIO, "rol", rol, "hash", claves.encode(CLAVE)));
        }
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('mod02_ejemplar', 'id'),"
                        + " coalesce(max(id), 0) + 1, false) FROM mod02_ejemplar",
                Map.of(),
                Long.class);
        ejemplar =
                jdbc.queryForObject(
                        "INSERT INTO mod02_ejemplar (codigo) VALUES ('FICHA-1') RETURNING id",
                        Map.of(),
                        Long.class);
        ingeniero = sesion("ingeniero-campo");
    }

    @Test
    void sinMedidasQuedaPendienteDeMedirYAlMedirDejaDeEstarlo() throws Exception {
        http.perform(get(ruta("ficha-tecnica/")).cookie(ingeniero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("FICHA-1"))
                .andExpect(jsonPath("$.pendiente_medir").value(true))
                .andExpect(jsonPath("$.evaluaciones.length()").value(0));

        cambiar(
                        """
                        {"altura_total_m": "9.5", "altura_fuste_m": "2.4", "dap_cm": "38.5",
                         "radio_copa_m": "3.2", "origen": "Nativo", "estado_salud": "Regular"}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.altura_total_m").value("9.50"))
                .andExpect(jsonPath("$.altura_fuste_m").value("2.40"))
                .andExpect(jsonPath("$.dap_cm").value("38.5"))
                .andExpect(jsonPath("$.radio_copa_m").value("3.20"))
                .andExpect(jsonPath("$.origen").value("Nativo"))
                .andExpect(jsonPath("$.estado_salud").value("Regular"))
                .andExpect(jsonPath("$.pendiente_medir").value(false));

        // Un campo nulo no cambia; vaciar las medidas lo deja otra vez pendiente.
        cambiar("{\"estado_salud\": \"Bueno\"}")
                .andExpect(jsonPath("$.altura_total_m").value("9.50"))
                .andExpect(jsonPath("$.estado_salud").value("Bueno"));
        cambiar(
                        """
                        {"vaciar": ["altura_total_m", "altura_fuste_m", "dap_cm", "radio_copa_m"]}
                        """)
                .andExpect(jsonPath("$.pendiente_medir").value(true))
                .andExpect(jsonPath("$.origen").value("Nativo"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM core_registro_auditoria"
                                        + " WHERE entidad = 'mod02_ejemplar' AND registro_id = :id",
                                Map.of("id", ejemplar),
                                Long.class))
                .isEqualTo(3);
    }

    @Test
    void validaLasMedidas() throws Exception {
        cambiar("{\"altura_total_m\": \"5\", \"altura_fuste_m\": \"6\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fuste-mayor-que-total"));
        cambiar("{\"dap_cm\": \"-1\"}").andExpect(status().isBadRequest());
        cambiar("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("cambio-vacio"));
        cambiar("{\"vaciar\": [\"codigo\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("campo-invalido"));
        cambiar("{\"origen\": \"Marciano\"}").andExpect(status().isBadRequest());
    }

    @Test
    void laEvaluacionGuardaHallazgosManejoFechaYAutor() throws Exception {
        evaluar(
                        """
                        {"fecha": "2026-10-09", "enfermedades_plagas": "Plaga observada",
                         "inclinacion_ramas_secas": "Dos ramas secas",
                         "manejo_recomendado": "Tratamiento y seguimiento"}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sin_hallazgos").value(false))
                .andExpect(jsonPath("$.enfermedades_plagas").value("Plaga observada"))
                .andExpect(jsonPath("$.raices_expuestas").doesNotExist())
                .andExpect(jsonPath("$.manejo_recomendado").value("Tratamiento y seguimiento"))
                .andExpect(jsonPath("$.autor").value("ingeniero-campo"));
        evaluar("{\"fecha\": \"2026-10-20\", \"sin_hallazgos\": true}")
                .andExpect(status().isCreated());

        http.perform(get(ruta("ficha-tecnica/")).cookie(ingeniero))
                .andExpect(jsonPath("$.evaluaciones.length()").value(2))
                .andExpect(jsonPath("$.evaluaciones[0].fecha").value("2026-10-20"))
                .andExpect(jsonPath("$.evaluaciones[0].sin_hallazgos").value(true))
                .andExpect(jsonPath("$.evaluaciones[1].fecha").value("2026-10-09"));
    }

    @Test
    void laEvaluacionVaciaOContradictoriaSeRechaza() throws Exception {
        evaluar("{\"fecha\": \"2026-10-09\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("evaluacion-vacia"));
        evaluar("{\"fecha\": \"2026-10-09\", \"raices_expuestas\": \"  \"}")
                .andExpect(jsonPath("$.codigo").value("evaluacion-vacia"));
        evaluar(
                        """
                        {"fecha": "2026-10-09", "sin_hallazgos": true,
                         "cavidades_pudricion": "Cavidad en la base"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("evaluacion-contradictoria"));
        evaluar("{\"fecha\": \"2026-10-09\", \"cavidades_pudricion\": \"Cavidad en la base\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("manejo-obligatorio"));
        String manana = LocalDate.now(ZoneId.of("America/Lima")).plusDays(1).toString();
        evaluar("{\"fecha\": \"%s\", \"sin_hallazgos\": true}".formatted(manana))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fecha-futura"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM mod02_evaluacion_ejemplar"
                                        + " WHERE ejemplar_id = :id",
                                Map.of("id", ejemplar),
                                Long.class))
                .isZero();
    }

    @Test
    void elCapatazConsultaYSoloRegistranQuienesRegistranInventario() throws Exception {
        for (String rol : ROLES) {
            Cookie cookie = sesion(rol);
            http.perform(get(ruta("ficha-tecnica/")).cookie(cookie)).andExpect(status().isOk());
            boolean registra = !rol.equals("capataz");
            http.perform(
                            patch(ruta("ficha-tecnica/"))
                                    .cookie(cookie)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"estado_salud\": \"Bueno\"}"))
                    .andExpect(registra ? status().isOk() : status().isForbidden());
            http.perform(
                            post(ruta("evaluaciones/"))
                                    .cookie(cookie)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"fecha\": \"2026-10-09\", \"sin_hallazgos\": true}"))
                    .andExpect(registra ? status().isCreated() : status().isForbidden());
        }
        http.perform(get(BASE + "999999/ficha-tecnica/").cookie(ingeniero))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ejemplar-no-encontrado"));
        http.perform(get(ruta("ficha-tecnica/"))).andExpect(status().isUnauthorized());
        http.perform(
                        patch(ruta("ficha-tecnica/"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado_salud\": \"Bueno\"}"))
                .andExpect(status().isUnauthorized());
        http.perform(
                        post(ruta("evaluaciones/"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fecha\": \"2026-10-09\", \"sin_hallazgos\": true}"))
                .andExpect(status().isUnauthorized());
    }

    private String ruta(String resto) {
        return BASE + ejemplar + "/" + resto;
    }

    private ResultActions cambiar(String cuerpo) throws Exception {
        return http.perform(
                patch(ruta("ficha-tecnica/"))
                        .cookie(ingeniero)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private ResultActions evaluar(String cuerpo) throws Exception {
        return http.perform(
                post(ruta("evaluaciones/"))
                        .cookie(ingeniero)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private Cookie sesion(String rol) throws Exception {
        return http.perform(
                        post("/api/v1/core/auth/login/")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"identificador\": \"%s\", \"contrasena\": \"%s\"}"
                                                .formatted(rol + DOMINIO, CLAVE)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(SeguridadMod01.COOKIE_SESION);
    }
}
