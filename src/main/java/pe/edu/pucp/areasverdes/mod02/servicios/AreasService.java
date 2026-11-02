package pe.edu.pucp.areasverdes.mod02.servicios;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.api.AreasController;
import pe.edu.pucp.areasverdes.mod02.api.AreasController.AreaVerde;
import pe.edu.pucp.areasverdes.mod02.api.AreasController.AsignacionSector;
import pe.edu.pucp.areasverdes.mod02.api.AreasController.NuevaAreaVerde;
import pe.edu.pucp.areasverdes.mod02.api.AreasController.PaginaAreas;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class AreasService {

    private static final int TAMANO_PAGINA = 25;
    private static final String CONSULTA =
            "SELECT a.id, a.nombre, a.lugar, a.ubicacion, a.superficie_m2, a.tipo_cobertura_id,"
                + " c.nombre tipo_cobertura_nombre, a.activo , a.tipo_uso_id, u.nombre"
                + " tipo_uso_nombre, a.cuartel_id, q.numero cuartel_numero, h.sector_id, s.nombre"
                + " sector_nombre FROM mod02_area_verde a LEFT JOIN mod02_cobertura_vegetal c ON"
                + " c.id = a.tipo_cobertura_id LEFT JOIN mod02_tipo_uso u ON u.id = a.tipo_uso_id"
                + " LEFT JOIN mod02_cuartel q ON q.id = a.cuartel_id LEFT JOIN"
                + " mod02_historial_area_sector h ON h.area_verde_id = a.id AND h.fecha_fin IS NULL"
                + " LEFT JOIN mod02_sector s ON s.id = h.sector_id WHERE (:incluir_inactivas OR"
                + " a.activo) AND (:q = '' OR a.nombre ILIKE '%' || :q || '%') AND (CAST(:sector AS"
                + " bigint) IS NULL OR h.sector_id = :sector) AND (CAST(:uso AS bigint) IS NULL OR"
                + " a.tipo_uso_id = :uso) AND (CAST(:cuartel AS bigint) IS NULL OR a.cuartel_id ="
                + " :cuartel)";

    private final NamedParameterJdbcTemplate jdbc;

    public AreasService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PaginaAreas listar(String q, Long sector, Long uso, Long cuartel, int pagina) {
        if (pagina < 1) {
            throw new ErrorCoberturas(400, "pagina-invalida", "La página debe ser mayor que cero");
        }
        String consulta = q == null || q.isBlank() ? "" : q.trim();
        int count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod02_area_verde a LEFT JOIN"
                            + " mod02_historial_area_sector h ON h.area_verde_id = a.id AND"
                            + " h.fecha_fin IS NULL WHERE a.activo AND (:q = '' OR a.nombre ILIKE"
                            + " '%' || :q || '%') AND (CAST(:sector AS bigint) IS NULL OR"
                            + " h.sector_id = :sector) AND (CAST(:uso AS bigint) IS NULL OR"
                            + " a.tipo_uso_id = :uso) AND (CAST(:cuartel AS bigint) IS NULL OR"
                            + " a.cuartel_id = :cuartel)",
                        new MapSqlParameterSource()
                                .addValue("q", consulta)
                                .addValue("sector", sector)
                                .addValue("uso", uso)
                                .addValue("cuartel", cuartel),
                        Integer.class);
        List<AreaVerde> resultados =
                jdbc.query(
                        CONSULTA + " ORDER BY lower(a.nombre), a.id LIMIT :limite OFFSET :offset",
                        new MapSqlParameterSource()
                                .addValue("q", consulta)
                                .addValue("incluir_inactivas", false)
                                .addValue("sector", sector)
                                .addValue("uso", uso)
                                .addValue("cuartel", cuartel)
                                .addValue("limite", TAMANO_PAGINA)
                                .addValue("offset", (pagina - 1) * TAMANO_PAGINA),
                        new DataClassRowMapper<>(AreaVerde.class));
        Integer anterior = pagina > 1 ? pagina - 1 : null;
        Integer siguiente = pagina * TAMANO_PAGINA < count ? pagina + 1 : null;
        return new PaginaAreas(count, siguiente, anterior, resultados);
    }

    @Transactional
    public AreaVerde crear(NuevaAreaVerde nueva, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        validarCobertura(nueva.tipoCoberturaId());
        validarReferencia("mod02_tipo_uso", nueva.tipoUsoId(), "activo", "tipo de uso", true);
        validarReferencia("mod02_cuartel", nueva.cuartelId(), "estado", "cuartel", false);
        validarReferencia("mod02_sector", nueva.sectorId(), "activo", "sector", true);
        MapSqlParameterSource parametros =
                new MapSqlParameterSource()
                        .addValue("nombre", nueva.nombre().trim())
                        .addValue("lugar", nueva.lugar().trim())
                        .addValue("ubicacion", nueva.ubicacion().trim())
                        .addValue("superficie", nueva.superficieM2())
                        .addValue("cobertura", nueva.tipoCoberturaId())
                        .addValue("uso", nueva.tipoUsoId())
                        .addValue("cuartel", nueva.cuartelId())
                        .addValue("usuario", usuario.id());
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                            + " tipo_cobertura_id, tipo_uso_id, cuartel_id, usuario_creacion_id,"
                            + " usuario_modificacion_id) VALUES (:nombre, :lugar, :ubicacion,"
                            + " :superficie, :cobertura, :uso, :cuartel, :usuario, :usuario)"
                            + " RETURNING id",
                        parametros,
                        Long.class);
        if (nueva.sectorId() != null) {
            jdbc.update(
                    "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, usuario_id)"
                            + " VALUES (:area, :sector, :usuario)",
                    Map.of("area", id, "sector", nueva.sectorId(), "usuario", usuario.id()));
        }
        AreaVerde area = obtener(id);
        jdbc.update(
                "INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id,"
                        + " fecha_hora, valores_anteriores, valores_nuevos, motivo) SELECT"
                        + " 'mod02_area_verde', a.id, 'Alta', :usuario, now(), '{}'::jsonb,"
                        + " to_jsonb(a), '' FROM mod02_area_verde a WHERE a.id = :id",
                Map.of("id", id, "usuario", usuario.id()));
        return area;
    }

    @Transactional
    public AreaVerde modificar(long id, AreasController.CambioAreaVerde cambio, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        if (cambio.nombre() == null
                && cambio.lugar() == null
                && cambio.ubicacion() == null
                && cambio.superficieM2() == null
                && cambio.activo() == null
                && cambio.tipoCoberturaId() == null
                && cambio.tipoUsoId() == null
                && cambio.cuartelId() == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica algún dato que quieras modificar");
        }
        validarCobertura(cambio.tipoCoberturaId());
        validarReferencia("mod02_tipo_uso", cambio.tipoUsoId(), "activo", "tipo de uso", true);
        validarReferencia("mod02_cuartel", cambio.cuartelId(), "estado", "cuartel", false);
        String anteriores =
                jdbc.query(
                        "SELECT to_jsonb(a)::text FROM mod02_area_verde a WHERE id = :id FOR"
                                + " UPDATE",
                        Map.of("id", id),
                        rs -> rs.next() ? rs.getString(1) : null);
        if (anteriores == null)
            throw new ErrorCoberturas(404, "area-no-encontrada", "No existe esa área verde");
        jdbc.update(
                "UPDATE mod02_area_verde SET nombre = coalesce(:nombre, nombre), lugar ="
                    + " coalesce(:lugar, lugar), ubicacion = coalesce(:ubicacion, ubicacion),"
                    + " superficie_m2 = coalesce(:superficie, superficie_m2), tipo_cobertura_id ="
                    + " coalesce(:cobertura, tipo_cobertura_id), tipo_uso_id = coalesce(:uso,"
                    + " tipo_uso_id), cuartel_id = coalesce(:cuartel, cuartel_id), activo ="
                    + " coalesce(:activo, activo), fecha_modificacion = now(),"
                    + " usuario_modificacion_id = :usuario WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("usuario", usuario.id())
                        .addValue("nombre", cambio.nombre() == null ? null : cambio.nombre().trim())
                        .addValue("lugar", cambio.lugar() == null ? null : cambio.lugar().trim())
                        .addValue(
                                "ubicacion",
                                cambio.ubicacion() == null ? null : cambio.ubicacion().trim())
                        .addValue("superficie", cambio.superficieM2())
                        .addValue("activo", cambio.activo())
                        .addValue("cobertura", cambio.tipoCoberturaId())
                        .addValue("uso", cambio.tipoUsoId())
                        .addValue("cuartel", cambio.cuartelId()));
        if (Boolean.FALSE.equals(cambio.activo())) {
            jdbc.update(
                    "UPDATE mod02_historial_area_sector SET fecha_fin = greatest(now(),"
                            + " fecha_inicio) WHERE area_verde_id = :id AND fecha_fin IS NULL",
                    Map.of("id", id));
        }
        auditarArea(id, "modificar", anteriores, usuario.id());
        return obtener(id);
    }

    @Transactional
    public AreaVerde asignarSector(long areaId, Long sectorId, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        String anteriores =
                jdbc.query(
                        "SELECT to_jsonb(a)::text FROM mod02_area_verde a WHERE id = :id AND activo"
                                + " FOR UPDATE",
                        Map.of("id", areaId),
                        rs -> rs.next() ? rs.getString(1) : null);
        if (anteriores == null)
            throw new ErrorCoberturas(
                    404, "area-no-encontrada", "No existe un área verde activa con ese id");
        validarReferencia("mod02_sector", sectorId, "activo", "sector", true);
        jdbc.update(
                "UPDATE mod02_historial_area_sector SET fecha_fin = greatest(now(), fecha_inicio) "
                        + "WHERE area_verde_id = :area AND fecha_fin IS NULL",
                Map.of("area", areaId));
        if (sectorId != null) {
            jdbc.update(
                    "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, usuario_id)"
                            + " VALUES (:area, :sector, :usuario)",
                    Map.of("area", areaId, "sector", sectorId, "usuario", usuario.id()));
        }
        auditarArea(areaId, "asignar-sector", anteriores, usuario.id());
        return obtener(areaId);
    }

    public List<AsignacionSector> historialSector(long areaId) {
        if (!existeArea(areaId)) {
            throw new ErrorCoberturas(404, "area-no-encontrada", "No existe esa área verde");
        }
        return jdbc.query(
                "SELECT h.id, h.sector_id, s.nombre sector_nombre, h.fecha_inicio::text"
                        + " fecha_inicio, h.fecha_fin::text fecha_fin FROM"
                        + " mod02_historial_area_sector h JOIN mod02_sector s ON s.id = h.sector_id"
                        + " WHERE h.area_verde_id = :id ORDER BY h.fecha_inicio DESC, h.id DESC",
                Map.of("id", areaId),
                (rs, row) ->
                        new AsignacionSector(
                                rs.getLong("id"),
                                rs.getLong("sector_id"),
                                rs.getString("sector_nombre"),
                                rs.getString("fecha_inicio"),
                                rs.getString("fecha_fin")));
    }

    private boolean existeArea(long id) {
        return Boolean.TRUE.equals(
                jdbc.query(
                        "SELECT EXISTS (SELECT 1 FROM mod02_area_verde WHERE id = :id)",
                        Map.of("id", id),
                        rs -> rs.next() && rs.getBoolean(1)));
    }

    private void validarCobertura(Long coberturaId) {
        if (coberturaId == null) return;
        Boolean activa =
                jdbc.query(
                        "SELECT activo FROM mod02_cobertura_vegetal WHERE id = :id",
                        Map.of("id", coberturaId),
                        rs -> rs.next() ? rs.getBoolean("activo") : null);
        if (activa == null || !activa) {
            throw new ErrorCoberturas(
                    404, "cobertura-no-encontrada", "La cobertura no existe o está inactiva");
        }
    }

    private void validarReferencia(
            String tabla, Long id, String campoEstado, String etiqueta, boolean trueEsActivo) {
        if (id == null) return;
        String valor =
                jdbc.query(
                        "SELECT " + campoEstado + "::text FROM " + tabla + " WHERE id = :id",
                        Map.of("id", id),
                        rs -> rs.next() ? rs.getString(1) : null);
        boolean valida = trueEsActivo ? "true".equals(valor) : "vigente".equals(valor);
        if (!valida) {
            throw new ErrorCoberturas(
                    404,
                    "referencia-no-encontrada",
                    "El " + etiqueta + " no existe o no está vigente");
        }
    }

    private AreaVerde obtener(long id) {
        return jdbc
                .query(
                        CONSULTA + " AND a.id = :id",
                        new MapSqlParameterSource()
                                .addValue("q", "")
                                .addValue("incluir_inactivas", true)
                                .addValue("sector", null)
                                .addValue("uso", null)
                                .addValue("cuartel", null)
                                .addValue("id", id),
                        new DataClassRowMapper<>(AreaVerde.class))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ErrorCoberturas(
                                        404, "area-no-encontrada", "No existe esa área verde"));
    }

    private void auditarArea(long id, String accion, String anteriores, long usuarioId) {
        jdbc.update(
                "INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id,"
                        + " fecha_hora, valores_anteriores, valores_nuevos, motivo) SELECT"
                        + " 'mod02_area_verde', a.id, 'Modificacion', :usuario, now(),"
                        + " CAST(:anteriores AS jsonb), to_jsonb(a), ''"
                        + " FROM mod02_area_verde a WHERE a.id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("accion", accion)
                        .addValue("anteriores", anteriores)
                        .addValue("usuario", usuarioId));
    }
}
