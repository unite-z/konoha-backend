package pe.edu.pucp.areasverdes.mod02.servicios;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.AreasVerdesApplication;
import pe.edu.pucp.areasverdes.mod02.api.ContornoAreaController.CambioContorno;
import pe.edu.pucp.areasverdes.mod02.api.ContornoAreaController.CambioContornoHistorial;
import pe.edu.pucp.areasverdes.mod02.api.ContornoAreaController.ContornoArea;
import pe.edu.pucp.areasverdes.mod02.api.ContornoAreaController.ImpactoContorno;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod02.api.MapaAreasController.EjemplarAfectado;
import pe.edu.pucp.areasverdes.mod02.servicios.ContornoAreaService.ContornoValido;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Redibuja el contorno de un área verde existente (AV-445).
 *
 * <p>Valida con {@link ContornoAreaService} (AV-430), sin contar el solape con el contorno vigente
 * de la propia área: polígono simple y cerrado en EPSG:4326, dentro del campus y sin solape con
 * otras áreas activas más allá de {@code mod02.contorno.tolerancia-solape-m2}.
 *
 * <p>La geometría vive en {@code mod02_mapa_geometria}. Un área sin contorno estrena uno con {@code
 * origen = 'dibujo'}, que {@code GET /inventario/mapa/geometria} ya lee vivo; si el polígono venía
 * de la capa de referencia, se sustituye también en {@code mod02_mapa_capa}.
 */
@Service
public class RedibujoContornoService {

    /** Metros cuadrados en UTM 18S, la zona del campus. */
    private static final int SRID_METRICO = 32718;

    private static final String NUEVO =
            "ST_Force2D(ST_SetSRID(ST_GeomFromGeoJSON(CAST(:contorno AS text)), 4326))";

    private final NamedParameterJdbcTemplate jdbc;
    private final GeometriaCampus geometria;
    private final ContornoAreaService contornos;
    private final ObjectMapper json;

    public RedibujoContornoService(
            NamedParameterJdbcTemplate jdbc,
            GeometriaCampus geometria,
            ContornoAreaService contornos,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.geometria = geometria;
        this.contornos = contornos;
        this.json = json;
    }

    private record Vigente(BigDecimal superficieRegistrada, List<UUID> poligonos) {}

    public ContornoArea consultar(long areaId, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        Vigente vigente = vigente(areaId);
        if (vigente.poligonos().isEmpty()) {
            return new ContornoArea(areaId, null, 0, null, null, vigente.superficieRegistrada());
        }
        var fila =
                jdbc.queryForMap(
                        """
                        SELECT ST_AsGeoJSON(ST_Union(g.geometria)) contorno,
                               ST_Area(ST_Transform(ST_Union(g.geometria), :srid)) superficie
                        FROM mod02_mapa_poligono p JOIN mod02_mapa_geometria g USING (id_fuente)
                        WHERE p.area_verde_id = :area
                        """,
                        Map.of("area", areaId, "srid", SRID_METRICO));
        return new ContornoArea(
                areaId,
                vigente.poligonos().size() == 1 ? vigente.poligonos().get(0) : null,
                vigente.poligonos().size(),
                leer((String) fila.get("contorno")),
                redondear((Number) fila.get("superficie")),
                vigente.superficieRegistrada());
    }

    /** Solo lectura: el mapa lo pide mientras se arrastran los vértices. */
    public ImpactoContorno impacto(long areaId, JsonNode contorno, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        Vigente vigente = vigente(areaId);
        exigirUnSoloContorno(vigente);
        ContornoValido valido = contornos.validar(contorno, areaId);
        return calcular(areaId, vigente, valido);
    }

