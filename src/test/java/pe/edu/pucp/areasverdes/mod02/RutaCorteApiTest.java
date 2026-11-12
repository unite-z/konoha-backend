package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.comun.BasePruebaGlobal;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Ruta de jardines del corte de césped (AV-129, RF-INV-004, HU-MOD02-009). Sector ordinario «Zona
 * RUTA» con dos jardines y sector a demanda «Zona RUTA a demanda» con uno. Es también la matriz de
 * permisos de {@code RutaCorteController}.
 */
public class RutaCorteApiTest extends BasePruebaGlobal {

    private static final String CLAVE = "prueba-integration-local";
    private static final String DOMINIO = "@ruta.prueba.pucp.edu.pe";
    private static final String BASE = "/api/v1/inventario/ruta-corte/";
    private static final List<String> ROLES =
            List.of("jefe-seccion", "ingeniero-campo", "practicante", "capataz");
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Lima"));

    @Autowired ObjectMapper json;

    private long jardinA, jardinB, jardinDemanda;
    private Cookie jefe;

    /** Matriz de permisos de {@code RutaCorteController} para el contrato de rutas (AV-244). */
    public static List<String> endpointsCubiertos() {
        return List.of(
                "GET " + BASE,
                "GET " + BASE + "retiros/",
                "POST " + BASE + "retiros/",
                "POST " + BASE + "retiros/1/reincorporar/");
    }

