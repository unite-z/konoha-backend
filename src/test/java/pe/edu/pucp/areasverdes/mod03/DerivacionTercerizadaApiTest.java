package pe.edu.pucp.areasverdes.mod03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Derivar una incidencia clasificada a un proveedor (AV-278, RF-INC-004): crea la atención de
 * MOD-05 con el ejemplar de la incidencia y la deja Derivada · tercerizada; el informe del
 * proveedor la pasa a Atendida y vale como evidencia para cerrarla. «Podas Altura Demo» tiene
 * contrato vigente; «Otro Demo», uno vencido.
 */
class DerivacionTercerizadaApiTest extends ApiPlanificacion {

    static final String RUTA = "/api/v1/planificacion/incidencias/";

    @Autowired ObjectMapper json;

    long proveedor;
    long contrato;
    long otro;
    long vencido;

    @BeforeEach
    void escenario() throws Exception {
        var sql = jdbc.getJdbcTemplate();
        long sector =
                sql.queryForObject(
                        "INSERT INTO mod02_sector (nombre, usuario_creacion_id,"
                            + " usuario_modificacion_id) VALUES ('Sociales', 1, 1) RETURNING id",
                        Long.class);
        long area =
                sql.queryForObject(
                        "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                                + " usuario_creacion_id, usuario_modificacion_id) VALUES ('Jardín"
                                + " AV-278', 'Biblioteca', 'Campus', 120, 1, 1) RETURNING id",
                        Long.class);
        sql.update(
                "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, usuario_id)"
                        + " VALUES (?, ?, 1)",
                area,
                sector);
        sql.update("INSERT INTO mod02_ejemplar (codigo) VALUES ('T-0278') ON CONFLICT DO NOTHING");
        // Tipos de servicio con ids fijos: el 3 es «Poda mayor programada», tercerizada y sin
        // riesgo.
        sql.execute(
                new ClassPathResource("mod03/escenario-programacion.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
        long servicio = 3;
        proveedor = proveedor("Podas Altura Demo");
        otro = proveedor("Otro Demo");
        contrato =
                contrato("C-278-V", proveedor, servicio, "current_date - 30", "current_date + 300");
        vencido = contrato("C-278-X", otro, servicio, "current_date - 300", "current_date - 30");
    }

    long proveedor(String nombre) {
        return jdbc.queryForObject(
                "INSERT INTO mod05_proveedor (razon_social, activo) VALUES (:n, true) RETURNING id",
                Map.of("n", nombre),
                Long.class);
    }

    long contrato(String numero, long proveedor, long servicio, String desde, String hasta) {
        return jdbc.queryForObject(
                ("INSERT INTO mod05_contrato (numero, proveedor_id, tipo_servicio_id,"
                                + " vigencia_inicio, vigencia_fin) VALUES (:n, :p, :s, %s, %s)"
                                + " RETURNING id")
                        .formatted(desde, hasta),
                Map.of(
                        "n",
                        numero + UUID.randomUUID().toString().substring(0, 6),
                        "p",
                        proveedor,
                        "s",
                        servicio),
                Long.class);
    }

    /** Una incidencia del ejemplar T-0278, ya clasificada por la vía tercerizada. */
    long clasificada() throws Exception {
        String ficha =
                http.perform(
                                post(RUTA)
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"canal\": \"Telefono\", \"tipo\": \"Rama"
                                                        + " colgante\", \"reportado_por\":"
                                                        + " {\"nombre\": \"Ana\"}, \"espacio\":"
                                                        + " \"Jardín AV-278\", \"ejemplar\":"
                                                        + " \"T-0278\", \"descripcion\": \"Rama"
                                                        + " a 9 m\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id = json.readTree(ficha).get("id").asLong();
        accion(
                        id,
                        "clasificar",
                        "{\"severidad\": \"Alta\", \"impacto\": \"Medio\", \"via\":"
                                + " \"Tercerizada\", \"motivo_cambio_via\": \"Rama a 9 m\"}")
                .andExpect(status().isOk());
        return id;
    }

    ResultActions accion(long id, String accion, String cuerpo) throws Exception {
        return http.perform(
                post(RUTA + id + "/" + accion + "/")
                        .with(httpBasic("jefe", CLAVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
    }

    ResultActions derivar(long id, Long proveedor, Long contrato) throws Exception {
        return accion(
                id,
                "requerimiento",
                ("{\"proveedor_id\": %d, \"contrato_id\": %s, \"tipo_atencion\":"
                                + " \"Extraordinaria\", \"crear_poda\": true}")
                        .formatted(proveedor, contrato));
    }

    @Test
    void listaLosProveedoresConSusContratos() throws Exception {
        long id = clasificada();
        http.perform(get(RUTA + id + "/proveedores/").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.razon_social == 'Podas Altura Demo')].contratos[0].vigente")
                                .value(hasItem(true)))
                .andExpect(
                        jsonPath("$[?(@.razon_social == 'Otro Demo')].contratos[0].vigente")
                                .value(hasItem(false)));
    }

    @Test
    void derivarComoProgramadaConservaLaClaseYLaOrdenDeCompra() throws Exception {
        long id = clasificada();
        accion(
                        id,
                        "requerimiento",
                        ("{\"proveedor_id\": %d, \"contrato_id\": %d, \"tipo_atencion\":"
                                        + " \"Programada\", \"crear_poda\": true}")
                                .formatted(proveedor, contrato))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requerimiento.tipo_atencion").value("Programada"))
                .andExpect(jsonPath("$.requerimiento.pasos[1].nombre").value("Orden de compra"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT a.clase FROM mod03_requerimiento_incidencia r JOIN"
                                    + " mod05_atenciontercerizada a ON a.id = r.atencion_id WHERE"
                                    + " r.incidencia_id = :id",
                                Map.of("id", id),
                                String.class))
                .isEqualTo("ProgramadaContrato");
    }

    @Test
    void derivarCreaLaAtencionDelEjemplarYLaDejaDerivada() throws Exception {
        long id = clasificada();
        derivar(id, proveedor, contrato)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DerivadaTercerizada"))
                .andExpect(jsonPath("$.via").value("Tercerizada"))
                .andExpect(jsonPath("$.responsable").value("Podas Altura Demo"))
                .andExpect(jsonPath("$.requerimiento.codigo").value(startsWith("AT-")))
                .andExpect(jsonPath("$.requerimiento.proveedor").value("Podas Altura Demo"))
                .andExpect(jsonPath("$.requerimiento.tipo_atencion").value("Extraordinaria"))
                .andExpect(jsonPath("$.requerimiento.pasos[0].estado").value("Hecho"))
                .andExpect(jsonPath("$.requerimiento.pasos[1].nombre").value("Orden de servicio"))
                .andExpect(jsonPath("$.requerimiento.pasos[1].estado").value("EnCurso"))
                .andExpect(jsonPath("$.requerimiento.pasos[3].estado").value("Pendiente"));
        var atencion =
                jdbc.queryForMap(
                        "SELECT a.origen, a.clase, a.contrato_id, al.ejemplar_id, e.codigo"
                                + " FROM mod03_requerimiento_incidencia r"
                                + " JOIN mod05_atenciontercerizada a ON a.id = r.atencion_id"
                                + " JOIN mod05_alcanceatencion al ON al.atencion_id = a.id"
                                + " JOIN mod02_ejemplar e ON e.id = al.ejemplar_id"
                                + " WHERE r.incidencia_id = :id",
                        Map.of("id", id));
        assertThat(atencion.get("origen")).isEqualTo("Demanda");
        assertThat(atencion.get("clase")).isEqualTo("Extraordinaria");
        assertThat(((Number) atencion.get("contrato_id")).longValue()).isEqualTo(contrato);
        assertThat(atencion.get("codigo")).isEqualTo("T-0278");
        // Ya derivada, no se deriva otra vez.
        derivar(id, proveedor, contrato)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("transicion-invalida"));
    }

    @Test
    void sinContratoOConUnoVencidoNoDeriva() throws Exception {
        long id = clasificada();
        derivar(id, otro, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("sin-contrato"));
        derivar(id, otro, vencido)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("contrato-no-vigente"));
        derivar(id, otro, contrato)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("contrato-no-vigente"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT estado FROM mod03_incidencia WHERE id = :id",
                                Map.of("id", id),
                                String.class))
                .isEqualTo("Clasificada");
    }

    @Test
    void elInformeDelProveedorLaDejaAtendidaYPermiteCerrarla() throws Exception {
        long id = clasificada();
        derivar(id, proveedor, contrato).andExpect(status().isOk());
        long atencion =
                jdbc.queryForObject(
                        "SELECT atencion_id FROM mod03_requerimiento_incidencia"
                                + " WHERE incidencia_id = :id",
                        Map.of("id", id),
                        Long.class);
        jdbc.update(
                "UPDATE mod05_atenciontercerizada SET estado = 'Reporte adjunto',"
                        + " fecha_orden_compra = now() WHERE id = :a",
                Map.of("a", atencion));
        jdbc.update(
                "INSERT INTO mod05_historialestado (atencion_id, estado_anterior, estado_nuevo,"
                        + " usuario_id, fecha_hora) VALUES (:a, 'Ejecutado pendiente de reporte',"
                        + " 'Reporte adjunto', 1, now())",
                Map.of("a", atencion));
        http.perform(get(RUTA + id + "/").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Atendida"))
                .andExpect(jsonPath("$.atencion.atendida_por").value("Sistema"))
                .andExpect(jsonPath("$.requerimiento.pasos[2].estado").value("Hecho"))
                .andExpect(jsonPath("$.requerimiento.pasos[3].estado").value("EnCurso"));
        accion(id, "cerrar", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Cerrada"));
    }

    private long atencionDerivada(long incidencia) throws Exception {
        derivar(incidencia, proveedor, contrato).andExpect(status().isOk());
        return jdbc.queryForObject(
                "SELECT atencion_id FROM mod03_requerimiento_incidencia"
                        + " WHERE incidencia_id = :id",
                Map.of("id", incidencia),
                Long.class);
    }

    private void llegaA(long atencion, String estado, String... recorrido) {
        jdbc.update(
                "UPDATE mod05_atenciontercerizada SET estado = :estado,"
                        + " fecha_orden_compra = now() WHERE id = :a",
                Map.of("a", atencion, "estado", estado));
        String anterior = "Ejecutado pendiente de reporte";
        for (String nuevo : recorrido) {
            jdbc.update(
                    "INSERT INTO mod05_historialestado (atencion_id, estado_anterior,"
                            + " estado_nuevo, usuario_id, fecha_hora)"
                            + " VALUES (:a, :antes, :nuevo, 1, now())",
                    Map.of("a", atencion, "antes", anterior, "nuevo", nuevo));
            anterior = nuevo;
        }
    }

    @Test
    void conLaConformidadLaAtencionSigueContandoConInformeYCompletaElPasoDeConformidad()
            throws Exception {
        long id = clasificada();
        long atencion = atencionDerivada(id);
        llegaA(atencion, "Conforme / cerrado", "Reporte adjunto", "Conforme / cerrado");
        http.perform(get(RUTA + id + "/").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Atendida"))
                .andExpect(jsonPath("$.requerimiento.pasos[2].estado").value("Hecho"))
                .andExpect(jsonPath("$.requerimiento.pasos[3].estado").value("Hecho"));
        accion(id, "cerrar", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Cerrada"));
    }

    @Test
    void unaAtencionObservadaNoDejaAtendidaLaIncidenciaPeroConservaLaFechaDelInforme()
            throws Exception {
        long id = clasificada();
        long atencion = atencionDerivada(id);
        llegaA(atencion, "Observado", "Reporte adjunto", "Observado");
        http.perform(get(RUTA + id + "/").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DerivadaTercerizada"))
                .andExpect(jsonPath("$.requerimiento.pasos[2].estado").value("Hecho"))
                .andExpect(jsonPath("$.requerimiento.pasos[3].estado").value("EnCurso"));
    }

    // --- AV-312 (RF-POD-005): la derivación con crear_poda registra la poda de altura en MOD-02
    // ---

    @Test
    void conCrearPodaSeRegistraLaPodaDeAlturaDelEjemplarConSuMotivo() throws Exception {
        long id = clasificada();
        accion(
                        id,
                        "requerimiento",
                        ("{\"proveedor_id\": %d, \"contrato_id\": %d, \"tipo_atencion\":"
                                        + " \"Extraordinaria\", \"crear_poda\": true,"
                                        + " \"motivo_poda\": \"Obstruccion\"}")
                                .formatted(proveedor, contrato))
                .andExpect(status().isOk());
        var poda =
                jdbc.queryForMap(
                        """
                        SELECT i.categoria, i.motivo, i.fecha_ejecucion, i.atencion_id, e.codigo
                        FROM mod02_intervencion_arbolado i
                        JOIN mod02_ejemplar e ON e.id = i.ejemplar_id
                        WHERE i.incidencia_id = :id
                        """,
                        Map.of("id", id));
        assertThat(poda.get("categoria")).isEqualTo("AlturaDemanda");
        assertThat(poda.get("motivo")).isEqualTo("Obstruccion");
        assertThat(poda.get("fecha_ejecucion")).isNull();
        assertThat(poda.get("codigo")).isEqualTo("T-0278");
        assertThat(((Number) poda.get("atencion_id")).longValue())
                .isEqualTo(
                        jdbc.queryForObject(
                                "SELECT atencion_id FROM mod03_requerimiento_incidencia"
                                        + " WHERE incidencia_id = :id",
                                Map.of("id", id),
                                Long.class));
    }

    @Test
    void sinMotivoLaPodaEsPorRiesgoYSinCrearPodaNoSeRegistra() throws Exception {
        long conPoda = clasificada();
        derivar(conPoda, proveedor, contrato).andExpect(status().isOk());
        assertThat(motivosDePoda(conPoda)).containsExactly("Riesgo");

        long sinPoda = clasificada();
        accion(
                        sinPoda,
                        "requerimiento",
                        ("{\"proveedor_id\": %d, \"contrato_id\": %d, \"tipo_atencion\":"
                                        + " \"Extraordinaria\", \"crear_poda\": false}")
                                .formatted(proveedor, contrato))
                .andExpect(status().isOk());
        assertThat(motivosDePoda(sinPoda)).isEmpty();
    }

    @Test
    void sinEjemplarNoSePuedeCrearLaPodaYNoSeDeriva() throws Exception {
        long id = clasificada();
        jdbc.update(
                "UPDATE mod03_incidencia SET ejemplar_id = NULL WHERE id = :id", Map.of("id", id));
        derivar(id, proveedor, contrato)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("poda-sin-ejemplar"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT estado FROM mod03_incidencia WHERE id = :id",
                                Map.of("id", id),
                                String.class))
                .isEqualTo("Clasificada");
    }

    private java.util.List<String> motivosDePoda(long incidencia) {
        return jdbc.queryForList(
                "SELECT motivo FROM mod02_intervencion_arbolado WHERE incidencia_id = :id",
                Map.of("id", incidencia),
                String.class);
    }
}
