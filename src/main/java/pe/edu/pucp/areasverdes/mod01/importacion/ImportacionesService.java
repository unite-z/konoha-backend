package pe.edu.pucp.areasverdes.mod01.importacion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.importacion.Importador.Clasificacion;
import pe.edu.pucp.areasverdes.mod01.servicios.Autorizacion;
import pe.edu.pucp.areasverdes.mod01.servicios.ErrorCore;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/**
 * Importación repetible desde Excel (RF-T-009, HU-MOD01-011), en dos pasos: validar clasifica cada
 * fila sin tocar datos oficiales y confirmar aplica las válidas. Las filas con errores o por
 * revisar nunca se incorporan solas, y el resultado queda en {@code core_importacion}.
 */
@Service
public class ImportacionesService {

    static final int MAXIMO_FILAS = 10_000;
    static final long MAXIMO_BYTES = 10L * 1024 * 1024;

    /** Tamaño máximo descomprimido: un xlsx es un zip, y uno malicioso puede crecer sin límite. */
    static final long MAXIMO_DESCOMPRIMIDO = 200L * 1024 * 1024;

    /** Tipos previstos por el requisito cuya tabla aún no existe en ningún módulo. */
    private static final Set<String> SIN_MODULO = Set.of("ruta");

    public record Importacion(
            long id,
            String tipo,
            String archivo,
            String estado,
            int total,
            int altas,
            int actualizaciones,
            int sinCambios,
            int revisar,
            int errores,
            String usuario,
            OffsetDateTime fechaCreacion,
            OffsetDateTime fechaConfirmacion) {}

    public record Fila(int fila, String codigo, String resultado, String detalle) {}

    private record Leida(int numero, Map<String, String> valores) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final Autorizacion autorizacion;
    private final ObjectMapper json;
    private final Map<String, Importador> importadores;

    public ImportacionesService(
            NamedParameterJdbcTemplate jdbc,
            Autorizacion autorizacion,
            ObjectMapper json,
            List<Importador> importadores) {
        this.jdbc = jdbc;
        this.autorizacion = autorizacion;
        this.json = json;
        this.importadores =
                importadores.stream()
                        .collect(Collectors.toMap(Importador::tipo, Function.identity()));
    }

