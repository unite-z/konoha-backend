package pe.edu.pucp.areasverdes.mod02.servicios;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.api.Pagina;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Registro de la poda por ejemplar con su evidencia (AV-309, RF-POD-002, HU-MOD02-022). */
@Service
public class IntervencionesPodaService {

    private static final String ENTIDAD = "mod02_intervencion_arbolado";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Verificable: tiene una fotografía en los adjuntos. Sin foto queda incompleta. */
    static final String TIENE_FOTO =
            """
            EXISTS (SELECT 1 FROM core_adjunto a JOIN core_archivo f ON f.id = a.archivo_id
                WHERE a.entidad = 'intervencion' AND a.registro_id = i.id
                AND f.tipo_mime LIKE 'image/%')
            """;

    private static final String CONSULTA =
            """
            SELECT i.id, i.ejemplar_id, e.codigo ejemplar_codigo, i.categoria, i.campana_id,
                i.atencion_id, i.incidencia_id, i.tipo_intervencion_id,
                t.nombre tipo_intervencion_nombre, i.fecha_ejecucion, i.reduccion_copa_pct,
                i.ramas_laterales_cortadas, i.cantidad_solicitada, i.cantidad_ejecutada,
                i.unidad_medida_id, u.simbolo unidad_medida_simbolo, i.prioridad,
                CASE WHEN %s THEN 'Verificada' ELSE 'Incompleta' END estado
            FROM mod02_intervencion_arbolado i
            JOIN mod02_ejemplar e ON e.id = i.ejemplar_id
            LEFT JOIN mod02_tipo_intervencion t ON t.id = i.tipo_intervencion_id
            LEFT JOIN mod05_unidadmedida u ON u.id = i.unidad_medida_id
            WHERE i.activo
            """
                    .formatted(TIENE_FOTO);

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;
    private final Clock reloj;

    public IntervencionesPodaService(
            NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria, Clock reloj) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public enum Prioridad {
        Alta,
        Media,
        Baja
    }

    public enum EstadoIntervencion {
        Verificada,
        Incompleta
    }

    public record NuevaIntervencion(
            @NotNull(message = "Indica el ejemplar") Long ejemplarId,
            Long campanaId,
            Long atencionId,
            Long incidenciaId,
            @NotNull(message = "Indica el tipo de poda") Long tipoIntervencionId,
            @NotNull(message = "Indica la fecha de ejecución") LocalDate fechaEjecucion,
            @DecimalMin(value = "0", message = "La reducción de copa va de 0 a 100")
                    @DecimalMax(value = "100", message = "La reducción de copa va de 0 a 100")
                    BigDecimal reduccionCopaPct,
            @PositiveOrZero(message = "Las ramas cortadas no pueden ser negativas")
                    Integer ramasLateralesCortadas,
            @DecimalMin(value = "0", message = "La cantidad no puede ser negativa")
                    BigDecimal cantidadSolicitada,
            @DecimalMin(value = "0", message = "La cantidad no puede ser negativa")
                    BigDecimal cantidadEjecutada,
            Long unidadMedidaId,
            Prioridad prioridad) {}

    public record CambioIntervencion(
            Long tipoIntervencionId,
            LocalDate fechaEjecucion,
            Long incidenciaId,
            @DecimalMin(value = "0", message = "La reducción de copa va de 0 a 100")
                    @DecimalMax(value = "100", message = "La reducción de copa va de 0 a 100")
                    BigDecimal reduccionCopaPct,
            @PositiveOrZero(message = "Las ramas cortadas no pueden ser negativas")
                    Integer ramasLateralesCortadas,
            @DecimalMin(value = "0", message = "La cantidad no puede ser negativa")
                    BigDecimal cantidadSolicitada,
            @DecimalMin(value = "0", message = "La cantidad no puede ser negativa")
                    BigDecimal cantidadEjecutada,
            Long unidadMedidaId,
            Prioridad prioridad) {}

    public record Intervencion(
            long id,
            long ejemplarId,
            String ejemplarCodigo,
            String categoria,
            Long campanaId,
            Long atencionId,
            Long incidenciaId,
            Long tipoIntervencionId,
            String tipoIntervencionNombre,
            LocalDate fechaEjecucion,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal reduccionCopaPct,
            Integer ramasLateralesCortadas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cantidadSolicitada,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cantidadEjecutada,
            Long unidadMedidaId,
            String unidadMedidaSimbolo,
            Prioridad prioridad,
            EstadoIntervencion estado) {}

