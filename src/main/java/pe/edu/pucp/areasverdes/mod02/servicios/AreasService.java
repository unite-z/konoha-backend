package pe.edu.pucp.areasverdes.mod02.servicios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
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
import pe.edu.pucp.areasverdes.mod02.api.AreasController.SugerenciaArea;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class AreasService {

    private static final int TAMANO_PAGINA = 25;
    private static final String CONSULTA =
            "SELECT a.id, a.codigo, a.nombre, a.lugar, a.ubicacion, a.superficie_m2,"
                + " a.tipo_cobertura_id, c.nombre tipo_cobertura_nombre, a.tipo_uso_id, tu.nombre"
                + " tipo_uso_nombre, a.cuartel_id, q.numero cuartel_numero, h.sector_id, s.nombre"
                + " sector_nombre, a.activo, CAST(NULL AS uuid) poligono_mapa_id, CAST(NULL AS"
                + " text) contorno FROM mod02_area_verde a LEFT JOIN mod02_cobertura_vegetal c ON"
                + " c.id = a.tipo_cobertura_id LEFT JOIN mod02_tipo_uso tu ON tu.id ="
                + " a.tipo_uso_id LEFT JOIN mod02_cuartel q ON q.id = a.cuartel_id LEFT JOIN"
                + " mod02_historial_area_sector h ON h.area_verde_id = a.id AND h.fecha_fin IS NULL"
                + " LEFT JOIN mod02_sector s ON s.id = h.sector_id WHERE (:incluir_inactivas OR"
                + " a.activo) AND (:q = '' OR a.nombre ILIKE '%' || :q || '%') AND (CAST(:cuartel"
                + " AS bigint) IS NULL OR a.cuartel_id = :cuartel) AND (CAST(:sector AS bigint) IS"
                + " NULL OR h.sector_id = :sector) AND (CAST(:tipo_uso AS bigint) IS NULL OR"
                + " a.tipo_uso_id = :tipo_uso)";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final NamedParameterJdbcTemplate jdbc;

    private final MapaAreasService mapa;

    private final ContornoAreaService contornos;

    public AreasService(
            NamedParameterJdbcTemplate jdbc, MapaAreasService mapa, ContornoAreaService contornos) {
        this.jdbc = jdbc;
        this.mapa = mapa;
        this.contornos = contornos;
    }

    public PaginaAreas listar(String q, Long tipoUso, Long sector, Long cuartel, int pagina) {
        if (pagina < 1) {
            throw new ErrorCoberturas(400, "pagina-invalida", "La página debe ser mayor que cero");
        }
        String consulta = q == null || q.isBlank() ? "" : q.trim();
        int count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod02_area_verde a LEFT JOIN"
                            + " mod02_historial_area_sector h ON h.area_verde_id = a.id AND"
                            + " h.fecha_fin IS NULL WHERE a.activo AND (:q = '' OR a.nombre ILIKE"
                            + " '%' || :q || '%') AND (CAST(:tipo_uso AS bigint) IS NULL OR"
                            + " a.tipo_uso_id = :tipo_uso) AND (CAST(:sector AS bigint) IS NULL OR"
                            + " h.sector_id = :sector) AND (CAST(:cuartel AS bigint) IS NULL OR"
                            + " a.cuartel_id = :cuartel)",
                        new MapSqlParameterSource()
                                .addValue("q", consulta)
                                .addValue("tipo_uso", tipoUso)
                                .addValue("sector", sector)
                                .addValue("cuartel", cuartel),
                        Integer.class);
        List<AreaVerde> resultados =
                jdbc.query(
                        CONSULTA + " ORDER BY lower(a.nombre), a.id LIMIT :limite OFFSET :offset",
                        new MapSqlParameterSource()
                                .addValue("q", consulta)
                                .addValue("incluir_inactivas", false)
                                .addValue("tipo_uso", tipoUso)
                                .addValue("sector", sector)
                                .addValue("cuartel", cuartel)
                                .addValue("limite", TAMANO_PAGINA)
                                .addValue("offset", (pagina - 1) * TAMANO_PAGINA),
                        new DataClassRowMapper<>(AreaVerde.class));
        Integer anterior = pagina > 1 ? pagina - 1 : null;
        Integer siguiente = pagina * TAMANO_PAGINA < count ? pagina + 1 : null;
        return new PaginaAreas(count, siguiente, anterior, resultados);
    }

    public AreaVerde obtenerPorId(long id) {
        return obtener(id);
    }

    /** El código que tocaría es orientativo: se asigna de verdad al guardar. */
    public SugerenciaArea sugerir(String contorno, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        String geojson = geometriaGeoJson(contorno);
        BigDecimal superficie;
        try {
            superficie =
                    jdbc.queryForObject(
                            "SELECT CASE WHEN GeometryType(g) <> 'POLYGON' THEN NULL WHEN NOT"
                                + " ST_IsValid(g) THEN -1 ELSE ST_Area(ST_Transform(g, 32718)) END"
                                + " FROM (SELECT"
                                + " ST_SetSRID(ST_Force2D(ST_GeomFromGeoJSON(:contorno)), 4326) g)"
                                + " c",
                            Map.of("contorno", geojson),
                            BigDecimal.class);
        } catch (DataAccessResourceFailureException | TransientDataAccessException e) {
            throw e;
        } catch (DataAccessException e) {
            // PostGIS rechaza el GeoJSON o las coordenadas (fuera de EPSG:4326).
            throw contornoNoValido();
        }
        if (superficie == null) {
            throw contornoNoValido();
        }
        if (superficie.signum() <= 0) {
            throw new ErrorCoberturas(
                    422, "contorno-no-valido", "El contorno no es un polígono simple y cerrado");
        }
        long numero =
                jdbc.queryForObject(
                        "SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END FROM"
                                + " mod02_area_verde_codigo_seq",
                        Map.of(),
                        Long.class);
        String codigo =
                jdbc.queryForObject(
                        "SELECT mod02_codigo_area_verde(:numero)",
                        Map.of("numero", numero),
                        String.class);
        String superficieTexto = superficie.setScale(2, RoundingMode.HALF_UP).toPlainString();
        // El sector es el del área activa con sector vigente más cercana al contorno. Las
        // candidatas salen del índice GiST (KNN con <->, en grados) y se ordenan después por la
        // distancia geodésica en metros: así no se calcula la distancia a todo el campus.
        return jdbc.query(
                "WITH contorno AS (SELECT ST_SetSRID(ST_Force2D(ST_GeomFromGeoJSON(:contorno)),"
                        + " 4326) g), candidatas AS (SELECT s.id, s.nombre, a.id area_id,"
                        + " g.geometria FROM mod02_mapa_geometria g JOIN mod02_mapa_poligono p"
                        + " ON p.id_fuente = g.id_fuente JOIN mod02_area_verde a ON a.id ="
                        + " p.area_verde_id AND a.activo JOIN mod02_historial_area_sector h ON"
                        + " h.area_verde_id = a.id AND h.fecha_fin IS NULL JOIN mod02_sector s"
                        + " ON s.id = h.sector_id AND s.activo WHERE NOT"
                        + " ST_IsEmpty(g.geometria) ORDER BY g.geometria <-> (SELECT g FROM"
                        + " contorno) LIMIT 10) SELECT c.id, c.nombre,"
                        + " ST_Distance(c.geometria::geography, (SELECT g FROM"
                        + " contorno)::geography) distancia FROM candidatas c ORDER BY"
                        + " distancia, c.area_id LIMIT 1",
                Map.of("contorno", geojson),
                rs -> {
                    if (!rs.next()) {
                        return new SugerenciaArea(codigo, superficieTexto, null, null, null);
                    }
                    BigDecimal distancia = rs.getBigDecimal(3);
                    return new SugerenciaArea(
                            codigo,
                            superficieTexto,
                            rs.getLong(1),
                            rs.getString(2),
                            distancia == null ? null : distancia.setScale(1, RoundingMode.HALF_UP));
                });
    }

    private static String geometriaGeoJson(String contorno) {
        try {
            JsonNode nodo = JSON.readTree(contorno);
            if (nodo != null && nodo.isObject() && nodo.hasNonNull("geometry")) {
                nodo = nodo.get("geometry");
            }
            if (nodo == null || !nodo.isObject() || !"Polygon".equals(nodo.path("type").asText())) {
                throw contornoNoValido();
            }
            return nodo.toString();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw contornoNoValido();
        }
    }

    private static ErrorCoberturas contornoNoValido() {
        return new ErrorCoberturas(
                400, "contorno-no-valido", "El contorno debe ser un polígono GeoJSON (EPSG:4326)");
    }

    @Transactional
    public AreaVerde crear(NuevaAreaVerde nueva, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        // «"contorno": null» llega como NullNode: equivale a no enviarlo.
        boolean conContorno = nueva.contorno() != null && !nueva.contorno().isNull();
        if (conContorno) {
            // Dibujar un contorno crea y vincula una geometría del mapa: exige el mismo permiso
            // que vincular un polígono existente (MapaAreasService.vincularPoligonoLibre).
            AccesoCoberturas.gestionar(usuario);
        }
        validarCobertura(nueva.tipoCoberturaId());
        validarTipoUso(nueva.tipoUsoId());
        validarReferencia("mod02_cuartel", nueva.cuartelId(), "estado", "cuartel", false);
        validarReferencia("mod02_sector", nueva.sectorId(), "activo", "sector", true);
        ContornoAreaService.ContornoValido contorno = null;
        if (conContorno) {
            if (nueva.poligonoMapaId() != null) {
                throw new ErrorCoberturas(
                        422,
                        "contorno-y-poligono",
                        "Indica el contorno dibujado o un polígono existente, no los dos");
            }
            contorno = contornos.validar(nueva.contorno());
        } else if (nueva.superficieM2() == null) {
            throw new ErrorCoberturas(400, "superficie-obligatoria", "Indica la superficie");
        }
        MapSqlParameterSource parametros =
                new MapSqlParameterSource()
                        .addValue("nombre", nueva.nombre().trim())
                        .addValue("lugar", nueva.lugar().trim())
                        .addValue("ubicacion", nueva.ubicacion().trim())
                        .addValue(
                                "superficie",
                                contorno != null ? contorno.superficieM2() : nueva.superficieM2())
                        .addValue("cobertura", nueva.tipoCoberturaId())
                        .addValue("tipoUso", nueva.tipoUsoId())
                        .addValue("cuartel", nueva.cuartelId())
                        .addValue("usuario", usuario.id());
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,"
                            + " tipo_cobertura_id, tipo_uso_id, cuartel_id, usuario_creacion_id,"
                            + " usuario_modificacion_id) VALUES (:nombre, :lugar, :ubicacion,"
                            + " :superficie, :cobertura, :tipoUso, :cuartel, :usuario, :usuario)"
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
        UUID idFuente = nueva.poligonoMapaId();
        if (idFuente != null) {
            mapa.vincularPoligonoLibre(idFuente, id, usuario);
        } else if (contorno != null) {
            idFuente = contornos.guardar(contorno, id, usuario);
        }
        if (idFuente == null) {
            return area;
        }
        // El mapa recibe el área y su contorno en una sola respuesta (AV-430).
        return new AreaVerde(
                area.id(),
                area.codigo(),
                area.nombre(),
                area.lugar(),
                area.ubicacion(),
                area.superficieM2(),
                area.tipoCoberturaId(),
                area.tipoCoberturaNombre(),
                area.tipoUsoId(),
                area.tipoUsoNombre(),
                area.cuartelId(),
                area.cuartelNumero(),
                area.sectorId(),
                area.sectorNombre(),
                area.activo(),
                idFuente,
                contornos.contorno(idFuente));
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
        validarTipoUso(cambio.tipoUsoId());
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
                    + " coalesce(:cobertura, tipo_cobertura_id), tipo_uso_id = coalesce(:tipoUso,"
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
                        .addValue("tipoUso", cambio.tipoUsoId())
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
        if (anteriores == null) {
            throw new ErrorCoberturas(
                    404, "area-no-encontrada", "No existe un área verde activa con ese id");
        }
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
        boolean existe =
                Boolean.TRUE.equals(
                        jdbc.query(
                                "SELECT EXISTS (SELECT 1 FROM mod02_area_verde WHERE id = :id)",
                                Map.of("id", areaId),
                                rs -> rs.next() && rs.getBoolean(1)));
        if (!existe) {
            throw new ErrorCoberturas(404, "area-no-encontrada", "No existe esa área verde");
        }
        return jdbc.query(
                "SELECT h.id, h.sector_id, s.nombre sector_nombre, h.fecha_inicio::text"
                        + " fecha_inicio, h.fecha_fin::text fecha_fin FROM"
                        + " mod02_historial_area_sector h JOIN mod02_sector s ON s.id = h.sector_id"
                        + " WHERE h.area_verde_id = :id ORDER BY h.fecha_inicio DESC, h.id DESC",
                Map.of("id", areaId),
                new DataClassRowMapper<>(AsignacionSector.class));
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

    private void validarTipoUso(Long tipoUsoId) {
        if (tipoUsoId == null) return;
        Boolean activo =
                jdbc.query(
                        "SELECT activo FROM mod02_tipo_uso WHERE id = :id",
                        Map.of("id", tipoUsoId),
                        rs -> rs.next() ? rs.getBoolean("activo") : null);
        if (activo == null || !activo) {
            throw new ErrorCoberturas(
                    404, "tipo-uso-no-encontrado", "El tipo de uso no existe o está inactivo");
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
                                .addValue("tipo_uso", null)
                                .addValue("sector", null)
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