    @Transactional
    public Importacion validar(String tipo, String nombre, byte[] archivo, UsuarioSesion usuario) {
        Importador importador = importador(tipo, usuario);
        if (archivo.length > MAXIMO_BYTES) {
            throw ErrorCore.invalido("archivo-demasiado-grande", "El archivo supera los 10 MB");
        }
        List<Leida> filas = leer(archivo, importador);
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO core_importacion (tipo, nombre_archivo, total, altas,
                            actualizaciones, sin_cambios, revisar, errores, usuario_id)
                        VALUES (:tipo, :nombre, :total, 0, 0, 0, 0, 0, :usuario)
                        RETURNING id
                        """,
                        Map.of(
                                "tipo",
                                tipo,
                                "nombre",
                                nombre == null ? "archivo.xlsx" : nombre,
                                "total",
                                filas.size(),
                                "usuario",
                                usuario.id()),
                        Long.class);
        Set<String> vistos = new HashSet<>();
        List<MapSqlParameterSource> lote = new ArrayList<>();
        Map<String, Integer> cuentas = new HashMap<>();
        for (Leida fila : filas) {
            Clasificacion c = importador.clasificar(fila.valores(), vistos);
            cuentas.merge(c.resultado(), 1, Integer::sum);
            lote.add(
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("fila", fila.numero())
                            .addValue("codigo", recortar(c.codigo(), 150))
                            .addValue("resultado", c.resultado())
                            .addValue("detalle", recortar(c.detalle(), 500))
                            .addValue("datos", aJson(fila.valores())));
        }
        jdbc.batchUpdate(
                """
                INSERT INTO core_importacion_fila (importacion_id, fila, codigo, resultado, detalle,
                    datos)
                VALUES (:id, :fila, :codigo, :resultado, :detalle, CAST(:datos AS jsonb))
                """,
                lote.toArray(MapSqlParameterSource[]::new));
        guardarCuentas(id, cuentas);
        return buscar(id);
    }

    public Importacion obtener(long id, UsuarioSesion usuario) {
        Importacion importacion = buscar(id);
        importador(importacion.tipo(), usuario);
        return importacion;
    }

    public List<Fila> filas(long id, UsuarioSesion usuario) {
        obtener(id, usuario);
        return jdbc.query(
                """
                SELECT fila, codigo, resultado, detalle FROM core_importacion_fila
                WHERE importacion_id = :id ORDER BY fila
                """,
                Map.of("id", id),
                (f, n) ->
                        new Fila(
                                f.getInt("fila"),
                                f.getString("codigo"),
                                f.getString("resultado"),
                                f.getString("detalle")));
    }

    /**
     * Aplica las filas válidas en una sola transacción. Vuelve a clasificarlas, porque los datos
     * pueden haber cambiado desde la validación, y una importación solo se confirma una vez.
     */
    @Transactional
    public Importacion confirmar(long id, UsuarioSesion usuario) {
        Importacion importacion = bloquear(id);
        Importador importador = importador(importacion.tipo(), usuario);
        if (!"Validada".equals(importacion.estado())) {
            throw ErrorCore.conflicto(
                    "importacion-ya-confirmada", "Esta importación ya se confirmó");
        }
        Set<String> vistos = new HashSet<>();
        Map<String, Integer> cuentas = new HashMap<>();
        for (Leida fila : guardadas(id)) {
            Clasificacion c = importador.clasificar(fila.valores(), vistos);
            cuentas.merge(c.resultado(), 1, Integer::sum);
            if (Importador.ALTA.equals(c.resultado())
                    || Importador.ACTUALIZACION.equals(c.resultado())) {
                importador.aplicar(fila.valores(), c, usuario.id());
            }
            jdbc.update(
                    """
                    UPDATE core_importacion_fila SET resultado = :resultado, detalle = :detalle
                    WHERE importacion_id = :id AND fila = :fila
                    """,
                    Map.of(
                            "resultado", c.resultado(),
                            "detalle", recortar(c.detalle(), 500),
                            "id", id,
                            "fila", fila.numero()));
        }
        guardarCuentas(id, cuentas);
        jdbc.update(
                """
                UPDATE core_importacion SET estado = 'Confirmada', fecha_confirmacion = now()
                WHERE id = :id
                """,
                Map.of("id", id));
        return buscar(id);
    }

    private Importador importador(String tipo, UsuarioSesion usuario) {
        if (usuario == null) {
            throw ErrorCore.noAutenticado("Inicia sesión para continuar");
        }
        if (tipo != null && SIN_MODULO.contains(tipo)) {
            throw ErrorCore.conflicto(
                    "entidad-no-disponible", "El módulo de " + tipo + " aún no está disponible");
        }
        Importador importador = tipo == null ? null : importadores.get(tipo);
        if (importador == null) {
            throw ErrorCore.invalido(
                    "tipo-desconocido",
                    "El tipo debe ser uno de: " + String.join(", ", importadores.keySet()));
        }
        autorizacion.exigir(usuario, importador.permiso());
        return importador;
    }

    private List<Leida> leer(byte[] archivo, Importador importador) {
        exigirXlsx(archivo);
        try (var libro = new ReadableWorkbook(new ByteArrayInputStream(archivo));
                var lineas = libro.getFirstSheet().openStream()) {
            List<Leida> filas = new ArrayList<>();
            List<String> cabeceras = new ArrayList<>();
            for (Row fila : (Iterable<Row>) lineas::iterator) {
                if (cabeceras.isEmpty()) {
                    for (int c = 0; c < fila.getCellCount(); c++) {
                        cabeceras.add(textoDe(fila, c).toLowerCase(Locale.ROOT));
                    }
                    exigirColumnas(cabeceras, importador);
                    continue;
                }
                Map<String, String> valores = new LinkedHashMap<>();
                for (int c = 0; c < cabeceras.size(); c++) {
                    String valor = textoDe(fila, c);
                    if (!valor.isEmpty() && !cabeceras.get(c).isEmpty()) {
                        valores.put(cabeceras.get(c), valor);
                    }
                }
                if (valores.isEmpty()) {
                    continue;
                }
                if (filas.size() == MAXIMO_FILAS) {
                    throw ErrorCore.invalido(
                            "archivo-demasiado-grande",
                            "El archivo supera las " + MAXIMO_FILAS + " filas");
                }
                filas.add(new Leida(fila.getRowNum(), valores));
            }
            if (cabeceras.isEmpty()) {
                throw ErrorCore.invalido("archivo-vacio", "El archivo no tiene datos");
            }
            return filas;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ErrorCore error) {
                throw error;
            }
            throw archivoInvalido();
        }
    }

    private static void exigirColumnas(List<String> cabeceras, Importador importador) {
        List<String> faltan =
                importador.columnas().stream().filter(c -> !cabeceras.contains(c)).toList();
        if (!faltan.isEmpty()) {
            throw ErrorCore.invalido(
                    "columnas-faltantes", "Faltan las columnas: " + String.join(", ", faltan));
        }
    }

    private static String textoDe(Row fila, int columna) {
        return fila.getOptionalCell(columna).map(Cell::getText).map(String::trim).orElse("");
    }

    /**
     * Comprueba el contenido, no la extensión, y limita lo que ocupa descomprimido contando lo que
     * se lee: los tamaños declarados en un zip se pueden falsear. Usa {@link ZipFile} porque {@code
     * ZipInputStream} no admite las entradas con descriptor que generan algunas hojas de cálculo.
     */
    private static void exigirXlsx(byte[] archivo) {
        Path temporal = null;
        try {
            temporal = Files.createTempFile("importacion-", ".xlsx");
            Files.write(temporal, archivo);
            try (ZipFile zip = new ZipFile(temporal.toFile())) {
                if (zip.getEntry("xl/workbook.xml") == null) {
                    throw archivoInvalido();
                }
                long total = 0;
                byte[] bufer = new byte[8192];
                for (var entradas = zip.entries(); entradas.hasMoreElements(); ) {
                    try (InputStream datos = zip.getInputStream(entradas.nextElement())) {
                        int leidos;
                        while ((leidos = datos.read(bufer)) > 0) {
                            total += leidos;
                            if (total > MAXIMO_DESCOMPRIMIDO) {
                                throw archivoInvalido();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw archivoInvalido();
        } finally {
            borrar(temporal);
        }
    }

    private static void borrar(Path temporal) {
        try {
            if (temporal != null) {
                Files.deleteIfExists(temporal);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ErrorCore archivoInvalido() {
        return ErrorCore.invalido("archivo-invalido", "El archivo no es un Excel .xlsx válido");
    }

    private List<Leida> guardadas(long id) {
        return jdbc.query(
                "SELECT fila, datos FROM core_importacion_fila WHERE importacion_id = :id"
                        + " ORDER BY fila",
                Map.of("id", id),
                (f, n) -> new Leida(f.getInt("fila"), deJson(f.getString("datos"))));
    }

    private void guardarCuentas(long id, Map<String, Integer> cuentas) {
        jdbc.update(
                """
                UPDATE core_importacion SET altas = :altas, actualizaciones = :actualizaciones,
                    sin_cambios = :sinCambios, revisar = :revisar, errores = :errores
                WHERE id = :id
                """,
                Map.of(
                        "id", id,
                        "altas", cuentas.getOrDefault(Importador.ALTA, 0),
                        "actualizaciones", cuentas.getOrDefault(Importador.ACTUALIZACION, 0),
                        "sinCambios", cuentas.getOrDefault(Importador.SIN_CAMBIOS, 0),
                        "revisar", cuentas.getOrDefault(Importador.REVISAR, 0),
                        "errores", cuentas.getOrDefault(Importador.ERROR, 0)));
    }

    private static final String SELECCION =
            """
            SELECT i.id, i.tipo, i.nombre_archivo archivo, i.estado, i.total, i.altas,
                i.actualizaciones, i.sin_cambios, i.revisar, i.errores, u.nombre usuario,
                i.fecha_creacion, i.fecha_confirmacion
            FROM core_importacion i JOIN core_usuario u ON u.id = i.usuario_id
            WHERE i.id = :id
            """;

    private Importacion buscar(long id) {
        return consultar(SELECCION, id);
    }

    private Importacion bloquear(long id) {
        return consultar(SELECCION + " FOR UPDATE OF i", id);
    }

    private Importacion consultar(String sql, long id) {
        return jdbc
                .query(
                        sql,
                        Map.of("id", id),
                        (f, n) ->
                                new Importacion(
                                        f.getLong("id"),
                                        f.getString("tipo"),
                                        f.getString("archivo"),
                                        f.getString("estado"),
                                        f.getInt("total"),
                                        f.getInt("altas"),
                                        f.getInt("actualizaciones"),
                                        f.getInt("sin_cambios"),
                                        f.getInt("revisar"),
                                        f.getInt("errores"),
                                        f.getString("usuario"),
                                        f.getObject("fecha_creacion", OffsetDateTime.class),
                                        f.getObject("fecha_confirmacion", OffsetDateTime.class)))
                .stream()
                .findFirst()
                .orElseThrow(() -> ErrorCore.noEncontrado("La importación no existe"));
    }

    private String aJson(Map<String, String> valores) {
        try {
            return json.writeValueAsString(valores);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> deJson(String datos) {
        try {
            return json.readValue(datos, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