    public record OpcionCatalogo(long id, String nombre) {}

    public record CatalogosRegistro(List<OpcionCatalogo> tipos, List<OpcionCatalogo> unidades) {}

    public CatalogosRegistro catalogos(Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return new CatalogosRegistro(
                jdbc.query(
                        "SELECT id, nombre FROM mod02_tipo_intervencion"
                                + " WHERE activo AND es_poda ORDER BY nombre",
                        new DataClassRowMapper<>(OpcionCatalogo.class)),
                jdbc.query(
                        "SELECT id, nombre FROM mod05_unidadmedida ORDER BY nombre",
                        new DataClassRowMapper<>(OpcionCatalogo.class)));
    }

    public Pagina<Intervencion> listar(Long campanaId, Long ejemplarId, int page, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        if (page < 1) {
            throw new ErrorCoberturas(400, "pagina-invalida", "La página debe ser mayor que cero");
        }
        var filtros =
                new MapSqlParameterSource()
                        .addValue("campana", campanaId, Types.BIGINT)
                        .addValue("ejemplar", ejemplarId, Types.BIGINT)
                        .addValue("limite", Pagina.TAMANO)
                        .addValue("offset", (page - 1) * Pagina.TAMANO);
        String condicion =
                " AND (CAST(:campana AS bigint) IS NULL OR i.campana_id = :campana)"
                        + " AND (CAST(:ejemplar AS bigint) IS NULL OR i.ejemplar_id = :ejemplar)";
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod02_intervencion_arbolado i WHERE i.activo"
                                + condicion,
                        filtros,
                        Long.class);
        List<Intervencion> filas =
                jdbc.query(
                        CONSULTA
                                + condicion
                                + " ORDER BY i.fecha_ejecucion DESC, i.id DESC"
                                + " LIMIT :limite OFFSET :offset",
                        filtros,
                        new DataClassRowMapper<>(Intervencion.class));
        return Pagina.conTotal(filas, total, page);
    }

    /**
     * Incidencias del ejemplar, para indicar la de origen sin depender de los permisos de MOD-03.
     */
    public List<OpcionCatalogo> incidencias(long ejemplarId, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return jdbc.query(
                """
                SELECT id, codigo nombre FROM mod03_incidencia WHERE ejemplar_id = :ejemplar
                ORDER BY fecha_registro DESC LIMIT 100
                """,
                Map.of("ejemplar", ejemplarId),
                new DataClassRowMapper<>(OpcionCatalogo.class));
    }

    public Intervencion detalle(long id, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return buscar(id);
    }

    @Transactional
    public Intervencion crear(NuevaIntervencion entrada, Usuario usuario) {
        AccesoPoda.registrar(usuario);
        if ((entrada.campanaId() == null) == (entrada.atencionId() == null)) {
            throw new ErrorCoberturas(
                    400, "origen-obligatorio", "Indica la campaña o la atención de la poda");
        }
        validarFechaNoFutura(entrada.fechaEjecucion());
        if (entrada.campanaId() != null) {
            validarCampana(entrada.campanaId(), entrada.ejemplarId(), entrada.fechaEjecucion());
        } else {
            validarEjemplar(entrada.ejemplarId());
            validarAtencion(entrada.atencionId());
        }
        validarTipo(entrada.tipoIntervencionId());
        validarUnidad(
                entrada.cantidadSolicitada(),
                entrada.cantidadEjecutada(),
                entrada.unidadMedidaId());
        validarIncidencia(entrada.incidenciaId(), entrada.ejemplarId());
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod02_intervencion_arbolado (ejemplar_id, categoria, campana_id,
                            atencion_id, incidencia_id, tipo_intervencion_id, fecha_ejecucion,
                            reduccion_copa_pct, ramas_laterales_cortadas, cantidad_solicitada,
                            cantidad_ejecutada, unidad_medida_id, prioridad, usuario_creacion_id,
                            usuario_modificacion_id)
                        VALUES (:ejemplar, :categoria, :campana, :atencion, :incidencia, :tipo,
                            :fecha, :reduccion, :ramas, :solicitada, :ejecutada, :unidad, :prioridad,
                            :usuario, :usuario)
                        RETURNING id
                        """,
                        new MapSqlParameterSource()
                                .addValue("ejemplar", entrada.ejemplarId())
                                .addValue(
                                        "categoria",
                                        entrada.campanaId() != null
                                                ? "MayorCampana"
                                                : "AlturaDemanda")
                                .addValue("campana", entrada.campanaId(), Types.BIGINT)
                                .addValue("atencion", entrada.atencionId(), Types.BIGINT)
                                .addValue("incidencia", entrada.incidenciaId(), Types.BIGINT)
                                .addValue("tipo", entrada.tipoIntervencionId())
                                .addValue("fecha", entrada.fechaEjecucion())
                                .addValue("reduccion", entrada.reduccionCopaPct(), Types.NUMERIC)
                                .addValue("ramas", entrada.ramasLateralesCortadas(), Types.INTEGER)
                                .addValue("solicitada", entrada.cantidadSolicitada(), Types.NUMERIC)
                                .addValue("ejecutada", entrada.cantidadEjecutada(), Types.NUMERIC)
                                .addValue("unidad", entrada.unidadMedidaId(), Types.BIGINT)
                                .addValue(
                                        "prioridad",
                                        entrada.prioridad() == null
                                                ? null
                                                : entrada.prioridad().name(),
                                        Types.VARCHAR)
                                .addValue("usuario", usuario.id()),
                        Long.class);
        Intervencion creada = buscar(id);
        auditoria.registrarAlta(ENTIDAD, id, valores(creada), usuario.id());
        return creada;
    }

    @Transactional
    public Intervencion cambiar(long id, CambioIntervencion cambio, Usuario usuario) {
        AccesoPoda.registrar(usuario);
        if (cambio.tipoIntervencionId() == null
                && cambio.fechaEjecucion() == null
                && cambio.incidenciaId() == null
                && cambio.reduccionCopaPct() == null
                && cambio.ramasLateralesCortadas() == null
                && cambio.cantidadSolicitada() == null
                && cambio.cantidadEjecutada() == null
                && cambio.unidadMedidaId() == null
                && cambio.prioridad() == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica algún dato que quieras modificar");
        }
        Intervencion antes = buscarParaCambiar(id);
        LocalDate fecha =
                cambio.fechaEjecucion() == null ? antes.fechaEjecucion() : cambio.fechaEjecucion();
        validarFechaNoFutura(fecha);
        if (antes.campanaId() != null) {
            validarFechaEnPeriodo(antes.campanaId(), fecha);
        }
        if (cambio.tipoIntervencionId() != null) {
            validarTipo(cambio.tipoIntervencionId());
        }
        validarUnidad(
                primero(cambio.cantidadSolicitada(), antes.cantidadSolicitada()),
                primero(cambio.cantidadEjecutada(), antes.cantidadEjecutada()),
                primero(cambio.unidadMedidaId(), antes.unidadMedidaId()));
        validarIncidencia(cambio.incidenciaId(), antes.ejemplarId());
        jdbc.update(
                """
                UPDATE mod02_intervencion_arbolado SET
                    tipo_intervencion_id = :tipo, fecha_ejecucion = :fecha, incidencia_id = :incidencia,
                    reduccion_copa_pct = :reduccion, ramas_laterales_cortadas = :ramas,
                    cantidad_solicitada = :solicitada, cantidad_ejecutada = :ejecutada,
                    unidad_medida_id = :unidad, prioridad = :prioridad,
                    fecha_modificacion = now(), usuario_modificacion_id = :usuario
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue(
                                "tipo",
                                primero(cambio.tipoIntervencionId(), antes.tipoIntervencionId()),
                                Types.BIGINT)
                        .addValue("fecha", fecha)
                        .addValue(
                                "incidencia",
                                primero(cambio.incidenciaId(), antes.incidenciaId()),
                                Types.BIGINT)
                        .addValue(
                                "reduccion",
                                primero(cambio.reduccionCopaPct(), antes.reduccionCopaPct()),
                                Types.NUMERIC)
                        .addValue(
                                "ramas",
                                primero(
                                        cambio.ramasLateralesCortadas(),
                                        antes.ramasLateralesCortadas()),
                                Types.INTEGER)
                        .addValue(
                                "solicitada",
                                primero(cambio.cantidadSolicitada(), antes.cantidadSolicitada()),
                                Types.NUMERIC)
                        .addValue(
                                "ejecutada",
                                primero(cambio.cantidadEjecutada(), antes.cantidadEjecutada()),
                                Types.NUMERIC)
                        .addValue(
                                "unidad",
                                primero(cambio.unidadMedidaId(), antes.unidadMedidaId()),
                                Types.BIGINT)
                        .addValue(
                                "prioridad",
                                nombre(primero(cambio.prioridad(), antes.prioridad())),
                                Types.VARCHAR)
                        .addValue("usuario", usuario.id()));
        Intervencion despues = buscar(id);
        auditoria.registrarCambio(
                ENTIDAD, id, "Modificacion", valores(antes), valores(despues), null, usuario.id());
        return despues;
    }

    @Transactional
    public void anular(long id, Usuario usuario) {
        AccesoPoda.registrar(usuario);
        Intervencion antes = buscarParaCambiar(id);
        jdbc.update(
                """
                UPDATE mod02_intervencion_arbolado SET activo = false, fecha_modificacion = now(),
                    usuario_modificacion_id = :usuario
                WHERE id = :id
                """,
                Map.of("id", id, "usuario", usuario.id()));
        auditoria.registrarCambio(
                ENTIDAD,
                id,
                "BajaLogica",
                valores(antes),
                Map.of("activo", false),
                null,
                usuario.id());
    }

    private Intervencion buscar(long id) {
        return jdbc
                .query(
                        CONSULTA + " AND i.id = :id",
                        Map.of("id", id),
                        new DataClassRowMapper<>(Intervencion.class))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ErrorCoberturas(
                                        404,
                                        "intervencion-no-encontrada",
                                        "La intervención no existe"));
    }

    /** Bloquea la fila y exige que la campaña siga abierta: una finalizada ya no se corrige. */
    private Intervencion buscarParaCambiar(long id) {
        jdbc.query(
                "SELECT id FROM mod02_intervencion_arbolado WHERE id = :id FOR UPDATE",
                Map.of("id", id),
                (fila) -> {});
        Intervencion intervencion = buscar(id);
        if (intervencion.campanaId() != null
                && !"EnEjecucion".equals(estadoCampana(intervencion.campanaId()))) {
            throw new ErrorCoberturas(
                    409,
                    "campana-no-en-ejecucion",
                    "Solo se registra o corrige la poda de una campaña en ejecución");
        }
        return intervencion;
    }

    private String estadoCampana(long campanaId) {
        List<String> estados =
                jdbc.queryForList(
                        "SELECT estado FROM mod02_campana_poda WHERE id = :id AND activo",
                        Map.of("id", campanaId),
                        String.class);
        if (estados.isEmpty()) {
            throw new ErrorCoberturas(404, "campana-no-encontrada", "La campaña no existe");
        }
        return estados.get(0);
    }

    private void validarCampana(long campanaId, long ejemplarId, LocalDate fecha) {
        if (!"EnEjecucion".equals(estadoCampana(campanaId))) {
            throw new ErrorCoberturas(
                    409,
                    "campana-no-en-ejecucion",
                    "Solo se registra o corrige la poda de una campaña en ejecución");
        }
        validarEjemplar(ejemplarId);
        Boolean enLista =
                jdbc.queryForObject(
                        """
                        SELECT EXISTS (SELECT 1 FROM mod02_campana_ejemplar
                            WHERE campana_id = :campana AND ejemplar_id = :ejemplar)
                        """,
                        Map.of("campana", campanaId, "ejemplar", ejemplarId),
                        Boolean.class);
        if (!Boolean.TRUE.equals(enLista)) {
            throw new ErrorCoberturas(
                    400,
                    "ejemplar-fuera-de-campana",
                    "El ejemplar no está en la lista de la campaña");
        }
        validarFechaEnPeriodo(campanaId, fecha);
    }

    private void validarFechaEnPeriodo(long campanaId, LocalDate fecha) {
        Boolean dentro =
                jdbc.queryForObject(
                        """
                        SELECT :fecha BETWEEN fecha_inicio AND fecha_fin
                        FROM mod02_campana_poda WHERE id = :campana
                        """,
                        new MapSqlParameterSource()
                                .addValue("fecha", fecha)
                                .addValue("campana", campanaId),
                        Boolean.class);
        if (!Boolean.TRUE.equals(dentro)) {
            throw new ErrorCoberturas(
                    400,
                    "fecha-fuera-de-periodo",
                    "La fecha debe estar dentro del periodo de la campaña");
        }
    }

    private void validarFechaNoFutura(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now(reloj.withZone(LIMA)))) {
            throw new ErrorCoberturas(
                    400, "fecha-futura", "La fecha de ejecución no puede ser futura");
        }
    }

    private void validarEjemplar(long ejemplarId) {
        if (!existe("SELECT EXISTS (SELECT 1 FROM mod02_ejemplar WHERE id = :id)", ejemplarId)) {
            throw new ErrorCoberturas(400, "ejemplar-inexistente", "El ejemplar no existe");
        }
    }

    private void validarAtencion(long atencionId) {
        if (!existe(
                "SELECT EXISTS (SELECT 1 FROM mod05_atenciontercerizada WHERE id = :id)",
                atencionId)) {
            throw new ErrorCoberturas(400, "atencion-inexistente", "La atención no existe");
        }
    }

    private void validarTipo(long tipoId) {
        if (!existe(
                "SELECT EXISTS (SELECT 1 FROM mod02_tipo_intervencion"
                        + " WHERE id = :id AND activo AND es_poda)",
                tipoId)) {
            throw new ErrorCoberturas(
                    400, "tipo-invalido", "El tipo de poda no existe o no está activo");
        }
    }

    private void validarUnidad(BigDecimal solicitada, BigDecimal ejecutada, Long unidadId) {
        if (unidadId == null) {
            if (solicitada != null || ejecutada != null) {
                throw new ErrorCoberturas(
                        400, "unidad-obligatoria", "Indica la unidad de las cantidades");
            }
            return;
        }
        if (!existe("SELECT EXISTS (SELECT 1 FROM mod05_unidadmedida WHERE id = :id)", unidadId)) {
            throw new ErrorCoberturas(400, "unidad-inexistente", "La unidad no existe");
        }
    }

    private void validarIncidencia(Long incidenciaId, long ejemplarId) {
        if (incidenciaId == null) {
            return;
        }
        List<Map<String, Object>> filas =
                jdbc.queryForList(
                        "SELECT ejemplar_id FROM mod03_incidencia WHERE id = :id",
                        Map.of("id", incidenciaId));
        if (filas.isEmpty()) {
            throw new ErrorCoberturas(400, "incidencia-inexistente", "La incidencia no existe");
        }
        Object delaIncidencia = filas.get(0).get("ejemplar_id");
        if (delaIncidencia != null && ((Number) delaIncidencia).longValue() != ejemplarId) {
            throw new ErrorCoberturas(
                    400,
                    "incidencia-de-otro-ejemplar",
                    "La incidencia corresponde a otro ejemplar");
        }
    }

    private boolean existe(String consulta, long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(consulta, Map.of("id", id), Boolean.class));
    }

    private static <T> T primero(T nuevo, T anterior) {
        return nuevo != null ? nuevo : anterior;
    }

    private static String nombre(Prioridad prioridad) {
        return prioridad == null ? null : prioridad.name();
    }

    private static Map<String, Object> valores(Intervencion i) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("ejemplar_id", i.ejemplarId());
        valores.put("campana_id", i.campanaId());
        valores.put("atencion_id", i.atencionId());
        valores.put("incidencia_id", i.incidenciaId());
        valores.put("tipo_intervencion_id", i.tipoIntervencionId());
        valores.put("fecha_ejecucion", String.valueOf(i.fechaEjecucion()));
        valores.put("reduccion_copa_pct", i.reduccionCopaPct());
        valores.put("ramas_laterales_cortadas", i.ramasLateralesCortadas());
        valores.put("cantidad_solicitada", i.cantidadSolicitada());
        valores.put("cantidad_ejecutada", i.cantidadEjecutada());
        valores.put("unidad_medida_id", i.unidadMedidaId());
        valores.put("prioridad", nombre(i.prioridad()));
        return valores;
    }
}
