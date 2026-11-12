package pe.edu.pucp.areasverdes.mod03;

import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.areasverdes.mod01.servicios.AdjuntosService;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/**
 * Incidencias de MOD-03 (AV-274, RF-INC-001, HU-020 y CU-020): registro desde la app o desde la
 * oficina en nombre de otra persona, la bandeja, la ficha y las propias del reportante. La foto va
 * a los adjuntos de MOD-01 (entidad {@code incidencia}).
 */
@Service
public class Incidencias {

    static final Set<String> CANALES =
            Set.of("App", "Web", "Telefono", "WhatsApp", "Correo", "Presencial");

    /** Margen para relojes del móvil algo adelantados al aceptar la hora original del reporte. */
    static final Duration TOLERANCIA_RELOJ = Duration.ofMinutes(5);

    /** Quién la reporta: un usuario del sistema o una persona externa con nombre y contacto. */
    public record Reportante(
            Long usuarioId,
            @Size(max = 120, message = "Máximo 120 caracteres") String nombre,
            @Size(max = 120, message = "Máximo 120 caracteres") String contacto) {}

    public record NuevaIncidencia(
            @NotBlank(message = "Indica el canal") String canal,
            @NotBlank(message = "Indica el tipo") String tipo,
            @NotNull(message = "Indica quién la reporta") @Valid Reportante reportadoPor,
            @NotBlank(message = "Indica el espacio") String espacio,
            String ejemplar,
            @Size(max = 300, message = "Máximo 300 caracteres") String descripcion,
            OffsetDateTime reportadoEn) {

        /**
         * Sin la fecha original (antes de RF-T-012), igual que hasta ahora: se toma la del
         * servidor.
         */
        public NuevaIncidencia(
                String canal,
                String tipo,
                Reportante reportadoPor,
                String espacio,
                String ejemplar,
                String descripcion) {
            this(canal, tipo, reportadoPor, espacio, ejemplar, descripcion, null);
        }
    }

    /**
     * Fila de la bandeja y de «Mis incidencias». {@code responsable}: capataz o proveedor. {@code
     * nivel_plazo} (RN-019, AV-280): en las sencillas del personal propio sin atender,
     * «SuperaObjetivo» pasadas 24 h, «CercaMaximo» desde 40 h y «Vencida» pasadas 48 h; si no,
     * nulo.
     */
    public record Resumen(
            long id,
            String codigo,
            String tipo,
            boolean tipoSencilla,
            String espacio,
            String severidad,
            String via,
            String estado,
            String canal,
            OffsetDateTime fechaRegistro,
            OffsetDateTime fechaCierre,
            String responsable,
            Long minutosCierre,
            String nivelPlazo) {}

    public record Ejemplar(String codigo, String especie, Double alturaM) {}

    /** Una transición: «Registrada · Ana Q.», de la más reciente a la más antigua. */
    public record Evento(String texto, OffsetDateTime fechaHora) {}

    /** Foto de la evidencia (adjunto de MOD-01). */
    public record FotoEvidencia(long id, String url) {}

    /** Atendida: quién y cuándo, el encargo y la evidencia que dejó (AV-279). */
    public record Atencion(
            String atendidaPor,
            OffsetDateTime atendidaEn,
            String encargoCodigo,
            List<FotoEvidencia> evidencias) {}

    /** Cerrada: minutos del registro al cierre y a quién se avisó (AV-279). */
    public record Cierre(long minutos, String avisadoA) {}

    /** La ficha: la fila de la bandeja con la ubicación, el ejemplar, la foto y el historial. */
    public record Detalle(
            @JsonUnwrapped Resumen resumen,
            String zona,
            String descripcion,
            Ejemplar ejemplar,
            String reportadoPor,
            String impacto,
            String fotoUrl,
            Atencion atencion,
            Cierre cierre,
            List<Evento> historial,
            DerivacionTercerizada.Requerimiento requerimiento) {}

    /**
     * Las fotos de los reportes; todo otro adjunto de la incidencia es evidencia de la atención.
     */
    static final List<String> FOTOS_DE_REPORTE =
            List.of("Foto del reporte", "Foto de otro reporte");

