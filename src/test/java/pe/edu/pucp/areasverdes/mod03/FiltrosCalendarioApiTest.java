package pe.edu.pucp.areasverdes.mod03;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * RF-PLA-007 (AV-254): el calendario se filtra por cuadrilla, sector y lugar, y el capataz ve el de
 * su cuadrilla. Áreas «Jardín de Sociales» (sector Sociales) y «Jardín de Ciencias» (sector
 * Ciencias), y la «Cuadrilla B» del capataz.
 */
class FiltrosCalendarioApiTest extends ApiPlanificacion {

    static final String DOMINIO = "@mod03.prueba.pucp.edu.pe";
    static final String RUTA = "/api/v1/planificacion/";

    long sociales;
    long ciencias;
    long sectorCiencias;
    long cuadrilla;
    long proveedor;

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        sql.execute(
                "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                        + " core_usuario RESTART IDENTITY CASCADE");
        for (String rol : new String[] {"jefe-seccion", "capataz"}) {
            jdbc.update(
                    """
                    INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                    SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                    """,
                    Map.of("correo", rol + DOMINIO, "rol", rol, "hash", claves.encode(CLAVE)));
        }
        // El escenario firma con el usuario 1: con las FK de MOD-03 en core_usuario (AV-287),
        // tiene que existir antes.
        sql.execute(
                new ClassPathResource("mod03/escenario-programacion.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
        long sectorSociales = sector("Sociales");
        sectorCiencias = sector("Ciencias");
        sociales = area("Jardín de Sociales", sectorSociales);
        ciencias = area("Jardín de Ciencias", sectorCiencias);
        long capataz =
                sql.queryForObject(
                        "INSERT INTO mod04_personal (nombres, apellidos, tipo_personal, usuario_id)"
                                + " VALUES ('Luis', 'Pérez', 'Capataz', 2) RETURNING id",
                        Long.class);
        cuadrilla =
                sql.queryForObject(
                        "INSERT INTO mod04_cuadrilla (nombre, capataz_id, usuario_creacion_id,"
                                + " usuario_modificacion_id) VALUES ('Cuadrilla B', ?, 1, 1)"
                                + " RETURNING id",
                        Long.class,
                        capataz);
        proveedor =
                sql.queryForObject(
                        "INSERT INTO mod05_proveedor(razon_social, activo) VALUES ('Jardines"
                                + " Demo', true) RETURNING id",
                        Long.class);
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

    ResultActions programar(String resto) throws Exception {
        return http.perform(
                post(RUTA + "programaciones/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_servicio_id\": 2, \"fecha_inicio_prevista\":"
                                        + " \"2027-01-11\", \"fecha_fin_prevista\": \"2027-01-12\""
                                        + resto
                                        + "}"));
    }

    ResultActions calendario(String filtros) throws Exception {
        return http.perform(
                get(RUTA + "programaciones/?desde=2027-01-01&hasta=2027-01-31" + filtros)
                        .with(httpBasic("jefe", CLAVE)));
    }

    /** Un ciclo de la cuadrilla en Sociales y otro tercerizado en Ciencias. */
    void dosCiclos() throws Exception {
        programar(", \"cuadrilla_id\": " + cuadrilla + ", \"area_verde_ids\": [" + sociales + "]")
                .andExpect(status().isCreated());
        programar(", \"proveedor_id\": " + proveedor + ", \"area_verde_ids\": [" + ciencias + "]")
                .andExpect(status().isCreated());
    }

    @Test
    void elAltaGuardaLaCuadrillaYLasAreasConSuSector() throws Exception {
        programar(", \"cuadrilla_id\": " + cuadrilla + ", \"area_verde_ids\": [" + sociales + "]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cuadrilla").value("Cuadrilla B"))
                .andExpect(jsonPath("$.areas[0].nombre").value("Jardín de Sociales"))
                .andExpect(jsonPath("$.areas[0].sector").value("Sociales"));
    }

    @Test
    void rechazaCuadrillaConProveedorYAreasQueNoExisten() throws Exception {
        programar(", \"cuadrilla_id\": " + cuadrilla + ", \"proveedor_id\": " + proveedor)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("cuadrilla-con-proveedor"));
        programar(", \"area_verde_ids\": [999999]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("area-invalida"));
        programar(", \"cuadrilla_id\": 999999")
                .andExpect(jsonPath("$.codigo").value("cuadrilla-invalida"));
    }

    @Test
    void filtraPorCuadrillaSectorYLugarCombinados() throws Exception {
        dosCiclos();
        calendario("").andExpect(jsonPath("$.length()").value(2));
        calendario("&cuadrilla_id=" + cuadrilla)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cuadrilla").value("Cuadrilla B"));
        calendario("&proveedor_id=" + proveedor + "&sector_id=" + sectorCiencias)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].proveedor").value("Jardines Demo"));
        calendario("&lugar_id=" + sociales).andExpect(jsonPath("$.length()").value(1));
        calendario("&proveedor_id=" + proveedor + "&lugar_id=" + sociales)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    Cookie sesion(String rol) throws Exception {
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

    @Test
    void elCapatazVeElCalendarioDeSuCuadrilla() throws Exception {
        dosCiclos();
        String periodo = "calendario-cuadrilla/?desde=2027-01-01&hasta=2027-01-31";
        http.perform(get(RUTA + periodo).cookie(sesion("capataz")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuadrilla").value("Cuadrilla B"))
                .andExpect(jsonPath("$.programaciones.length()").value(1))
                .andExpect(
                        jsonPath("$.programaciones[0].areas[0].nombre").value("Jardín de Sociales"))
                .andExpect(jsonPath("$.restricciones").isArray());
        http.perform(get(RUTA + periodo).cookie(sesion("jefe-seccion")))
                .andExpect(jsonPath("$.cuadrilla").isEmpty())
                .andExpect(jsonPath("$.programaciones.length()").value(0));
    }
}
