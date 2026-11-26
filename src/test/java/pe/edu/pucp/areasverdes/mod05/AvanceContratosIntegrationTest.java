package pe.edu.pucp.areasverdes.mod05;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;

import com.fasterxml.jackson.databind.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

/**
 * AV-435 (RD-16): el listado de contratos trae el avance de sus atenciones (totales, conformes,
 * observadas y pendientes de verificación) en una sola consulta agregada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AvanceContratosIntegrationTest {
    static final String PASSWORD = "prueba-integration-local";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
    }

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired Mod05Repository repo;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean NamedParameterJdbcTemplate jdbcEspiado;
    long jefe, proveedor, tipo;
    int secuencia;

    @BeforeEach
    void preparar() {
        repo.jdbc()
                .getJdbcTemplate()
                .execute(
                        "TRUNCATE mod05_usuario,mod05_proveedor,mod05_tiposervicio RESTART IDENTITY"
                                + " CASCADE");
        jefe =
                repo.insert(
                        "usuario",
                        values(
                                "usuario",
                                "jefe",
                                "nombre",
                                "Nombre jefe",
                                "password_hash",
                                passwords.encode(PASSWORD),
                                "activo",
                                true));
        repo.jdbc()
                .update(
                        "INSERT INTO mod05_usuario_rol(usuario_id,rol) VALUES (:id,:rol)",
                        Map.of("id", jefe, "rol", Usuario.JEFE));
        proveedor =
                repo.insert(
                        "proveedor",
                        values("razon_social", "Proveedor Demo", "ruc", null, "activo", true));
        tipo =
                repo.insert(
                        "tiposervicio",
                        values(
                                "nombre",
                                "Poda",
                                "ejecutor",
                                "Tercerizado",
                                "es_riesgo",
                                false,
                                "activo",
                                true));
    }

    long contrato(String numero) {
        return repo.insert(
                "contrato",
                values(
                        "numero",
                        numero,
                        "proveedor_id",
                        proveedor,
                        "tipo_servicio_id",
                        tipo,
                        "vigencia_inicio",
                        LocalDate.now(Mod05Application.LIMA).minusDays(10),
                        "vigencia_fin",
                        LocalDate.now(Mod05Application.LIMA).plusDays(10)));
    }

    void atencion(long contrato, String estado) {
        repo.jdbc()
                .update(
                        """
                        INSERT INTO mod05_atenciontercerizada(fecha_creacion,fecha_modificacion,codigo,clase,descripcion,origen,
                            numero_orden_compra,estado,es_riesgo,comentarios_acuerdo_unidad,solicitado_por_id,
                            usuario_creacion_id,usuario_modificacion_id,contrato_id,tipo_servicio_id)
                        VALUES (now(),now(),:codigo,'ProgramadaContrato','Prueba','Demanda','',:estado,false,'',:u,:u,:u,:c,:t)
                        """,
                        Map.of(
                                "codigo",
                                "AT-AV435-" + (++secuencia),
                                "estado",
                                estado,
                                "u",
                                jefe,
                                "c",
                                contrato,
                                "t",
                                tipo));
    }

    JsonNode listado() throws Exception {
        var resultado =
                http.perform(get("/api/tercerizados/contratos/").with(httpBasic("jefe", PASSWORD)))
                        .andExpect(status().isOk())
                        .andReturn();
        return json.readTree(resultado.getResponse().getContentAsByteArray());
    }

    static JsonNode fila(JsonNode listado, String numero) {
        for (var c : listado.get("results")) if (c.get("numero").asText().equals(numero)) return c;
        throw new AssertionError("Falta el contrato " + numero);
    }

    static void avance(JsonNode c, int total, int conformes, int observadas, int pendientes) {
        assertEquals(total, c.get("atenciones").asInt());
        assertEquals(conformes, c.get("atenciones_conformes").asInt());
        assertEquals(observadas, c.get("atenciones_observadas").asInt());
        assertEquals(pendientes, c.get("atenciones_pendientes").asInt());
    }

    @Test
    void contratoSinAtencionesTraeTodoACero() throws Exception {
        contrato("CT-VACIO");
        avance(fila(listado(), "CT-VACIO"), 0, 0, 0, 0);
    }

    @Test
    void cuentaCadaEstadoPorSuCuentaYNoMezclaContratos() throws Exception {
        long a = contrato("CT-A"), b = contrato("CT-B");
        for (String e :
                List.of(
                        Atencion.CONFORME,
                        Atencion.CONFORME,
                        Atencion.OBSERVADO,
                        Atencion.REPORTE,
                        Atencion.REPORTE,
                        Atencion.REPORTE,
                        Atencion.EJECUCION,
                        // Sin informe del proveedor aún: no está pendiente de verificación.
                        Atencion.EJECUTADO,
                        Atencion.OC,
                        Atencion.REQUERIMIENTO)) atencion(a, e);
        atencion(b, Atencion.OBSERVADO);
        var lista = listado();
        avance(fila(lista, "CT-A"), 10, 2, 1, 3);
        avance(fila(lista, "CT-B"), 1, 0, 1, 0);
        // El detalle de un contrato trae las mismas cifras.
        var detalle =
                json.readTree(
                        http.perform(
                                        get("/api/tercerizados/contratos/" + a + "/")
                                                .with(httpBasic("jefe", PASSWORD)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray());
        avance(detalle, 10, 2, 1, 3);
    }

    @Test
    void elNumeroDeConsultasNoDependeDelNumeroDeContratos() throws Exception {
        contrato("CT-1");
        listado();
        clearInvocations(jdbcEspiado);
        listado();
        int conUno = consultas();
        // El espía cuenta también las llamadas internas de la plantilla; basta con que vea algo,
        // porque si no viera nada la igualdad de abajo pasaría sin probar nada.
        assertTrue(conUno > 0, "El espía no registra las consultas del listado");

        for (int i = 2; i <= 6; i++) atencion(contrato("CT-" + i), Atencion.CONFORME);
        clearInvocations(jdbcEspiado);
        var lista = listado();
        assertEquals(6, lista.get("count").asInt());
        assertEquals(conUno, consultas());
    }

    int consultas() {
        return mockingDetails(jdbcEspiado).getInvocations().stream()
                .filter(i -> i.getMethod().getName().startsWith("query"))
                .filter(
                        i ->
                                i.getArguments()[0] instanceof String sql
                                        && sql.contains("mod05_contrato"))
                .toList()
                .size();
    }
}
