package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.comun.BasePruebaGlobal;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Resumen e historial agregados del área (AV-431): un área con datos de cada fuente, el alcance por
 * permiso (capataz) y el número de consultas, que no crece con los datos.
 */
public class ResumenAreaApiTest extends BasePruebaGlobal {

    private static final String CLAVE = "prueba-integration-local";
    private static final String JEFE = "jefe-seccion@resumen.prueba.pucp.edu.pe";
    private static final String CAPATAZ = "capataz@resumen.prueba.pucp.edu.pe";
    private static final String OTRO_CAPATAZ = "otro-capataz@resumen.prueba.pucp.edu.pe";

    @MockitoSpyBean NamedParameterJdbcTemplate consultas;

    private long area;
    private long otraArea;
    private long usuarioJefe;
    private long usuarioCapataz;

    @BeforeEach
    void preparar() {
        jdbc.getJdbcTemplate()
                .execute(
                        "TRUNCATE core_sesion, core_registro_acceso, core_registro_auditoria,"
                                + " core_usuario, mod05_usuario, mod05_areaverde, mod05_proveedor,"
                                + " mod05_tiposervicio, mod02_area_verde, mod02_sector,"
                                + " mod04_personal, mod06_sugerencia_ia RESTART IDENTITY CASCADE");
        usuarioJefe = usuario(JEFE, "jefe-seccion");
        usuarioCapataz = usuario(CAPATAZ, "capataz");
        long otroUsuario = usuario(OTRO_CAPATAZ, "capataz");
        jdbc.update(
                "INSERT INTO mod05_usuario (usuario, nombre, password_hash, activo) VALUES"
                        + " ('solicitante', 'Solicitante', 'x', true)",
                Map.of());

        area = area("Jardín Central");
        otraArea = area("Plaza de Armas");
        // Un espacio de MOD-05 sin equivalente desplaza los id: el cruce tiene que ir por nombre.
        for (String nombre : new String[] {"Almacén", "Plaza de Armas", "Jardín Central"}) {
            jdbc.update(
                    "INSERT INTO mod05_areaverde (nombre, activo) VALUES (:n, true)",
                    Map.of("n", nombre));
        }

        // Sector: Sur hasta marzo y Norte desde entonces.
        long sur = sector("Sur");
        long norte = sector("Norte");
        jdbc.update(
                "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, fecha_inicio,"
                        + " fecha_fin, usuario_id) VALUES (:a, :s, '2026-01-01 08:00-05',"
                        + " '2026-03-01 08:00-05', :u)",
                Map.of("a", area, "s", sur, "u", usuarioJefe));
        jdbc.update(
                "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, fecha_inicio,"
                        + " usuario_id) VALUES (:a, :s, '2026-03-01 08:00-05', :u)",
                Map.of("a", area, "s", norte, "u", usuarioJefe));

        // Ejemplares: 3 activos, 1 muerto y 1 retirado en el área; 1 activo en la otra.
        ejemplares(area, "activo", 3);
        ejemplares(area, "muerto", 1);
        ejemplares(area, "retirado", 1);
        ejemplares(otraArea, "activo", 1);

        // Incidencias: tres abiertas y una cerrada; la del 16/10 la reportó el capataz.
        incidencia(area, "Registrada", "2026-10-10 09:00-05", usuarioJefe);
        incidencia(area, "EnAtencion", "2026-10-12 09:00-05", usuarioJefe);
        incidencia(area, "Cerrada", "2026-10-14 09:00-05", usuarioJefe);
        incidencia(area, "Registrada", "2026-10-16 09:00-05", usuarioCapataz);
        incidencia(otraArea, "Registrada", "2026-10-17 09:00-05", usuarioJefe);

        // Actividades: una del capataz de la sesión y otra de otro capataz.
        jdbc.update(
                "INSERT INTO mod04_tipo_actividad (id, nombre, activo) VALUES (9431, 'Riego"
                        + " prueba', true) ON CONFLICT (id) DO NOTHING",
                Map.of());
        long propio = capataz("Rosa", usuarioCapataz);
        long ajeno = capataz("Pedro", otroUsuario);
        actividad(propio, "2026-10-05 08:00-05", "Riego del césped");
        actividad(ajeno, "2026-10-20 08:00-05", "Riego de la zona alta");

        // Atenciones tercerizadas: dos en el área y una en la otra.
        jdbc.update(
                "INSERT INTO mod05_tiposervicio (id, nombre, ejecutor, es_riesgo, activo)"
                        + " VALUES (1, 'Corte de césped', 'Tercerizado', false, true)",
                Map.of());
        jdbc.update(
                "INSERT INTO mod05_proveedor (id, razon_social, ruc, activo) VALUES"
                        + " (1, 'Proveedor prueba', '20100000001', true)",
                Map.of());
        jdbc.update(
                "INSERT INTO mod05_contrato (id, numero, vigencia_inicio, vigencia_fin,"
                        + " proveedor_id, tipo_servicio_id) VALUES (1, 'CT-1', '2026-01-01',"
                        + " '2026-12-31', 1, 1)",
                Map.of());
        atencion("AT-001", "2026-10-08 09:00-05", "Jardín Central");
        atencion("AT-002", "2026-10-25 09:00-05", "Jardín Central");
        atencion("AT-003", "2026-10-26 09:00-05", "Plaza de Armas");

        // Programación: la completada no cuenta; entre las vigentes manda la más cercana.
        programacion("Completada", "current_date - 20", "current_date - 15");
        programacion("Programada", "current_date + 30", "current_date + 31");
        programacion("Programada", "current_date + 10", "current_date + 12");

        // Prioridad: la decisión de una priorización validada para el área.
        jdbc.update(
                """
                INSERT INTO mod06_sugerencia_ia (tipo, estado, contenido, validador_id,
                    fecha_validacion, usuario_creacion_id, usuario_modificacion_id)
                VALUES ('Priorizacion', 'Validada',
                    jsonb_build_object('decision', jsonb_build_object('por_elemento',
                        jsonb_build_object('area-' || :a, jsonb_build_object('accion', 'corregir',
                            'prioridad_final', 'Alta')))),
                    :u, '2026-10-27 10:00-05', :u, :u)
                """,
                Map.of("a", area, "u", usuarioJefe));
        // Ni una rechazada posterior ni una validada que no incluye el área cambian la prioridad.
        prioridad("Rechazada", area, "Baja", "2026-10-28 10:00-05");
        prioridad("Validada", otraArea, "Media", "2026-10-29 10:00-05");
    }