    @BeforeEach
    void preparar() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        jdbc.update(
                "DELETE FROM mod02_ruta_corte_retiro WHERE area_verde_id IN"
                        + " (SELECT id FROM mod02_area_verde WHERE nombre LIKE 'RUTA %')",
                Map.of());
        jdbc.update(
                "DELETE FROM mod02_historial_area_sector WHERE area_verde_id IN"
                        + " (SELECT id FROM mod02_area_verde WHERE nombre LIKE 'RUTA %')",
                Map.of());
        jdbc.update("DELETE FROM mod02_area_verde WHERE nombre LIKE 'RUTA %'", Map.of());
        jdbc.update("DELETE FROM mod02_sector WHERE nombre LIKE 'Zona RUTA%'", Map.of());
        for (String rol : ROLES) {
            jdbc.update(
                    """
                    INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                    SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                    """,
                    Map.of("correo", rol + DOMINIO, "rol", rol, "hash", claves.encode(CLAVE)));
        }
        long usuario =
                jdbc.queryForObject("SELECT min(id) FROM core_usuario", Map.of(), Long.class);
        long ordinario = sector("Zona RUTA", false, usuario);
        long demanda = sector("Zona RUTA a demanda", true, usuario);
        jardinA = jardin("RUTA Jardín A", "100.00", ordinario, usuario);
        jardinB = jardin("RUTA Jardín B", "250.50", ordinario, usuario);
        jardinDemanda = jardin("RUTA Jardín a demanda", "80.00", demanda, usuario);
        jefe = sesion("jefe-seccion");
    }

    @Test
    void laRutaTieneLosJardinesOrdinariosConSuSuperficieTotal() throws Exception {
        JsonNode ruta = ruta(null);
        assertThat(ruta.get("fecha").asText()).isEqualTo(HOY.toString());
        assertThat(jardin(ruta, jardinA).get("en_ruta").asBoolean()).isTrue();
        assertThat(jardin(ruta, jardinB).get("superficie_m2").decimalValue())
                .isEqualByComparingTo("250.50");
        assertThat(jardin(ruta, jardinB).get("sector").asText()).isEqualTo("Zona RUTA");
        JsonNode demanda = jardin(ruta, jardinDemanda);
        assertThat(demanda.get("a_demanda").asBoolean()).isTrue();
        assertThat(demanda.get("en_ruta").asBoolean()).isFalse();
    }

    @Test
    void retirarConMotivoSacaElJardinDesdeSuFechaYConservaElHistorial() throws Exception {
        JsonNode antes = ruta(null);
        retirar(jardinB, HOY, "Obra de la nueva biblioteca")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.area_verde_nombre").value("RUTA Jardín B"))
                .andExpect(jsonPath("$.motivo").value("Obra de la nueva biblioteca"))
                .andExpect(jsonPath("$.autor").value("jefe-seccion"));

        JsonNode despues = ruta(null);
        JsonNode b = jardin(despues, jardinB);
        assertThat(b.get("en_ruta").asBoolean()).isFalse();
        assertThat(b.get("retiro").get("motivo").asText()).isEqualTo("Obra de la nueva biblioteca");
        assertThat(despues.get("total_jardines").asInt())
                .isEqualTo(antes.get("total_jardines").asInt() - 1);
        assertThat(
                        antes.get("superficie_total_m2")
                                .decimalValue()
                                .subtract(despues.get("superficie_total_m2").decimalValue()))
                .isEqualByComparingTo(new BigDecimal("250.50"));
        // Antes de la fecha del retiro, el jardín sigue en la ruta.
        assertThat(jardin(ruta(HOY.minusDays(1)), jardinB).get("en_ruta").asBoolean()).isTrue();
        auditado("Alta");
    }

    @Test
    void unRetiroFuturoSeAplicaDesdeElCicloCorrespondiente() throws Exception {
        retirar(jardinA, HOY.plusDays(7), "Cierre por remodelación")
                .andExpect(status().isCreated());
        JsonNode hoy = jardin(ruta(null), jardinA);
        assertThat(hoy.get("en_ruta").asBoolean()).isTrue();
        assertThat(hoy.get("retiro").get("desde").asText()).isEqualTo(HOY.plusDays(7).toString());
        assertThat(jardin(ruta(HOY.plusDays(7)), jardinA).get("en_ruta").asBoolean()).isFalse();
    }

    @Test
    void reincorporarCierraElRetiroSinBorrarlo() throws Exception {
        long retiro = id(retirar(jardinA, HOY, "Obra temporal"));
        reincorporar(retiro, HOY.plusDays(3), "Terminó la obra")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reincorporado_desde").value(HOY.plusDays(3).toString()))
                .andExpect(jsonPath("$.motivo_reincorporacion").value("Terminó la obra"))
                .andExpect(jsonPath("$.autor_reincorporacion").value("jefe-seccion"));
        assertThat(jardin(ruta(HOY.plusDays(1)), jardinA).get("en_ruta").asBoolean()).isFalse();
        JsonNode luego = jardin(ruta(HOY.plusDays(3)), jardinA);
        assertThat(luego.get("en_ruta").asBoolean()).isTrue();
        assertThat(luego.get("retiro").isNull()).isTrue();

        reincorporar(retiro, HOY.plusDays(4), "Otra vez")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ya-reincorporado"));
        http.perform(get(BASE + "retiros/").cookie(sesion("ingeniero-campo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].motivo").value("Obra temporal"))
                .andExpect(jsonPath("$[0].motivo_reincorporacion").value("Terminó la obra"));
        // Se puede volver a retirar: el historial guarda los dos.
        retirar(jardinA, HOY.plusDays(10), "Segunda obra").andExpect(status().isCreated());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM mod02_ruta_corte_retiro WHERE area_verde_id ="
                                        + " :a",
                                Map.of("a", jardinA),
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void validaElRetiroYLaReincorporacion() throws Exception {
        retirar(jardinA, HOY, "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("motivo-obligatorio"));
        retirar(jardinA, HOY.minusDays(1), "Obra")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fecha-pasada"));
        retirar(jardinDemanda, HOY, "Obra")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fuera-de-ruta"));
        retirar(999_999_999L, HOY, "Obra")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("jardin-no-encontrado"));
        long retiro = id(retirar(jardinA, HOY.plusDays(5), "Obra"));
        retirar(jardinA, HOY.plusDays(6), "Otra")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ya-retirado"));
        reincorporar(retiro, HOY.plusDays(2), "Antes de tiempo")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("fecha-anterior-al-retiro"));
        reincorporar(retiro, HOY.plusDays(6), "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("motivo-obligatorio"));
        reincorporar(999_999_999L, HOY, "Obra")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("retiro-no-encontrado"));
    }

    @Test
    void consultanTodosYSoloElJefeCambiaLaRuta() throws Exception {
        for (String rol : List.of("ingeniero-campo", "practicante", "capataz")) {
            Cookie sesion = sesion(rol);
            http.perform(get(BASE).cookie(sesion)).andExpect(status().isOk());
            http.perform(get(BASE + "retiros/").cookie(sesion)).andExpect(status().isOk());
            enviar(BASE + "retiros/", cuerpoRetiro(jardinA, HOY, "Obra"), sesion)
                    .andExpect(status().isForbidden());
            enviar(BASE + "retiros/1/reincorporar/", "{\"desde\": \"" + HOY + "\"}", sesion)
                    .andExpect(status().isForbidden());
        }
        http.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    private long sector(String nombre, boolean aDemanda, long usuario) {
        return jdbc.queryForObject(
                "INSERT INTO mod02_sector (nombre, atencion_a_demanda, usuario_creacion_id,"
                        + " usuario_modificacion_id) VALUES (:n, :d, :u, :u) RETURNING id",
                Map.of("n", nombre, "d", aDemanda, "u", usuario),
                Long.class);
    }

    private long jardin(String nombre, String superficie, long sector, long usuario) {
        long area =
                jdbc.queryForObject(
                        "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                                + " usuario_creacion_id, usuario_modificacion_id) VALUES (:n, :n,"
                                + " 'Campus', :s, :u, :u) RETURNING id",
                        Map.of("n", nombre, "s", new BigDecimal(superficie), "u", usuario),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, usuario_id)"
                        + " VALUES (:a, :s, :u)",
                Map.of("a", area, "s", sector, "u", usuario));
        return area;
    }

    private JsonNode ruta(LocalDate fecha) throws Exception {
        String url = fecha == null ? BASE : BASE + "?fecha=" + fecha;
        return json.readTree(
                http.perform(get(url).cookie(jefe))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private static JsonNode jardin(JsonNode ruta, long area) {
        for (JsonNode j : ruta.get("jardines")) {
            if (j.get("area_verde_id").asLong() == area) return j;
        }
        throw new AssertionError("Falta el jardín " + area);
    }

    private ResultActions retirar(long area, LocalDate desde, String motivo) throws Exception {
        return enviar(BASE + "retiros/", cuerpoRetiro(area, desde, motivo), jefe);
    }

    private ResultActions reincorporar(long retiro, LocalDate desde, String motivo)
            throws Exception {
        return enviar(
                BASE + "retiros/" + retiro + "/reincorporar/",
                json.writeValueAsString(Map.of("desde", desde.toString(), "motivo", motivo)),
                jefe);
    }

    private String cuerpoRetiro(long area, LocalDate desde, String motivo) throws Exception {
        return json.writeValueAsString(
                Map.of("area_verde_id", area, "desde", desde.toString(), "motivo", motivo));
    }

    private ResultActions enviar(String url, String cuerpo, Cookie sesion) throws Exception {
        return http.perform(
                post(url)
                        .with(csrf())
                        .cookie(sesion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    private long id(ResultActions respuesta) throws Exception {
        return json.readTree(respuesta.andReturn().getResponse().getContentAsString())
                .get("id")
                .asLong();
    }

    private void auditado(String accion) {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM core_registro_auditoria WHERE entidad ="
                                        + " 'mod02_ruta_corte_retiro' AND accion = :a",
                                Map.of("a", accion),
                                Integer.class))
                .isPositive();
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