    /** Grupos de estado de la bandeja (AV-279). */
    static final Map<String, List<String>> GRUPOS =
            Map.of(
                    "abiertas", List.of("Registrada", "Clasificada"),
                    "en_proceso",
                            List.of(
                                    "DerivadaInterna",
                                    "DerivadaTercerizada",
                                    "EnAtencion",
                                    "Atendida"),
                    "cerradas", List.of("Cerrada", "Descartada"));

    public record Espacio(String nombre, List<String> ejemplares) {}

    public record UsuarioCatalogo(long id, String nombre) {}

    /** Lo que necesita el formulario de registro (AV-285 y AV-282). */
    public record Catalogos(
            List<String> tipos,
            List<Espacio> espacios,
            List<UsuarioCatalogo> usuarios,
            String espacioSugerido) {}

    public record Paso(String estado, OffsetDateTime fechaHora) {}

    /** Incidencia abierta que parece la misma (Figma 391-8469). */
    public record PosibleDuplicado(
            long id, String codigo, String tipo, String ejemplar, OffsetDateTime fechaRegistro) {}

    public record Evidencia(String url) {}

    /** El seguimiento de una propia (Figma 391-8560). */
    public record Seguimiento(
            long id,
            String codigo,
            String tipo,
            String estado,
            Long minutosCierre,
            List<Paso> pasos,
            Evidencia evidencia) {}

    static final Map<String, String> TEXTO_ESTADO =
            Map.of(
                    "Registrada", "Registrada",
                    "Clasificada", "Clasificada",
                    "DerivadaInterna", "Derivada · interna",
                    "DerivadaTercerizada", "Derivada · tercerizada",
                    "EnAtencion", "En atención",
                    "Atendida", "Atendida",
                    "Cerrada", "Cerrada",
                    "Descartada", "Descartada");

