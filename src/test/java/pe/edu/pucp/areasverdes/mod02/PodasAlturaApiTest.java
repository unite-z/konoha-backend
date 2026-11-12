package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.comun.BasePruebaGlobal;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Poda de altura a demanda vinculada al ejemplar (AV-312, HU-MOD02-024, RF-POD-005). Usa el
 * contexto compartido de {@link BasePruebaGlobal}: es además la matriz de permisos de su
 * controlador, y así no abre otro contexto de Spring ni otro pool de conexiones.
 */
public class PodasAlturaApiTest extends BasePruebaGlobal {

    private static final String CLAVE = "prueba-integration-local";
    private static final String DOMINIO = "@altura.prueba.pucp.edu.pe";
    private static final String RUTA = "/api/v1/inventario/podas-altura/";
    private static final String INTERVENCIONES = "/api/v1/inventario/intervenciones-poda/";
    private static final List<String> ROLES =
            List.of("jefe-seccion", "ingeniero-campo", "practicante", "capataz");

    private long alto;
    private long bajo;
    private long sinAltura;
    private long contrato;
    private long vencido;
    private Cookie jefe;

    /**
     * Matriz de permisos de {@code PodasAlturaController} para el contrato de rutas (AV-244): la
     * cubre {@link #soloElJefeSolicitaYVeLosContratos()}, con los cuatro roles y sin sesión.
     */
    public static List<String> endpointsCubiertos() {
        return List.of(
                "POST " + RUTA, "GET " + RUTA + "contratos/", "GET " + RUTA + "ejemplares/1/");
    }

