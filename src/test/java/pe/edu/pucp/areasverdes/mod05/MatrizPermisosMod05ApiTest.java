package pe.edu.pucp.areasverdes.mod05;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import pe.edu.pucp.areasverdes.comun.BasePruebaGlobal;
import pe.edu.pucp.areasverdes.mod01.config.SeguridadMod01;

/**
 * Matriz de permisos de MOD-05 (AV-244) con la sesión de MOD-01. Cada fila lleva el permiso que
 * exige el código ({@code Usuario.exigir(...)}) y la tabla {@link #PERMISOS} lo cruza con la
 * semilla de {@code core_rol_permiso} (V9 y V76): el capataz no tiene ningún permiso {@code SER.*}.
 * Los cuerpos pasan la validación para que el 403 sea por permiso.
 *
 * <pre>
 * | Permiso                    | jefe | ingeniero | practicante | capataz |
 * |----------------------------|------|-----------|-------------|---------|
 * | SER.ATENCION.REGISTRAR     |  ok  |    ok     |     ok      |   403   |
 * | SER.ATENCION.GESTIONAR     |  ok  |   403     |    403      |   403   |
 * | SER.CONFORMIDAD.REGISTRAR  |  ok  |   403     |    403      |   403   |
 * | SER.SEGURIDAD.VERIFICAR    |  ok  |    ok     |    403      |   403   |
 * | SER.CONTRATO.CONSULTAR     |  ok  |    ok     |     ok      |   403   |
 * | SER.CONTRATO.GESTIONAR     |  ok  |   403     |    403      |   403   |
 * | sesión iniciada (/me)      |  ok  |    ok     |     ok      |   ok    |
 * </pre>
 */
public class MatrizPermisosMod05ApiTest extends BasePruebaGlobal {

    static final String DOMINIO = "@mod05.prueba.pucp.edu.pe";
    static final String CLAVE = "prueba-integration-local";
    static final String B = "/api/tercerizados/";
    static final List<String> ROLES =
            List.of("jefe-seccion", "ingeniero-campo", "practicante", "capataz");

    static final String REGISTRAR = "SER.ATENCION.REGISTRAR";
    static final String GESTIONAR = "SER.ATENCION.GESTIONAR";
    static final String CONFORMIDAD = "SER.CONFORMIDAD.REGISTRAR";
    static final String SEGURIDAD = "SER.SEGURIDAD.VERIFICAR";
    static final String CONSULTAR_CONTRATOS = "SER.CONTRATO.CONSULTAR";
    static final String GESTIONAR_CONTRATOS = "SER.CONTRATO.GESTIONAR";
    static final String SESION = "sesión iniciada";

    /** Quién lo tiene, en el orden de {@link #ROLES}. */
    static final Map<String, List<Boolean>> PERMISOS =
            Map.of(
                    REGISTRAR, List.of(true, true, true, false),
                    GESTIONAR, List.of(true, false, false, false),
                    CONFORMIDAD, List.of(true, false, false, false),
                    SEGURIDAD, List.of(true, true, false, false),
                    CONSULTAR_CONTRATOS, List.of(true, true, true, false),
                    GESTIONAR_CONTRATOS, List.of(true, false, false, false),
                    SESION, List.of(true, true, true, true));

    record Fila(String permiso, MockHttpServletRequestBuilder peticion) {}

    static Fila lectura(String permiso, String url) {
        return new Fila(permiso, get(url));
    }

