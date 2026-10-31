package pe.edu.pucp.areasverdes.mod02;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class EjemplaresService {

    private static final String CONSULTA =
            "SELECT e.id, e.codigo, e.referencia_catastro, e.codigo_catastro,"
                + " e.codigo_placa_antigua, e.especie_id, s.nombre_comun especie_nombre_comun,"
                + " s.nombre_cientifico especie_nombre_cientifico, s.forma_biologica"
                + " especie_forma_biologica, e.tipo_vegetacion, e.ubicacion, e.latitud, e.longitud,"
                + " e.cantidad, e.area_verde_id, (e.codigo_placa_antigua IS NOT NULL AND EXISTS"
                + " (SELECT 1 FROM mod02_ejemplar otro WHERE otro.id <> e.id AND"
                + " lower(otro.codigo_placa_antigua) = lower(e.codigo_placa_antigua)))"
                + " advertencia_placa_repetida FROM mod02_ejemplar e LEFT JOIN"
                + " mod02_especie_vegetal s ON s.id = e.especie_id";
    private static final String ESPECIES =
            "SELECT id, nombre_comun, nombre_cientifico, forma_biologica, activo FROM"
                    + " mod02_especie_vegetal";

    private final NamedParameterJdbcTemplate jdbc;

    public EjemplaresService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Ejemplar> ejemplares(String q, String referencia, int page) {
        if (page < 1) {
            throw new ErrorCoberturas(400, "pagina-invalida", "La página debe ser mayor que cero");
        }
        String termino = q == null ? "" : q.strip();
        String ref = referencia == null ? "" : referencia.strip();
        var parametros =
                new MapSqlParameterSource()
                        .addValue("q", termino)
                        .addValue("referencia", ref)
                        .addValue("limite", 25)
                        .addValue("offset", (page - 1) * 25);
        return jdbc.query(
                CONSULTA
                        + " WHERE (:q = '' OR e.codigo ILIKE '%' || :q || '%' OR"
                        + " e.referencia_catastro ILIKE '%' || :q || '%' OR"
                        + " s.nombre_comun ILIKE '%' || :q || '%' OR"
                        + " s.nombre_cientifico ILIKE '%' || :q || '%')"
                        + " AND (:referencia = '' OR e.referencia_catastro = :referencia)"
                        + " ORDER BY e.id LIMIT :limite OFFSET :offset",
                parametros,
                new DataClassRowMapper<>(Ejemplar.class));
    }

    public List<Especie> especies(String q, boolean incluirInactivas) {
        String termino = q == null ? "" : q.strip();
        return jdbc.query(
                ESPECIES
                        + " WHERE (:incluir OR activo) AND (:q = '' OR nombre_comun ILIKE '%' || :q"
                        + " || '%' OR coalesce(nombre_cientifico, '') ILIKE '%' || :q || '%') ORDER"
                        + " BY lower(nombre_comun), id",
                new MapSqlParameterSource()
                        .addValue("q", termino)
                        .addValue("incluir", incluirInactivas),
                new DataClassRowMapper<>(Especie.class));
    }

    public Especie crearEspecie(
            String nombreComun, String nombreCientifico, String formaBiologica, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        try {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO mod02_especie_vegetal (nombre_comun, nombre_cientifico,"
                                + " forma_biologica) VALUES (:comun, :cientifico, :forma) RETURNING"
                                + " id",
                            new MapSqlParameterSource()
                                    .addValue("comun", nombreComun.strip())
                                    .addValue(
                                            "cientifico",
                                            nombreCientifico == null || nombreCientifico.isBlank()
                                                    ? null
                                                    : nombreCientifico.strip())
                                    .addValue("forma", formaBiologica.strip()),
                            Long.class);
            return jdbc.queryForObject(
                    ESPECIES + " WHERE id = :id",
                    Map.of("id", id),
                    new DataClassRowMapper<>(Especie.class));
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "especie-duplicada", "Ya existe una especie con ese nombre científico");
        }
    }

    public Especie cambiarEspecie(
            long id,
            String nombreComun,
            String nombreCientifico,
            String formaBiologica,
            Boolean activo,
            Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        if (nombreComun == null
                && nombreCientifico == null
                && formaBiologica == null
                && activo == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica algún dato que quieras modificar");
        }
        try {
            int filas =
                    jdbc.update(
                            "UPDATE mod02_especie_vegetal SET nombre_comun = coalesce(:comun,"
                                + " nombre_comun), nombre_cientifico = coalesce(:cientifico,"
                                + " nombre_cientifico), forma_biologica = coalesce(:forma,"
                                + " forma_biologica), activo = coalesce(:activo, activo) WHERE id ="
                                + " :id",
                            new MapSqlParameterSource()
                                    .addValue("id", id)
                                    .addValue(
                                            "comun",
                                            nombreComun == null ? null : nombreComun.strip())
                                    .addValue(
                                            "cientifico",
                                            nombreCientifico == null
                                                    ? null
                                                    : nombreCientifico.strip())
                                    .addValue(
                                            "forma",
                                            formaBiologica == null ? null : formaBiologica.strip())
                                    .addValue("activo", activo));
            if (filas == 0)
                throw new ErrorCoberturas(404, "especie-no-encontrada", "No existe esa especie");
            return jdbc.queryForObject(
                    ESPECIES + " WHERE id = :id",
                    Map.of("id", id),
                    new DataClassRowMapper<>(Especie.class));
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "especie-duplicada", "Ya existe una especie con ese nombre científico");
        }
    }

    public Ejemplar crearEjemplar(NuevoEjemplar entrada, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        validarEspecie(entrada.especieId());
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod02_ejemplar (codigo_placa_antigua, especie_id,"
                                + " tipo_vegetacion, ubicacion, latitud, longitud, cantidad)"
                                + " VALUES (:placa, :especie, :vegetacion, :ubicacion, :latitud,"
                                + " :longitud, :cantidad) RETURNING id",
                        parametros(entrada),
                        Long.class);
        return obtenerEjemplar(id);
    }

    public Ejemplar cambiarEjemplar(long id, CambioEjemplar cambio, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        if (cambio.especieId() == null
                && cambio.tipoVegetacion() == null
                && cambio.ubicacion() == null
                && cambio.latitud() == null
                && cambio.longitud() == null
                && cambio.cantidad() == null
                && cambio.codigoPlacaAntigua() == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica algún dato que quieras modificar");
        }
        validarEspecie(cambio.especieId());
        int filas =
                jdbc.update(
                        "UPDATE mod02_ejemplar SET especie_id = coalesce(:especie, especie_id),"
                                + " tipo_vegetacion = coalesce(:vegetacion, tipo_vegetacion),"
                                + " ubicacion = coalesce(:ubicacion, ubicacion),"
                                + " latitud = coalesce(:latitud, latitud),"
                                + " longitud = coalesce(:longitud, longitud),"
                                + " cantidad = coalesce(:cantidad, cantidad),"
                                + " codigo_placa_antigua = coalesce(:placa, codigo_placa_antigua)"
                                + " WHERE id = :id",
                        parametrosCambio(cambio).addValue("id", id));
        if (filas == 0)
            throw new ErrorCoberturas(404, "ejemplar-no-encontrado", "No existe ese ejemplar");
        return obtenerEjemplar(id);
    }

    public Ejemplar quitarPlacaAntigua(long id, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        int filas =
                jdbc.update(
                        "UPDATE mod02_ejemplar SET codigo_placa_antigua = NULL WHERE id = :id",
                        Map.of("id", id));
        if (filas == 0)
            throw new ErrorCoberturas(404, "ejemplar-no-encontrado", "No existe ese ejemplar");
        return obtenerEjemplar(id);
    }

    private Ejemplar obtenerEjemplar(long id) {
        return jdbc.queryForObject(
                CONSULTA + " WHERE e.id = :id",
                Map.of("id", id),
                new DataClassRowMapper<>(Ejemplar.class));
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

    private static MapSqlParameterSource parametros(NuevoEjemplar entrada) {
        return new MapSqlParameterSource()
                .addValue("placa", entrada.codigoPlacaAntigua())
                .addValue("especie", entrada.especieId())
                .addValue("vegetacion", entrada.tipoVegetacion())
                .addValue("ubicacion", entrada.ubicacion())
                .addValue("latitud", entrada.latitud())
                .addValue("longitud", entrada.longitud())
                .addValue("cantidad", entrada.cantidad() == null ? 1 : entrada.cantidad());
    }

    private static MapSqlParameterSource parametrosCambio(CambioEjemplar cambio) {
        return new MapSqlParameterSource()
                .addValue("placa", cambio.codigoPlacaAntigua())
                .addValue("especie", cambio.especieId())
                .addValue("vegetacion", cambio.tipoVegetacion())
                .addValue("ubicacion", cambio.ubicacion())
                .addValue("latitud", cambio.latitud())
                .addValue("longitud", cambio.longitud())
                .addValue("cantidad", cambio.cantidad());
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
            boolean advertenciaPlacaRepetida) {}

    public record Especie(
            long id,
            String nombreComun,
            String nombreCientifico,
            String formaBiologica,
            boolean activo) {}

    public record NuevoEjemplar(
            String codigoPlacaAntigua,
            Long especieId,
            String tipoVegetacion,
            String ubicacion,
            BigDecimal latitud,
            BigDecimal longitud,
            Integer cantidad) {}

    public record CambioEjemplar(
            String codigoPlacaAntigua,
            Long especieId,
            String tipoVegetacion,
            String ubicacion,
            BigDecimal latitud,
            BigDecimal longitud,
            Integer cantidad) {}
}