    @Transactional
    public ContornoArea redibujar(long areaId, CambioContorno cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        geometria.bloquearCambios();
        Vigente vigente = vigente(areaId);
        exigirUnSoloContorno(vigente);
        ContornoValido valido = contornos.validar(cambio.contorno(), areaId);
        if (!vigente.poligonos().isEmpty() && igualAlVigente(vigente.poligonos().get(0), valido)) {
            // Reenviar el mismo contorno no cambia nada ni deja otra fila en el historial.
            return consultar(areaId, usuario);
        }
        ImpactoContorno impacto = calcular(areaId, vigente, valido);
        if (!impacto.conflictos().isEmpty()) {
            throw new ErrorCoberturas(
                    409,
                    "conflictos-cartograficos",
                    "El contorno invade ejemplares de otras áreas. Revisa el impacto antes de"
                            + " continuar.",
                    impacto);
        }
        var ids = impacto.reasignables().stream().map(EjemplarAfectado::id).toList();
        var confirmacion = cambio.confirmacion();
        if (!ids.isEmpty()
                && (confirmacion == null
                        || !Objects.equals(confirmacion.areaAnteriorId(), areaId)
                        || confirmacion.ejemplaresIds() == null
                        || !new HashSet<>(confirmacion.ejemplaresIds())
                                .equals(new HashSet<>(ids)))) {
            throw new ErrorCoberturas(
                    409,
                    "confirmar-impacto-contorno",
                    "Revisa y confirma los ejemplares afectados antes de redibujar el contorno.",
                    impacto);
        }
        String anteriores =
                jdbc.queryForObject(
                        "SELECT to_jsonb(a)::text FROM mod02_area_verde a WHERE a.id = :id",
                        Map.of("id", areaId),
                        String.class);
        boolean nuevo = vigente.poligonos().isEmpty();
        UUID fuente = nuevo ? UUID.randomUUID() : vigente.poligonos().get(0);
        var parametros =
                new MapSqlParameterSource()
                        .addValue("contorno", valido.geojson())
                        .addValue("fuente", fuente)
                        .addValue("area", areaId)
                        .addValue("usuario", usuario.id())
                        .addValue("superficie", valido.superficieM2())
                        .addValue("srid", SRID_METRICO);
        if (nuevo) {
            jdbc.update(
                    "INSERT INTO mod02_mapa_poligono (id_fuente, area_verde_id, fecha_modificacion,"
                            + " usuario_modificacion_id) VALUES (:fuente, :area, now(), :usuario)",
                    parametros);
        } else {
            jdbc.update(
                    "UPDATE mod02_mapa_poligono SET fecha_modificacion = now(),"
                            + " usuario_modificacion_id = :usuario WHERE id_fuente = :fuente",
                    parametros);
        }
        jdbc.update(
                "INSERT INTO mod02_mapa_geometria (id_fuente, geometria, origen) VALUES (:fuente, "
                        + NUEVO
                        + ", 'dibujo') ON CONFLICT (id_fuente) DO UPDATE SET geometria ="
                        + " EXCLUDED.geometria",
                parametros);
        if (!ids.isEmpty()) {
            jdbc.update(
                    "UPDATE mod02_ejemplar SET area_verde_inventario_id = NULL WHERE id IN (:ids)"
                            + " AND area_verde_inventario_id = :area",
                    new MapSqlParameterSource("ids", ids).addValue("area", areaId));
        }
        jdbc.update(
                "UPDATE mod02_area_verde SET superficie_m2 = :superficie, fecha_modificacion ="
                        + " now(), usuario_modificacion_id = :usuario WHERE id = :area",
                parametros);
        if (!nuevo) {
            actualizarCapa(parametros);
        }
        jdbc.update(
                "INSERT INTO mod02_historial_contorno_area (area_verde_id, id_fuente, usuario_id,"
                    + " superficie_anterior_m2, superficie_nueva_m2, ejemplares_desvinculados)"
                    + " VALUES (:area, :fuente, :usuario, :anterior, :superficie, :desvinculados)",
                parametros
                        .addValue("anterior", vigente.superficieRegistrada())
                        .addValue("desvinculados", ids.size()));
        jdbc.update(
                "INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id,"
                        + " fecha_hora, valores_anteriores, valores_nuevos, motivo) SELECT"
                        + " 'mod02_area_verde', a.id, 'Modificacion', :usuario, now(),"
                        + " CAST(:anteriores AS jsonb), to_jsonb(a), 'redibujar-contorno'"
                        + " FROM mod02_area_verde a WHERE a.id = :area",
                parametros.addValue("anteriores", anteriores));
        return consultar(areaId, usuario);
    }

    public List<CambioContornoHistorial> historial(long areaId, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        vigente(areaId);
        return jdbc.query(
                "SELECT h.id, h.fecha, h.usuario_id, u.nombre usuario_nombre,"
                        + " h.superficie_anterior_m2, h.superficie_nueva_m2,"
                        + " h.ejemplares_desvinculados FROM mod02_historial_contorno_area h"
                        + " LEFT JOIN core_usuario u ON u.id = h.usuario_id WHERE h.area_verde_id"
                        + " = :area ORDER BY h.fecha DESC, h.id DESC",
                Map.of("area", areaId),
                (rs, n) ->
                        new CambioContornoHistorial(
                                rs.getLong("id"),
                                rs.getObject("fecha", OffsetDateTime.class)
                                        .atZoneSameInstant(AreasVerdesApplication.LIMA)
                                        .toOffsetDateTime(),
                                rs.getObject("usuario_id", Long.class),
                                rs.getString("usuario_nombre"),
                                rs.getBigDecimal("superficie_anterior_m2"),
                                rs.getBigDecimal("superficie_nueva_m2"),
                                rs.getInt("ejemplares_desvinculados")));
    }

