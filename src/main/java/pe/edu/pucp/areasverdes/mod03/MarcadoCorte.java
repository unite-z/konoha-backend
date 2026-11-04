package pe.edu.pucp.areasverdes.mod03;

import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod03.CorteCesped.JardinRuta;

/**
 * Ruta de hoy y marcado de jardines del capataz (AV-260, RF-COR-002, HU-011 y CU-011). La ruta de
 * hoy son los jardines del corte del día en los sectores que el capataz tiene asignados en MOD-02
 * ({@code mod02_historial_capataz_sector}). Una marca reenviada con la misma {@code
 * Idempotency-Key} (cola sin conexión) devuelve la existente; si otro ya marcó el jardín, 409.
 */
@Service
public class MarcadoCorte {

    public record RutaDeHoy(
            long programacionId, String codigo, String servicio, List<JardinRuta> jardines) {}

    public record RespuestaRutaDeHoy(RutaDeHoy ruta) {}

    public record NuevaMarca(OffsetDateTime marcadoEn) {}

    public record Observacion(@Size(max = 300, message = "Máximo 300 caracteres") String texto) {}

    public record ObservacionGuardada(long id, String texto) {}

    public record JardinMaestro(long id, String codigo, String nombre, String zona) {}

    public record Busqueda(int count, List<JardinMaestro> results) {}

    public record NuevaMarcaFueraDeRuta(
            Long jardinId,
            OffsetDateTime marcadoEn,
            @Size(max = 300, message = "Máximo 300 caracteres") String nota) {}