    static final String RESUMEN =
            """
            SELECT i.id, i.codigo, t.nombre tipo, t.sencilla tipo_sencilla, a.nombre espacio,
                i.severidad, i.via, i.estado, i.canal, i.fecha_registro, i.fecha_cierre,
                coalesce(cp.nombres || ' ' || left(cp.apellidos, 1) || '.', pv.razon_social)
                    responsable,
                CAST(floor(extract(epoch FROM i.fecha_cierre - i.fecha_registro) / 60) AS bigint)
                    minutos_cierre,
                CASE WHEN t.sencilla AND coalesce(i.via, 'PersonalPropio') <> 'Tercerizada'
                        AND i.estado NOT IN ('Atendida', 'Cerrada', 'Descartada') THEN
                    CASE WHEN now() - i.fecha_registro > interval '48 hours' THEN 'Vencida'
                        WHEN now() - i.fecha_registro >= interval '40 hours' THEN 'CercaMaximo'
                        WHEN now() - i.fecha_registro > interval '24 hours' THEN 'SuperaObjetivo'
                    END
                END nivel_plazo
            FROM mod03_incidencia i
            JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
            JOIN mod02_area_verde a ON a.id = i.area_verde_id
            LEFT JOIN mod04_personal cp ON cp.id = i.capataz_id
            LEFT JOIN LATERAL (
                SELECT v.razon_social FROM mod03_requerimiento_incidencia ri
                JOIN mod05_proveedor v ON v.id = ri.proveedor_id
                WHERE ri.incidencia_id = i.id ORDER BY ri.fecha_hora DESC, ri.id DESC LIMIT 1
            ) pv ON i.via = 'Tercerizada'
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final AdjuntosService adjuntos;
    private final Avisos avisos;
    private final DerivacionTercerizada derivacion;

    public Incidencias(
            NamedParameterJdbcTemplate jdbc,
            AdjuntosService adjuntos,
            Avisos avisos,
            DerivacionTercerizada derivacion) {
        this.jdbc = jdbc;
        this.adjuntos = adjuntos;
        this.avisos = avisos;
        this.derivacion = derivacion;
    }

    public Catalogos catalogos(UsuarioPlanificacion usuario) {
        Acceso.registrarIncidencia(usuario);
        List<String> tipos =
                jdbc.queryForList(
                        "SELECT nombre FROM mod03_tipo_incidencia WHERE activo ORDER BY id",
                        Map.of(),
                        String.class);
        // Desde V67 cada ejemplar guarda su área de MOD-02 en area_verde_inventario_id (AV-274).
        List<Espacio> espacios =
                jdbc.query(
                        """
                        SELECT a.nombre, coalesce(array_agg(e.codigo ORDER BY e.codigo)
                            FILTER (WHERE e.codigo IS NOT NULL), '{}') ejemplares
                        FROM mod02_area_verde a
                        LEFT JOIN mod02_ejemplar e ON e.area_verde_inventario_id = a.id
                        WHERE a.activo GROUP BY a.id, a.nombre ORDER BY a.nombre
                        """,
                        (rs, n) ->
                                new Espacio(
                                        rs.getString("nombre"),
                                        List.of((String[]) rs.getArray("ejemplares").getArray())));
        List<UsuarioCatalogo> usuarios =
                jdbc.query(
                        "SELECT id, nombre FROM core_usuario WHERE activo ORDER BY nombre",
                        (rs, n) -> new UsuarioCatalogo(rs.getLong("id"), rs.getString("nombre")));
        return new Catalogos(tipos, espacios, usuarios, null);
    }

    /**
     * Registra la incidencia (Registrada) y devuelve su id. Con la misma {@code clave} de un envío
     * anterior (cola sin conexión) devuelve aquella sin crear otra. Desde la app la foto es
     * obligatoria (CU-020); desde la oficina, opcional.
     */
    @Transactional
    public long registrar(
            NuevaIncidencia nueva, MultipartFile foto, UUID clave, UsuarioPlanificacion usuario) {
        Acceso.registrarIncidencia(usuario);
        if (clave != null) {
            List<Long> previa =
                    jdbc.queryForList(
                            "SELECT id FROM mod03_incidencia WHERE clave_idempotencia = :c",
                            Map.of("c", clave),
                            Long.class);
            if (!previa.isEmpty()) {
                return previa.getFirst();
            }
        }
        if (!CANALES.contains(nueva.canal())) {
            throw new ErrorPlanificacion(400, "canal-invalido", "Ese canal no existe");
        }
        long tipo = idDe("mod03_tipo_incidencia", "nombre", nueva.tipo(), "tipo-invalido");
        long area = idDe("mod02_area_verde", "nombre", nueva.espacio(), "espacio-invalido");
        Long ejemplar =
                nueva.ejemplar() == null || nueva.ejemplar().isBlank()
                        ? null
                        : idDe("mod02_ejemplar", "codigo", nueva.ejemplar(), "ejemplar-invalido");
        Reportante r = nueva.reportadoPor();
        if (r.usuarioId() != null) {
            if (jdbc.queryForObject(
                            "SELECT count(*) FROM core_usuario WHERE id = :id AND activo",
                            Map.of("id", r.usuarioId()),
                            Integer.class)
                    == 0) {
                throw new ErrorPlanificacion(
                        400, "reportante-invalido", "No existe ese usuario del sistema");
            }
        } else if (r.nombre() == null || r.nombre().isBlank()) {
            throw new ErrorPlanificacion(
                    400, "reportante-requerido", "Escribe el nombre de quien la reporta");
        }
        // RF-T-012 (AV-226): un reporte guardado sin conexión conserva la hora en que se hizo.
        if (nueva.reportadoEn() != null
                && nueva.reportadoEn().isAfter(OffsetDateTime.now().plus(TOLERANCIA_RELOJ))) {
            throw new ErrorPlanificacion(
                    400, "fecha-futura", "La hora del reporte no puede ser posterior a la actual");
        }
        if ("App".equals(nueva.canal()) && (foto == null || foto.isEmpty())) {
            throw new ErrorPlanificacion(400, "foto-obligatoria", "Añade una foto");
        }
        var datos =
                new MapSqlParameterSource()
                        .addValue("tipo", tipo)
                        .addValue("area", area)
                        .addValue("ejemplar", ejemplar)
                        .addValue(
                                "descripcion",
                                nueva.descripcion() == null ? "" : nueva.descripcion().trim())
                        .addValue("canal", nueva.canal())
                        .addValue("reportante", r.usuarioId())
                        .addValue("nombre", r.usuarioId() == null ? r.nombre().trim() : null)
                        .addValue(
                                "contacto",
                                r.usuarioId() == null && r.contacto() != null
                                        ? r.contacto().trim()
                                        : null)
                        .addValue("usuario", usuario.id())
                        .addValue("clave", clave)
                        .addValue("reportado", nueva.reportadoEn());
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod03_incidencia (codigo, tipo_incidencia_id, area_verde_id,
                            ejemplar_id, descripcion, canal, reportante_usuario_id, reportante_nombre,
                            reportante_contacto, registrada_por_id, clave_idempotencia, fecha_registro)
                        VALUES ('INC-' || to_char(now() AT TIME ZONE 'America/Lima', 'YYYY') || '-'
                                || lpad(nextval('mod03_incidencia_numero_seq')::text, 3, '0'),
                            :tipo, :area, :ejemplar, :descripcion, :canal, :reportante, :nombre,
                            :contacto, :usuario, :clave,
                            coalesce(cast(:reportado AS timestamptz), now()))
                        RETURNING id
                        """,
                        datos,
                        Long.class);
        jdbc.update(
                "INSERT INTO mod03_historial_incidencia (incidencia_id, estado, usuario_id) VALUES"
                        + " (:id, 'Registrada', :usuario)",
                Map.of("id", id, "usuario", usuario.id()));
        if (foto != null && !foto.isEmpty()) {
            adjuntos.subir(
                    "incidencia",
                    id,
                    clave != null ? clave : UUID.randomUUID(),
                    "Foto del reporte",
                    foto,
                    comoSesion(usuario));
        }
        avisarAlJefe(id);
        return id;
    }

    /**
     * HU-020 escenario 2: las abiertas (salvo Cerrada y Descartada) del mismo tipo en el mismo
     * ejemplar o, sin ejemplar, en el mismo espacio.
     */
    public List<PosibleDuplicado> posiblesDuplicados(
            String tipo, String espacio, String ejemplar, UsuarioPlanificacion usuario) {
        Acceso.registrarIncidencia(usuario);
        boolean conEjemplar = ejemplar != null && !ejemplar.isBlank();
        return jdbc.query(
                """
                SELECT i.id, i.codigo, t.nombre tipo, e.codigo ejemplar, i.fecha_registro
                FROM mod03_incidencia i
                JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                JOIN mod02_area_verde a ON a.id = i.area_verde_id
                LEFT JOIN mod02_ejemplar e ON e.id = i.ejemplar_id
                WHERE i.estado NOT IN ('Cerrada', 'Descartada') AND t.nombre = :tipo
                  AND (CASE WHEN :conEjemplar THEN e.codigo = :ejemplar ELSE a.nombre = :espacio END)
                ORDER BY i.fecha_registro DESC, i.id DESC
                """,
                new MapSqlParameterSource()
                        .addValue("tipo", tipo == null ? "" : tipo.trim())
                        .addValue("espacio", espacio == null ? "" : espacio.trim())
                        .addValue("ejemplar", conEjemplar ? ejemplar.trim() : "")
                        .addValue("conEjemplar", conEjemplar),
                (rs, n) ->
                        new PosibleDuplicado(
                                rs.getLong("id"),
                                rs.getString("codigo"),
                                rs.getString("tipo"),
                                rs.getString("ejemplar"),
                                instante(rs, "fecha_registro")));
    }

    /** «Añadir a la existente»: suma la descripción y la foto a una incidencia abierta. */
    @Transactional
    public void sumarReporte(
            long id, String descripcion, MultipartFile foto, UsuarioPlanificacion usuario) {
        Acceso.registrarIncidencia(usuario);
        List<String> estado =
                jdbc.queryForList(
                        "SELECT estado FROM mod03_incidencia WHERE id = :id FOR UPDATE",
                        Map.of("id", id),
                        String.class);
        if (estado.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "incidencia-no-encontrada", "No existe esa incidencia");
        }
        if (Set.of("Cerrada", "Descartada").contains(estado.getFirst())) {
            throw new ErrorPlanificacion(
                    409,
                    "incidencia-cerrada",
                    "Esa incidencia ya está cerrada: repórtala de nuevo");
        }
        String texto = descripcion == null ? "" : descripcion.trim();
        if (texto.length() > 300) {
            throw new ErrorPlanificacion(400, "descripcion-larga", "Máximo 300 caracteres");
        }
        jdbc.update(
                "INSERT INTO mod03_reporte_incidencia (incidencia_id, usuario_id, descripcion)"
                        + " VALUES (:id, :usuario, :descripcion)",
                Map.of("id", id, "usuario", usuario.id(), "descripcion", texto));
        if (foto != null && !foto.isEmpty()) {
            adjuntos.subir(
                    "incidencia",
                    id,
                    UUID.randomUUID(),
                    "Foto de otro reporte",
                    foto,
                    comoSesion(usuario));
        }
    }

    /** AV-281: cada jefe de sección activo recibe un aviso de la incidencia nueva. */
    private void avisarAlJefe(long id) {
        Map<String, Object> i =
                jdbc.queryForMap(
                        """
                        SELECT i.codigo, t.nombre tipo, a.nombre espacio FROM mod03_incidencia i
                        JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                        JOIN mod02_area_verde a ON a.id = i.area_verde_id WHERE i.id = :id
                        """,
                        Map.of("id", id));
        String texto =
                "%s registrada: %s en %s"
                        .formatted(i.get("codigo"), i.get("tipo"), i.get("espacio"));
        for (Long jefe :
                jdbc.queryForList(
                        "SELECT u.id FROM core_usuario u JOIN core_rol r ON r.id = u.rol_id WHERE"
                                + " u.activo AND r.codigo = 'jefe-seccion'",
                        Map.of(),
                        Long.class)) {
            avisos.aUsuario(jefe, texto, "incidencia", id);
        }
    }

    /**
     * La bandeja de la oficina, de la más reciente a la más antigua. {@code grupo}: abiertas, en
     * proceso o cerradas (AV-279); sin él, todas.
     */
    public List<Resumen> bandeja(String grupo, UsuarioPlanificacion usuario) {
        Acceso.consultarIncidencias(usuario);
        derivacion.sincronizar();
        if (grupo != null && !GRUPOS.containsKey(grupo)) {
            throw new ErrorPlanificacion(
                    400, "grupo-invalido", "El grupo es abiertas, en_proceso o cerradas");
        }
        return jdbc.query(
                RESUMEN
                        + " WHERE (CAST(:todas AS boolean) OR i.estado IN (:estados))"
                        + " ORDER BY i.fecha_registro DESC, i.id DESC",
                new MapSqlParameterSource()
                        .addValue("todas", grupo == null)
                        .addValue("estados", grupo == null ? List.of("") : GRUPOS.get(grupo)),
                Incidencias::resumen);
    }

    /** Las que reportó el usuario (HU-024). */
    public List<Resumen> mias(UsuarioPlanificacion usuario) {
        Acceso.registrarIncidencia(usuario);
        derivacion.sincronizar();
        return jdbc.query(
                RESUMEN
                        + " WHERE i.reportante_usuario_id = :usuario"
                        + " ORDER BY i.fecha_registro DESC, i.id DESC",
                Map.of("usuario", usuario.id()),
                Incidencias::resumen);
    }

    public Detalle detalle(long id, UsuarioPlanificacion usuario) {
        Acceso.consultarIncidencias(usuario);
        derivacion.sincronizar();
        return ficha(id);
    }

    /** La ficha recién registrada, para quien la registró (aunque no gestione la bandeja). */
    Detalle ficha(long id) {
        List<Detalle> fichas =
                jdbc.query(
                        """
                        SELECT r.*, s.nombre zona, i.descripcion, e.codigo ejemplar_codigo,
                            coalesce(ev.nombre_comun, ev.nombre_cientifico) especie,
                            coalesce(u.nombre, i.reportante_nombre) reportado_por, i.impacto
                        FROM (%s WHERE i.id = :id) r
                        JOIN mod03_incidencia i ON i.id = r.id
                        LEFT JOIN mod02_historial_area_sector h ON h.area_verde_id = i.area_verde_id
                            AND h.fecha_fin IS NULL
                        LEFT JOIN mod02_sector s ON s.id = h.sector_id
                        LEFT JOIN mod02_ejemplar e ON e.id = i.ejemplar_id
                        LEFT JOIN mod02_especie_vegetal ev ON ev.id = e.especie_id
                        LEFT JOIN core_usuario u ON u.id = i.reportante_usuario_id
                        """
                                .formatted(RESUMEN),
                        Map.of("id", id),
                        (rs, n) ->
                                new Detalle(
                                        resumen(rs, n),
                                        rs.getString("zona"),
                                        rs.getString("descripcion"),
                                        rs.getString("ejemplar_codigo") == null
                                                ? null
                                                : new Ejemplar(
                                                        rs.getString("ejemplar_codigo"),
                                                        rs.getString("especie"),
                                                        null),
                                        rs.getString("reportado_por"),
                                        rs.getString("impacto"),
                                        fotoUrl(id),
                                        atencion(id, rs.getString("estado")),
                                        cierre(id, rs.getString("estado"), rs),
                                        historial(id),
                                        derivacion.requerimiento(id)));
        if (fichas.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "incidencia-no-encontrada", "No existe esa incidencia");
        }
        return fichas.getFirst();
    }

    /** El seguimiento de una que reportó el usuario; 404 si no es suya. */
    public Seguimiento seguimiento(long id, UsuarioPlanificacion usuario) {
        Acceso.registrarIncidencia(usuario);
        derivacion.sincronizar();
        List<Seguimiento> lista =
                jdbc.query(
                        """
                        SELECT i.id, i.codigo, t.nombre tipo, i.estado, i.fecha_registro,
                            i.fecha_cierre
                        FROM mod03_incidencia i
                        JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                        WHERE i.id = :id AND i.reportante_usuario_id = :usuario
                        """,
                        Map.of("id", id, "usuario", usuario.id()),
                        (rs, n) -> {
                            OffsetDateTime registro = instante(rs, "fecha_registro");
                            OffsetDateTime cierre = instante(rs, "fecha_cierre");
                            return new Seguimiento(
                                    rs.getLong("id"),
                                    rs.getString("codigo"),
                                    rs.getString("tipo"),
                                    rs.getString("estado"),
                                    cierre == null
                                            ? null
                                            : Duration.between(registro, cierre).toMinutes(),
                                    pasos(id),
                                    evidencias(id).stream()
                                            .findFirst()
                                            .map(e -> new Evidencia(e.url()))
                                            .orElse(null));
                        });
        if (lista.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "incidencia-no-encontrada", "No existe esa incidencia");
        }
        return lista.getFirst();
    }

    private List<Evento> historial(long id) {
        return jdbc.query(
                """
                SELECT h.estado, h.fecha_hora, coalesce(u.nombre, 'Sistema') autor, h.detalle
                FROM mod03_historial_incidencia h LEFT JOIN core_usuario u ON u.id = h.usuario_id
                WHERE h.incidencia_id = :id ORDER BY h.fecha_hora DESC, h.id DESC
                """,
                Map.of("id", id),
                (rs, n) ->
                        new Evento(
                                TEXTO_ESTADO.getOrDefault(
                                                rs.getString("estado"), rs.getString("estado"))
                                        + " · "
                                        + rs.getString("autor")
                                        + (rs.getString("detalle") == null
                                                ? ""
                                                : " (" + rs.getString("detalle") + ")"),
                                instante(rs, "fecha_hora")));
    }

    private List<Paso> pasos(long id) {
        return jdbc.query(
                """
                SELECT estado, fecha_hora FROM mod03_historial_incidencia
                WHERE incidencia_id = :id ORDER BY fecha_hora DESC, id DESC
                """,
                Map.of("id", id),
                (rs, n) -> new Paso(rs.getString("estado"), instante(rs, "fecha_hora")));
    }

    private String fotoUrl(long id) {
        List<Long> fotos =
                jdbc.queryForList(
                        "SELECT id FROM core_adjunto WHERE entidad = 'incidencia' AND registro_id ="
                                + " :id AND descripcion IN (:reporte) ORDER BY id LIMIT 1",
                        Map.of("id", id, "reporte", FOTOS_DE_REPORTE),
                        Long.class);
        return fotos.isEmpty() ? null : url(fotos.getFirst());
    }

    private static String url(long adjunto) {
        return "/api/v1/core/adjuntos/" + adjunto + "/contenido/";
    }

    /** La evidencia de la atención: los adjuntos que no son fotos de un reporte. */
    List<FotoEvidencia> evidencias(long id) {
        return jdbc.query(
                """
                SELECT id FROM core_adjunto WHERE entidad = 'incidencia' AND registro_id = :id
                  AND (descripcion IS NULL OR descripcion NOT IN (:reporte))
                ORDER BY id
                """,
                Map.of("id", id, "reporte", FOTOS_DE_REPORTE),
                (rs, n) -> new FotoEvidencia(rs.getLong("id"), url(rs.getLong("id"))));
    }

    /** Quién la atendió y cuándo (la última transición a Atendida), si está Atendida o Cerrada. */
    private Atencion atencion(long id, String estado) {
        if (!Set.of("Atendida", "Cerrada").contains(estado)) {
            return null;
        }
        return jdbc
                .query(
                        """
                        SELECT coalesce(u.nombre, 'Sistema') autor, h.fecha_hora
                        FROM mod03_historial_incidencia h LEFT JOIN core_usuario u ON u.id = h.usuario_id
                        WHERE h.incidencia_id = :id AND h.estado = 'Atendida'
                        ORDER BY h.fecha_hora DESC, h.id DESC LIMIT 1
                        """,
                        Map.of("id", id),
                        (rs, n) ->
                                new Atencion(
                                        rs.getString("autor"),
                                        instante(rs, "fecha_hora"),
                                        null,
                                        evidencias(id)))
                .stream()
                .findFirst()
                .orElse(new Atencion(null, null, null, evidencias(id)));
    }

    private static Cierre cierre(long id, String estado, ResultSet rs) throws SQLException {
        Long minutos = rs.getObject("minutos_cierre", Long.class);
        if (!"Cerrada".equals(estado) || minutos == null) {
            return null;
        }
        return new Cierre(minutos, rs.getString("reportado_por"));
    }

    /** El id de la fila activa con ese valor; 400 con el código dado si no la hay. */
    private long idDe(String tabla, String columna, String valor, String codigo) {
        String activo = "mod02_ejemplar".equals(tabla) ? "" : " AND activo";
        List<Long> ids =
                jdbc.queryForList(
                        "SELECT id FROM "
                                + tabla
                                + " WHERE "
                                + columna
                                + " = :v"
                                + activo
                                + " ORDER BY id LIMIT 1",
                        Map.of("v", valor.trim()),
                        Long.class);
        if (ids.isEmpty()) {
            throw new ErrorPlanificacion(400, codigo, "No existe «" + valor.trim() + "»");
        }
        return ids.getFirst();
    }

    /** Los adjuntos de MOD-01 piden el usuario de la sesión: el mismo id y sus permisos. */
    private static UsuarioSesion comoSesion(UsuarioPlanificacion u) {
        return new UsuarioSesion(u.id(), "", u.nombre(), "", "", u.permisos(), false, 0L);
    }

    static Resumen resumen(ResultSet rs, int n) throws SQLException {
        return new Resumen(
                rs.getLong("id"),
                rs.getString("codigo"),
                rs.getString("tipo"),
                rs.getBoolean("tipo_sencilla"),
                rs.getString("espacio"),
                rs.getString("severidad"),
                rs.getString("via"),
                rs.getString("estado"),
                rs.getString("canal"),
                instante(rs, "fecha_registro"),
                instante(rs, "fecha_cierre"),
                rs.getString("responsable"),
                rs.getObject("minutos_cierre", Long.class),
                rs.getString("nivel_plazo"));
    }

    static OffsetDateTime instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.atZoneSameInstant(LIMA).toOffsetDateTime();
    }
}
