package pe.edu.pucp.areasverdes.mod02;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.CoberturasController.CambioCobertura;
import pe.edu.pucp.areasverdes.mod02.CoberturasController.Cobertura;
import pe.edu.pucp.areasverdes.mod02.CoberturasController.NuevaCobertura;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class CoberturasService {

    private final NamedParameterJdbcTemplate jdbc;

    public CoberturasService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Cobertura> listar(boolean incluirInactivas) {
        return jdbc.query(
                "SELECT id, nombre, activo FROM mod02_cobertura_vegetal "
                        + "WHERE (:incluir = true OR activo) ORDER BY lower(nombre), id",
                Map.of("incluir", incluirInactivas),
                new DataClassRowMapper<>(Cobertura.class));
    }

    @Transactional
    public Cobertura crear(NuevaCobertura nueva, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        String nombre = nueva.nombre().trim();
        try {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO mod02_cobertura_vegetal "
                                    + "(nombre, usuario_creacion_id, usuario_modificacion_id) "
                                    + "VALUES (:nombre, :usuario, :usuario) RETURNING id",
                            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                                    .addValue("nombre", nombre)
                                    .addValue("usuario", usuario.id()),
                            Long.class);
            Cobertura cobertura = new Cobertura(id, nombre, true);
            auditar(id, "crear", "{}", usuario.id());
            return cobertura;
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "cobertura-duplicada", "Ya existe una cobertura con ese nombre");
        }
    }

    @Transactional
    public Cobertura modificar(long id, CambioCobertura cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        if (cambio.nombre() == null && cambio.activo() == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica el nombre o el estado que quieres modificar");
        }
        if (cambio.nombre() != null && cambio.nombre().isBlank()) {
            throw new ErrorCoberturas(400, "nombre-invalido", "El nombre no puede estar vacío");
        }
        String nombre = cambio.nombre() == null ? null : cambio.nombre().trim();
        Boolean activo = cambio.activo();
        try {
            String anteriores =
                    jdbc.query(
                            "SELECT to_jsonb(c)::text FROM mod02_cobertura_vegetal c WHERE c.id ="
                                    + " :id",
                            Map.of("id", id),
                            rs -> rs.next() ? rs.getString(1) : null);
            if (anteriores == null) {
                throw new ErrorCoberturas(
                        404, "cobertura-no-encontrada", "No existe esa cobertura");
            }
            List<Cobertura> actualizadas =
                    jdbc.query(
                            "UPDATE mod02_cobertura_vegetal SET nombre = coalesce(:nombre, nombre),"
                                + " activo = coalesce(:activo, activo), fecha_modificacion = now(),"
                                + " usuario_modificacion_id = :usuario WHERE id = :id RETURNING id,"
                                + " nombre, activo",
                            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                                    .addValue("id", id)
                                    .addValue("nombre", nombre)
                                    .addValue("activo", activo)
                                    .addValue("usuario", usuario.id()),
                            new DataClassRowMapper<>(Cobertura.class));
            Cobertura resultado =
                    actualizadas.stream()
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new ErrorCoberturas(
                                                    404,
                                                    "cobertura-no-encontrada",
                                                    "No existe esa cobertura"));
            auditar(id, "modificar", anteriores, usuario.id());
            return resultado;
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "cobertura-duplicada", "Ya existe una cobertura con ese nombre");
        }
    }

    private void auditar(long id, String accion, String anteriores, long usuarioId) {
        jdbc.update(
                "INSERT INTO mod05_registroauditoria (entidad, registro_id, accion, fecha_hora, "
                        + "valores_anteriores, valores_nuevos, motivo, usuario_id) "
                        + "SELECT 'mod02_cobertura_vegetal', c.id, :accion, now(), "
                        + "CAST(:anteriores AS jsonb), to_jsonb(c), '', :usuario "
                        + "FROM mod02_cobertura_vegetal c WHERE c.id = :id",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("accion", accion)
                        .addValue("anteriores", anteriores)
                        .addValue("usuario", usuarioId));
    }
}
