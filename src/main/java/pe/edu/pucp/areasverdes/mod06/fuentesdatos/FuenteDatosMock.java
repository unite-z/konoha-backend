package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.comun.errores.FuenteDatosNoDisponible;
import pe.edu.pucp.areasverdes.mod06.comun.errores.RecursoNoEncontrado;

/**
 * Fuente de datos simulados: lee {@code mock-data/<carpeta>/<Entidad>.json} desde {@code MOCK_DATA_DIR}.
 *
 * <ul>
 *   <li>Lectura sin caché: los JSON pueden editarse a mano durante las pruebas.</li>
 *   <li>Escritura solo en las entidades propias del MOD-06 (SugerenciaIA, ReporteGenerado), bajo un candado del
 *   proceso y con reemplazo atómico del archivo.</li>
 *   <li>{@link #leerContenidoArchivo} no permite salir de {@code MOCK_DATA_DIR} (rechaza rutas absolutas o con
 *   {@code ..}).</li>
 * </ul>
 *
 * <p>Requisitos que atiende: RF-REP-002, RF-REP-004, RF-REP-006 y RF-IA-001 (como fuente de datos).
 */
public class FuenteDatosMock implements FuenteDatos {

    private static final Logger log = LoggerFactory.getLogger(FuenteDatosMock.class);

    public static final String MENSAJE_ARCHIVO_NO_ENCONTRADO =
        "No se encontró el archivo del informe en el almacenamiento; súbelo de nuevo o registra los datos manualmente";

    /** Un solo candado por proceso para todas las escrituras en mock-data. */
    private static final ReentrantLock CANDADO_ESCRITURA = new ReentrantLock();

    private static final Pattern UNIDAD_WINDOWS = Pattern.compile("^[A-Za-z]:.*", Pattern.DOTALL);

    private final Path directorio;

    public FuenteDatosMock(Path directorio) {
        this.directorio = directorio;
    }

    // --- utilidades internas ------------------------------------------------

    /** Directorio raíz de los datos simulados (ruta absoluta). */
    public Path directorio() {
        Path ruta = directorio.toAbsolutePath().normalize();
        try {
            return Files.exists(ruta) ? ruta.toRealPath() : ruta;
        } catch (IOException e) {
            return ruta;
        }
    }

    private Path ruta(String entidad) {
        FuenteDatos.validarEntidad(entidad);
        return directorio().resolve(Entidades.CARPETA_POR_ENTIDAD.get(entidad)).resolve(entidad + ".json");
    }

    private static String rutaRelativa(String entidad) {
        return Entidades.CARPETA_POR_ENTIDAD.get(entidad) + "/" + entidad + ".json";
    }

    private List<Object> leer(String entidad) {
        Path ruta = ruta(entidad);
        Object datos;
        try {
            byte[] bytes = Files.readAllBytes(ruta);
            String texto = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
            // Modo texto de Python: saltos de línea universales.
            texto = texto.replace("\r\n", "\n").replace('\r', '\n');
            datos = JsonPy.loads(texto);
        } catch (NoSuchFileException e) {
            log.error("Falta el archivo de datos simulados {}", rutaRelativa(entidad));
            throw new FuenteDatosNoDisponible("No se encontraron los datos de " + entidad + " (" + rutaRelativa(entidad)
                + "); revisa la carpeta mock-data e inténtalo de nuevo");
        } catch (JsonPy.JsonDecodeError | IOException e) {
            log.error("No se pudo leer el archivo de datos simulados {}", rutaRelativa(entidad));
            throw new FuenteDatosNoDisponible("No se pudieron leer los datos de " + entidad + " (" + rutaRelativa(entidad)
                + "); revisa que el JSON sea válido e inténtalo de nuevo");
        }
        if (!(datos instanceof List<?> lista)) {
            throw new FuenteDatosNoDisponible("Los datos de " + entidad + " (" + rutaRelativa(entidad)
                + ") deben ser un arreglo JSON; corrige el archivo e inténtalo de nuevo");
        }
        return new ArrayList<>(lista);
    }