    public record MarcaFueraDeRuta(
            long id, JardinMaestro jardin, String nota, OffsetDateTime marcadoEn) {}

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Jardines de los sectores que el capataz {@code :usuario} tiene asignados ahora. */
    private static final String DE_SUS_SECTORES =
            """
            r.programacion_id = :programacion AND EXISTS (SELECT 1
                FROM mod02_historial_area_sector ha
                JOIN mod02_historial_capataz_sector hc ON hc.sector_id = ha.sector_id
                    AND hc.fecha_fin IS NULL AND hc.capataz_id = :usuario
                WHERE ha.area_verde_id = r.area_verde_id AND ha.fecha_fin IS NULL)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final CorteCesped corte;
    private final Clock reloj;

    public MarcadoCorte(NamedParameterJdbcTemplate jdbc, CorteCesped corte, Clock reloj) {
        this.jdbc = jdbc;
        this.corte = corte;
        this.reloj = reloj;
    }

    /** El corte de hoy con los jardines de sus sectores; {@code ruta} nula si hoy no hay. */
    public RespuestaRutaDeHoy rutaDeHoy(UsuarioPlanificacion usuario) {
        Acceso.registrarCorte(usuario);
        for (Map<String, Object> ciclo : ciclosDeHoy()) {
            long programacion = ((Number) ciclo.get("id")).longValue();
            List<JardinRuta> jardines =
                    corte.jardines(
                            DE_SUS_SECTORES,
                            Map.of("programacion", programacion, "usuario", usuario.id()));
            if (!jardines.isEmpty()) {
                String servicio = (String) ciclo.get("servicio");
                LocalDate inicio = ((java.sql.Date) ciclo.get("inicio")).toLocalDate();
                return new RespuestaRutaDeHoy(
                        new RutaDeHoy(programacion, codigo(servicio, inicio), servicio, jardines));
            }
        }
        return new RespuestaRutaDeHoy(null);
    }

    /**
     * Marca el jardín como cortado con la hora real. El mismo envío otra vez (misma clave) responde
     * la marca existente; si ya lo marcó otro, 409 {@code ya-marcado} con quién y cuándo.
     */
    @Transactional
    public JardinRuta marcar(
            long programacion,
            long jardin,
            NuevaMarca marca,
            UUID clave,
            UsuarioPlanificacion usuario) {
        Acceso.registrarCorte(usuario);
        exigirCicloAbierto(programacion);
        List<Map<String, Object>> filas =
                jdbc.queryForList(
                        """
                        SELECT r.id, r.estado, r.clave_idempotencia, r.marcado_en, u.nombre marcado_por
                        FROM mod03_ruta_corte r LEFT JOIN core_usuario u ON u.id = r.marcado_por_id
                        WHERE r.programacion_id = :programacion AND r.area_verde_id = :jardin
                        FOR UPDATE OF r
                        """,
                        Map.of("programacion", programacion, "jardin", jardin));
        if (filas.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "jardin-no-encontrado", "Ese jardín no está en la ruta del ciclo");
        }
        Map<String, Object> fila = filas.getFirst();
        if ("Cortado".equals(fila.get("estado"))) {
            if (clave == null || !clave.equals(fila.get("clave_idempotencia"))) {
                OffsetDateTime hora =
                        ((java.sql.Timestamp) fila.get("marcado_en"))
                                .toInstant()
                                .atZone(LIMA)
                                .toOffsetDateTime();
                throw new ErrorPlanificacion(
                        409,
                        "ya-marcado",
                        "Ya lo marcó %s a las %s"
                                .formatted(fila.get("marcado_por"), HORA.format(hora)));
            }
        } else {
            OffsetDateTime hora =
                    marca == null || marca.marcadoEn() == null
                            ? OffsetDateTime.now(reloj)
                            : marca.marcadoEn();
            jdbc.update(
                    """
                    UPDATE mod03_ruta_corte SET estado = 'Cortado', marcado_en = :hora,
                        marcado_por_id = :usuario, clave_idempotencia = :clave, sincronizado_en = now()
                    WHERE id = :id
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", fila.get("id"))
                            .addValue("hora", hora)
                            .addValue("usuario", usuario.id())
                            .addValue("clave", clave));
        }
        return corte.jardines(
                        "r.programacion_id = :programacion AND r.area_verde_id = :jardin",
                        Map.of("programacion", programacion, "jardin", jardin))
                .getFirst();
    }

    /** Una observación del jardín; no cambia la marca. */
    @Transactional
    public ObservacionGuardada observar(
            long programacion, long jardin, Observacion o, UsuarioPlanificacion usuario) {
        Acceso.registrarCorte(usuario);
        String texto = o == null || o.texto() == null ? "" : o.texto().trim();
        if (texto.isEmpty()) {
            throw new ErrorPlanificacion(400, "texto-requerido", "Escribe la observación");
        }
        List<Long> ruta =
                jdbc.queryForList(
                        "SELECT id FROM mod03_ruta_corte WHERE programacion_id = :programacion AND"
                                + " area_verde_id = :jardin",
                        Map.of("programacion", programacion, "jardin", jardin),
                        Long.class);
        if (ruta.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "jardin-no-encontrado", "Ese jardín no está en la ruta del ciclo");
        }
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod03_observacion_jardin (ruta_corte_id, texto, usuario_id)"
                                + " VALUES (:ruta, :texto, :usuario) RETURNING id",
                        Map.of("ruta", ruta.getFirst(), "texto", texto, "usuario", usuario.id()),
                        Long.class);
        return new ObservacionGuardada(id, texto);
    }

    /** Jardines del maestro que no están en la ruta de hoy, por código o nombre (CU-011 2b). */
    public Busqueda buscarFueraDeRuta(String q, UsuarioPlanificacion usuario) {
        Acceso.registrarCorte(usuario);
        RutaDeHoy hoy = rutaDeHoy(usuario).ruta();
        List<JardinMaestro> encontrados =
                jdbc.query(
                        """
                        SELECT a.id, 'J-' || lpad(a.id::text, 3, '0') codigo, a.nombre,
                            coalesce(s.nombre, '') zona
                        FROM mod02_area_verde a
                        LEFT JOIN mod02_historial_area_sector h ON h.area_verde_id = a.id
                            AND h.fecha_fin IS NULL
                        LEFT JOIN mod02_sector s ON s.id = h.sector_id
                        WHERE a.activo
                          AND (a.nombre ILIKE :q OR ('J-' || lpad(a.id::text, 3, '0')) ILIKE :q)
                          AND NOT EXISTS (SELECT 1 FROM mod03_ruta_corte r
                              WHERE r.programacion_id = :programacion AND r.area_verde_id = a.id)
                        ORDER BY a.nombre LIMIT 20
                        """,
                        Map.of(
                                "q",
                                "%" + (q == null ? "" : q.trim()) + "%",
                                "programacion",
                                hoy == null ? 0L : hoy.programacionId()),
                        (rs, n) ->
                                new JardinMaestro(
                                        rs.getLong("id"),
                                        rs.getString("codigo"),
                                        rs.getString("nombre"),
                                        rs.getString("zona")));
        return new Busqueda(encontrados.size(), encontrados);
    }

    /**
     * El capataz cortó un jardín que no está en la ruta: queda pendiente de que el jefe lo añada.
     */
    @Transactional
    public MarcaFueraDeRuta marcarFueraDeRuta(
            long programacion, NuevaMarcaFueraDeRuta m, UUID clave, UsuarioPlanificacion usuario) {
        Acceso.registrarCorte(usuario);
        exigirCicloAbierto(programacion);
        if (clave != null) {
            List<MarcaFueraDeRuta> previa =
                    fueraDeRuta("f.clave_idempotencia = :clave", Map.of("clave", clave));
            if (!previa.isEmpty()) {
                return previa.getFirst();
            }
        }
        String nota = m == null || m.nota() == null ? "" : m.nota().trim();
        if (nota.isEmpty()) {
            throw new ErrorPlanificacion(400, "nota-requerida", "Explica por qué lo cortaste");
        }
        if (m.jardinId() == null
                || jdbc.queryForObject(
                                "SELECT count(*) FROM mod02_area_verde WHERE id = :id AND activo",
                                Map.of("id", m.jardinId()),
                                Integer.class)
                        == 0) {
            throw new ErrorPlanificacion(400, "jardin-invalido", "No existe ese jardín");
        }
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod03_fuera_de_ruta (programacion_id, area_verde_id, nota, marcado_en,
                            marcado_por_id, clave_idempotencia)
                        VALUES (:programacion, :jardin, :nota, :hora, :usuario, :clave) RETURNING id
                        """,
                        new MapSqlParameterSource()
                                .addValue("programacion", programacion)
                                .addValue("jardin", m.jardinId())
                                .addValue("nota", nota)
                                .addValue(
                                        "hora",
                                        m.marcadoEn() == null
                                                ? OffsetDateTime.now(reloj)
                                                : m.marcadoEn())
                                .addValue("usuario", usuario.id())
                                .addValue("clave", clave),
                        Long.class);
        return fueraDeRuta("f.id = :id", Map.of("id", id)).getFirst();
    }

    private List<MarcaFueraDeRuta> fueraDeRuta(String filtro, Map<String, ?> datos) {
        return jdbc.query(
                """
                SELECT f.id, f.nota, f.marcado_en, a.id jardin_id, a.nombre,
                    'J-' || lpad(a.id::text, 3, '0') codigo, coalesce(s.nombre, '') zona
                FROM mod03_fuera_de_ruta f JOIN mod02_area_verde a ON a.id = f.area_verde_id
                LEFT JOIN mod02_historial_area_sector h ON h.area_verde_id = a.id AND h.fecha_fin IS NULL
                LEFT JOIN mod02_sector s ON s.id = h.sector_id
                WHERE %s
                """
                        .formatted(filtro),
                datos,
                (rs, n) ->
                        new MarcaFueraDeRuta(
                                rs.getLong("id"),
                                new JardinMaestro(
                                        rs.getLong("jardin_id"),
                                        rs.getString("codigo"),
                                        rs.getString("nombre"),
                                        rs.getString("zona")),
                                rs.getString("nota"),
                                rs.getObject("marcado_en", OffsetDateTime.class)
                                        .atZoneSameInstant(LIMA)
                                        .toOffsetDateTime()));
    }

    /** Ciclos con ruta cuyo periodo previsto incluye hoy, sin cancelar ni completar. */
    private List<Map<String, Object>> ciclosDeHoy() {
        return jdbc.queryForList(
                """
                SELECT p.id, t.nombre servicio, p.fecha_inicio_prevista inicio
                FROM mod03_programacion_servicio p JOIN mod05_tiposervicio t ON t.id = p.tipo_servicio_id
                WHERE p.estado IN ('Programada', 'EnCurso')
                  AND CAST(:hoy AS date) BETWEEN p.fecha_inicio_prevista AND p.fecha_fin_prevista
                  AND EXISTS (SELECT 1 FROM mod03_ruta_corte r WHERE r.programacion_id = p.id)
                ORDER BY p.fecha_inicio_prevista, p.id
                """,
                Map.of("hoy", LocalDate.now(reloj)));
    }

    private void exigirCicloAbierto(long programacion) {
        List<String> estado =
                jdbc.queryForList(
                        "SELECT estado FROM mod03_programacion_servicio WHERE id = :id",
                        Map.of("id", programacion),
                        String.class);
        if (estado.isEmpty()) {
            throw new ErrorPlanificacion(404, "ciclo-no-encontrado", "No existe ese ciclo");
        }
        if (List.of("Cancelada", "Completada").contains(estado.getFirst())) {
            throw new ErrorPlanificacion(409, "ciclo-cerrado", "Ese ciclo ya terminó");
        }
    }

    /** «CC-2026-11»: iniciales del servicio, año y mes del inicio (hasta el código de AV-364). */
    static String codigo(String servicio, LocalDate inicio) {
        StringBuilder iniciales = new StringBuilder();
        for (String palabra : servicio.split("[\\s·.]+")) {
            if (iniciales.length() < 2
                    && !palabra.isEmpty()
                    && Character.isLetter(palabra.charAt(0))
                    && !List.of("de", "del", "la", "el", "los", "las", "y")
                            .contains(palabra.toLowerCase())) {
                iniciales.append(Character.toUpperCase(palabra.charAt(0)));
            }
        }
        return "%s-%d-%02d".formatted(iniciales, inicio.getYear(), inicio.getMonthValue());
    }
}
