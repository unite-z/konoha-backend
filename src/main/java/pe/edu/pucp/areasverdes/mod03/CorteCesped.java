package pe.edu.pucp.areasverdes.mod03;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ciclo de corte de césped (AV-259, RF-COR-001, HU-010 y CU-010): la ruta de jardines de MOD-02 se
 * propone por zonas (sectores) y se copia al programar. Un jardín es un área verde; su código,
 * «J-014», sale de su id mientras MOD-02 no tenga uno propio.
 */
@Service
public class CorteCesped {

    /** Una zona de la ruta: sus jardines y si es de la ruta ordinaria (no «a demanda»). */
    public record ZonaRuta(String zona, int jardines, boolean enRuta) {}

    public record FueraDeFrecuencia(int diasDesdeUltimo, int maximoDias) {}

    public record RutaCorte(List<ZonaRuta> zonas, FueraDeFrecuencia fueraDeFrecuencia) {}

    public record JardinRuta(
            long id,
            String codigo,
            String nombre,
            String zona,
            String estado,
            OffsetDateTime marcadoEn,
            String marcadoPor,
            boolean detenidoPorInterrupcion) {}

    public record Limpieza(long id, LocalDate fecha, String autor) {}

    /**
     * El ciclo con su ruta y sus jornadas de limpieza. {@code dias_corte} son los días con marcas;
     * {@code cumple}, si días de corte más limpieza no pasan de la duración máxima más limpieza
     * (RN-002, 3 + 1). Las interrupciones llegan con AV-258.
     */
    public record CicloCorte(
            long programacionId,
            List<JardinRuta> jardines,
            List<Object> interrupciones,
            List<Limpieza> limpiezas,
            double diasInterrupcionDescontados,
            int diasCorte,
            int maximoDias,
            boolean cumple) {}

    public record CortadosDia(LocalDate fecha, int cortados) {}

    /**
     * Avance del ciclo (AV-261, RF-COR-003 y 004, HU-012): porcentaje cortado, cortados por día, lo
     * que falta y la última marca. {@code desactualizado} si la última marca tiene más de 24 h.
     */
    public record Avance(
            long programacionId,
            int total,
            int cortados,
            int porcentaje,
            List<CortadosDia> porDia,
            List<JardinRuta> porCortar,
            OffsetDateTime ultimaMarca,
            boolean desactualizado,
            LocalDate fechaInicioPrevista,
            LocalDate fechaFinPrevista) {}