    static Fila json(String permiso, MockHttpServletRequestBuilder p, String cuerpo) {
        return new Fila(permiso, p.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    static List<Fila> filas() {
        String atencion = B + "atenciones/999999/";
        String fecha = "\"2027-01-11T10:00:00-05:00\"";
        String alcance = "{\"area_verde_id\": 1}";
        String proveedor =
                "{\"razon_social\": \"Verde SAC\", \"ruc\": \"20123456789\","
                        + " \"tipo_vinculo\": \"SinVinculo\"}";
        String contrato =
                "{\"proveedor_id\": 1, \"tipo_servicio_id\": 1, \"area_metrado_estimado\": \"1"
                        + " ha\", \"monto_fijo\": 100, \"frecuencia_cantidad\": 4,"
                        + " \"frecuencia_unidad\": \"Anio\", \"vigencia_inicio\": \"2027-01-01\","
                        + " \"vigencia_fin\": \"2027-12-31\"}";
        String producto = "{\"nombre\": \"Fungicida\"}";
        String observacion =
                "{\"area_verde_id\": 1, \"plaga_id\": 1, \"descripcion\": \"Hojas\","
                        + " \"fecha_observacion\": \"2027-01-11\"}";
        List<Fila> filas = new ArrayList<>();
        filas.add(lectura(SESION, B + "me/"));
        // Ciclo de atención
        filas.add(lectura(REGISTRAR, B + "atenciones/"));
        filas.add(
                json(
                        GESTIONAR,
                        post(B + "atenciones/"),
                        "{\"contrato_id\": 1, \"descripcion\": \"Poda\", \"origen\": \"Demanda\","
                                + " \"alcances\": ["
                                + alcance
                                + "]}"));
        filas.add(lectura(REGISTRAR, atencion));
        filas.add(
                json(
                        REGISTRAR,
                        patch(atencion + "orden-compra/"),
                        "{\"numero_orden_compra\": \"OC-1\"}"));
        filas.add(
                json(
                        REGISTRAR,
                        post(atencion + "inicio/"),
                        "{\"fecha_hora_inicio\": " + fecha + "}"));
        filas.add(json(REGISTRAR, post(atencion + "fin/"), "{\"fecha_hora_fin\": " + fecha + "}"));
        filas.add(
                json(
                        GESTIONAR,
                        patch(atencion + "origen/"),
                        "{\"origen\": \"Demanda\", \"motivo\": \"Cambio\"}"));
        filas.add(
                json(GESTIONAR, patch(atencion + "acuerdo-unidad/"), "{\"acuerdo_unidad\": true}"));
        filas.add(
                json(
                        CONFORMIDAD,
                        post(atencion + "conformidad/"),
                        "{\"resultado\": \"Conforme\", \"fecha_conformidad\": \"2027-01-11\"}"));
        filas.add(lectura(REGISTRAR, atencion + "formato-reporte/"));
        filas.add(lectura(REGISTRAR, atencion + "reportes/"));
        filas.add(
                new Fila(
                        REGISTRAR,
                        multipart(atencion + "reportes/")
                                .file("archivos", new byte[] {1})
                                .param("fecha_recepcion", "2027-01-11")
                                .param("documentos", "[{\"categoria\": \"InformeFinal\"}]")));
        filas.add(lectura(REGISTRAR, atencion + "reportes/999999/"));
        filas.add(lectura(REGISTRAR, atencion + "reportes/999999/documentos/999999/descarga/"));
        filas.add(lectura(REGISTRAR, B + "catalogos/tipos-servicio/"));
        filas.add(
                json(
                        SEGURIDAD,
                        post("/api/v1/tercerizados/atenciones/999999/seguridad/"),
                        "{\"medidas\": \"Cinta y EPP\", \"confirmado\": true}"));
        // Proveedores, contratos y productos
        filas.add(lectura(CONSULTAR_CONTRATOS, B + "proveedores/"));
        filas.add(json(GESTIONAR_CONTRATOS, post(B + "proveedores/"), proveedor));
        filas.add(lectura(CONSULTAR_CONTRATOS, B + "proveedores/999999/"));
        filas.add(json(GESTIONAR_CONTRATOS, put(B + "proveedores/999999/"), proveedor));
        filas.add(
                json(
                        GESTIONAR_CONTRATOS,
                        patch(B + "proveedores/999999/estado/"),
                        "{\"activo\": true}"));
        filas.add(lectura(CONSULTAR_CONTRATOS, B + "contratos/"));
        filas.add(json(GESTIONAR_CONTRATOS, post(B + "contratos/"), contrato));
        filas.add(lectura(CONSULTAR_CONTRATOS, B + "contratos/999999/"));
        filas.add(json(GESTIONAR_CONTRATOS, put(B + "contratos/999999/"), contrato));
        filas.add(lectura(CONSULTAR_CONTRATOS, B + "productos-agronomicos/"));
        filas.add(json(GESTIONAR_CONTRATOS, post(B + "productos-agronomicos/"), producto));
        filas.add(lectura(CONSULTAR_CONTRATOS, B + "productos-agronomicos/999999/"));
        filas.add(json(GESTIONAR_CONTRATOS, put(B + "productos-agronomicos/999999/"), producto));
        filas.add(
                json(
                        GESTIONAR_CONTRATOS,
                        patch(B + "productos-agronomicos/999999/estado/"),
                        "{\"activo\": true}"));
        // Atención especial, plagas y plan fitosanitario
        filas.add(lectura(REGISTRAR, B + "atenciones-especiales/"));
        filas.add(
                json(
                        REGISTRAR,
                        post(B + "atenciones-especiales/"),
                        "{\"ejemplar_id\": 1, \"plaga_id\": 1, \"aplicaciones_adicionales\": 1}"));
        filas.add(lectura(REGISTRAR, B + "atenciones-especiales/999999/"));
        filas.add(
                json(
                        GESTIONAR,
                        post(B + "atenciones-especiales/999999/aplicaciones/"),
                        "{\"contrato_id\": 1, \"fecha_inicio\": \"2027-01-11\", \"fecha_fin\":"
                                + " \"2027-01-12\"}"));
        filas.add(json(REGISTRAR, post(B + "atenciones-especiales/999999/cancelar/"), "{}"));
        filas.add(lectura(REGISTRAR, B + "plagas/"));
        filas.add(lectura(REGISTRAR, B + "observaciones-plagas/"));
        filas.add(json(REGISTRAR, post(B + "observaciones-plagas/"), observacion));
        filas.add(lectura(REGISTRAR, B + "observaciones-plagas/999999/"));
        filas.add(lectura(REGISTRAR, B + "observaciones-plagas/por-alcance/?area_verde_id=1"));
        filas.add(
                json(
                        GESTIONAR,
                        post(B + "plan-fitosanitario/"),
                        "{\"contrato_id\": 1, \"aplicaciones\": [{\"estacion\": \"Verano\","
                                + " \"fecha_inicio\": \"2027-01-11\", \"fecha_fin\":"
                                + " \"2027-01-12\", \"alcances\": ["
                                + alcance
                                + "]}]}"));
        return filas;
    }

    /** Endpoints de la matriz («MÉTODO url»), para la prueba global de contrato de rutas. */
    public static List<String> endpointsCubiertos() {
        List<String> cubiertos = new ArrayList<>();
        filas().forEach(
                        f -> {
                            MockHttpServletRequest r =
                                    f.peticion().buildRequest(new MockServletContext());
                            cubiertos.add(r.getMethod() + " " + r.getRequestURI());
                        });
        return cubiertos;
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

    @Test
    void cadaRolObtieneLoQueDiceSuPermiso() throws Exception {
        for (int i = 0; i < ROLES.size(); i++) {
            Cookie cookie = sesion(ROLES.get(i));
            for (Fila fila : filas()) {
                int estado =
                        http.perform(fila.peticion().cookie(cookie).with(csrf()))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                MockHttpServletRequest r = fila.peticion().buildRequest(new MockServletContext());
                String contexto =
                        "%s en %s %s (%s) respondió %d"
                                .formatted(
                                        ROLES.get(i),
                                        r.getMethod(),
                                        r.getRequestURI(),
                                        fila.permiso(),
                                        estado);
                if (PERMISOS.get(fila.permiso()).get(i)) {
                    assertNotEquals(403, estado, contexto);
                    assertNotEquals(401, estado, contexto);
                } else {
                    assertEquals(403, estado, contexto);
                }
            }
        }
    }
}
