package pe.edu.pucp.areasverdes.mod01.servicios;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.areasverdes.mod05.ApiError;
import pe.edu.pucp.areasverdes.mod05.ArchivoStorage;

/**
 * Fotografías y documentos asociados a actividades, atenciones, incidencias y ejemplares (RF-T-007,
 * HU-MOD01-009). Usa {@link ArchivoStorage}, la única puerta a los archivos (AGENTS.md): lista
 * blanca por contenido, tamaño máximo y ruta saneada.
 */
@Service
public class AdjuntosService {

    public record Adjunto(
            long id,
            UUID uuidCliente,
            String entidad,
            long registroId,
            String nombre,
            String tipoMime,
            long tamanoBytes,
            String descripcion,
            String usuario,
            OffsetDateTime fechaCreacion) {}

    public record Subida(Adjunto adjunto, boolean creado) {}

    public record Contenido(Resource recurso, String nombre, String tipoMime) {}

    /** Tabla del registro (la primera que exista) y permisos para adjuntar y consultar. */
    record Destino(List<String> tablas, String permisoRegistrar, String permisoConsultar) {}

    static final Map<String, Destino> DESTINOS =
            Map.of(
                    "actividad",
                    new Destino(
                            List.of("mod04_actividad"),
                            "CAM.PARTE.REGISTRAR",
                            "CAM.PARTE.CONSULTAR"),
                    "atencion",
                    new Destino(
                            List.of("mod05_atenciontercerizada"),
                            "SER.ATENCION.REGISTRAR",
                            "SER.ATENCION.REGISTRAR"),
                    "incidencia",
                    new Destino(
                            List.of("mod03_incidencia"),
                            "PLA.INCIDENCIA.REGISTRAR",
                            "PLA.INCIDENCIA.REGISTRAR"),
                    "ejemplar",
                    new Destino(
                            List.of("mod02_ejemplar", "mod05_ejemplar"),
                            "INV.INVENTARIO.REGISTRAR",
                            "INV.INVENTARIO.CONSULTAR"));

