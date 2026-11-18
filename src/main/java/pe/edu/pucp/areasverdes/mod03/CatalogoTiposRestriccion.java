package pe.edu.pucp.areasverdes.mod03;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;
import pe.edu.pucp.areasverdes.mod03.RestriccionesController.CicloAfectado;
import pe.edu.pucp.areasverdes.mod03.RestriccionesController.TipoRestriccion;

/** Administración del catálogo; conserva las restricciones y las decisiones originales de V27. */
@Service
public class CatalogoTiposRestriccion {
    static final String ENTIDAD = "mod03_tipo_restriccion";
    static final String CAMPUS = "Cierre del campus";
    private static final Set<String> CAMPOS =
            Set.of("nombre", "efecto", "aplica_servicios_ruidosos", "activo");
    private static final String SELECT =
            "SELECT id,nombre,efecto,aplica_servicios_ruidosos,activo,nombre='Cierre del campus'"
                    + " protegido FROM mod03_tipo_restriccion";
    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;

    public CatalogoTiposRestriccion(
            NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    public record Resultado(
            @com.fasterxml.jackson.annotation.JsonUnwrapped TipoRestriccion tipo,
            List<CicloAfectado> ciclosAfectados) {}

    public List<TipoRestriccion> listar(boolean todos) {
        return jdbc.query(
                SELECT + (todos ? "" : " WHERE activo") + " ORDER BY id",
                new DataClassRowMapper<>(TipoRestriccion.class));
    }

    private TipoRestriccion obtener(long id) {
        return jdbc
                .query(
                        SELECT + " WHERE id=:id",
                        Map.of("id", id),
                        new DataClassRowMapper<>(TipoRestriccion.class))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ErrorPlanificacion(
                                        404,
                                        "tipo-no-encontrado",
                                        "No existe ese tipo de restricción"));
    }