    @Test
    void unaLlamadaDevuelveElResumenCompletoDeTodasLasFuentes() throws Exception {
        pedir(JEFE, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area.id").value(area))
                .andExpect(jsonPath("$.area.nombre").value("Jardín Central"))
                .andExpect(jsonPath("$.area.sector_nombre").value("Norte"))
                .andExpect(jsonPath("$.ejemplares_por_situacion.activo").value(3))
                .andExpect(jsonPath("$.ejemplares_por_situacion.muerto").value(1))
                .andExpect(jsonPath("$.ejemplares_por_situacion.retirado").value(1))
                .andExpect(jsonPath("$.ejemplares_por_situacion.trasladado").value(0))
                .andExpect(jsonPath("$.incidencias_abiertas").value(3))
                .andExpect(jsonPath("$.ultima_atencion.tipo").value("atencion"))
                .andExpect(jsonPath("$.ultima_atencion.titulo").value("AT-002"))
                .andExpect(jsonPath("$.proxima_actividad.tipo_servicio").value("Corte de césped"))
                .andExpect(jsonPath("$.proxima_actividad.estado").value("Programada"))
                .andExpect(jsonPath("$.prioridad_validada.prioridad").value("Alta"))
                .andExpect(
                        jsonPath("$.fuentes_visibles")
                                .value(
                                        org.hamcrest.Matchers.containsInAnyOrder(
                                                "actividades",
                                                "incidencias",
                                                "atenciones",
                                                "programacion",
                                                "prioridad",
                                                "cambios_sector")))
                .andExpect(jsonPath("$.historial.count").value(10));
    }

    @Test
    void elHistorialMezclaLasFuentesDeMasRecienteAMasAntigua() throws Exception {
        pedir(JEFE, "page_size=100")
                .andExpect(jsonPath("$.historial.results.length()").value(10))
                .andExpect(jsonPath("$.historial.results[0].tipo").value("atencion"))
                .andExpect(jsonPath("$.historial.results[1].tipo").value("actividad"))
                .andExpect(jsonPath("$.historial.results[1].titulo").value("Riego prueba"))
                .andExpect(jsonPath("$.historial.results[2].tipo").value("incidencia"))
                .andExpect(jsonPath("$.historial.results[6].tipo").value("atencion"))
                .andExpect(jsonPath("$.historial.results[7].tipo").value("actividad"))
                .andExpect(jsonPath("$.historial.results[8].tipo").value("cambio_sector"))
                .andExpect(jsonPath("$.historial.results[8].estado").value("vigente"))
                .andExpect(jsonPath("$.historial.results[9].estado").value("finalizada"));
    }

    @Test
    void elHistorialPaginaSinRepetirNiPerderEntradas() throws Exception {
        pedir(JEFE, "page_size=4")
                .andExpect(jsonPath("$.historial.count").value(10))
                .andExpect(jsonPath("$.historial.results.length()").value(4))
                .andExpect(jsonPath("$.historial.previous").isEmpty())
                .andExpect(
                        jsonPath("$.historial.next")
                                .value(
                                        org.hamcrest.Matchers.allOf(
                                                org.hamcrest.Matchers.containsString(
                                                        "/api/v1/inventario/areas-verdes/"
                                                                + area
                                                                + "/resumen?"),
                                                org.hamcrest.Matchers.containsString("page=2"),
                                                org.hamcrest.Matchers.containsString(
                                                        "page_size=4"))));
        pedir(JEFE, "page_size=4&page=2")
                .andExpect(jsonPath("$.historial.results.length()").value(4))
                .andExpect(jsonPath("$.historial.results[0].tipo").value("incidencia"))
                .andExpect(
                        jsonPath("$.historial.previous")
                                .value(org.hamcrest.Matchers.containsString("page=1")))
                .andExpect(
                        jsonPath("$.historial.next")
                                .value(org.hamcrest.Matchers.containsString("page=3")));
        pedir(JEFE, "page_size=4&page=3")
                .andExpect(jsonPath("$.historial.results.length()").value(2))
                .andExpect(jsonPath("$.historial.results[1].estado").value("finalizada"))
                .andExpect(jsonPath("$.historial.next").isEmpty());
        // Más allá de la última página: vacía, sin error.
        pedir(JEFE, "page_size=4&page=9")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial.results.length()").value(0));
    }

    @Test
    void quienNoPuedeVerAtencionesNiLasRecibeNiSabeDeEllas() throws Exception {
        pedir(CAPATAZ, "page_size=100")
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.fuentes_visibles")
                                .value(
                                        org.hamcrest.Matchers.containsInAnyOrder(
                                                "actividades",
                                                "incidencias",
                                                "programacion",
                                                "cambios_sector")))
                .andExpect(jsonPath("$.prioridad_validada").isEmpty())
                // Solo su actividad y su incidencia, más los dos cambios de sector.
                .andExpect(jsonPath("$.historial.count").value(4))
                .andExpect(jsonPath("$.historial.results[?(@.tipo == 'atencion')]").isEmpty())
                .andExpect(
                        jsonPath("$.historial.results[?(@.tipo == 'actividad')]")
                                .value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.incidencias_abiertas").value(1))
                .andExpect(jsonPath("$.ultima_atencion.tipo").value("actividad"))
                .andExpect(jsonPath("$.proxima_actividad.estado").value("Programada"));
    }

    @Test
    void unAreaInexistenteDaNoEncontrado() throws Exception {
        http.perform(get("/api/v1/inventario/areas-verdes/999999/resumen").cookie(sesion(JEFE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("area-no-encontrada"));
    }

    @Test
    void sinSesionNoSeConsulta() throws Exception {
        int estado =
                http.perform(get("/api/v1/inventario/areas-verdes/" + area + "/resumen"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        assertThat(estado).isIn(401, 403);
    }

    @Test
    void rechazaUnaPaginaOUnTamanoNoValidos() throws Exception {
        pedir(JEFE, "page=0").andExpect(status().isBadRequest());
        pedir(JEFE, "page_size=101").andExpect(status().isBadRequest());
    }

    @Test
    void elNumeroDeConsultasNoDependeDelTamanoDeLosDatos() throws Exception {
        Cookie cookie = sesion(JEFE);
        int pocas = consultasDeUnaLlamada(cookie);

        for (int i = 0; i < 30; i++) {
            incidencia(area, "Registrada", "2026-09-01 09:00-05", usuarioJefe);
            atencion("AT-9" + i, "2026-09-02 09:00-05", "Jardín Central");
        }
        ejemplares(area, "activo", 30);
        int muchas = consultasDeUnaLlamada(cookie);

        assertThat(muchas).isEqualTo(pocas);
        // Incluye las consultas de la sesión; el resumen en sí hace nueve.
        assertThat(muchas).isLessThanOrEqualTo(20);
    }

    private int consultasDeUnaLlamada(Cookie cookie) throws Exception {
        Mockito.clearInvocations(consultas);
        http.perform(
                        get("/api/v1/inventario/areas-verdes/" + area + "/resumen")
                                .param("page_size", "5")
                                .cookie(cookie))
                .andExpect(status().isOk());
        return (int)
                Mockito.mockingDetails(consultas).getInvocations().stream()
                        .filter(i -> i.getMethod().getName().startsWith("query"))
                        .count();
    }

    private void prioridad(String estado, long areaId, String valor, String fecha) {
        jdbc.update(
                """
                INSERT INTO mod06_sugerencia_ia (tipo, estado, contenido, validador_id,
                    fecha_validacion, usuario_creacion_id, usuario_modificacion_id)
                VALUES ('Priorizacion', :e,
                    jsonb_build_object('decision', jsonb_build_object('por_elemento',
                        jsonb_build_object('area-' || :a, jsonb_build_object('accion', 'corregir',
                            'prioridad_final', CAST(:v AS text))))),
                    :u, CAST(:f AS timestamptz), :u, :u)
                """,
                Map.of("e", estado, "a", areaId, "v", valor, "f", fecha, "u", usuarioJefe));
    }

    private ResultActions pedir(String correo, String extra) throws Exception {
        return http.perform(
                get("/api/v1/inventario/areas-verdes/" + area + "/resumen?" + extra)
                        .cookie(sesion(correo)));
    }

    private long usuario(String correo, String rol) {
        return jdbc.queryForObject(
                """
                INSERT INTO core_usuario (nombre_usuario, correo, nombre, credencial_hash, rol_id)
                SELECT :correo, :correo, :rol, :hash, id FROM core_rol WHERE codigo = :rol
                RETURNING id
                """,
                Map.of("correo", correo, "hash", claves.encode(CLAVE), "rol", rol),
                Long.class);
    }

    private long area(String nombre) {
        return jdbc.queryForObject(
                "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                        + " usuario_creacion_id, usuario_modificacion_id) VALUES (:n, 'Campus',"
                        + " 'Zona', 100, :u, :u) RETURNING id",
                Map.of("n", nombre, "u", usuarioJefe),
                Long.class);
    }

    private long sector(String nombre) {
        return jdbc.queryForObject(
                "INSERT INTO mod02_sector (nombre, usuario_creacion_id, usuario_modificacion_id)"
                        + " VALUES (:n, :u, :u) RETURNING id",
                Map.of("n", nombre, "u", usuarioJefe),
                Long.class);
    }

    private void ejemplares(long areaId, String situacion, int cantidad) {
        jdbc.update(
                "INSERT INTO mod02_ejemplar (codigo, area_verde_inventario_id, situacion)"
                        + " SELECT 'EJ-' || nextval('mod02_ejemplar_codigo_seq'), :a, :s"
                        + " FROM generate_series(1, :n)",
                Map.of("a", areaId, "s", situacion, "n", cantidad));
    }

    private void incidencia(long areaId, String estado, String fecha, long reportante) {
        jdbc.update(
                "INSERT INTO mod03_incidencia (codigo, tipo_incidencia_id, area_verde_id,"
                    + " descripcion, canal, reportante_usuario_id, estado, registrada_por_id,"
                    + " fecha_registro) SELECT 'INC-' || nextval('mod03_incidencia_numero_seq'),"
                    + " min(id), :a, 'Prueba', 'Web', :r, :e, :r, CAST(:f AS timestamptz) FROM"
                    + " mod03_tipo_incidencia",
                Map.of("a", areaId, "r", reportante, "e", estado, "f", fecha));
    }

    private long capataz(String nombre, long usuarioId) {
        return jdbc.queryForObject(
                "INSERT INTO mod04_personal (nombres, apellidos, tipo_personal, usuario_id)"
                        + " VALUES (:n, 'Prueba', 'Capataz', :u) RETURNING id",
                Map.of("n", nombre, "u", usuarioId),
                Long.class);
    }

    private void actividad(long capatazId, String fecha, String observaciones) {
        long cuadrilla =
                jdbc.queryForObject(
                        "INSERT INTO mod04_cuadrilla (nombre, capataz_id, usuario_creacion_id,"
                                + " usuario_modificacion_id) VALUES ('Cuadrilla ' || :c, :c, :u,"
                                + " :u) RETURNING id",
                        Map.of("c", capatazId, "u", usuarioJefe),
                        Long.class);
        long parte =
                jdbc.queryForObject(
                        "INSERT INTO mod04_parte_diario (cuadrilla_id, capataz_id, fecha,"
                                + " usuario_creacion_id) VALUES (:q, :c, CAST(:f AS date), :u)"
                                + " RETURNING id",
                        Map.of(
                                "q",
                                cuadrilla,
                                "c",
                                capatazId,
                                "f",
                                fecha.substring(0, 10),
                                "u",
                                usuarioJefe),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod04_actividad (parte_id, tipo_actividad_id, area_verde_id,"
                        + " fecha_hora_actividad, tiempo_minutos, observaciones,"
                        + " usuario_creacion_id) SELECT :p, 9431, m.id, CAST(:f AS timestamptz),"
                        + " 60, :o, :u FROM mod05_areaverde m WHERE m.nombre = 'Jardín Central'",
                Map.of("p", parte, "f", fecha, "o", observaciones, "u", usuarioJefe));
    }

    private void atencion(String codigo, String inicio, String espacio) {
        jdbc.update(
                """
                WITH nueva AS (
                    INSERT INTO mod05_atenciontercerizada (fecha_creacion, fecha_modificacion,
                        codigo, clase, descripcion, origen, numero_orden_compra, fecha_hora_inicio,
                        estado, es_riesgo, comentarios_acuerdo_unidad, solicitado_por_id,
                        usuario_creacion_id, usuario_modificacion_id, contrato_id, tipo_servicio_id)
                    VALUES (CAST(:f AS timestamptz), CAST(:f AS timestamptz), :c, 'Programada',
                        'Prueba', 'Demanda', '', CAST(:f AS timestamptz), 'En ejecución', false,
                        '', 1, 1, 1, 1, 1)
                    RETURNING id)
                INSERT INTO mod05_alcanceatencion (atencion_id, area_verde_id)
                SELECT nueva.id, m.id FROM nueva, mod05_areaverde m WHERE m.nombre = :e
                """,
                Map.of("f", inicio, "c", codigo, "e", espacio));
    }

    private void programacion(String estado, String inicio, String fin) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod03_programacion_servicio (tipo_servicio_id, estacion,"
                                + " fecha_inicio_prevista, fecha_fin_prevista, estado,"
                                + " creado_por_id, usuario_creacion_id, usuario_modificacion_id)"
                                + " VALUES (1, 'Verano', "
                                + inicio
                                + ", "
                                + fin
                                + ", :e, 1, 1, 1) RETURNING id",
                        Map.of("e", estado),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod03_programacion_area (programacion_id, area_verde_id) VALUES"
                        + " (:p, :a)",
                Map.of("p", id, "a", area));
    }

    private Cookie sesion(String correo) throws Exception {
        return http.perform(
                        post("/api/v1/core/auth/login/")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"identificador\": \"%s\", \"contrasena\": \"%s\"}"
                                                .formatted(correo, CLAVE)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie(SeguridadMod01.COOKIE_SESION);
    }
}
