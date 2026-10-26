package pe.edu.pucp.areasverdes.mod02;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.Asignacion;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.CambioCuartel;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.CambioElemento;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.CambioSector;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.Cuartel;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.Elemento;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.NuevoCuartel;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.NuevoNombre;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.NuevoSector;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.Persona;
import pe.edu.pucp.areasverdes.mod02.CatalogosMod02Controller.Sector;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class CatalogosMod02Service {

    private static final RowMapper<Elemento> ELEMENTO =
            (rs, row) ->
                    new Elemento(rs.getLong("id"), rs.getString("nombre"), rs.getBoolean("activo"));
    private static final RowMapper<Cuartel> CUARTEL =
            (rs, row) ->
                    new Cuartel(
                            rs.getLong("id"),
                            rs.getString("numero"),
                            rs.getString("nombre"),
                            rs.getString("estado"));
    private static final RowMapper<Sector> SECTOR =
            (rs, row) ->
                    new Sector(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getBoolean("activo"),
                            rs.getBoolean("atencion_a_demanda"),
                            rs.getString("capataz_nombre"),
                            rs.getString("superficie_m2"));
    private static final RowMapper<Asignacion> ASIGNACION =
            (rs, row) ->
                    new Asignacion(
                            rs.getLong("id"),
                            rs.getLong("capataz_id"),
                            rs.getString("capataz_nombre"),
                            rs.getString("fecha_inicio"),
                            rs.getString("fecha_fin"));

    private final NamedParameterJdbcTemplate jdbc;

    public CatalogosMod02Service(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Elemento> tiposUso(boolean incluirInactivos) {
        return jdbc.query(
                "SELECT id, nombre, activo FROM mod02_tipo_uso "
                        + "WHERE (:incluir OR activo) ORDER BY lower(nombre), id",
                Map.of("incluir", incluirInactivos),
                ELEMENTO);
    }

    @Transactional
    public Elemento crearTipoUso(NuevoNombre entrada, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        try {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO mod02_tipo_uso (nombre, usuario_creacion_id,"
                                + " usuario_modificacion_id) VALUES (:nombre, :usuario, :usuario)"
                                + " RETURNING id",
                            Map.of("nombre", entrada.nombre().trim(), "usuario", usuario.id()),
                            Long.class);
            auditar("mod02_tipo_uso", id, "crear", usuario.id());
            return tiposUso(true).stream().filter(v -> v.id() == id).findFirst().orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(409, "tipo-uso-duplicado", "Ya existe ese tipo de uso");
        }
    }

    @Transactional
    public Elemento cambiarTipoUso(long id, CambioElemento cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        if (cambio.nombre() == null && cambio.activo() == null) {
            throw new ErrorCoberturas(400, "cambio-vacio", "Indica un nombre o un estado");
        }
        try {
            int actualizados =
                    jdbc.update(
                            "UPDATE mod02_tipo_uso SET nombre = coalesce(:nombre, nombre), activo ="
                                    + " coalesce(:activo, activo), fecha_modificacion = now(),"
                                    + " usuario_modificacion_id = :usuario WHERE id = :id",
                            new MapSqlParameterSource()
                                    .addValue("id", id)
                                    .addValue("usuario", usuario.id())
                                    .addValue(
                                            "nombre",
                                            cambio.nombre() == null ? null : cambio.nombre().trim())
                                    .addValue("activo", cambio.activo()));
            if (actualizados == 0) throw noExiste("No existe ese tipo de uso");
            auditar("mod02_tipo_uso", id, "modificar", usuario.id());
            return tiposUso(true).stream().filter(v -> v.id() == id).findFirst().orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(409, "tipo-uso-duplicado", "Ya existe ese tipo de uso");
        }
    }

    public List<Cuartel> cuarteles(boolean incluirDesaparecidos) {
        return jdbc.query(
                "SELECT id, numero, nombre, estado FROM mod02_cuartel "
                        + "WHERE (:incluir OR estado = 'vigente') ORDER BY numero, id",
                Map.of("incluir", incluirDesaparecidos),
                CUARTEL);
    }

    @Transactional
    public Cuartel crearCuartel(NuevoCuartel entrada, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        try {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO mod02_cuartel (numero, nombre, usuario_creacion_id,"
                                + " usuario_modificacion_id) VALUES (:numero, :nombre, :usuario,"
                                + " :usuario) RETURNING id",
                            Map.of(
                                    "numero",
                                    entrada.numero().trim(),
                                    "nombre",
                                    entrada.nombre().trim(),
                                    "usuario",
                                    usuario.id()),
                            Long.class);
            auditar("mod02_cuartel", id, "crear", usuario.id());
            return cuarteles(true).stream().filter(v -> v.id() == id).findFirst().orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "cuartel-duplicado", "Ya existe un cuartel con ese número");
        }
    }

    @Transactional
    public Cuartel cambiarCuartel(long id, CambioCuartel cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        if (cambio.estado() != null
                && !List.of("vigente", "desaparecido").contains(cambio.estado())) {
            throw new ErrorCoberturas(
                    400, "estado-invalido", "El estado debe ser vigente o desaparecido");
        }
        try {
            int actualizados =
                    jdbc.update(
                            "UPDATE mod02_cuartel SET numero = coalesce(:numero, numero), nombre ="
                                + " coalesce(:nombre, nombre), estado = coalesce(:estado, estado),"
                                + " fecha_modificacion = now(), usuario_modificacion_id = :usuario"
                                + " WHERE id = :id",
                            new MapSqlParameterSource()
                                    .addValue("id", id)
                                    .addValue("usuario", usuario.id())
                                    .addValue("numero", cambio.numero())
                                    .addValue("nombre", cambio.nombre())
                                    .addValue("estado", cambio.estado()));
            if (actualizados == 0) throw noExiste("No existe ese cuartel");
            auditar("mod02_cuartel", id, "modificar", usuario.id());
            return cuarteles(true).stream().filter(v -> v.id() == id).findFirst().orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(
                    409, "cuartel-duplicado", "Ya existe un cuartel con ese número");
        }
    }

    public List<Sector> sectores(boolean incluirInactivos) {
        return jdbc.query(
                "SELECT s.id, s.nombre, s.activo, s.atencion_a_demanda, u.nombre capataz_nombre,"
                    + " coalesce(sum(a.superficie_m2), 0)::text superficie_m2 FROM mod02_sector s"
                    + " LEFT JOIN mod02_historial_capataz_sector hc ON hc.sector_id = s.id AND"
                    + " hc.fecha_fin IS NULL LEFT JOIN mod05_usuario u ON u.id = hc.capataz_id LEFT"
                    + " JOIN mod02_historial_area_sector ha ON ha.sector_id = s.id AND ha.fecha_fin"
                    + " IS NULL LEFT JOIN mod02_area_verde a ON a.id = ha.area_verde_id AND"
                    + " a.activo WHERE (:incluir OR s.activo) GROUP BY s.id, u.nombre ORDER BY"
                    + " lower(s.nombre), s.id",
                Map.of("incluir", incluirInactivos),
                SECTOR);
    }

    public List<Persona> capataces() {
        return jdbc.query(
                "SELECT u.id, u.nombre FROM mod05_usuario u "
                        + "JOIN mod05_usuario_rol r ON r.usuario_id = u.id "
                        + "WHERE u.activo AND r.rol = :rol ORDER BY lower(u.nombre), u.id",
                Map.of("rol", Usuario.CAPATAZ),
                (rs, row) -> new Persona(rs.getLong("id"), rs.getString("nombre")));
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

    @Transactional
    public Sector asignarCapataz(long sectorId, Long capatazId, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        if (!existe("mod02_sector", sectorId)) throw noExiste("No existe ese sector");
        if (capatazId != null
                && !Boolean.TRUE.equals(
                        jdbc.query(
                                "SELECT EXISTS (SELECT 1 FROM mod05_usuario u JOIN"
                                    + " mod05_usuario_rol r ON r.usuario_id = u.id WHERE u.id = :id"
                                    + " AND u.activo AND r.rol = :rol)",
                                Map.of("id", capatazId, "rol", Usuario.CAPATAZ),
                                rs -> rs.next() && rs.getBoolean(1)))) {
            throw noExiste("El usuario indicado no es un capataz activo");
        }
        jdbc.update(
                "UPDATE mod02_historial_capataz_sector SET fecha_fin = greatest(now(),"
                        + " fecha_inicio) WHERE sector_id = :sector AND fecha_fin IS NULL",
                Map.of("sector", sectorId));
        if (capatazId != null) {
            jdbc.update(
                    "INSERT INTO mod02_historial_capataz_sector (sector_id, capataz_id, usuario_id)"
                            + " VALUES (:sector, :capataz, :usuario)",
                    Map.of("sector", sectorId, "capataz", capatazId, "usuario", usuario.id()));
        }
        auditar("mod02_sector", sectorId, "asignar-capataz", usuario.id());
        return sector(sectorId);
    }

    public List<Asignacion> historialCapataz(long sectorId) {
        if (!existe("mod02_sector", sectorId)) throw noExiste("No existe ese sector");
        return jdbc.query(
                "SELECT h.id, h.capataz_id, u.nombre capataz_nombre, h.fecha_inicio::text"
                        + " fecha_inicio, h.fecha_fin::text fecha_fin FROM"
                        + " mod02_historial_capataz_sector h JOIN mod05_usuario u ON u.id ="
                        + " h.capataz_id WHERE h.sector_id = :id ORDER BY h.fecha_inicio DESC, h.id"
                        + " DESC",
                Map.of("id", sectorId),
                ASIGNACION);
    }

    private Sector sector(long id) {
        return sectores(true).stream().filter(s -> s.id() == id).findFirst().orElseThrow();
    }

    private boolean existe(String tabla, long id) {
        return Boolean.TRUE.equals(
                jdbc.query(
                        "SELECT EXISTS (SELECT 1 FROM " + tabla + " WHERE id = :id)",
                        Map.of("id", id),
                        rs -> rs.next() && rs.getBoolean(1)));
    }

    private void auditar(String entidad, long id, String accion, long usuarioId) {
        jdbc.update(
                "INSERT INTO mod05_registroauditoria (entidad, registro_id, accion, fecha_hora,"
                    + " valores_anteriores, valores_nuevos, motivo, usuario_id) VALUES (:entidad,"
                    + " :id, :accion, now(), '{}'::jsonb, '{}'::jsonb, '', :usuario)",
                Map.of("entidad", entidad, "id", id, "accion", accion, "usuario", usuarioId));
    }

    private static ErrorCoberturas noExiste(String detalle) {
        return new ErrorCoberturas(404, "referencia-no-encontrada", detalle);
    }
}