    private static final String SELECCION =
            """
            SELECT a.id, a.uuid_cliente, a.entidad, a.registro_id, f.nombre_original nombre,
                f.tipo_mime, f.tamano_bytes, a.descripcion, u.nombre usuario, a.fecha_creacion
            FROM core_adjunto a JOIN core_archivo f ON f.id = a.archivo_id
            JOIN core_usuario u ON u.id = a.usuario_creacion_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ArchivoStorage almacen;
    private final Autorizacion autorizacion;
    private final RegistradorAuditoria auditoria;
    private final DataClassRowMapper<Adjunto> filas = new DataClassRowMapper<>(Adjunto.class);

    public AdjuntosService(
            NamedParameterJdbcTemplate jdbc,
            ArchivoStorage almacen,
            Autorizacion autorizacion,
            RegistradorAuditoria auditoria) {
        this.jdbc = jdbc;
        this.almacen = almacen;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
    }

    /**
     * Adjunta un archivo. Reintentar con el mismo {@code uuidCliente} devuelve el adjunto ya creado
     * sin duplicarlo (cola sin conexión); el mismo archivo en el mismo registro responde 409. El
     * archivo se escribe al final de la transacción: si falla, no queda fila.
     */
    @Transactional
    public Subida subir(
            String entidad,
            long registroId,
            UUID uuidCliente,
            String descripcion,
            MultipartFile archivo,
            UsuarioSesion usuario) {
        Destino destino = destino(entidad);
        autorizacion.exigir(usuario, destino.permisoRegistrar());
        if (uuidCliente == null) {
            throw ErrorCore.invalido(
                    "uuid-cliente-obligatorio",
                    "Falta el identificador de la subida (Idempotency-Key)");
        }
        exigirRegistro(destino, entidad, registroId);
        ArchivoStorage.Validado validado;
        try {
            validado = almacen.validar(archivo);
        } catch (ApiError e) {
            throw ErrorCore.invalido("archivo-invalido", e.getMessage());
        }
        Optional<Adjunto> previo = porUuid(uuidCliente);
        if (previo.isPresent()) {
            Adjunto a = previo.get();
            if (!a.entidad().equals(entidad) || a.registroId() != registroId) {
                throw ErrorCore.conflicto(
                        "idempotency-key-reutilizada",
                        "Ese identificador de subida ya se usó para otro registro");
            }
            return new Subida(a, false);
        }
        Long duplicado =
                jdbc
                        .query(
                                """
                                SELECT id FROM core_adjunto
                                WHERE entidad = :e AND registro_id = :r AND hash_contenido = :h
                                """,
                                Map.of("e", entidad, "r", registroId, "h", validado.hash()),
                                (fila, n) -> fila.getLong("id"))
                        .stream()
                        .findFirst()
                        .orElse(null);
        if (duplicado != null) {
            throw new ErrorCore(
                    409,
                    "archivo-duplicado",
                    "Ese archivo ya está adjunto a este registro",
                    Map.of("adjunto_id", duplicado));
        }
        String clave =
                "core/"
                        + entidad
                        + "/"
                        + registroId
                        + "/"
                        + uuidCliente
                        + "-"
                        + saneado(validado.nombre());
        var p = new HashMap<String, Object>();
        p.put("nombre", validado.nombre());
        p.put("mime", validado.mime());
        p.put("tamano", validado.size());
        p.put("hash", validado.hash());
        p.put("clave", clave);
        long archivoId =
                jdbc.queryForObject(
                        """
                        INSERT INTO core_archivo (nombre_original, tipo_mime, tamano_bytes,
                            hash_contenido, clave)
                        VALUES (:nombre, :mime, :tamano, :hash, :clave) RETURNING id
                        """,
                        p,
                        Long.class);
        p.put("uuid", uuidCliente);
        p.put("entidad", entidad);
        p.put("registro", registroId);
        p.put("archivo", archivoId);
        p.put(
                "descripcion",
                descripcion == null || descripcion.isBlank() ? null : descripcion.trim());
        p.put("usuario", usuario.id());
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO core_adjunto (uuid_cliente, entidad, registro_id, archivo_id,
                            hash_contenido, descripcion, usuario_creacion_id)
                        VALUES (:uuid, :entidad, :registro, :archivo, :hash, :descripcion, :usuario)
                        RETURNING id
                        """,
                        p,
                        Long.class);
        auditoria.registrarAlta(
                "adjunto",
                id,
                Map.of("entidad", entidad, "registro_id", registroId, "nombre", validado.nombre()),
                usuario.id());
        try {
            almacen.guardar(clave, validado);
        } catch (ApiError e) {
            throw new ErrorCore(
                    503,
                    "almacenamiento-no-disponible",
                    "No se pudo guardar el archivo. Vuelve a intentarlo");
        }
        return new Subida(porUuid(uuidCliente).orElseThrow(), true);
    }

    public List<Adjunto> listar(String entidad, long registroId, UsuarioSesion usuario) {
        Destino destino = destino(entidad);
        autorizacion.exigir(usuario, destino.permisoConsultar());
        return jdbc.query(
                SELECCION
                        + " WHERE a.entidad = :e AND a.registro_id = :r"
                        + " ORDER BY a.fecha_creacion DESC, a.id DESC",
                Map.of("e", entidad, "r", registroId),
                filas);
    }

    /** Descarga tras comprobar el permiso: un rol no autorizado no accede al archivo. */
    public Contenido contenido(long id, UsuarioSesion usuario) {
        var fila =
                jdbc
                        .query(
                                """
                                SELECT a.entidad, f.clave, f.nombre_original, f.tipo_mime
                                FROM core_adjunto a JOIN core_archivo f ON f.id = a.archivo_id
                                WHERE a.id = :id
                                """,
                                Map.of("id", id),
                                (r, n) ->
                                        new String[] {
                                            r.getString("entidad"),
                                            r.getString("clave"),
                                            r.getString("nombre_original"),
                                            r.getString("tipo_mime")
                                        })
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> ErrorCore.noEncontrado("El adjunto no existe"));
        autorizacion.exigir(usuario, destino(fila[0]).permisoConsultar());
        try {
            return new Contenido(almacen.leer(fila[1]), fila[2], fila[3]);
        } catch (ApiError e) {
            throw new ErrorCore(
                    503,
                    "almacenamiento-no-disponible",
                    "El archivo no está disponible en este momento");
        }
    }

    private Optional<Adjunto> porUuid(UUID uuid) {
        return jdbc
                .query(SELECCION + " WHERE a.uuid_cliente = :u", Map.of("u", uuid), filas)
                .stream()
                .findFirst();
    }

    private void exigirRegistro(Destino destino, String entidad, long registroId) {
        String tabla =
                destino.tablas().stream()
                        .filter(
                                t ->
                                        Boolean.TRUE.equals(
                                                jdbc.queryForObject(
                                                        "SELECT to_regclass(:t) IS NOT NULL",
                                                        Map.of("t", t),
                                                        Boolean.class)))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        ErrorCore.conflicto(
                                                "entidad-no-disponible",
                                                "El módulo de "
                                                        + entidad
                                                        + " aún no está disponible"));
        // La tabla sale de DESTINOS, no de la petición.
        boolean existe =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS (SELECT 1 FROM " + tabla + " WHERE id = :id)",
                                Map.of("id", registroId),
                                Boolean.class));
        if (!existe) {
            throw ErrorCore.noEncontrado("El registro no existe");
        }
    }

    private static Destino destino(String entidad) {
        Destino destino = entidad == null ? null : DESTINOS.get(entidad);
        if (destino == null) {
            throw ErrorCore.invalido(
                    "entidad-desconocida",
                    "La entidad debe ser actividad, atencion, incidencia o ejemplar");
        }
        return destino;
    }

    static String saneado(String nombre) {
        String limpio =
                Normalizer.normalize(nombre, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9.]+", "-")
                        .replaceAll("-+", "-");
        return limpio.length() > 80 ? limpio.substring(limpio.length() - 80) : limpio;
    }
}
