package pe.edu.pucp.areasverdes.mod02.servicios;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.Asignacion;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.CambioCapataz;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.CambioElemento;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.CambioSector;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.Elemento;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.NuevoNombre;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.NuevoSector;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.Persona;
import pe.edu.pucp.areasverdes.mod02.api.CatalogosMod02Controller.Sector;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod02.datos.CatalogosMod02Repository;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class CatalogosMod02Service {

    private static final Pattern COLOR_HEXADECIMAL = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final String[] COLORES_INICIALES = {
        "#1B5E20", "#1565C0", "#9A6700", "#6A1B9A", "#00838F", "#AD3A16", "#455A64"
    };

    private static final RowMapper<Elemento> ELEMENTO =
            (rs, row) ->
                    new Elemento(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getBoolean("activo"),
                            rs.getString("color"));

    private static final RowMapper<Sector> SECTOR =
            (rs, row) ->
                    new Sector(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getBoolean("activo"),
                            rs.getBoolean("atencion_a_demanda"),
                            rs.getString("capataz_nombre"),
                            rs.getString("superficie_m2"));
    private final NamedParameterJdbcTemplate jdbc;
    private final CatalogosMod02Repository repository;

    public CatalogosMod02Service(
            NamedParameterJdbcTemplate jdbc, CatalogosMod02Repository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

    public List<Sector> sectores(boolean incluirInactivos) {
        return jdbc.query(
                "SELECT s.id, s.nombre, s.activo, s.atencion_a_demanda, u.nombre capataz_nombre,"
                    + " coalesce(sum(a.superficie_m2), 0)::text superficie_m2 FROM mod02_sector s"
                    + " LEFT JOIN LATERAL (SELECT capataz_id FROM mod02_historial_capataz_sector"
                    + " WHERE sector_id = s.id AND fecha_inicio <= now() AND (fecha_fin IS NULL OR"
                    + " fecha_fin > now()) ORDER BY fecha_inicio DESC, id DESC LIMIT 1) hc ON true"
                    + " LEFT JOIN core_usuario u ON u.id = hc.capataz_id LEFT JOIN"
                    + " mod02_historial_area_sector ha ON ha.sector_id = s.id AND ha.fecha_fin IS"
                    + " NULL LEFT JOIN mod02_area_verde a ON a.id = ha.area_verde_id AND a.activo"
                    + " WHERE (:incluir OR s.activo) GROUP BY s.id, u.nombre ORDER BY"
                    + " lower(s.nombre), s.id",
                Map.of("incluir", incluirInactivos),
                SECTOR);
    }

    public List<Persona> capataces() {
        return repository.capatacesActivos();
    }

    public List<Elemento> tiposUso(boolean incluirInactivos) {
        return jdbc.query(
                "SELECT id, nombre, activo, color FROM mod02_tipo_uso WHERE (:incluir OR activo)"
                        + " ORDER BY lower(nombre), id",
                Map.of("incluir", incluirInactivos),
                ELEMENTO);
    }

    @Transactional
    public Elemento crearTipoUso(NuevoNombre entrada, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        try {
            String color =
                    entrada.color() == null ? siguienteColor() : normalizarColor(entrada.color());
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO mod02_tipo_uso (nombre, usuario_creacion_id,"
                                    + " usuario_modificacion_id, color) VALUES (:nombre, :usuario,"
                                    + " :usuario, :color)"
                                    + " RETURNING id",
                            Map.of(
                                    "nombre", entrada.nombre().trim(),
                                    "usuario", usuario.id(),
                                    "color", color),
                            Long.class);
            auditar("mod02_tipo_uso", id, "crear", usuario.id());
            return tiposUso(true).stream().filter(t -> t.id() == id).findFirst().orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(409, "tipo-uso-duplicado", "Ya existe ese tipo de uso");
        }
    }

    @Transactional
    public Elemento cambiarTipoUso(long id, CambioElemento cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        if (cambio.nombre() == null && cambio.activo() == null && cambio.color() == null) {
            throw new ErrorCoberturas(400, "cambio-vacio", "Indica un nombre o un estado");
        }
        String color = cambio.color() == null ? null : normalizarColor(cambio.color());
        try {
            int actualizados =
                    jdbc.update(
                            "UPDATE mod02_tipo_uso SET nombre = coalesce(:nombre, nombre), activo ="
                                    + " coalesce(:activo, activo), color = coalesce(:color, color),"
                                    + " fecha_modificacion = now(),"
                                    + " usuario_modificacion_id = :usuario WHERE id = :id",
                            new MapSqlParameterSource()
                                    .addValue("id", id)
                                    .addValue("usuario", usuario.id())
                                    .addValue(
                                            "nombre",
                                            cambio.nombre() == null ? null : cambio.nombre().trim())
                                    .addValue("activo", cambio.activo())
                                    .addValue("color", color));
            if (actualizados == 0) throw noExiste("No existe ese tipo de uso");
            auditar("mod02_tipo_uso", id, "modificar", usuario.id());
            return tiposUso(true).stream().filter(t -> t.id() == id).findFirst().orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new ErrorCoberturas(409, "tipo-uso-duplicado", "Ya existe ese tipo de uso");
        }
    }

    private String siguienteColor() {
        Integer cantidad =
                jdbc.queryForObject("SELECT count(*) FROM mod02_tipo_uso", Map.of(), Integer.class);
        return COLORES_INICIALES[
                Math.floorMod(cantidad == null ? 0 : cantidad, COLORES_INICIALES.length)];
    }

    private static String normalizarColor(String color) {
        String normalizado = color.trim().toUpperCase(Locale.ROOT);
        if (!COLOR_HEXADECIMAL.matcher(normalizado).matches()) {
            throw new ErrorCoberturas(
                    400, "color-invalido", "El color debe tener el formato hexadecimal #RRGGBB");
        }
        return normalizado;
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
    public Sector asignarCapataz(long sectorId, CambioCapataz cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        var sectorBloqueado =
                jdbc.query(
                        "SELECT id FROM mod02_sector WHERE id = :id FOR UPDATE",
                        Map.of("id", sectorId),
                        (rs, fila) -> rs.getLong(1));
        if (sectorBloqueado.isEmpty()) throw noExiste("No existe ese sector");
        Long capatazId = cambio.capatazId();
        if (capatazId != null && !repository.esCapatazActivo(capatazId)) {
            throw noExiste("El usuario indicado no es un capataz activo");
        }
        var ultima =
                jdbc.query(
                        """
                        SELECT fecha_inicio, fecha_fin FROM mod02_historial_capataz_sector
                        WHERE sector_id = :id ORDER BY fecha_inicio DESC, id DESC LIMIT 1
                        """,
                        Map.of("id", sectorId),
                        (rs, fila) ->
                                new Periodo(
                                        rs.getObject("fecha_inicio", OffsetDateTime.class),
                                        rs.getObject("fecha_fin", OffsetDateTime.class)));
        OffsetDateTime inicio =
                cambio.fechaInicio() == null
                        ? jdbc.queryForObject("SELECT now()", Map.of(), OffsetDateTime.class)
                        : cambio.fechaInicio()
                                .atStartOfDay(ZoneId.of("America/Lima"))
                                .toOffsetDateTime();
        String motivo = cambio.motivo() == null ? "" : cambio.motivo().strip();
        if (!ultima.isEmpty()) {
            Periodo anterior = ultima.getFirst();
            if (!inicio.isAfter(anterior.inicio())
                    || (anterior.fin() != null && inicio.isBefore(anterior.fin()))) {
                throw new ErrorCoberturas(
                        409,
                        "fecha-asignacion-invalida",
                        "La fecha debe ser posterior al inicio de la última asignación y no solapar"
                                + " su vigencia");
            }
            if (capatazId != null
                    && cambio.fechaInicio() != null
                    && anterior.fin() == null
                    && motivo.isEmpty()) {
                throw new ErrorCoberturas(
                        400, "motivo-requerido", "Indica el motivo de la reasignación");
            }
        }
        jdbc.update(
                "UPDATE mod02_historial_capataz_sector SET fecha_fin = :fin"
                        + " WHERE sector_id = :sector AND fecha_fin IS NULL",
                Map.of(
                        "sector",
                        sectorId,
                        "fin",
                        cambio.fechaInicio() == null ? inicio : inicio.minusNanos(1000)));
        if (capatazId != null) {
            jdbc.update(
                    """
                    INSERT INTO mod02_historial_capataz_sector
                        (sector_id, capataz_id, usuario_id, fecha_inicio, motivo)
                    VALUES (:sector, :capataz, :usuario, :inicio, nullif(:motivo, ''))
                    """,
                    Map.of(
                            "sector",
                            sectorId,
                            "capataz",
                            capatazId,
                            "usuario",
                            usuario.id(),
                            "inicio",
                            inicio,
                            "motivo",
                            motivo));
        }
        auditar("mod02_sector", sectorId, "asignar-capataz", usuario.id());
        return sector(sectorId);
    }

    private record Periodo(OffsetDateTime inicio, OffsetDateTime fin) {}

    public List<Asignacion> historialCapataz(long sectorId) {
        if (!repository.existeSector(sectorId)) throw noExiste("No existe ese sector");
        return repository.historialCapataz(sectorId);
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
