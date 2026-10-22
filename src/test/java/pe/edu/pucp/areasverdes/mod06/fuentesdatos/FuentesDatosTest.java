package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.d;
import static pe.edu.pucp.areasverdes.mod06.apoyo.Datos.l;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.edu.pucp.areasverdes.mod06.apoyo.Datos;
import pe.edu.pucp.areasverdes.mod06.apoyo.PruebaApi;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.comun.errores.FuenteDatosNoDisponible;
import pe.edu.pucp.areasverdes.mod06.comun.errores.RecursoNoEncontrado;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;

/**
 * Pruebas de la capa {@code fuentesdatos/} (patrón Estrategia) ({@code tests/test_fuentes_datos.py}).
 *
 * <p>Usan un directorio temporal como {@code MOCK_DATA_DIR}: no leen {@code mock-data/}.
 */
class FuentesDatosTest extends PruebaApi {

    @TempDir
    Path base;
    Path directorio;

    @BeforeEach
    void directorioMock() throws Exception {
        directorio = base.resolve("mock");
        Files.createDirectories(directorio);
        for (Map.Entry<String, String> e : Entidades.CARPETA_POR_ENTIDAD.entrySet()) {
            Files.createDirectories(directorio.resolve(e.getValue()));
            Files.writeString(directorio.resolve(e.getValue()).resolve(e.getKey() + ".json"), "[]", StandardCharsets.UTF_8);
        }
        Files.writeString(directorio.resolve("mod03").resolve("CicloCorte.json"),
            JsonPy.dumpsRespuesta(Datos.normalizar(l(d("id", 1, "estado", "En corte"), d("id", 2, "estado", "Cerrado")))), StandardCharsets.UTF_8);
    }

    private FuenteDatosMock fuente() {
        return new FuenteDatosMock(directorio);
    }

    @Test
    void obtenerFuenteRespetaDataSource() {
        assertInstanceOf(FuenteDatosMock.class, new SelectorFuenteDatos(new Mod06Propiedades("mock", null, null, null)).obtenerFuenteDatos());
        assertInstanceOf(FuenteDatosBD.class, new SelectorFuenteDatos(new Mod06Propiedades("db", null, null, null)).obtenerFuenteDatos());
    }

    @Test
    void fuenteBdNoDisponible() {
        FuenteDatosBD bd = new FuenteDatosBD();
        List<Runnable> llamadas = List.of(
            () -> bd.listar("CicloCorte"),
            () -> bd.obtener("CicloCorte", 1L),
            () -> bd.crear("SugerenciaIA", d()),
            () -> bd.actualizar("SugerenciaIA", 1L, d()),
            () -> bd.leerContenidoArchivo(d()));
        for (Runnable llamada : llamadas) {
            FuenteDatosNoDisponible e = assertThrows(FuenteDatosNoDisponible.class, llamada::run);
            assertEquals(FuenteDatosBD.MENSAJE_BD_NO_DISPONIBLE, e.mensaje());
        }
    }

    @Test
    void apiConBdResponde503() throws Exception {
        selector.usar(new FuenteDatosBD());
        MockHttpServletResponse r = getUrl("/api/v1/reportes/cumplimiento-corte/?desde=2026-01-01&hasta=2026-10-20");
        assertEquals(503, r.getStatus());
        Datos.igual(d("codigo", "FuenteDatosNoDisponible", "detalle", FuenteDatosBD.MENSAJE_BD_NO_DISPONIBLE), json(r));
    }

    @Test
    void listarYObtener() {
        FuenteDatosMock f = fuente();
        Datos.igual(l(1, 2), f.listar("CicloCorte").stream().map(c -> c.get("id")).toList());
        Datos.igual(l(d("id", 2, "estado", "Cerrado")), f.listar("CicloCorte", "estado", "Cerrado"));
        Datos.igual(d("id", 1, "estado", "En corte"), f.obtener("CicloCorte", 1L));
        assertNull(f.obtener("CicloCorte", 9L));
        // Devuelve copias
        f.listar("CicloCorte").get(0).put("estado", "modificado");
        assertEquals("En corte", f.obtener("CicloCorte", 1L).get("estado"));
    }

