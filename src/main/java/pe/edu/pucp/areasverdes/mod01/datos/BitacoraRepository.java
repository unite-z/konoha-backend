package pe.edu.pucp.areasverdes.mod01.datos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Auditoría de cambios ({@code core_registro_auditoria}) y registro de accesos ({@code
 * core_registro_acceso}). Solo inserta y consulta: la base rechaza UPDATE y DELETE (RI-35).
 */
@Repository
public class BitacoraRepository {

    public record Cambio(
            long id,
            String entidad,
            long registroId,
            String accion,
            long usuarioId,
            String usuarioNombre,
            OffsetDateTime fechaHora,
            Map<String, Object> valoresAnteriores,
            Map<String, Object> valoresNuevos,
            String motivo) {}

    public record Acceso(
            long id,
            Long usuarioId,
            String usuarioNombre,
            String identificador,
            OffsetDateTime fechaHora,
            String canal,
            String resultado,
            String motivo,
            String ruta) {}

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public BitacoraRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insertarCambio(
            String entidad,
            long registroId,
            String accion,
            long usuarioId,
            OffsetDateTime fechaHora,
            Map<String, ?> antes,
            Map<String, ?> despues,
            String motivo) {
        var p = new HashMap<String, Object>();
        p.put("entidad", entidad);
        p.put("registro", registroId);
        p.put("accion", accion);
        p.put("usuario", usuarioId);
        p.put("fecha", fechaHora);
        p.put("antes", escribir(antes));
        p.put("despues", escribir(despues));
        p.put("motivo", motivo);
        jdbc.update(
                """
                INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id,
                    fecha_hora, valores_anteriores, valores_nuevos, motivo)
                VALUES (:entidad, :registro, :accion, :usuario, :fecha, CAST(:antes AS jsonb),
                    CAST(:despues AS jsonb), :motivo)
                """,
                p);
    }

    public List<Cambio> cambios(String entidad, long registroId) {
        return jdbc.query(
                """
                SELECT a.*, u.nombre usuario_nombre FROM core_registro_auditoria a
                JOIN core_usuario u ON u.id = a.usuario_id
                WHERE lower(a.entidad) = lower(:entidad) AND a.registro_id = :registro
                ORDER BY a.fecha_hora DESC, a.id DESC
                """,
                Map.of("entidad", entidad, "registro", registroId),
                (fila, n) ->
                        new Cambio(
                                fila.getLong("id"),
                                fila.getString("entidad"),
                                fila.getLong("registro_id"),
                                fila.getString("accion"),
                                fila.getLong("usuario_id"),
                                fila.getString("usuario_nombre"),
                                fila.getObject("fecha_hora", OffsetDateTime.class),
                                leer(fila.getString("valores_anteriores")),
                                leer(fila.getString("valores_nuevos")),
                                fila.getString("motivo")));
    }

    public void insertarAcceso(
            Long usuarioId,
            String identificador,
            OffsetDateTime fechaHora,
            String canal,
            String resultado,
            String motivo,
            String ruta) {
        var p = new HashMap<String, Object>();
        p.put("usuario", usuarioId);
        p.put("identificador", recortar(identificador, 150));
        p.put("fecha", fechaHora);
        p.put("canal", canal);
        p.put("resultado", resultado);
        p.put("motivo", recortar(motivo, 200));
        p.put("ruta", recortar(ruta, 300));
        jdbc.update(
                """
                INSERT INTO core_registro_acceso (usuario_id, identificador, fecha_hora, canal,
                    resultado, motivo, ruta)
                VALUES (:usuario, :identificador, :fecha, :canal, :resultado, :motivo, :ruta)
                """,
                p);
    }

