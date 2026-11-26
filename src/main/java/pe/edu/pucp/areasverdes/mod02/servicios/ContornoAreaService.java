package pe.edu.pucp.areasverdes.mod02.servicios;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Contorno dibujado de un área verde (AV-430): lo valida, calcula su superficie en UTM 18S
 * (EPSG:32718) y lo guarda en {@code mod02_mapa_geometria} vinculado al área.
 *
 * <p>El perímetro del campus es la envolvente convexa de la capa de referencia (origen {@code
 * capa}); los contornos dibujados no lo amplían.
 */
@Service
public class ContornoAreaService {

    private static final int SRID_UTM_18S = 32718;
    private static final String GEOMETRIA =
            "ST_SetSRID(ST_GeomFromGeoJSON(CAST(:geojson AS text)), 4326)";

    /** Contorno ya comprobado: GeoJSON normalizado y superficie en m² con dos decimales. */
    public record ContornoValido(String geojson, BigDecimal superficieM2) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final GeometriaCampus campus;
    private final ObjectMapper json;
    private final BigDecimal toleranciaSolapeM2;

    public ContornoAreaService(
            NamedParameterJdbcTemplate jdbc,
            GeometriaCampus campus,
            ObjectMapper json,
            @Value("${mod02.contorno.tolerancia-solape-m2:1}") BigDecimal toleranciaSolapeM2) {
        this.jdbc = jdbc;
        this.campus = campus;
        this.json = json;
        this.toleranciaSolapeM2 = toleranciaSolapeM2;
    }

    /** Debe llamarse dentro de la transacción que guardará el contorno. */
    public ContornoValido validar(JsonNode contorno) {
        validarForma(contorno);
        String geojson = contorno.toString();
        campus.bloquearCambios();
        var parametros = new MapSqlParameterSource("geojson", geojson);
        var validez =
                jdbc.query(
                        "SELECT ST_IsValid(g), ST_IsValidReason(g),"
                                + " ST_Area(ST_Transform(g, "
                                + SRID_UTM_18S
                                + ")) FROM (SELECT "
                                + GEOMETRIA
                                + " g) x",
                        parametros,
                        rs -> {
                            rs.next();
                            return new Object[] {
                                rs.getBoolean(1), rs.getString(2), rs.getBigDecimal(3)
                            };
                        });
        if (!(Boolean) validez[0]) {
            throw sinProcesar(
                    "contorno-invalido",
                    "El contorno no es un polígono simple: " + validez[1] + ".");
        }
        BigDecimal superficie = ((BigDecimal) validez[2]).setScale(2, RoundingMode.HALF_UP);
        if (superficie.signum() <= 0) {
            throw sinProcesar("contorno-invalido", "El contorno no encierra ninguna superficie.");
        }
        boolean dentro =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT ST_Covers((SELECT ST_ConvexHull(ST_Collect(geometria))"
                                        + " FROM mod02_mapa_geometria WHERE origen = 'capa'), "
                                        + GEOMETRIA
                                        + ")",
                                parametros,
                                Boolean.class));
        if (!dentro) {
            throw sinProcesar(
                    "contorno-fuera-del-campus",
                    "El contorno queda fuera del perímetro del campus.");
        }
        parametros.addValue("tolerancia", toleranciaSolapeM2);
        String solapada =
                jdbc.query(
                        "SELECT a.nombre FROM mod02_mapa_geometria g JOIN mod02_mapa_poligono p"
                                + " USING (id_fuente) JOIN mod02_area_verde a ON a.id ="
                                + " p.area_verde_id WHERE a.activo AND ST_Intersects(g.geometria, "
                                + GEOMETRIA
                                + ") AND ST_Area(ST_Transform(ST_Intersection(g.geometria, "
                                + GEOMETRIA
                                + "), "
                                + SRID_UTM_18S
                                + ")) > :tolerancia ORDER BY a.nombre LIMIT 1",
                        parametros,
                        rs -> rs.next() ? rs.getString(1) : null);
        if (solapada != null) {
            throw sinProcesar(
                    "contorno-solapado",
                    "El contorno se solapa con el área «"
                            + solapada
                            + "» más de lo permitido ("
                            + toleranciaSolapeM2.stripTrailingZeros().toPlainString()
                            + " m²).");
        }
        return new ContornoValido(geojson, superficie);
    }

    /** Guarda la geometría y la vincula al área. Devuelve el id de la geometría en el mapa. */
    public UUID guardar(ContornoValido contorno, long areaId, Usuario usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO mod02_mapa_geometria (id_fuente, geometria, origen) VALUES (:id,"
                        + " ST_Force2D("
                        + GEOMETRIA
                        + "), 'dibujo')",
                new MapSqlParameterSource("id", id).addValue("geojson", contorno.geojson()));
        jdbc.update(
                "INSERT INTO mod02_mapa_poligono (id_fuente, area_verde_id, fecha_modificacion,"
                        + " usuario_modificacion_id) VALUES (:id, :area, now(), :usuario)",
                new MapSqlParameterSource("id", id)
                        .addValue("area", areaId)
                        .addValue("usuario", usuario.id()));
        return id;
    }

    /** El contorno guardado, tal como lo devuelve PostGIS. */
    public JsonNode contorno(UUID idFuente) {
        String texto =
                jdbc.queryForObject(
                        "SELECT ST_AsGeoJSON(geometria) FROM mod02_mapa_geometria WHERE id_fuente"
                                + " = :id",
                        new MapSqlParameterSource("id", idFuente),
                        String.class);
        try {
            return json.readTree(texto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("PostGIS devolvió un GeoJSON ilegible", e);
        }
    }

    private static void validarForma(JsonNode c) {
        if (c == null || !c.isObject() || !"Polygon".equals(c.path("type").asText(null))) {
            throw sinProcesar(
                    "contorno-invalido", "El contorno debe ser un GeoJSON de tipo Polygon.");
        }
        JsonNode anillos = c.path("coordinates");
        if (!anillos.isArray() || anillos.isEmpty()) {
            throw sinProcesar("contorno-invalido", "El contorno no tiene coordenadas.");
        }
        for (JsonNode anillo : anillos) {
            if (!anillo.isArray() || anillo.size() < 4) {
                throw sinProcesar(
                        "contorno-invalido",
                        "Cada anillo del contorno necesita al menos cuatro puntos.");
            }
            for (JsonNode punto : anillo) {
                if (!punto.isArray()
                        || punto.size() < 2
                        || punto.size() > 3
                        || !punto.get(0).isNumber()
                        || !punto.get(1).isNumber()
                        || Math.abs(punto.get(0).asDouble()) > 180
                        || Math.abs(punto.get(1).asDouble()) > 90) {
                    throw sinProcesar(
                            "contorno-invalido",
                            "Las coordenadas deben ser [longitud, latitud] en EPSG:4326.");
                }
            }
            if (!anillo.get(0).equals(anillo.get(anillo.size() - 1))) {
                throw sinProcesar(
                        "contorno-no-cerrado",
                        "El contorno no está cerrado: el último punto debe repetir el primero.");
            }
        }
    }

    private static ErrorCoberturas sinProcesar(String codigo, String detalle) {
        return new ErrorCoberturas(422, codigo, detalle);
    }
}
