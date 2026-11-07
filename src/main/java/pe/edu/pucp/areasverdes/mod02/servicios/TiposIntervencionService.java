package pe.edu.pucp.areasverdes.mod02.servicios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Catálogo de tipos de intervención tercerizada (AV-313, RF-POD-006). Los tipos no se borran: se
 * desactivan, y las intervenciones ya registradas conservan su tipo.
 */
@Service
public class TiposIntervencionService {

    private static final String ENTIDAD = "mod02_tipo_intervencion";

    private static final String CONSULTA =
            """
            SELECT t.id, t.nombre, t.descripcion, t.es_poda, t.activo,
                (SELECT count(*) FROM mod02_intervencion_arbolado i
                    WHERE i.tipo_intervencion_id = t.id AND i.activo) usos
            FROM mod02_tipo_intervencion t
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;

    public TiposIntervencionService(
            NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    /**
     * Tipo de intervención.
     *
     * @param esPoda se ofrece al registrar la poda de una campaña
     * @param usos intervenciones vigentes que lo usan
     */
    public record TipoIntervencion(
            long id,
            String nombre,
            String descripcion,
            boolean esPoda,
            boolean activo,
            long usos) {}

    public List<TipoIntervencion> listar(boolean incluirInactivos, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return jdbc.query(
                CONSULTA + " WHERE :incluir OR t.activo ORDER BY t.activo DESC, lower(t.nombre)",
                Map.of("incluir", incluirInactivos),
                new DataClassRowMapper<>(TipoIntervencion.class));
    }

    @Transactional
    public TipoIntervencion crear(
            String nombre, String descripcion, boolean esPoda, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        long id;
        try {
            id =
                    jdbc.queryForObject(
                            """
                            INSERT INTO mod02_tipo_intervencion (nombre, descripcion, es_poda)
                            VALUES (:nombre, :descripcion, :esPoda)
                            RETURNING id
                            """,
                            new MapSqlParameterSource()
                                    .addValue("nombre", nombre.strip())
                                    .addValue("descripcion", textoOpcional(descripcion))
                                    .addValue("esPoda", esPoda),
                            Long.class);
        } catch (DuplicateKeyException e) {
            throw duplicado();
        }
        TipoIntervencion creado = buscar(id);
        auditoria.registrarAlta(ENTIDAD, id, valores(creado), usuario.id());
        return creado;
    }

    @Transactional
    public TipoIntervencion cambiar(
            long id, String nombre, String descripcion, Boolean activo, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        if (nombre == null && descripcion == null && activo == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica algún dato que quieras modificar");
        }
        if (nombre != null && nombre.isBlank()) {
            throw new ErrorCoberturas(400, "nombre-obligatorio", "Indica el nombre del tipo");
        }
        TipoIntervencion antes = buscar(id);
        try {
            jdbc.update(
                    """
                    UPDATE mod02_tipo_intervencion SET nombre = coalesce(:nombre, nombre),
                        descripcion = CASE WHEN :cambiaDescripcion THEN :descripcion
                            ELSE descripcion END,
                        activo = coalesce(:activo, activo)
                    WHERE id = :id
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("nombre", nombre == null ? null : nombre.strip())
                            .addValue("cambiaDescripcion", descripcion != null)
                            .addValue("descripcion", textoOpcional(descripcion))
                            .addValue("activo", activo));
        } catch (DuplicateKeyException e) {
            throw duplicado();
        }
        TipoIntervencion despues = buscar(id);
        String accion = antes.activo() && !despues.activo() ? "BajaLogica" : "Modificacion";
        auditoria.registrarCambio(
                ENTIDAD, id, accion, valores(antes), valores(despues), null, usuario.id());
        return despues;
    }

    private TipoIntervencion buscar(long id) {
        return jdbc
                .query(
                        CONSULTA + " WHERE t.id = :id",
                        Map.of("id", id),
                        new DataClassRowMapper<>(TipoIntervencion.class))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ErrorCoberturas(
                                        404,
                                        "tipo-no-encontrado",
                                        "No existe ese tipo de intervención"));
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private static ErrorCoberturas duplicado() {
        return new ErrorCoberturas(
                409, "tipo-duplicado", "Ya existe un tipo de intervención con ese nombre");
    }

    private static Map<String, Object> valores(TipoIntervencion tipo) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("nombre", tipo.nombre());
        valores.put("descripcion", tipo.descripcion());
        valores.put("es_poda", tipo.esPoda());
        valores.put("activo", tipo.activo());
        return valores;
    }
}
