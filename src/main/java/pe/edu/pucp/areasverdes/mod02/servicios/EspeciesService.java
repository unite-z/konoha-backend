package pe.edu.pucp.areasverdes.mod02.servicios;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class EspeciesService {

    public record Especie(
            long id,
            String nombreComun,
            String nombreCientifico,
            String formaBiologica,
            boolean activo) {}

    private static final String CONSULTA =
            "SELECT id, nombre_comun, nombre_cientifico, forma_biologica, activo"
                    + " FROM mod02_especie_vegetal";

    private final NamedParameterJdbcTemplate jdbc;

    public EspeciesService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Especie> listar(String q, boolean incluirInactivas) {
        String termino = q == null ? "" : q.strip();
        return jdbc.query(
                CONSULTA
                        + " WHERE (:incluir OR activo) AND (:q = '' OR nombre_comun ILIKE '%' || :q"
                        + " || '%' OR coalesce(nombre_cientifico, '') ILIKE '%' || :q || '%')"
                        + " ORDER BY lower(coalesce(nombre_comun, nombre_cientifico)), id",
                new MapSqlParameterSource()
                        .addValue("q", termino)
                        .addValue("incluir", incluirInactivas),
                new DataClassRowMapper<>(Especie.class));
    }

    @Transactional
    public Especie crear(
            String nombreComun, String nombreCientifico, String formaBiologica, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        try {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO mod02_especie_vegetal (nombre_comun, nombre_cientifico,"
                                    + " forma_biologica) VALUES (:comun, :cientifico, :forma)"
                                    + " RETURNING id",
                            new MapSqlParameterSource()
                                    .addValue("comun", nombreComun.strip())
                                    .addValue(
                                            "cientifico",
                                            nombreCientifico == null || nombreCientifico.isBlank()
                                                    ? null
                                                    : nombreCientifico.strip())
                                    .addValue("forma", formaBiologica.strip()),
                            Long.class);
            return buscar(id);
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "especie-duplicada", "Ya existe una especie con ese nombre científico");
        }
    }

    @Transactional
    public Especie cambiar(
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
                                    + " forma_biologica), activo = coalesce(:activo, activo)"
                                    + " WHERE id = :id",
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
            if (filas == 0) {
                throw new ErrorCoberturas(404, "especie-no-encontrada", "No existe esa especie");
            }
            return buscar(id);
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "especie-duplicada", "Ya existe una especie con ese nombre científico");
        }
    }

    private Especie buscar(long id) {
        return jdbc.queryForObject(
                CONSULTA + " WHERE id = :id",
                Map.of("id", id),
                new DataClassRowMapper<>(Especie.class));
    }
}
