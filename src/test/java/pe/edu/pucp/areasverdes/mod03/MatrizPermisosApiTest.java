package pe.edu.pucp.areasverdes.mod03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Matriz de permisos de MOD-03 con la sesión de MOD-01 (AV-252): los cuatro roles consultan y solo
 * el jefe escribe. Las escrituras llevan cuerpos válidos para que el 403 sea por permiso.
 */
public class MatrizPermisosApiTest extends ApiPlanificacion {

    static final String DOMINIO = "@mod03.prueba.pucp.edu.pe";
    static final String RUTA = "/api/v1/planificacion/";
    static final List<String> ROLES =
            List.of("jefe-seccion", "ingeniero-campo", "practicante", "capataz");

    /** Consultas de los endpoints que ya existen. */
    static final List<String> CONSULTAS =
            List.of(
                    "alertas/",
                    "tipos-restriccion/",
                    "restricciones/?desde=2027-01-01&hasta=2027-01-31",
                    "programaciones/?desde=2027-01-01&hasta=2027-01-31",
                    "servicios/",
                    "incidencias/catalogos-registro/",
                    "incidencias/mias/",
                    "incidencias/",
                    "incidencias/posibles-duplicados/?tipo=Rama%20ca%C3%ADda",
                    "servicios/1/historial/",
                    "servicios/1/ruta/",
                    "avisos/",
                    "calendario-cuadrilla/?desde=2027-01-01&hasta=2027-01-31");

    /**
     * Quién obtiene el permiso, en el orden de {@link #ROLES}: jefe, ingeniero, practicante,
     * capataz.
     */
    static final Map<String, List<Boolean>> PERMISOS =
            Map.of(
                    "CONSULTAR / REGISTRAR_INCIDENCIA", List.of(true, true, true, true),
                    "GESTIONAR / CLASIFICAR_INCIDENCIA", List.of(true, false, false, false),
                    "REGISTRAR_CORTE", List.of(false, true, true, true),
                    "LIMPIEZA", List.of(true, true, true, false));

    static final String TODOS = "CONSULTAR / REGISTRAR_INCIDENCIA";
    static final String JEFE = "GESTIONAR / CLASIFICAR_INCIDENCIA";
    static final String CORTE = "REGISTRAR_CORTE";
    static final String LIMPIEZA = "LIMPIEZA";

    /** Fila por permiso: el que exige el código (Acceso.java) y la petición de ejemplo. */
    record PorPermiso(String permiso, MockHttpServletRequestBuilder peticion) {}

