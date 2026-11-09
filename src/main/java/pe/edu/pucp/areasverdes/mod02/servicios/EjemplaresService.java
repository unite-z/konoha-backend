package pe.edu.pucp.areasverdes.mod02.servicios;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class EjemplaresService {

    private static final String CONSULTA =
            "SELECT e.id, e.codigo, e.referencia_catastro, e.codigo_catastro,"
                    + " e.codigo_placa_antigua, e.especie_id, s.nombre_comun especie_nombre_comun,"
                    + " s.nombre_cientifico especie_nombre_cientifico, s.forma_biologica"
                    + " especie_forma_biologica, e.tipo_vegetacion, e.ubicacion,"
                    + " ST_Y(e.ubicacion_geografica)::numeric(10, 7) latitud,"
                    + " ST_X(e.ubicacion_geografica)::numeric(10, 7) longitud, e.cantidad,"
                    + " e.area_verde_id, e.altura_total_m, e.situacion, e.fecha_situacion,"
                    + " (e.codigo_placa_antigua IS NOT NULL AND"
                    + " EXISTS (SELECT 1 FROM mod02_ejemplar otro WHERE otro.id <> e.id AND"
                    + " lower(otro.codigo_placa_antigua) = lower(e.codigo_placa_antigua)))"
                    + " advertencia_placa_repetida FROM mod02_ejemplar e LEFT JOIN"
                    + " mod02_especie_vegetal s ON s.id = e.especie_id";

    private final NamedParameterJdbcTemplate jdbc;

    public EjemplaresService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Ejemplar> listar(
            String q, String referencia, Long areaVerdeId, int page, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        if (page < 1) {
            throw new ErrorCoberturas(400, "pagina-invalida", "La página debe ser mayor que cero");
        }
        String termino = q == null ? "" : q.strip();
        String ref = referencia == null ? "" : referencia.strip();
        var parametros =
                new MapSqlParameterSource()
                        .addValue("q", termino)
                        .addValue("referencia", ref)
                        .addValue("area", areaVerdeId)
                        .addValue("limite", 25)
                        .addValue("offset", (page - 1) * 25);
        return jdbc.query(
                CONSULTA
                        + " WHERE (:q = '' OR e.codigo ILIKE '%' || :q || '%' OR"
                        + " e.referencia_catastro ILIKE '%' || :q || '%' OR"
                        + " e.codigo_placa_antigua ILIKE '%' || :q || '%' OR"
                        + " s.nombre_comun ILIKE '%' || :q || '%' OR"
                        + " s.nombre_cientifico ILIKE '%' || :q || '%')"
                        + " AND (:referencia = '' OR e.referencia_catastro = :referencia)"
                        + " AND (CAST(:area AS bigint) IS NULL OR e.area_verde_id = :area)"
                        + " ORDER BY e.id LIMIT :limite OFFSET :offset",
                parametros,
                new DataClassRowMapper<>(Ejemplar.class));
    }

    @Transactional
    public Ejemplar crear(NuevoEjemplar entrada, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        validarParCoordenadas(entrada.latitud(), entrada.longitud());
        validarEspecie(entrada.especieId());
        validarArea(entrada.areaVerdeId());
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod02_ejemplar (codigo_placa_antigua, especie_id,"
                                + " tipo_vegetacion, ubicacion, ubicacion_geografica, cantidad,"
                                + " altura_total_m, area_verde_id) VALUES (:placa, :especie,"
                                + " :vegetacion, :ubicacion, CASE WHEN CAST(:latitud AS numeric) IS"
                                + " NULL THEN NULL ELSE ST_SetSRID (ST_MakePoint(CAST(:longitud AS"
                                + " double precision), CAST(:latitud AS double precision)),"
                                + " 4326)::geometry(Point, 4326) END, :cantidad, :altura, :area)"
                                + " RETURNING id",
                        parametros(entrada).addValue("area", entrada.areaVerdeId()),
                        Long.class);
        return buscar(id);
    }

    @Transactional
    public Ejemplar cambiar(long id, CambioEjemplar cambio, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        if (cambio.especieId() == null
                && cambio.tipoVegetacion() == null
                && cambio.ubicacion() == null
                && cambio.latitud() == null
                && cambio.longitud() == null
                && cambio.cantidad() == null
                && cambio.alturaTotalM() == null
                && cambio.codigoPlacaAntigua() == null
                && cambio.areaVerdeId() == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica algún dato que quieras modificar");
        }
        boolean cambiaUbicacion = cambio.latitud() != null || cambio.longitud() != null;
        if (cambiaUbicacion
                && ((cambio.latitud() == null || cambio.longitud() == null)
                        && !tieneUbicacion(id))) {
            throw new ErrorCoberturas(
                    400,
                    "coordenadas-incompletas",
                    "Indica latitud y longitud para registrar la ubicación");
        }
        if (cambio.latitud() != null
                && cambio.longitud() != null
                && ((cambio.latitud().compareTo(BigDecimal.valueOf(-90)) < 0)
                        || (cambio.latitud().compareTo(BigDecimal.valueOf(90)) > 0)
                        || (cambio.longitud().compareTo(BigDecimal.valueOf(-180)) < 0)
                        || (cambio.longitud().compareTo(BigDecimal.valueOf(180)) > 0))) {
            throw new ErrorCoberturas(
                    400, "coordenadas-invalidas", "Las coordenadas están fuera de rango");
        }
        validarEspecie(cambio.especieId());
        validarArea(cambio.areaVerdeId());
        int filas =
                jdbc.update(
                        "UPDATE mod02_ejemplar SET especie_id = coalesce(:especie, especie_id),"
                                + " tipo_vegetacion = coalesce(:vegetacion, tipo_vegetacion),"
                                + " ubicacion = coalesce(:ubicacion, ubicacion),"
                                + " ubicacion_geografica = CASE WHEN :latitud IS NULL AND"
                                + " :longitud IS NULL THEN ubicacion_geografica ELSE"
                                + " ST_SetSRID(ST_MakePoint(coalesce(:longitud,"
                                + " ST_X(ubicacion_geografica)), coalesce(:latitud,"
                                + " ST_Y(ubicacion_geografica))), 4326)::geometry(Point, 4326) END,"
                                + " cantidad = coalesce(:cantidad, cantidad),"
                                + " altura_total_m = coalesce(:altura, altura_total_m),"
                                + " codigo_placa_antigua = coalesce(:placa, codigo_placa_antigua),"
                                + " area_verde_id = coalesce(:area, area_verde_id)"
                                + " WHERE id = :id",
                        parametros(cambio)
                                .addValue("area", cambio.areaVerdeId())
                                .addValue("id", id));
        if (filas == 0) {
            throw noExiste();
        }
        return buscar(id);
    }

    @Transactional
    public Ejemplar quitarPlacaAntigua(long id, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        int filas =
                jdbc.update(
                        "UPDATE mod02_ejemplar SET codigo_placa_antigua = NULL WHERE id = :id",
                        Map.of("id", id));
        if (filas == 0) {
            throw noExiste();
        }
        return buscar(id);
    }

    public List<Intervencion> historialActividades(long id, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        buscar(id);
        return jdbc.query(
                """
                SELECT a.id actividad_id,a.fecha_hora_actividad,coalesce(a.tipo_actividad_nombre,t.nombre) tipo_actividad,
                    a.observaciones,a.pendiente_vinculacion
                FROM mod04_actividad a
                JOIN mod04_tipo_actividad t ON t.id=a.tipo_actividad_id
                WHERE a.ejemplar_id=:id ORDER BY a.fecha_hora_actividad DESC,a.id DESC
                """,
                Map.of("id", id),
                new DataClassRowMapper<>(Intervencion.class));
    }

    @Transactional
    public Ejemplar cambiarSituacion(long id, CambioSituacion cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        boolean traslado = cambio.situacion() == Situacion.trasladado;
        String ubicacion = cambio.ubicacion() == null ? "" : cambio.ubicacion().strip();
        validarParCoordenadas(cambio.latitud(), cambio.longitud());
        if (traslado && ubicacion.isEmpty() && cambio.latitud() == null) {
            throw new ErrorCoberturas(
                    400, "destino-requerido", "Indica la nueva ubicación del ejemplar");
        }
        if (!traslado
                && (!ubicacion.isEmpty()
                        || cambio.latitud() != null
                        || cambio.areaVerdeId() != null)) {
            throw new ErrorCoberturas(
                    400,
                    "destino-no-aplicable",
                    "La nueva ubicación solo se registra en un traslado");
        }
        if (cambio.fecha().isAfter(LocalDate.now(ZoneId.of("America/Lima")))) {
            throw new ErrorCoberturas(
                    400, "fecha-futura", "La fecha del cambio no puede ser futura");
        }
        validarArea(cambio.areaVerdeId());
        var parametros =
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("situacion", cambio.situacion().name())
                        .addValue("fecha", cambio.fecha())
                        .addValue("motivo", cambio.motivo().strip())
                        .addValue("usuario", usuario.id())
                        .addValue("traslado", traslado)
                        .addValue("ubicacion", ubicacion.isEmpty() ? null : ubicacion)
                        .addValue("latitud", cambio.latitud())
                        .addValue("longitud", cambio.longitud())
                        .addValue("area", cambio.areaVerdeId());
        var filas =
                jdbc.query(
                        CONSULTA + " WHERE e.id = :id FOR UPDATE OF e",
                        parametros,
                        new DataClassRowMapper<>(Ejemplar.class));
        if (filas.isEmpty()) throw noExiste();
        Ejemplar anterior = filas.getFirst();
        if (anterior.fechaSituacion() != null
                && cambio.fecha().isBefore(anterior.fechaSituacion())) {
            throw new ErrorCoberturas(
                    409, "fecha-anterior", "La fecha es anterior al último cambio de situación");
        }
        int cambios =
                jdbc.update(
                        """
                        WITH destino AS (
                            SELECT CASE WHEN CAST(:latitud AS numeric) IS NOT NULL
                                THEN ST_SetSRID(ST_MakePoint(
                                    CAST(:longitud AS double precision), CAST(:latitud AS double precision)), 4326)
                                END AS punto
                        ), actualizado AS (
                            UPDATE mod02_ejemplar e
                            SET situacion = :situacion, fecha_situacion = :fecha,
                                ubicacion = CASE WHEN :traslado THEN :ubicacion ELSE e.ubicacion END,
                                ubicacion_geografica = CASE WHEN :traslado THEN d.punto ELSE e.ubicacion_geografica END,
                                area_verde_id = CASE WHEN :traslado THEN :area ELSE e.area_verde_id END
                            FROM destino d
                            WHERE e.id = :id AND (e.situacion <> :situacion OR (:traslado AND (
                                e.ubicacion IS DISTINCT FROM :ubicacion OR
                                e.ubicacion_geografica IS DISTINCT FROM d.punto OR
                                e.area_verde_id IS DISTINCT FROM :area)))
                            RETURNING e.*
                        )
                        INSERT INTO mod02_historial_situacion_ejemplar
                            (ejemplar_id, situacion_anterior, situacion_nueva, fecha, motivo, usuario_id,
                             ubicacion_anterior, ubicacion_nueva, punto_anterior, punto_nuevo,
                             area_anterior_id, area_nueva_id)
                        SELECT e.id, e.situacion, a.situacion, :fecha, :motivo, :usuario,
                               e.ubicacion, a.ubicacion, e.ubicacion_geografica, a.ubicacion_geografica,
                               e.area_verde_id, a.area_verde_id
                        FROM mod02_ejemplar e JOIN actualizado a ON a.id = e.id
                        """,
                        parametros);
        if (cambios == 0) {
            throw new ErrorCoberturas(
                    409, "sin-cambio-situacion", "El ejemplar ya tiene esa situación y ubicación");
        }
        return buscar(id);
    }

    public List<CambioRegistrado> historialSituacion(long id, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        exigirEjemplar(id);
        return jdbc.query(
                """
                SELECT h.id, h.situacion_anterior, h.situacion_nueva, h.fecha, h.fecha_registro,
                       h.motivo, h.usuario_id, u.nombre autor, h.ubicacion_anterior, h.ubicacion_nueva,
                       ST_Y(h.punto_anterior)::numeric(10, 7) latitud_anterior,
                       ST_X(h.punto_anterior)::numeric(10, 7) longitud_anterior,
                       ST_Y(h.punto_nuevo)::numeric(10, 7) latitud_nueva,
                       ST_X(h.punto_nuevo)::numeric(10, 7) longitud_nueva,
                       h.area_anterior_id, h.area_nueva_id
                FROM mod02_historial_situacion_ejemplar h
                JOIN core_usuario u ON u.id = h.usuario_id
                WHERE h.ejemplar_id = :id
                ORDER BY h.fecha DESC, h.id DESC
                """,
                Map.of("id", id),
                new DataClassRowMapper<>(CambioRegistrado.class));
    }

    public List<EventoHistorial> historialIntervenciones(
            long id, FuenteHistorial fuente, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        if (fuente == FuenteHistorial.fitosanitario) usuario.exigir(false);
        exigirEjemplar(id);
        String consulta =
                switch (fuente) {
                    case podas ->
                            """
                            SELECT a.id, a.fecha_hora_actividad momento, 'Poda menor' tipo,
                                   'Operación de campo' origen, 'actividad' entidad,
                                   coalesce(equipo.operarios, c.nombre) ejecutor, a.observaciones detalle
                            FROM mod04_actividad a
                            JOIN mod04_tipo_actividad t ON t.id = a.tipo_actividad_id
                            JOIN mod04_parte_diario parte ON parte.id = a.parte_id
                            JOIN mod04_cuadrilla c ON c.id = parte.cuadrilla_id
                            LEFT JOIN LATERAL (
                                SELECT string_agg(p.nombres || ' ' || p.apellidos, ', ' ORDER BY p.apellidos, p.nombres) operarios
                                FROM mod04_participacion_actividad pa
                                JOIN mod04_personal p ON p.id = pa.operario_id
                                WHERE pa.actividad_id = a.id
                            ) equipo ON true
                            WHERE a.ejemplar_id = :id AND lower(coalesce(a.tipo_actividad_nombre, t.nombre)) = 'poda menor'
                            UNION ALL
                            SELECT p.id, p.fecha_ejecucion::timestamp AT TIME ZONE 'America/Lima',
                                   CASE p.categoria WHEN 'Menor' THEN 'Poda menor'
                                       WHEN 'MayorCampana' THEN 'Poda mayor de campaña'
                                       ELSE 'Poda de altura a demanda' END,
                                   'Inventario', 'intervencion', proveedor.razon_social, NULL::text
                            FROM mod02_intervencion_arbolado p
                            LEFT JOIN mod05_atenciontercerizada a ON a.id = p.atencion_id
                            LEFT JOIN mod05_contrato contrato ON contrato.id = a.contrato_id
                            LEFT JOIN mod05_proveedor proveedor ON proveedor.id = contrato.proveedor_id
                            WHERE p.ejemplar_id = :id AND p.activo AND p.fecha_ejecucion IS NOT NULL
                            """;
                    case fitosanitario ->
                            """
                            SELECT a.id, a.fecha_hora_fin momento,
                                   'Aplicación fitosanitaria · ' || a.codigo tipo,
                                   'Servicios tercerizados' origen, 'atencion' entidad,
                                   proveedor.razon_social ejecutor, a.descripcion detalle
                            FROM mod05_atenciontercerizada a
                            JOIN mod05_tiposervicio t ON t.id = a.tipo_servicio_id
                            JOIN mod05_contrato contrato ON contrato.id = a.contrato_id
                            JOIN mod05_proveedor proveedor ON proveedor.id = contrato.proveedor_id
                            WHERE t.nombre = 'Control fitosanitario' AND a.fecha_hora_fin IS NOT NULL
                                AND EXISTS (
                                    SELECT 1 FROM mod05_alcanceatencion o
                                    WHERE o.atencion_id = a.id AND o.ejemplar_id = :id
                                )
                            """;
                    case incidencias ->
                            """
                            SELECT i.id, i.fecha_registro momento,
                                   i.codigo || ' · ' || t.nombre tipo, 'Incidencias' origen,
                                   'incidencia' entidad, NULL::text ejecutor, i.descripcion detalle
                            FROM mod03_incidencia i
                            JOIN mod03_tipo_incidencia t ON t.id = i.tipo_incidencia_id
                            WHERE i.ejemplar_id = :id
                            """;
                };
        return jdbc.query(
                "SELECT evento.id, (evento.momento AT TIME ZONE 'America/Lima')::date fecha,"
                        + " evento.momento, evento.tipo, evento.origen, evento.ejecutor,"
                        + " evento.detalle, evidencia.id evidencia_id, documento.id documento_id,"
                        + " documento.reporte_id FROM ("
                        + consulta
                        + ") evento "
                        + """
                        LEFT JOIN LATERAL (
                            SELECT adjunto.id FROM core_adjunto adjunto
                            WHERE adjunto.entidad = evento.entidad AND adjunto.registro_id = evento.id
                            ORDER BY adjunto.fecha_creacion DESC, adjunto.id DESC LIMIT 1
                        ) evidencia ON true
                        LEFT JOIN LATERAL (
                            SELECT archivo.id, reporte.id reporte_id
                            FROM mod05_reporteproveedor reporte
                            JOIN mod05_archivoasociado archivo ON archivo.reporte_id = reporte.id
                            LEFT JOIN mod05_alcanceatencion alcance ON alcance.id = archivo.alcance_id
                            WHERE evento.entidad = 'atencion' AND reporte.atencion_id = evento.id
                              AND (archivo.alcance_id IS NULL OR alcance.ejemplar_id = :id)
                            ORDER BY reporte.revision DESC, archivo.id DESC LIMIT 1
                        ) documento ON true
                        ORDER BY evento.momento DESC, evento.origen, evento.id DESC
                        """,
                Map.of("id", id),
                new DataClassRowMapper<>(EventoHistorial.class));
    }

    private Ejemplar buscar(long id) {
        return jdbc.queryForObject(
                CONSULTA + " WHERE e.id = :id",
                Map.of("id", id),
                new DataClassRowMapper<>(Ejemplar.class));
    }

    private void exigirEjemplar(long id) {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM mod02_ejemplar WHERE id = :id)",
                        Map.of("id", id),
                        Boolean.class))) {
            throw noExiste();
        }
    }

    private void validarArea(Long areaId) {
        if (areaId == null) return;
        Boolean activa =
                jdbc.query(
                        "SELECT activo FROM mod02_area_verde WHERE id = :id",
                        Map.of("id", areaId),
                        rs -> rs.next() ? rs.getBoolean(1) : null);
        if (!Boolean.TRUE.equals(activa)) {
            throw new ErrorCoberturas(
                    404, "area-no-encontrada", "El área no existe o está inactiva");
        }
    }

    private void validarEspecie(Long especieId) {
        if (especieId == null) return;
        Boolean activa =
                jdbc.query(
                        "SELECT activo FROM mod02_especie_vegetal WHERE id = :id",
                        Map.of("id", especieId),
                        rs -> rs.next() ? rs.getBoolean(1) : null);
        if (!Boolean.TRUE.equals(activa)) {
            throw new ErrorCoberturas(
                    404, "especie-no-encontrada", "La especie no existe o está inactiva");
        }
    }

    private boolean tieneUbicacion(long id) {
        return Boolean.TRUE.equals(
                jdbc.query(
                        "SELECT ubicacion_geografica IS NOT NULL FROM mod02_ejemplar WHERE id ="
                                + " :id",
                        Map.of("id", id),
                        rs -> rs.next() && rs.getBoolean(1)));
    }

    private static void validarParCoordenadas(BigDecimal latitud, BigDecimal longitud) {
        if ((latitud == null) != (longitud == null)) {
            throw new ErrorCoberturas(
                    400,
                    "coordenadas-incompletas",
                    "Indica latitud y longitud juntas, o deja ambas vacías");
        }
        if (latitud != null
                && (latitud.compareTo(BigDecimal.valueOf(-90)) < 0
                        || latitud.compareTo(BigDecimal.valueOf(90)) > 0
                        || longitud.compareTo(BigDecimal.valueOf(-180)) < 0
                        || longitud.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new ErrorCoberturas(
                    400, "coordenadas-invalidas", "Las coordenadas están fuera de rango");
        }
    }

    private static MapSqlParameterSource parametros(NuevoEjemplar entrada) {
        return new MapSqlParameterSource()
                .addValue("placa", entrada.codigoPlacaAntigua())
                .addValue("especie", entrada.especieId())
                .addValue("vegetacion", entrada.tipoVegetacion())
                .addValue("ubicacion", entrada.ubicacion())
                .addValue("latitud", entrada.latitud())
                .addValue("longitud", entrada.longitud())
                .addValue("cantidad", entrada.cantidad() == null ? 1 : entrada.cantidad())
                .addValue("altura", entrada.alturaTotalM());
    }

    private static MapSqlParameterSource parametros(CambioEjemplar cambio) {
        return new MapSqlParameterSource()
                .addValue("placa", cambio.codigoPlacaAntigua())
                .addValue("especie", cambio.especieId())
                .addValue("vegetacion", cambio.tipoVegetacion())
                .addValue("ubicacion", cambio.ubicacion())
                .addValue("latitud", cambio.latitud())
                .addValue("longitud", cambio.longitud())
                .addValue("cantidad", cambio.cantidad())
                .addValue("altura", cambio.alturaTotalM());
    }

    private ErrorCoberturas noExiste() {
        return new ErrorCoberturas(404, "ejemplar-no-encontrado", "No existe ese ejemplar");
    }

    public record Ejemplar(
            long id,
            String codigo,
            String referenciaCatastro,
            String codigoCatastro,
            String codigoPlacaAntigua,
            Long especieId,
            String especieNombreComun,
            String especieNombreCientifico,
            String especieFormaBiologica,
            String tipoVegetacion,
            String ubicacion,
            BigDecimal latitud,
            BigDecimal longitud,
            int cantidad,
            Long areaVerdeId,
            BigDecimal alturaTotalM,
            Situacion situacion,
            LocalDate fechaSituacion,
            boolean advertenciaPlacaRepetida) {}

    public enum Situacion {
        activo,
        retirado,
        muerto,
        reemplazado,
        trasladado
    }

    public record CambioSituacion(
            @NotNull Situacion situacion,
            @NotNull LocalDate fecha,
            @NotBlank @Size(max = 500) String motivo,
            @Size(max = 200) String ubicacion,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud,
            @Positive Long areaVerdeId) {}

    public record CambioRegistrado(
            long id,
            Situacion situacionAnterior,
            Situacion situacionNueva,
            LocalDate fecha,
            OffsetDateTime fechaRegistro,
            String motivo,
            long usuarioId,
            String autor,
            String ubicacionAnterior,
            String ubicacionNueva,
            BigDecimal latitudAnterior,
            BigDecimal longitudAnterior,
            BigDecimal latitudNueva,
            BigDecimal longitudNueva,
            Long areaAnteriorId,
            Long areaNuevaId) {}

    public record Intervencion(
            long actividadId,
            java.time.OffsetDateTime fechaHoraActividad,
            String tipoActividad,
            String observaciones,
            boolean pendienteVinculacion) {}

    public enum FuenteHistorial {
        podas,
        fitosanitario,
        incidencias
    }

    public record EventoHistorial(
            long id,
            LocalDate fecha,
            OffsetDateTime momento,
            String tipo,
            String origen,
            String ejecutor,
            String detalle,
            Long evidenciaId,
            Long documentoId,
            Long reporteId) {}

    public record NuevoEjemplar(
            String codigoPlacaAntigua,
            Long especieId,
            String tipoVegetacion,
            String ubicacion,
            BigDecimal latitud,
            BigDecimal longitud,
            Integer cantidad,
            BigDecimal alturaTotalM,
            Long areaVerdeId) {}

    public record CambioEjemplar(
            String codigoPlacaAntigua,
            Long especieId,
            String tipoVegetacion,
            String ubicacion,
            BigDecimal latitud,
            BigDecimal longitud,
            Integer cantidad,
            BigDecimal alturaTotalM,
            Long areaVerdeId) {}
}