    private Vigente vigente(long areaId) {
        BigDecimal superficie =
                jdbc.query(
                        "SELECT superficie_m2 FROM mod02_area_verde WHERE id = :id AND activo",
                        Map.of("id", areaId),
                        rs -> rs.next() ? rs.getBigDecimal(1) : null);
        if (superficie == null) {
            throw new ErrorCoberturas(
                    404, "area-no-encontrada", "No existe un área verde activa con ese id");
        }
        List<UUID> poligonos =
                jdbc.query(
                        "SELECT p.id_fuente FROM mod02_mapa_poligono p JOIN mod02_mapa_geometria g"
                                + " USING (id_fuente) WHERE p.area_verde_id = :id ORDER BY"
                                + " p.id_fuente",
                        Map.of("id", areaId),
                        (rs, n) -> rs.getObject(1, UUID.class));
        return new Vigente(superficie, poligonos);
    }

    private static void exigirUnSoloContorno(Vigente vigente) {
        if (vigente.poligonos().size() > 1) {
            throw new ErrorCoberturas(
                    409,
                    "area-con-varios-contornos",
                    "El área tiene varios polígonos vinculados. Desvincula los que sobren antes de"
                            + " redibujar.");
        }
    }

    private boolean igualAlVigente(UUID fuente, ContornoValido valido) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT ST_Equals(geometria, "
                                + NUEVO
                                + ") FROM mod02_mapa_geometria WHERE id_fuente = :fuente",
                        new MapSqlParameterSource("contorno", valido.geojson())
                                .addValue("fuente", fuente),
                        Boolean.class));
    }

    private ImpactoContorno calcular(long areaId, Vigente vigente, ContornoValido valido) {
        var parametros =
                new MapSqlParameterSource("contorno", valido.geojson()).addValue("area", areaId);
        var filas = new DataClassRowMapper<>(EjemplarAfectado.class);
        String propios =
                "SELECT e.id, e.codigo FROM mod02_ejemplar e WHERE e.area_verde_inventario_id ="
                        + " :area AND e.ubicacion_geografica IS NOT NULL AND %s ST_Covers("
                        + NUEVO
                        + ", e.ubicacion_geografica) ORDER BY e.codigo";
        var dentro = jdbc.query(propios.formatted(""), parametros, filas);
        var fuera = jdbc.query(propios.formatted("NOT"), parametros, filas);
        var conflictos =
                jdbc.query(
                        """
                        SELECT e.id, e.codigo FROM mod02_ejemplar e
                        WHERE e.area_verde_inventario_id IS NOT NULL
                            AND e.area_verde_inventario_id <> :area
                            AND e.ubicacion_geografica IS NOT NULL
                            AND ST_Covers(%s, e.ubicacion_geografica)
                            AND EXISTS (
                                SELECT 1 FROM mod02_mapa_poligono p
                                JOIN mod02_mapa_geometria g USING (id_fuente)
                                WHERE p.area_verde_id = e.area_verde_inventario_id)
                            AND NOT EXISTS (
                                SELECT 1 FROM mod02_mapa_poligono p
                                JOIN mod02_mapa_geometria g USING (id_fuente)
                                WHERE p.area_verde_id = e.area_verde_inventario_id
                                    AND ST_Covers(g.geometria, e.ubicacion_geografica))
                        ORDER BY e.codigo
                        """
                                .formatted(NUEVO),
                        parametros,
                        filas);
        return new ImpactoContorno(
                areaId,
                dentro,
                fuera,
                conflictos,
                vigente.superficieRegistrada(),
                valido.superficieM2());
    }

    /**
     * Sustituye la geometría del polígono en la capa que sirve {@code GET /mapa/geometria}, si
     * venía de ella. Un contorno dibujado (AV-430) no está en la capa: se lee vivo.
     */
    private void actualizarCapa(MapSqlParameterSource parametros) {
        jdbc.update(
                """
                UPDATE mod02_mapa_capa c
                SET geojson = jsonb_set(c.geojson, ARRAY['features', f.indice],
                    f.elemento || jsonb_build_object(
                        'geometry', CAST(ST_AsGeoJSON(ST_Multi(g.geometria)) AS jsonb),
                        'properties', (f.elemento->'properties') || jsonb_build_object(
                            'Área', CAST(:superficie AS numeric),
                            'Perimetro', round(CAST(ST_Perimeter(ST_Transform(g.geometria, :srid)) AS numeric), 3))))
                FROM mod02_mapa_geometria g,
                    (SELECT CAST(orden - 1 AS text) indice, elemento
                     FROM mod02_mapa_capa,
                         jsonb_array_elements(geojson->'features') WITH ORDINALITY t(elemento, orden)
                     WHERE unica AND elemento->'properties'->>'mapa_id' = CAST(:fuente AS text)
                     LIMIT 1) f
                WHERE c.unica AND g.id_fuente = :fuente
                """,
                parametros);
    }

    private JsonNode leer(String geojson) {
        try {
            return json.readTree(geojson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("PostGIS devolvió un GeoJSON ilegible", e);
        }
    }

    private static BigDecimal redondear(Number valor) {
        return new BigDecimal(valor.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