    static final String ZONAS =
            """
            SELECT s.nombre zona, count(a.id) jardines, NOT s.atencion_a_demanda en_ruta
            FROM mod02_sector s
            JOIN mod02_historial_area_sector h ON h.sector_id = s.id AND h.fecha_fin IS NULL
            JOIN mod02_area_verde a ON a.id = h.area_verde_id AND a.activo
            WHERE s.activo
            GROUP BY s.id, s.nombre, s.atencion_a_demanda ORDER BY s.nombre
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock reloj;

    public CorteCesped(NamedParameterJdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** La ruta propuesta para programar el servicio y si va fuera de frecuencia (CU-010 1a). */
    public RutaCorte ruta(long servicio, UsuarioPlanificacion usuario) {
        Acceso.consultar(usuario);
        List<ZonaRuta> zonas =
                jdbc.query(
                        ZONAS,
                        (rs, n) ->
                                new ZonaRuta(
                                        rs.getString("zona"),
                                        rs.getInt("jardines"),
                                        rs.getBoolean("en_ruta")));
        LocalDate hoy = LocalDate.now(reloj);
        List<FueraDeFrecuencia> fuera =
                jdbc.query(
                        """
                        SELECT CAST(:hoy AS date) - max(coalesce(p.fecha_fin_real, p.fecha_fin_prevista))
                                dias,
                            f.frecuencia_maxima_dias maximo
                        FROM mod03_frecuencia_servicio f
                        JOIN mod03_programacion_servicio p ON p.tipo_servicio_id = f.tipo_servicio_id
                            AND p.estado = 'Completada'
                        WHERE f.tipo_servicio_id = :servicio AND f.estacion = :estacion
                          AND f.frecuencia_maxima_dias IS NOT NULL
                        GROUP BY f.frecuencia_maxima_dias
                        """,
                        Map.of(
                                "servicio",
                                servicio,
                                "hoy",
                                hoy,
                                "estacion",
                                Programador.estacion(hoy)),
                        (rs, n) -> new FueraDeFrecuencia(rs.getInt("dias"), rs.getInt("maximo")));
        return new RutaCorte(
                zonas,
                fuera.stream()
                        .filter(f -> f.diasDesdeUltimo() > f.maximoDias())
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Copia en el ciclo los jardines de las zonas elegidas, en orden de zona y nombre. Una zona que
     * no existe es un 400; sin jardines, también.
     */
    void copiarRuta(long programacion, List<String> zonas) {
        Set<String> elegidas = new TreeSet<>(zonas);
        Set<String> existentes = new TreeSet<>();
        jdbc.query(
                ZONAS,
                rs -> {
                    existentes.add(rs.getString("zona"));
                });
        if (!existentes.containsAll(elegidas)) {
            elegidas.removeAll(existentes);
            throw new ErrorPlanificacion(
                    400, "zona-invalida", "No existe la zona " + String.join(", ", elegidas));
        }
        int copiados =
                jdbc.update(
                        """
                        INSERT INTO mod03_ruta_corte (programacion_id, area_verde_id, zona, orden)
                        SELECT :programacion, a.id, s.nombre,
                            row_number() OVER (ORDER BY s.nombre, a.nombre, a.id)
                        FROM mod02_sector s
                        JOIN mod02_historial_area_sector h ON h.sector_id = s.id AND h.fecha_fin IS NULL
                        JOIN mod02_area_verde a ON a.id = h.area_verde_id AND a.activo
                        WHERE s.activo AND s.nombre IN (:zonas)
                        """,
                        new MapSqlParameterSource()
                                .addValue("programacion", programacion)
                                .addValue("zonas", List.copyOf(elegidas)));
        if (copiados == 0) {
            throw new ErrorPlanificacion(400, "ruta-vacia", "La ruta no tiene jardines");
        }
        jdbc.update(
                """
                INSERT INTO mod03_programacion_area (programacion_id, area_verde_id)
                SELECT programacion_id, area_verde_id FROM mod03_ruta_corte
                WHERE programacion_id = :programacion
                ON CONFLICT DO NOTHING
                """,
                Map.of("programacion", programacion));
    }

    /** El ciclo de corte con su ruta y las marcas de cada jardín. */
    public CicloCorte ciclo(long programacion, UsuarioPlanificacion usuario) {
        Acceso.consultar(usuario);
        if (jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_programacion_servicio WHERE id = :id",
                        Map.of("id", programacion),
                        Integer.class)
                == 0) {
            throw new ErrorPlanificacion(404, "ciclo-no-encontrado", "No existe ese ciclo");
        }
        List<JardinRuta> jardines =
                jardines("r.programacion_id = :programacion", Map.of("programacion", programacion));
        List<Limpieza> limpiezas = limpiezas(programacion);
        int diasCorte =
                (int)
                        jardines.stream()
                                .filter(j -> j.marcadoEn() != null)
                                .map(j -> j.marcadoEn().toLocalDate())
                                .distinct()
                                .count();
        int maximo =
                jdbc.queryForObject(
                        """
                        SELECT coalesce(d.duracion_maxima_dias + coalesce(d.dias_limpieza, 0), 4)
                        FROM mod03_programacion_servicio p
                        LEFT JOIN mod03_duracion_servicio d ON d.tipo_servicio_id = p.tipo_servicio_id
                        WHERE p.id = :id
                        """,
                        Map.of("id", programacion),
                        Integer.class);
        return new CicloCorte(
                programacion,
                jardines,
                List.of(),
                limpiezas,
                0,
                diasCorte,
                maximo,
                diasCorte + limpiezas.size() <= maximo);
    }

    public Avance avance(long programacion, UsuarioPlanificacion usuario) {
        CicloCorte ciclo = ciclo(programacion, usuario);
        Map<String, Object> fechas =
                jdbc.queryForMap(
                        "SELECT fecha_inicio_prevista, fecha_fin_prevista FROM"
                                + " mod03_programacion_servicio WHERE id = :id",
                        Map.of("id", programacion));
        List<JardinRuta> cortados =
                ciclo.jardines().stream().filter(j -> "Cortado".equals(j.estado())).toList();
        List<CortadosDia> porDia =
                cortados.stream()
                        .collect(
                                groupingBy(
                                        j -> j.marcadoEn().toLocalDate(), TreeMap::new, counting()))
                        .entrySet()
                        .stream()
                        .map(e -> new CortadosDia(e.getKey(), e.getValue().intValue()))
                        .toList();
        OffsetDateTime ultima =
                cortados.stream()
                        .map(JardinRuta::marcadoEn)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
        int total = ciclo.jardines().size();
        return new Avance(
                programacion,
                total,
                cortados.size(),
                total == 0 ? 0 : Math.round(cortados.size() * 100f / total),
                porDia,
                ciclo.jardines().stream().filter(j -> !"Cortado".equals(j.estado())).toList(),
                ultima,
                ultima != null && ultima.isBefore(OffsetDateTime.now(reloj).minusHours(24)),
                ((Date) fechas.get("fecha_inicio_prevista")).toLocalDate(),
                ((Date) fechas.get("fecha_fin_prevista")).toLocalDate());
    }

    /**
     * Registra una jornada de limpieza (jefe, ingeniero o practicante). Solo con la ruta completa:
     * si no, 409 {@code ruta-incompleta} con los jardines por cortar.
     */
    @Transactional
    public Limpieza registrarLimpieza(
            long programacion, LocalDate fecha, UsuarioPlanificacion usuario) {
        Acceso.registrarLimpieza(usuario);
        List<JardinRuta> pendientes =
                jardines(
                        "r.programacion_id = :programacion AND r.estado = 'Pendiente'",
                        Map.of("programacion", programacion));
        if (jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_ruta_corte WHERE programacion_id = :id",
                        Map.of("id", programacion),
                        Integer.class)
                == 0) {
            throw new ErrorPlanificacion(
                    404, "ciclo-no-encontrado", "Ese ciclo no tiene ruta de corte");
        }
        if (!pendientes.isEmpty()) {
            throw new ErrorPlanificacion(
                    409,
                    "ruta-incompleta",
                    "Faltan %d jardines por cortar: %s"
                            .formatted(
                                    pendientes.size(),
                                    String.join(
                                            ", ",
                                            pendientes.stream().map(JardinRuta::codigo).toList())));
        }
        LocalDate dia = fecha == null ? LocalDate.now(reloj) : fecha;
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod03_limpieza_corte (programacion_id, fecha, usuario_id)
                        VALUES (:programacion, :fecha, :usuario)
                        ON CONFLICT (programacion_id, fecha) DO UPDATE SET fecha = EXCLUDED.fecha
                        RETURNING id
                        """,
                        Map.of("programacion", programacion, "fecha", dia, "usuario", usuario.id()),
                        Long.class);
        return limpiezas(programacion).stream().filter(l -> l.id() == id).findFirst().orElseThrow();
    }

    /** Si el ciclo tiene ruta y le faltan jardines, no se puede finalizar (CU-014). */
    void exigirRutaCompleta(long programacion) {
        Integer pendientes =
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_ruta_corte WHERE programacion_id = :id AND"
                                + " estado = 'Pendiente'",
                        Map.of("id", programacion),
                        Integer.class);
        if (pendientes > 0) {
            throw new ErrorPlanificacion(
                    409,
                    "ruta-incompleta",
                    "Faltan %d jardines por cortar para cerrar el ciclo".formatted(pendientes));
        }
    }

    private List<Limpieza> limpiezas(long programacion) {
        return jdbc.query(
                """
                SELECT l.id, l.fecha, u.nombre autor FROM mod03_limpieza_corte l
                JOIN core_usuario u ON u.id = l.usuario_id
                WHERE l.programacion_id = :id ORDER BY l.fecha
                """,
                Map.of("id", programacion),
                (rs, n) ->
                        new Limpieza(
                                rs.getLong("id"),
                                rs.getObject("fecha", LocalDate.class),
                                rs.getString("autor")));
    }

    /** Los jardines de la ruta que cumplen el filtro (lo da el código), en su orden. */
    List<JardinRuta> jardines(String filtro, Map<String, ?> datos) {
        return jdbc.query(
                """
                SELECT a.id, 'J-' || lpad(a.id::text, 3, '0') codigo, a.nombre, r.zona,
                    r.estado, r.marcado_en, u.nombre marcado_por
                FROM mod03_ruta_corte r JOIN mod02_area_verde a ON a.id = r.area_verde_id
                LEFT JOIN core_usuario u ON u.id = r.marcado_por_id
                WHERE %s ORDER BY r.orden
                """
                        .formatted(filtro),
                datos,
                (rs, n) -> {
                    OffsetDateTime marcado = rs.getObject("marcado_en", OffsetDateTime.class);
                    return new JardinRuta(
                            rs.getLong("id"),
                            rs.getString("codigo"),
                            rs.getString("nombre"),
                            rs.getString("zona"),
                            rs.getString("estado"),
                            marcado == null
                                    ? null
                                    : marcado.atZoneSameInstant(LIMA).toOffsetDateTime(),
                            rs.getString("marcado_por"),
                            false);
                });
    }
}