    private void escribirAtomico(String entidad, List<Object> registros) {
        Path ruta = ruta(entidad);
        Path temporal = null;
        try {
            temporal = Files.createTempFile(ruta.getParent(), "." + entidad + ".", ".tmp");
            String texto = JsonPy.dumpsArchivo(registros) + "\n";
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(texto));
            byte[] contenido = new byte[bytes.remaining()];
            bytes.get(contenido);
            Files.write(temporal, contenido);
            Files.move(temporal, ruta, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignorada) {
                    // se conserva la excepción original
                }
            }
            if (e instanceof CharacterCodingException) throw new Py.ValueError("'utf-8' codec can't encode characters");
            if (e instanceof RuntimeException r) throw r;
            throw new java.io.UncheckedIOException((IOException) e);
        }
    }

    // --- interfaz FuenteDatos ----------------------------------------------

    @Override
    public List<Map<String, Object>> listar(String entidad, Map<String, Object> igualA) {
        return FuenteDatos.filtrar(leer(entidad), igualA);
    }

    @Override
    public Map<String, Object> obtener(String entidad, Object id) {
        List<Map<String, Object>> encontrados = FuenteDatos.filtrar(leer(entidad), Py.dict("id", id));
        return encontrados.isEmpty() ? null : encontrados.get(0);
    }

    @Override
    public Map<String, Object> crear(String entidad, Map<String, Object> datos) {
        FuenteDatos.validarEscritura(entidad);
        Map<String, Object> nuevo;
        CANDADO_ESCRITURA.lock();
        try {
            List<Object> registros = leer(entidad);
            // dict(datos); nuevo["id"] = ...: la clave "id" va al final salvo que ya existiera.
            nuevo = new java.util.LinkedHashMap<>(datos);
            nuevo.put("id", FuenteDatos.siguienteId(registros));
            registros.add(nuevo);
            escribirAtomico(entidad, registros);
        } finally {
            CANDADO_ESCRITURA.unlock();
        }
        return FuenteDatos.filtrar(List.of(nuevo), Map.of()).get(0);
    }

    @Override
    public Map<String, Object> actualizar(String entidad, Object id, Map<String, Object> cambios) {
        FuenteDatos.validarEscritura(entidad);
        CANDADO_ESCRITURA.lock();
        try {
            List<Object> registros = leer(entidad);
            for (Object r : registros) {
                if (Py.eq(Py.get(r, "id"), id)) {
                    Map<String, Object> registro = FuenteDatos.comoRegistro(r);
                    cambios.forEach((k, v) -> {
                        if (!k.equals("id")) registro.put(k, v);
                    });
                    escribirAtomico(entidad, registros);
                    return FuenteDatos.filtrar(List.of(registro), Map.of()).get(0);
                }
            }
        } finally {
            CANDADO_ESCRITURA.unlock();
        }
        throw new RecursoNoEncontrado("No existe el registro " + Py.str(id) + " de " + entidad);
    }

    @Override
    public byte[] leerContenidoArchivo(Map<String, Object> archivo) {
        Object valor = archivo.get("ruta_almacenamiento");
        String rutaTexto = Py.str(Py.or(valor, ""));
        if (rutaTexto.isEmpty() || !esRutaRelativaSegura(rutaTexto)) {
            log.warn("Ruta de almacenamiento rechazada para el archivo {}", Py.str(archivo.get("id")));
            throw new RecursoNoEncontrado(MENSAJE_ARCHIVO_NO_ENCONTRADO);
        }
        Path raiz = directorio();
        Path ruta;
        try {
            ruta = raiz.resolve(rutaTexto).normalize();
            if (Files.exists(ruta)) ruta = ruta.toRealPath();
        } catch (InvalidPathException | IOException e) {
            throw new RecursoNoEncontrado(MENSAJE_ARCHIVO_NO_ENCONTRADO);
        }
        if (!ruta.startsWith(raiz) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontrado(MENSAJE_ARCHIVO_NO_ENCONTRADO);
        }
        try {
            return Files.readAllBytes(ruta);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** {@code false} si la ruta es absoluta (POSIX o Windows) o contiene {@code ..}. */
    static boolean esRutaRelativaSegura(String ruta) {
        // PurePosixPath
        if (ruta.startsWith("/")) return false;
        for (String parte : ruta.split("/")) if (parte.equals("..")) return false;
        // PureWindowsPath: raíz, unidad o UNC, y partes separadas por / o \
        if (ruta.startsWith("\\") || UNIDAD_WINDOWS.matcher(ruta).matches()) return false;
        for (String parte : ruta.split("[/\\\\]")) if (parte.equals("..")) return false;
        return true;
    }
}
