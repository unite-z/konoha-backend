package pe.edu.pucp.areasverdes.mod02.servicios;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.api.CuartelesController.CambioCuartel;
import pe.edu.pucp.areasverdes.mod02.api.CuartelesController.Cuartel;
import pe.edu.pucp.areasverdes.mod02.api.CuartelesController.NuevoCuartel;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class CuartelesService {

    private final NamedParameterJdbcTemplate jdbc;

    public CuartelesService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Cuartel> listar(boolean incluirDesaparecidos) {
        return jdbc.query(
                "SELECT id, numero, nombre, estado FROM mod02_cuartel "
                        + "WHERE (:incluir OR estado = 'vigente') ORDER BY numero, id",
                Map.of("incluir", incluirDesaparecidos),
                new DataClassRowMapper<>(Cuartel.class));
    }

    @Transactional
    public Cuartel crear(NuevoCuartel entrada, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        try {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO mod02_cuartel (numero, nombre, usuario_creacion_id, "
                                    + "usuario_modificacion_id) VALUES (:numero, :nombre, "
                                    + ":usuario, :usuario) RETURNING id",
                            Map.of(
                                    "numero", entrada.numero().trim(),
                                    "nombre", entrada.nombre().trim(),
                                    "usuario", usuario.id()),
                            Long.class);
            auditar(id, "crear", usuario.id());
            return buscar(id);
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "cuartel-duplicado", "Ya existe un cuartel con ese número");
        }
    }

    @Transactional
    public Cuartel modificar(long id, CambioCuartel cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        if (cambio.estado() != null
                && !List.of("vigente", "desaparecido").contains(cambio.estado())) {
            throw new ErrorCoberturas(
                    400, "estado-invalido", "El estado debe ser vigente o desaparecido");
        }
        try {
            int actualizados =
                    jdbc.update(
                            "UPDATE mod02_cuartel SET numero = coalesce(:numero, numero), "
                                    + "nombre = coalesce(:nombre, nombre), estado = "
                                    + "coalesce(:estado, estado), fecha_modificacion = now(), "
                                    + "usuario_modificacion_id = :usuario WHERE id = :id",
                            new MapSqlParameterSource()
                                    .addValue("id", id)
                                    .addValue("usuario", usuario.id())
                                    .addValue("numero", cambio.numero())
                                    .addValue("nombre", cambio.nombre())
                                    .addValue("estado", cambio.estado()));
            if (actualizados == 0) throw noExiste();
            auditar(id, "modificar", usuario.id());
            return buscar(id);
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "cuartel-duplicado", "Ya existe un cuartel con ese número");
        }
    }

    private Cuartel buscar(long id) {
        return jdbc.queryForObject(
                "SELECT id, numero, nombre, estado FROM mod02_cuartel WHERE id = :id",
                Map.of("id", id),
                new DataClassRowMapper<>(Cuartel.class));
    }

    private void auditar(long id, String accion, long usuarioId) {
        jdbc.update(
                "INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id, "
                        + "fecha_hora, valores_anteriores, valores_nuevos, motivo) VALUES "
                        + "('mod02_cuartel', :id, :accion, :usuario, now(), '{}'::jsonb, "
                        + "'{}'::jsonb, '')",
                Map.of("id", id, "accion", accion, "usuario", usuarioId));
    }

    private ErrorCoberturas noExiste() {
        return new ErrorCoberturas(404, "cuartel-no-encontrado", "No existe ese cuartel");
    }
}