    private void bloquear() {
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtext('mod03_tipos_restriccion'))",
                Map.of(),
                rs -> {});
    }

    private void validar(JsonNode datos, boolean alta) {
        if (datos == null || !datos.isObject() || datos.isEmpty())
            throw error("Indica los campos del tipo");
        datos.fieldNames()
                .forEachRemaining(
                        c -> {
                            if (!CAMPOS.contains(c) || alta && c.equals("activo"))
                                throw error("Campo no permitido: " + c);
                        });
        for (String campo : List.of("nombre", "efecto", "aplica_servicios_ruidosos"))
            if (alta && !datos.has(campo)) throw error("Falta el campo " + campo);
        if (datos.has("nombre")
                && (!datos.get("nombre").isTextual()
                        || nombre(datos.get("nombre").asText()).isEmpty()
                        || nombre(datos.get("nombre").asText()).length() > 80))
            throw error("Indica un nombre de entre 1 y 80 caracteres");
        if (datos.has("efecto")
                && (!datos.get("efecto").isTextual()
                        || !Set.of("Prohibicion", "Advertencia", "Favorable")
                                .contains(datos.get("efecto").asText())))
            throw error("El efecto debe ser Prohibicion, Advertencia o Favorable");
        for (String campo : List.of("aplica_servicios_ruidosos", "activo"))
            if (datos.has(campo) && !datos.get(campo).isBoolean())
                throw error(campo + " debe ser un booleano");
    }

    private static String nombre(String valor) {
        return valor.strip().replaceAll("\\s+", " ");
    }

    private static ErrorPlanificacion error(String detalle) {
        return new ErrorPlanificacion(400, "validacion", detalle);
    }

    private void nombreDisponible(String nombre, long id) {
        if (jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_tipo_restriccion WHERE id<>:id AND"
                                + " lower(regexp_replace(btrim(nombre),'[[:space:]]+','"
                                + " ','g'))=lower(:nombre)",
                        Map.of("id", id, "nombre", nombre),
                        Integer.class)
                > 0)
            throw new ErrorPlanificacion(
                    409,
                    "tipo-duplicado",
                    "Ya existe un tipo con ese nombre, también puede estar inactivo");
    }

    private Map<String, Object> valores(TipoRestriccion t) {
        return Map.of(
                "nombre",
                t.nombre(),
                "efecto",
                t.efecto(),
                "aplica_servicios_ruidosos",
                t.aplicaServiciosRuidosos(),
                "activo",
                t.activo());
    }

    @Transactional
    public Resultado crear(JsonNode datos, UsuarioPlanificacion usuario) {
        Acceso.gestionar(usuario);
        validar(datos, true);
        bloquear();
        String nombre = nombre(datos.get("nombre").asText());
        nombreDisponible(nombre, -1);
        long id =
                jdbc.queryForObject(
                        "INSERT INTO"
                                + " mod03_tipo_restriccion(nombre,efecto,aplica_servicios_ruidosos)"
                                + " VALUES (:nombre,:efecto,:ruidosos) RETURNING id",
                        Map.of(
                                "nombre",
                                nombre,
                                "efecto",
                                datos.get("efecto").asText(),
                                "ruidosos",
                                datos.get("aplica_servicios_ruidosos").booleanValue()),
                        Long.class);
        TipoRestriccion tipo = obtener(id);
        auditoria.registrarAlta(ENTIDAD, id, valores(tipo), usuario.id());
        return new Resultado(tipo, List.of());
    }

    @Transactional
    public Resultado modificar(long id, JsonNode datos, UsuarioPlanificacion usuario) {
        Acceso.gestionar(usuario);
        validar(datos, false);
        // Mismo orden que altas/reprogramaciones: evita perder ciclos que se programan durante el
        // cambio.
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtext('mod03_disponibilidad'))",
                Map.of(),
                rs -> {});
        bloquear();
        jdbc.query(
                "SELECT id FROM mod03_tipo_restriccion WHERE id=:id FOR UPDATE",
                Map.of("id", id),
                rs -> {});
        TipoRestriccion antes = obtener(id);
        String nombre = datos.has("nombre") ? nombre(datos.get("nombre").asText()) : antes.nombre();
        boolean activo = datos.has("activo") ? datos.get("activo").booleanValue() : antes.activo();
        if (antes.protegido() && (!nombre.equals(CAMPUS) || !activo))
            throw new ErrorPlanificacion(
                    409,
                    "tipo-protegido",
                    "Cierre del campus no puede renombrarse ni desactivarse: participa en la"
                            + " planificación de poda mayor de MOD-02");
        nombreDisponible(nombre, id);
        String efecto = datos.has("efecto") ? datos.get("efecto").asText() : antes.efecto();
        boolean ruidosos =
                datos.has("aplica_servicios_ruidosos")
                        ? datos.get("aplica_servicios_ruidosos").booleanValue()
                        : antes.aplicaServiciosRuidosos();
        jdbc.update(
                "UPDATE mod03_tipo_restriccion SET"
                    + " nombre=:nombre,efecto=:efecto,aplica_servicios_ruidosos=:ruidosos,activo=:activo"
                    + " WHERE id=:id",
                Map.of(
                        "id",
                        id,
                        "nombre",
                        nombre,
                        "efecto",
                        efecto,
                        "ruidosos",
                        ruidosos,
                        "activo",
                        activo));
        TipoRestriccion despues = obtener(id);
        Map<String, Object> previos = new LinkedHashMap<>(), nuevos = new LinkedHashMap<>();
        valores(antes)
                .forEach(
                        (campo, valor) -> {
                            Object nuevo = valores(despues).get(campo);
                            if (!Objects.equals(valor, nuevo)) {
                                previos.put(campo, valor);
                                nuevos.put(campo, nuevo);
                            }
                        });
        if (!nuevos.isEmpty())
            auditoria.registrarCambio(
                    ENTIDAD,
                    id,
                    antes.activo() && !activo
                            ? "BajaLogica"
                            : !antes.activo() && activo ? "CambioEstado" : "Modificacion",
                    previos,
                    nuevos,
                    null,
                    usuario.id());
        List<CicloAfectado> afectados = new ArrayList<>();
        if (efecto.equals("Prohibicion")
                && (!antes.efecto().equals(efecto)
                        || antes.aplicaServiciosRuidosos() != ruidosos)) {
            var restricciones =
                    jdbc.query(
                            RestriccionesController.CONSULTA.formatted(
                                    "r.tipo_restriccion_id=:tipo"),
                            Map.of("tipo", id),
                            new DataClassRowMapper<>(RestriccionesController.Restriccion.class));
            Map<Long, CicloAfectado> unicos = new LinkedHashMap<>();
            for (var r : restricciones)
                jdbc.query(
                                RestriccionesController.AFECTADOS,
                                Map.of(
                                        "efecto",
                                        r.efecto(),
                                        "inicio",
                                        r.fechaInicio(),
                                        "fin",
                                        r.fechaFin(),
                                        "soloRuidosos",
                                        r.aplicaServiciosRuidosos()),
                                new DataClassRowMapper<>(CicloAfectado.class))
                        .forEach(c -> unicos.put(c.id(), c));
            afectados.addAll(unicos.values());
        }
        return new Resultado(despues, afectados);
    }
}