    /** Accesos más recientes primero, con filtros opcionales (CU-MOD01-007). */
    /**
     * Filtros del registro de accesos; un valor nulo no filtra.
     *
     * @param usuario texto en el nombre, la cuenta o el identificador escrito al intentar entrar
     * @param desde primer día, en hora de Lima
     * @param hasta último día, incluido, en hora de Lima
     */
    public record FiltroAccesos(
            Long usuarioId,
            String usuario,
            String resultado,
            String canal,
            LocalDate desde,
            LocalDate hasta) {

        private Map<String, Object> parametros() {
            var p = new HashMap<String, Object>();
            p.put("usuario", usuarioId);
            p.put("texto", usuario == null ? null : "%" + escaparLike(usuario) + "%");
            p.put("resultado", resultado);
            p.put("canal", canal);
            p.put("desde", desde);
            p.put("hasta", hasta);
            return p;
        }

        private static String escaparLike(String texto) {
            return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
    }

    /** Solo lleva parámetros. Un intento sin cuenta se busca por lo que escribió la persona. */
    private static final String DESDE_ACCESOS =
            """
            FROM core_registro_acceso a
            LEFT JOIN core_usuario u ON u.id = a.usuario_id
            WHERE (CAST(:usuario AS bigint) IS NULL OR a.usuario_id = :usuario)
                AND (CAST(:resultado AS varchar) IS NULL OR a.resultado = :resultado)
                AND (CAST(:canal AS varchar) IS NULL OR a.canal = :canal)
                AND (CAST(:texto AS varchar) IS NULL OR u.nombre ILIKE :texto
                    OR u.nombre_usuario ILIKE :texto OR a.identificador ILIKE :texto)
                AND (CAST(:desde AS date) IS NULL
                    OR a.fecha_hora >= CAST(CAST(:desde AS date) AS timestamp) AT TIME ZONE 'America/Lima')
                AND (CAST(:hasta AS date) IS NULL
                    OR a.fecha_hora < CAST(CAST(:hasta AS date) + 1 AS timestamp) AT TIME ZONE 'America/Lima')
            """;

    public List<Acceso> accesos(FiltroAccesos filtro, int limite, int desplazamiento) {
        var p = filtro.parametros();
        p.put("limite", limite);
        p.put("desde_fila", desplazamiento);
        return jdbc.query(
                "SELECT a.*, u.nombre usuario_nombre "
                        + DESDE_ACCESOS
                        + " ORDER BY a.fecha_hora DESC, a.id DESC LIMIT :limite OFFSET :desde_fila",
                p,
                (fila, n) ->
                        new Acceso(
                                fila.getLong("id"),
                                (Long) fila.getObject("usuario_id"),
                                fila.getString("usuario_nombre"),
                                fila.getString("identificador"),
                                fila.getObject("fecha_hora", OffsetDateTime.class),
                                fila.getString("canal"),
                                fila.getString("resultado"),
                                fila.getString("motivo"),
                                fila.getString("ruta")));
    }

    public long contarAccesos(FiltroAccesos filtro) {
        Long n =
                jdbc.queryForObject(
                        "SELECT count(*) " + DESDE_ACCESOS, filtro.parametros(), Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Usuarios con varios rechazos por falta de permiso desde {@code desde} (destacado de CU-007).
     */
    public Map<Long, Integer> rechazosRecientes(OffsetDateTime desde) {
        Map<Long, Integer> resultado = new HashMap<>();
        jdbc.query(
                """
                SELECT usuario_id, count(*) n FROM core_registro_acceso
                WHERE resultado = 'SinPermiso' AND usuario_id IS NOT NULL AND fecha_hora >= :desde
                GROUP BY usuario_id
                """,
                Map.of("desde", desde),
                fila -> {
                    resultado.put(fila.getLong("usuario_id"), fila.getInt("n"));
                });
        return resultado;
    }

    private String escribir(Map<String, ?> valores) {
        try {
            return json.writeValueAsString(valores == null ? Map.of() : valores);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Valores de auditoría no serializables", e);
        }
    }

    private Map<String, Object> leer(String valores) {
        try {
            return valores == null ? Map.of() : json.readValue(valores, MAPA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Auditoría con JSON inválido", e);
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