    @Test
    void entidadDesconocida() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> fuente().listar("Inventada"));
        assertTrue(e.getMessage().contains("Inventada"));
    }

    @Test
    void archivoFaltanteOInvalido() throws Exception {
        Files.delete(directorio.resolve("mod03").resolve("CicloCorte.json"));
        FuenteDatosNoDisponible e = assertThrows(FuenteDatosNoDisponible.class, () -> fuente().listar("CicloCorte"));
        assertTrue(e.mensaje().contains("mod03/CicloCorte.json"));
        Files.writeString(directorio.resolve("mod03").resolve("CicloCorte.json"), "{no es json", StandardCharsets.UTF_8);
        e = assertThrows(FuenteDatosNoDisponible.class, () -> fuente().listar("CicloCorte"));
        assertTrue(e.mensaje().contains("CicloCorte"));
    }

    @Test
    void escrituraFueraDelMod06SeRechaza() {
        FuenteDatosMock f = fuente();
        EscrituraNoPermitida e = assertThrows(EscrituraNoPermitida.class, () -> f.crear("CicloCorte", d("estado", "Cerrado")));
        assertTrue(e.getMessage().contains("El MOD-06 solo escribe en SugerenciaIA y ReporteGenerado"));
        assertThrows(EscrituraNoPermitida.class, () -> f.actualizar("ReporteProveedor", 1L, d("resumen", "x")));
        assertEquals(java.util.Set.of("SugerenciaIA", "ReporteGenerado"), Entidades.ENTIDADES_ESCRIBIBLES);
    }

    @Test
    void escrituraAtomica() throws Exception {
        FuenteDatosMock f = fuente();
        Map<String, Object> creada = f.crear("SugerenciaIA", d("estado", "Propuesta", "contenido", d("texto", "Pendiente de revisión")));
        Datos.igual(1, creada.get("id"));
        Datos.igual(2, f.crear("SugerenciaIA", d("estado", "Propuesta")).get("id"));
        Map<String, Object> actualizada = f.actualizar("SugerenciaIA", 1L, d("estado", "Validada", "id", 50));
        Datos.igual(1, actualizada.get("id"));
        assertEquals("Validada", actualizada.get("estado"));
        Path ruta = directorio.resolve("mod06").resolve("SugerenciaIA.json");
        String texto = Files.readString(ruta, StandardCharsets.UTF_8);
        assertTrue(texto.contains("Pendiente de revisión")); // ensure_ascii=False
        assertTrue(texto.startsWith("[\n  {")); // indentación de 2 espacios
        Datos.igual(l(1, 2), ((List<?>) JsonPy.loads(texto)).stream().map(r -> ((Map<?, ?>) r).get("id")).toList());
        // No quedan archivos temporales
        List<String> nombres = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(ruta.getParent())) {
            archivos.forEach(p -> nombres.add(p.getFileName().toString()));
        }
        nombres.sort(String::compareTo);
        List<String> esperados = new ArrayList<>();
        Entidades.CARPETA_POR_ENTIDAD.forEach((e, c) -> {
            if (c.equals("mod06")) esperados.add(e + ".json");
        });
        esperados.sort(String::compareTo);
        assertEquals(esperados, nombres);
        assertThrows(RecursoNoEncontrado.class, () -> f.actualizar("SugerenciaIA", 99L, d("estado", "Validada")));
    }

    @Test
    void escrituraConcurrenteSinPerdidas() throws Exception {
        FuenteDatosMock f = fuente();
        List<Thread> hilos = new ArrayList<>();
        for (int i = 0; i < 10; i++) hilos.add(new Thread(() -> f.crear("SugerenciaIA", d("estado", "Propuesta"))));
        for (Thread h : hilos) h.start();
        for (Thread h : hilos) h.join();
        List<Long> ids = new ArrayList<>(f.listar("SugerenciaIA").stream().map(r -> (Long) r.get("id")).toList());
        ids.sort(Long::compareTo);
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), ids);
    }

    @Test
    void leerContenidoArchivo() throws Exception {
        Files.createDirectories(directorio.resolve("archivos"));
        Files.write(directorio.resolve("archivos").resolve("informe.pdf"), "%PDF".getBytes(StandardCharsets.US_ASCII));
        FuenteDatosMock f = fuente();
        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), f.leerContenidoArchivo(d("ruta_almacenamiento", "archivos/informe.pdf")));
        RecursoNoEncontrado e = assertThrows(RecursoNoEncontrado.class,
            () -> f.leerContenidoArchivo(d("ruta_almacenamiento", "archivos/no-existe.pdf")));
        assertTrue(e.mensaje().contains("súbelo de nuevo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secreto.txt", "archivos/../../secreto.txt", "/etc/passwd", "C:/Windows/win.ini", "C:\\Windows\\win.ini",
        "\\\\servidor\\x.pdf", ""})
    void noSePuedeSalirDeMockDataDir(String ruta) throws Exception {
        Files.writeString(directorio.getParent().resolve("secreto.txt"), "secreto", StandardCharsets.UTF_8);
        assertThrows(RecursoNoEncontrado.class, () -> fuente().leerContenidoArchivo(d("ruta_almacenamiento", ruta)));
    }

    // --- pruebas adicionales del Java ---------------------------------------

    @Test
    void dataSourceInvalidoNoArranca() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new SelectorFuenteDatos(new Mod06Propiedades("valor_invalido", null, null, null)));
        assertEquals("DATA_SOURCE='valor_invalido' no es válido. Usa 'mock' o 'db' en backend/.env.", e.getMessage());
    }

    @Test
    void escrituraCompatibleConElDjango() throws Exception {
        FuenteDatosMock f = fuente();
        f.crear("SugerenciaIA", d("tipo", "ExtraccionReporte", "contenido", d("ia", d("duracion_ms", 56690)),
            "propuesta", d("valor", 7152.0, "texto", "línea\nnueva \"comillas\""), "decision", null));
        String texto = Files.readString(directorio.resolve("mod06").resolve("SugerenciaIA.json"), StandardCharsets.UTF_8);
        String esperado = "[\n  {\n    \"tipo\": \"ExtraccionReporte\",\n    \"contenido\": {\n      \"ia\": {\n"
            + "        \"duracion_ms\": 56690\n      }\n    },\n    \"propuesta\": {\n      \"valor\": 7152.0,\n"
            + "      \"texto\": \"línea\\nnueva \\\"comillas\\\"\"\n    },\n    \"decision\": null,\n    \"id\": 1\n  }\n]\n";
        assertEquals(esperado, texto);
    }
}
