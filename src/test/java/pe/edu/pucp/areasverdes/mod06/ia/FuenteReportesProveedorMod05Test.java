package pe.edu.pucp.areasverdes.mod06.ia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.pucp.areasverdes.mod05.ArchivoStorage;
import pe.edu.pucp.areasverdes.mod06.apoyo.ClienteIAFalso;
import pe.edu.pucp.areasverdes.mod06.apoyo.DatosIa;
import pe.edu.pucp.areasverdes.mod06.apoyo.DoblesPrueba;
import pe.edu.pucp.areasverdes.mod06.apoyo.FuenteDatosMemoria;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.errores.RecursoNoEncontrado;
import pe.edu.pucp.areasverdes.mod06.comun.errores.TipoArchivoNoAdmitido;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatosMock;

/**
 * RF-IA-001 con {@code IA_FUENTE_REPORTES=mod05}: lectura de reportes reales del MOD-05.
 *
 * <p>Usa la base exclusiva de pruebas del MOD-05 ({@code MOD05_TEST_DB_*}, la misma que {@code Mod05IntegrationTest}).
 * Todo lo que inserta se hace dentro de una transacción que <b>siempre se revierte</b>; los archivos van a un
 * directorio temporal. Si la base no está disponible, las pruebas se omiten. Nunca llama a Gemini.
 */
class FuenteReportesProveedorMod05Test {

    private static final byte[] PDF = "%PDF-1.4 informe real del MOD-05".getBytes(StandardCharsets.US_ASCII);
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static DriverManagerDataSource baseDatos;

    @TempDir
    Path temporal;

    private Path raizArchivos;
    private NamedParameterJdbcTemplate jdbc;
    private TransactionTemplate transaccion;
    private FuenteReportesProveedorMod05 fuente;

    @BeforeAll
    static void conectar() {
        String url = System.getenv().getOrDefault("MOD05_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:55432/mod05_spring_test");
        Assumptions.assumeTrue(url.endsWith("_test"), "Se requiere una base exclusiva terminada en _test");
        String usuario = System.getenv().getOrDefault("MOD05_TEST_DB_USER", System.getProperty("user.name"));
        String clave = System.getenv().getOrDefault("MOD05_TEST_DB_PASSWORD", "");
        try (Connection c = DriverManager.getConnection(url, usuario, clave);
             var rs = c.getMetaData().getTables(null, null, "mod05_reporteproveedor", null)) {
            Assumptions.assumeTrue(rs.next(), "La base de prueba no tiene el esquema del MOD-05 (ejecuta antes Mod05IntegrationTest)");
        } catch (java.sql.SQLException e) {
            Assumptions.abort("Base de prueba del MOD-05 no disponible: " + e.getClass().getSimpleName());
        }
        baseDatos = new DriverManagerDataSource(url, usuario, clave);
    }