    @BeforeEach
    void preparar() throws Exception {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM mod02_ejemplar WHERE codigo LIKE 'ALT-%'", Map.of());
        for (String rol : ROLES) {
            jdbc.update(
                    """
                    INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                    SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                    """,
                    Map.of("correo", rol + DOMINIO, "rol", rol, "hash", claves.encode(CLAVE)));
        }
        alto = ejemplar("ALT-8", "8");
        bajo = ejemplar("ALT-4", "4");
        sinAltura = ejemplar("ALT-SIN", null);
        // Otras suites vacían el catálogo de servicios: esta crea el suyo.
        long servicio =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod05_tiposervicio (nombre, ejecutor, es_riesgo, activo)
                        VALUES ('Poda de altura AV-312', 'Tercerizado', false, true) RETURNING id
                        """,
                        Map.of(),
                        Long.class);
        long proveedor =
                jdbc.queryForObject(
                        "INSERT INTO mod05_proveedor (razon_social, activo)"
                                + " VALUES ('Arbolistas AV-312', true) RETURNING id",
                        Map.of(),
                        Long.class);
        contrato = contrato(proveedor, servicio, "current_date - 30", "current_date + 300");
        vencido = contrato(proveedor, servicio, "current_date - 300", "current_date - 30");
        jefe = sesion("jefe-seccion");
    }

    @Test
    void elJefeSolicitaLaPodaYSeCreaLaAtencionDeRiesgo() throws Exception {
        String respuesta =
                solicitar(alto, "Riesgo", contrato, null)
                        .andExpect(status().isCreated())
                        .andExpect(header().exists("Location"))
                        .andExpect(jsonPath("$.categoria").value("AlturaDemanda"))
                        .andExpect(jsonPath("$.estado").value("Solicitada"))
                        .andExpect(jsonPath("$.motivo").value("Riesgo"))
                        .andExpect(jsonPath("$.altura_referencia_m").value("8.00"))
                        .andExpect(jsonPath("$.prioridad").value("Alta"))
                        .andExpect(jsonPath("$.fecha_ejecucion").doesNotExist())
                        .andExpect(jsonPath("$.atencion_codigo").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long atencion = ((Number) JsonPath.read(respuesta, "$.atencion_id")).longValue();

        Map<String, Object> fila =
                jdbc.queryForMap(
                        """
                        SELECT a.origen, a.es_riesgo, a.contrato_id, a.descripcion, al.ejemplar_id
                        FROM mod05_atenciontercerizada a
                        JOIN mod05_alcanceatencion al ON al.atencion_id = a.id
                        WHERE a.id = :id
                        """,
                        Map.of("id", atencion));
        assertThat(fila.get("origen")).isEqualTo("Demanda");
        assertThat(fila.get("es_riesgo")).isEqualTo(true);
        assertThat(((Number) fila.get("contrato_id")).longValue()).isEqualTo(contrato);
        assertThat(((Number) fila.get("ejemplar_id")).longValue()).isEqualTo(alto);
        assertThat((String) fila.get("descripcion")).contains("ALT-8", "8 m", "riesgo");

        // Queda en el historial del ejemplar.
        http.perform(
                        get(INTERVENCIONES)
                                .queryParam("ejemplar_id", String.valueOf(alto))
                                .cookie(jefe))
                .andExpect(jsonPath("$.results[0].motivo").value("Riesgo"));
    }

    @Test
    void otroMotivoNoMarcaRiesgoSiElServicioNoLoEs() throws Exception {
        String respuesta =
                solicitar(alto, "Obstruccion", contrato, null)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long atencion = ((Number) JsonPath.read(respuesta, "$.atencion_id")).longValue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT es_riesgo FROM mod05_atenciontercerizada WHERE id = :id",
                                Map.of("id", atencion),
                                Boolean.class))
                .isFalse();
    }

    @Test
    void porDebajoDeCincoMetrosExigeJustificacion() throws Exception {
        long antes = atenciones();
        solicitar(bajo, "Reduccion", contrato, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("justificacion-obligatoria"));
        assertThat(atenciones()).isEqualTo(antes);

        solicitar(bajo, "Reduccion", contrato, "La copa toca el tendido eléctrico")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.justificacion").value("La copa toca el tendido eléctrico"));

        // Sin altura registrada no se puede aplicar RN-005: se admite.
        solicitar(sinAltura, "PlantaMuerta", contrato, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.altura_referencia_m").doesNotExist());

        http.perform(get(RUTA + "ejemplares/" + bajo + "/").cookie(jefe))
                .andExpect(jsonPath("$.altura_total_m").value("4.00"))
                .andExpect(jsonPath("$.requiere_justificacion").value(true));
    }

    @Test
    void siMod05RechazaLaAtencionNoQuedaNadaRegistrado() throws Exception {
        long antes = intervenciones();
        solicitar(alto, "Riesgo", vencido, null).andExpect(status().is4xxClientError());
        assertThat(intervenciones()).isEqualTo(antes);
    }

    @Test
    void laIncidenciaDebeSerDelMismoEjemplar() throws Exception {
        http.perform(
                        post(RUTA)
                                .cookie(jefe)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"ejemplar_id": %d, "motivo": "Riesgo", "contrato_id": %d,
                                         "descripcion": "Rama suelta", "incidencia_id": 999999}
                                        """
                                                .formatted(alto, contrato)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("incidencia-inexistente"));
    }

    @Test
    void alEjecutarseSeRegistraLaFechaYPasaAIncompletaHastaLaFoto() throws Exception {
        String respuesta =
                solicitar(alto, "Riesgo", contrato, null)
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();

        http.perform(
                        patch(INTERVENCIONES + id + "/")
                                .cookie(sesion("ingeniero-campo"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fecha_ejecucion\": \"2026-01-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Incompleta"))
                .andExpect(jsonPath("$.motivo").value("Riesgo"));
    }

    @Test
    void soloElJefeSolicitaYVeLosContratos() throws Exception {
        http.perform(get(RUTA + "contratos/").cookie(jefe))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.id == %d)].proveedor".formatted(contrato))
                                .value("Arbolistas AV-312"))
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(vencido)).isEmpty());
        for (String rol : List.of("ingeniero-campo", "practicante", "capataz")) {
            Cookie cookie = sesion(rol);
            http.perform(
                            post(RUTA)
                                    .cookie(cookie)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(cuerpo(alto, "Riesgo", contrato, null)))
                    .andExpect(status().isForbidden());
            http.perform(get(RUTA + "contratos/").cookie(cookie)).andExpect(status().isForbidden());
        }
        for (String rol : ROLES) {
            http.perform(get(RUTA + "ejemplares/" + alto + "/").cookie(sesion(rol)))
                    .andExpect(status().isOk());
        }
        http.perform(get(RUTA + "contratos/")).andExpect(status().isUnauthorized());
        http.perform(get(RUTA + "ejemplares/" + alto + "/")).andExpect(status().isUnauthorized());
        http.perform(
                        post(RUTA)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo(alto, "Riesgo", contrato, null)))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions solicitar(
            long ejemplar, String motivo, long contrato, String justificacion) throws Exception {
        return http.perform(
                post(RUTA)
                        .cookie(jefe)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ejemplar, motivo, contrato, justificacion)));
    }

    private static String cuerpo(
            long ejemplar, String motivo, long contrato, String justificacion) {
        return """
        {"ejemplar_id": %d, "motivo": "%s", "contrato_id": %d, "prioridad": "Alta",
         "descripcion": "Rama sobre la vereda", "justificacion": %s}
        """
                .formatted(
                        ejemplar,
                        motivo,
                        contrato,
                        justificacion == null ? "null" : "\"" + justificacion + "\"");
    }

    private long atenciones() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mod05_atenciontercerizada", Map.of(), Long.class);
    }

    private long intervenciones() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mod02_intervencion_arbolado", Map.of(), Long.class);
    }

    private long contrato(long proveedor, long servicio, String desde, String hasta) {
        return jdbc.queryForObject(
                ("INSERT INTO mod05_contrato (numero, proveedor_id, tipo_servicio_id,"
                                + " vigencia_inicio, vigencia_fin) VALUES (:n, :p, :s, %s, %s)"
                                + " RETURNING id")
                        .formatted(desde, hasta),
                Map.of(
                        "n",
                        "C-312-" + UUID.randomUUID().toString().substring(0, 8),
                        "p",
                        proveedor,
                        "s",
                        servicio),
                Long.class);
    }

    /** Otras suites insertan ids a mano: alinea el contador sin dejarlo atrasado para ellas. */
    private long ejemplar(String codigo, String altura) {
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('mod02_ejemplar', 'id'),"
                        + " coalesce(max(id), 0) + 1, false) FROM mod02_ejemplar",
                Map.of(),
                Long.class);
        return jdbc.queryForObject(
                "INSERT INTO mod02_ejemplar (codigo, altura_total_m)"
                        + " VALUES (:codigo, CAST(:altura AS numeric)) RETURNING id",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("codigo", codigo)
                        .addValue("altura", altura, java.sql.Types.VARCHAR),
                Long.class);
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