    static PorPermiso json(String permiso, MockHttpServletRequestBuilder p, String cuerpo) {
        return new PorPermiso(permiso, p.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    /**
     * Los endpoints restantes de MOD-03; los cuerpos pasan la validación para que el 403 sea de
     * permiso.
     */
    static List<PorPermiso> porPermiso() {
        String incidencia = RUTA + "incidencias/999999/";
        String revision = "{\"observaciones\": \"Revisado\"}";
        return List.of(
                new PorPermiso(TODOS, get(RUTA + "ciclos/999999/")),
                new PorPermiso(TODOS, get(RUTA + "ciclos/999999/avance/")),
                new PorPermiso(TODOS, get(RUTA + "programaciones/999999/")),
                new PorPermiso(TODOS, get(RUTA + "programaciones/999999/zonas-alternativas/")),
                new PorPermiso(TODOS, get(RUTA + "servicios/1/propuesta/")),
                new PorPermiso(TODOS, get(RUTA + "servicios/1/valores-iniciales/")),
                json(
                        JEFE,
                        post(RUTA + "programaciones/999999/interrupciones/"),
                        "{\"zona_afectada\": \"A\", \"zona_alternativa\": \"B\", \"motivo\":"
                                + " \"Lluvia\", \"hora\": \"2027-01-11T10:00:00-05:00\","
                                + " \"aviso_proveedor\": false, \"registrar_restriccion\": false}"),
                json(LIMPIEZA, post(RUTA + "ciclos/999999/limpiezas/"), "{}"),
                new PorPermiso(CORTE, get(RUTA + "ruta-de-hoy/")),
                new PorPermiso(CORTE, get(RUTA + "ruta-de-hoy/fuera-de-ruta/?q=a")),
                json(CORTE, post(RUTA + "ciclos/999999/jardines/1/marca/"), "{}"),
                json(
                        CORTE,
                        post(RUTA + "ciclos/999999/jardines/1/observaciones/"),
                        "{\"texto\": \"Seco\"}"),
                json(
                        CORTE,
                        post(RUTA + "ciclos/999999/fuera-de-ruta/"),
                        "{\"jardin_id\": 1, \"nota\": \"Pedido\"}"),
                json(TODOS, post(RUTA + "avisos/999999/leido/"), "{}"),
                new PorPermiso(TODOS, get(RUTA + "incidencias/mias/999999/")),
                new PorPermiso(TODOS, get(incidencia)),
                json(
                        TODOS,
                        post(RUTA + "incidencias/"),
                        "{\"canal\": \"Web\", \"tipo\": \"Rama caída\", \"reportado_por\":"
                                + " {\"nombre\": \"Vecino\"}, \"espacio\": \"Parque\"}"),
                new PorPermiso(
                        TODOS, multipart(incidencia + "reportes/").param("descripcion", "Otra")),
                json(
                        JEFE,
                        post(incidencia + "clasificar/"),
                        "{\"severidad\": \"Alta\", \"impacto\": \"Alto\", \"via\":"
                                + " \"PersonalPropio\"}"),
                json(JEFE, post(incidencia + "descartar/"), "{}"),
                json(JEFE, post(incidencia + "inspeccion/"), "{\"responsable_id\": 1}"),
                json(JEFE, post(incidencia + "cerrar/"), revision),
                json(JEFE, post(incidencia + "devolver/"), revision),
                json(JEFE, post(incidencia + "causa-retraso/"), "{\"causa\": \"Lluvia\"}"),
                json(JEFE, post(incidencia + "reasignar/"), "{\"capataz_id\": 1}"));
    }

    @BeforeEach
    void cuentasDeMod01() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario RESTART IDENTITY CASCADE");
        for (String rol : ROLES) {
            jdbc.update(
                    """
                    INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                    SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                    """,
                    Map.of("correo", rol + DOMINIO, "rol", rol, "hash", claves.encode(CLAVE)));
        }
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

    /** Escrituras de los endpoints que ya existen, con un cuerpo que pasa la validación. */
    static List<MockHttpServletRequestBuilder> escrituras() {
        return List.of(
                put(RUTA + "servicios/1/frecuencias/Verano/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"frecuencia_teorica_dias\": 21}"),
                put(RUTA + "servicios/1/duracion/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracion_maxima_dias\": 10, \"hora_inicio\": \"06:00\"}"),
                post(RUTA + "restricciones/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_restriccion_id\": 1, \"fecha_inicio\": \"2027-01-08\","
                                        + " \"fecha_fin\": \"2027-01-08\"}"),
                delete(RUTA + "restricciones/1/"),
                post(RUTA + "programaciones/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"tipo_servicio_id\": 1, \"fecha_inicio_prevista\":"
                                    + " \"2027-01-11\", \"fecha_fin_prevista\": \"2027-01-12\"}"),
                patch(RUTA + "programaciones/1/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fecha_inicio_prevista\": \"2027-01-11\","
                                        + " \"fecha_fin_prevista\": \"2027-01-12\"}"),
                post(RUTA + "programaciones/1/cancelacion/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Lluvia\"}"),
                get(RUTA + "incidencias/inspectores/"),
                get(RUTA + "incidencias/1/capataces/"),
                get(RUTA + "incidencias/actividades/"),
                post(RUTA + "incidencias/999999/encargo/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"capataz_id\": 1, \"actividad\": \"Poda menor\","
                                        + " \"plazo\": \"2027-01-11\"}"),
                get(RUTA + "incidencias/1/proveedores/"),
                post(RUTA + "incidencias/999999/requerimiento/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"proveedor_id\": 1, \"contrato_id\": 1,"
                                        + " \"tipo_atencion\": \"Programada\"}"),
                post(RUTA + "alertas/1/motivo/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Lluvia\"}"));
    }

    /** Endpoints de la matriz («MÉTODO url»), para la prueba global de contrato de rutas. */
    public static List<String> endpointsCubiertos() {
        List<String> cubiertos = new ArrayList<>();
        CONSULTAS.forEach(c -> cubiertos.add("GET " + RUTA + c));
        escrituras()
                .forEach(
                        e -> {
                            MockHttpServletRequest r = e.buildRequest(new MockServletContext());
                            cubiertos.add(r.getMethod() + " " + r.getRequestURI());
                        });
        porPermiso()
                .forEach(
                        f -> {
                            MockHttpServletRequest r =
                                    f.peticion().buildRequest(new MockServletContext());
                            cubiertos.add(r.getMethod() + " " + r.getRequestURI());
                        });
        return cubiertos;
    }

    @Test
    void losCuatroRolesConsultanElCalendario() throws Exception {
        for (String rol : ROLES) {
            Cookie cookie = sesion(rol);
            for (String consulta : CONSULTAS) {
                http.perform(get(RUTA + consulta).cookie(cookie)).andExpect(status().isOk());
            }
        }
    }

    @Test
    void losCuatroRolesPasanElPermisoDelDetalleDeUnCicloYDeLosValoresIniciales() throws Exception {
        for (String rol : ROLES) {
            Cookie cookie = sesion(rol);
            http.perform(get(RUTA + "programaciones/999999/").cookie(cookie))
                    .andExpect(status().isNotFound());
            http.perform(get(RUTA + "servicios/999999/valores-iniciales/").cookie(cookie))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void cadaRolObtieneLoQueDiceSuPermiso() throws Exception {
        for (int i = 0; i < ROLES.size(); i++) {
            Cookie cookie = sesion(ROLES.get(i));
            for (PorPermiso fila : porPermiso()) {
                int estado =
                        http.perform(fila.peticion().cookie(cookie).with(csrf()))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                MockHttpServletRequest r = fila.peticion().buildRequest(new MockServletContext());
                String contexto =
                        ROLES.get(i)
                                + " en "
                                + r.getMethod()
                                + " "
                                + r.getRequestURI()
                                + " ("
                                + fila.permiso()
                                + ")";
                if (PERMISOS.get(fila.permiso()).get(i)) {
                    assertNotEquals(403, estado, contexto);
                    assertNotEquals(401, estado, contexto);
                } else {
                    assertEquals(403, estado, contexto);
                }
            }
        }
    }

    @Test
    void soloElJefeEscribe() throws Exception {
        for (String rol : List.of("ingeniero-campo", "practicante", "capataz")) {
            Cookie cookie = sesion(rol);
            for (var escritura : escrituras()) {
                http.perform(escritura.cookie(cookie).with(csrf()))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void elJefePasaElPermisoAlEscribir() throws Exception {
        Cookie cookie = sesion("jefe-seccion");
        for (var escritura : escrituras()) {
            int estado =
                    http.perform(escritura.cookie(cookie).with(csrf()))
                            .andReturn()
                            .getResponse()
                            .getStatus();
            assertNotEquals(403, estado);
            assertNotEquals(401, estado);
        }
    }
}
