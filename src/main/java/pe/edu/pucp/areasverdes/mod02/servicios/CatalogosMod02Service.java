package pe.edu.pucp.areasverdes.mod02.servicios;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.CambioSector;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.NuevoSector;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.Sector;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class CatalogosMod02Service {

    private static final RowMapper<Sector> SECTOR =
            (rs, row) ->
                    new Sector(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getBoolean("activo"),
                            rs.getBoolean("atencion_a_demanda"),
                            rs.getString("superficie_m2"));
    private final NamedParameterJdbcTemplate jdbc;

    public CatalogosMod02Service(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Sector> sectores(boolean incluirInactivos) {
        return jdbc.query(
                "SELECT s.id, s.nombre, s.activo, s.atencion_a_demanda,"
                    + " coalesce(sum(a.superficie_m2), 0)::text superficie_m2 FROM mod02_sector s"
                    + " LEFT JOIN mod02_historial_area_sector ha ON ha.sector_id = s.id AND"
                    + " ha.fecha_fin IS NULL LEFT JOIN mod02_area_verde a ON a.id ="
                    + " ha.area_verde_id AND a.activo WHERE (:incluir OR s.activo) GROUP BY s.id"
                    + " ORDER BY lower(s.nombre), s.id",
                Map.of("incluir", incluirInactivos),
                SECTOR);
    }

    @Transactional
    public Sector crearSector(NuevoSector entrada, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        try {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO mod02_sector (nombre, atencion_a_demanda,"
                                + " usuario_creacion_id, usuario_modificacion_id) VALUES (:nombre,"
                                + " :demanda, :usuario, :usuario) RETURNING id",
                            Map.of(
                                    "nombre",
                                    entrada.nombre().trim(),
                                    "demanda",
                                    Boolean.TRUE.equals(entrada.atencionDemanda()),
                                    "usuario",
                                    usuario.id()),
                            Long.class);
            auditar("mod02_sector", id, "crear", usuario.id());
            return sector(id);
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "sector-duplicado", "Ya existe un sector activo con ese nombre");
        }
    }

    @Transactional
    public Sector cambiarSector(long id, CambioSector cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        if (cambio.activo() == null && cambio.atencionDemanda() == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica el estado o la atención a demanda");
        }
        int actualizados =
                jdbc.update(
                        "UPDATE mod02_sector SET activo = coalesce(:activo, activo),"
                                + " atencion_a_demanda = coalesce(:demanda, atencion_a_demanda),"
                                + " fecha_modificacion = now(), usuario_modificacion_id = :usuario"
                                + " WHERE id = :id",
                        new MapSqlParameterSource()
                                .addValue("id", id)
                                .addValue("usuario", usuario.id())
                                .addValue("activo", cambio.activo())
                                .addValue("demanda", cambio.atencionDemanda()));
        if (actualizados == 0) throw noExiste("No existe ese sector");
        auditar("mod02_sector", id, "modificar", usuario.id());
        return sector(id);
    }

    private Sector sector(long id) {
        return sectores(true).stream().filter(s -> s.id() == id).findFirst().orElseThrow();
    }

    private void auditar(String entidad, long id, String accion, long usuarioId) {
        jdbc.update(
                "INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id,"
                    + " fecha_hora, valores_anteriores, valores_nuevos, motivo) VALUES (:entidad,"
                    + " :id, CASE WHEN :accion = 'crear' THEN 'Alta' ELSE 'Modificacion' END,"
                    + " :usuario, now(), '{}'::jsonb, '{}'::jsonb, '')",
                Map.of("entidad", entidad, "id", id, "accion", accion, "usuario", usuarioId));
    }

    private static ErrorCoberturas noExiste(String detalle) {
        return new ErrorCoberturas(404, "referencia-no-encontrada", detalle);
    }
}