    @BeforeEach
    void preparar() throws Exception {
        raizArchivos = Files.createDirectories(temporal.resolve("private-media"));
        jdbc = new NamedParameterJdbcTemplate(baseDatos);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(baseDatos));
        fuente = new FuenteReportesProveedorMod05(jdbc, new ArchivoStorage(raizArchivos.toString(), 20 * 1024 * 1024));
    }

    /** Ejecuta {@code cuerpo} en una transacción que siempre se revierte. */
    private void conRollback(Consumer<Void> cuerpo) {
        transaccion.executeWithoutResult(estado -> {
            estado.setRollbackOnly();
            cuerpo.accept(null);
        });
    }

    // --- Datos del MOD-05 (las claves foráneas son DEFERRABLE INITIALLY DEFERRED: nunca se comprueban, porque
    // --- la transacción se revierte antes del COMMIT) --------------------------------------------------------

    private long insertar(String sql, Map<String, Object> valores) {
        return jdbc.queryForObject(sql + " RETURNING id", valores, Long.class);
    }

    private long atencion(long tipoServicioId) {
        OffsetDateTime ahora = OffsetDateTime.now();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("ahora", ahora);
        v.put("codigo", "PRUEBA-IA-" + UUID.randomUUID().toString().substring(0, 20));
        v.put("tipo", tipoServicioId);
        return insertar("""
            INSERT INTO mod05_atenciontercerizada (fecha_creacion, fecha_modificacion, codigo, clase, descripcion, origen,
              numero_orden_compra, estado, es_riesgo, comentarios_acuerdo_unidad, solicitado_por_id, usuario_creacion_id,
              usuario_modificacion_id, contrato_id, tipo_servicio_id)
            VALUES (:ahora, :ahora, :codigo, 'Programada', 'Prueba RF-IA-001', 'Demanda', '', 'Reporte adjunto', false, '',
              1, 1, 1, 1, :tipo)""", v);
    }

    private long reporte(long atencionId) {
        return insertar("""
            INSERT INTO mod05_reporteproveedor (fecha_creacion, fecha_modificacion, revision, fecha_recepcion, resumen,
              atencion_id, usuario_creacion_id, usuario_modificacion_id)
            VALUES (:ahora, :ahora, 1, :fecha, 'Resumen de prueba', :atencion, 1, 1)""",
            Map.of("ahora", OffsetDateTime.now(), "fecha", LocalDate.of(2026, 10, 20), "atencion", atencionId));
    }

    /** Guarda el archivo en el almacenamiento temporal y lo registra en {@code mod05_archivo}. */
    private long archivo(String ruta, String mime, byte[] contenido) {
        try {
            if (contenido != null) {
                Path destino = raizArchivos.resolve(ruta);
                Files.createDirectories(destino.getParent());
                Files.write(destino, contenido);
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return insertar("""
            INSERT INTO mod05_archivo (nombre_original, tipo_mime, tamano_bytes, ruta_almacenamiento, hash_contenido)
            VALUES ('informe', :mime, :tamano, :ruta, :hash)""",
            Map.of("mime", mime, "tamano", contenido == null ? 0 : contenido.length, "ruta", ruta, "hash", "0".repeat(64)));
    }

    private long asociar(long reporteId, long archivoId, String categoria) {
        return insertar("""
            INSERT INTO mod05_archivoasociado (categoria, descripcion, archivo_id, reporte_id)
            VALUES (:categoria, 'Prueba', :archivo, :reporte)""",
            Map.of("categoria", categoria, "archivo", archivoId, "reporte", reporteId));
    }

    private static String rutaNueva(String extension) {
        return "reportes/prueba-ia/" + UUID.randomUUID() + "." + extension;
    }

    /** Servicio con {@code IA_FUENTE_REPORTES=mod05}; formatos y SugerenciaIA en memoria (nunca en mock-data). */
    private ExtraccionReportesService servicio(FuenteDatosMemoria memoria) {
        DoblesPrueba.SelectorPrueba datos = new DoblesPrueba.SelectorPrueba(new Mod06Propiedades("mock", null, null, null));
        datos.usar(memoria);
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("fuenteMod05", fuente));
        SelectorFuenteReportesProveedor reportes = new SelectorFuenteReportesProveedor(
            new Mod06Propiedades("mock", null, null, null, new Mod06Propiedades.Ia("mod05")),
            beans.getBeanProvider(FuenteReportesProveedorMod05.class));
        return new ExtraccionReportesService(datos, null, Clock.system(Fechas.LIMA), reportes);
    }

    // --- Pruebas ----------------------------------------------------------------------------------------

    @Test
    void leeReporteAtencionYArchivoConCategoriaTraducida() {
        conRollback(x -> {
            long at = atencion(2);
            long rep = reporte(at);
            String ruta = rutaNueva("pdf");
            long arch = archivo(ruta, "application/pdf", PDF);
            long asoc = asociar(rep, arch, "InformeFinal");
            asociar(rep, archivo(rutaNueva("pdf"), "application/pdf", PDF), "Metrado"); // otra categoría: no se usa

            Map<String, Object> r = fuente.obtenerReporte(rep);
            assertEquals(rep, r.get("id"));
            assertEquals(at, r.get("atencion_id"));
            assertEquals("2026-10-20", r.get("fecha_recepcion"));
            assertEquals(2L, fuente.obtenerAtencion(at).get("tipo_servicio_id"));

            List<Map<String, Object>> asociados = fuente.archivosAsociados(rep, ExtraccionReportesService.CATEGORIA_ARCHIVO_REPORTE);
            assertEquals(1, asociados.size());
            Map<String, Object> a = asociados.getFirst();
            assertEquals(asoc, a.get("id"));
            assertEquals(arch, a.get("archivo_id"));
            assertEquals("ReporteProveedor", a.get("entidad"));
            assertEquals(rep, a.get("registro_id"));
            assertEquals("ReporteProveedor", a.get("categoria"));
            assertTrue(fuente.archivosAsociados(rep, "InformeFinal").isEmpty(), "el servicio solo pide la categoría de mock-data");

            Map<String, Object> metadatos = fuente.obtenerArchivo(arch);
            assertEquals("application/pdf", metadatos.get("tipo_mime"));
            assertEquals(ruta, metadatos.get("ruta_almacenamiento"));
            assertArrayEquals(PDF, fuente.leerContenidoArchivo(metadatos));
        });
    }

    @Test
    void flujoCompletoAnalizaElInformeDelMod05() {
        conRollback(x -> {
            long at = atencion(2);
            long rep = reporte(at);
            asociar(rep, archivo(rutaNueva("pdf"), "application/pdf", "%PDF-1.4 version anterior".getBytes(StandardCharsets.US_ASCII)), "InformeFinal");
            long arch = archivo(rutaNueva("pdf"), "application/pdf", PDF);
            asociar(rep, arch, "InformeFinal"); // varios InformeFinal: se usa el más reciente, como en mock

            FuenteDatosMemoria memoria = DatosIa.fuente();
            ClienteIAFalso cliente = new ClienteIAFalso(DatosIa.propuestaCompleta());
            Map<String, Object> sugerencia = servicio(memoria).solicitarExtraccion(rep, 7L, null, cliente);

            assertArrayEquals(PDF, cliente.llamadas.getFirst().pdf());
            assertEquals("Propuesta", sugerencia.get("estado"));
            assertEquals(rep, sugerencia.get("registro_id"));
            assertEquals(arch, sugerencia.get("archivo_id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> entrada = (Map<String, Object>) ((Map<String, Object>) sugerencia.get("contenido")).get("entrada");
            assertEquals(at, entrada.get("atencion_id"));
            assertEquals(2L, ((Number) entrada.get("tipo_servicio_id")).longValue());
            assertEquals(1, memoria.escrituras.size(), "solo se escribe la SugerenciaIA, en la fuente del MOD-06");
        });
    }

    @Test
    void reporteInexistenteDaNoEncontrado() {
        conRollback(x -> {
            long inexistente = jdbc.queryForObject("SELECT coalesce(max(id), 0) + 1000 FROM mod05_reporteproveedor", Map.of(), Long.class);
            assertNull(fuente.obtenerReporte(inexistente));
            RecursoNoEncontrado e = assertThrows(RecursoNoEncontrado.class,
                () -> servicio(DatosIa.fuente()).solicitarExtraccion(inexistente, 1L, null, new ClienteIAFalso(DatosIa.propuestaCompleta())));
            assertEquals(ExtraccionReportesService.MENSAJE_REPORTE_NO_EXISTE, e.getMessage());
        });
    }

    @Test
    void informeDocxLlegaConSuMimeYDaElErrorDeSiempre() {
        conRollback(x -> {
            long rep = reporte(atencion(2));
            long arch = archivo(rutaNueva("docx"), DOCX, "PK docx".getBytes(StandardCharsets.US_ASCII));
            asociar(rep, arch, "InformeFinal");
            assertEquals(DOCX, fuente.obtenerArchivo(arch).get("tipo_mime"));
            ClienteIAFalso cliente = new ClienteIAFalso(DatosIa.propuestaCompleta());
            TipoArchivoNoAdmitido e = assertThrows(TipoArchivoNoAdmitido.class,
                () -> servicio(DatosIa.fuente()).solicitarExtraccion(rep, 1L, null, cliente));
            assertEquals(ExtraccionReportesService.MENSAJE_NO_PDF, e.getMessage());
            assertTrue(cliente.llamadas.isEmpty());
        });
    }

    @Test
    void rutaFueraDelDirectorioBaseOArchivoAusenteDaNoEncontrado() throws Exception {
        Files.writeString(temporal.resolve("secreto.txt"), "secreto", StandardCharsets.UTF_8);
        for (String ruta : List.of("../secreto.txt", temporal.resolve("secreto.txt").toString(), "reportes/no-existe.pdf", "")) {
            Map<String, Object> archivo = new LinkedHashMap<>(Map.of("id", 1L, "ruta_almacenamiento", ruta));
            RecursoNoEncontrado e = assertThrows(RecursoNoEncontrado.class, () -> fuente.leerContenidoArchivo(archivo), ruta);
            assertEquals(FuenteDatosMock.MENSAJE_ARCHIVO_NO_ENCONTRADO, e.getMessage());
        }
    }
}
