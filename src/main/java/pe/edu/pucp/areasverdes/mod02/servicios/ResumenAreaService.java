package pe.edu.pucp.areasverdes.mod02.servicios;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.AreasVerdesApplication;
import pe.edu.pucp.areasverdes.mod02.api.AreasController.AreaVerde;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod02.api.ResumenAreaVerde;
import pe.edu.pucp.areasverdes.mod02.api.ResumenAreaVerde.EntradaHistorial;
import pe.edu.pucp.areasverdes.mod02.api.ResumenAreaVerde.PaginaHistorial;
import pe.edu.pucp.areasverdes.mod02.api.ResumenAreaVerde.PrioridadValidada;
import pe.edu.pucp.areasverdes.mod02.api.ResumenAreaVerde.ProximaActividad;
import pe.edu.pucp.areasverdes.mod02.api.ResumenAreaVerde.UltimaAtencion;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Resumen e historial agregados del área (AV-431). Solo lee: cada fuente respeta el permiso que ya
 * exige su módulo y, si el usuario no lo tiene, no se consulta ni se devuelve.
 *
 * <p>Las actividades (MOD-04) y las atenciones (MOD-05) apuntan al espacio de {@code
 * mod05_areaverde}; se cruza con el área de inventario por nombre, como hace MOD-06 mientras no se
 * consoliden las tablas. El número de consultas no depende del tamaño de los datos.
 */
@Service
public class ResumenAreaService {

    static final int TAMANO_PAGINA = 25;
    static final int TAMANO_MAXIMO = 100;
    static final List<String> SITUACIONES =
            List.of("activo", "retirado", "muerto", "reemplazado", "trasladado");

    static final String VER_ACTIVIDADES = "CAM.PARTE.CONSULTAR";
    static final String REGISTRAR_PARTE = "CAM.PARTE.REGISTRAR";
    static final String GESTIONAR_CUADRILLAS = "CAM.CUADRILLA.GESTIONAR";
    static final String VER_INCIDENCIAS = "PLA.INCIDENCIA.REGISTRAR";
    static final String VER_TODAS_INCIDENCIAS = "PLA.INCIDENCIA.CONSULTAR_TODAS";
    static final String VER_ATENCIONES = "SER.ATENCION.REGISTRAR";
    static final String VER_PROGRAMACION = "PLA.PLANIFICACION.CONSULTAR";

    /** Consultar sugerencias de la IA, como {@code GET /api/v1/ia/priorizaciones}. */
    static final String VER_PRIORIDAD = "REP.INDICADOR.CONSULTAR";

    /** Espacios de MOD-05 que equivalen al área de inventario (mismo nombre). */
    private static final String ESPACIOS =
            "SELECT m.id FROM mod05_areaverde m JOIN mod02_area_verde a ON"
                    + " lower(btrim(m.nombre)) = lower(btrim(a.nombre)) WHERE a.id = :area";

    /** Alcance de quien no ve toda la bandeja (AV-407), igual que el listado de incidencias. */
    private static final String ALCANCE_PROPIO =
            "(i.reportante_usuario_id = :usuario OR i.capataz_id IN (SELECT p.id FROM"
                    + " mod04_personal p WHERE p.usuario_id = :usuario) OR EXISTS (SELECT 1 FROM"
                    + " mod03_encargo_incidencia ei JOIN mod04_encargo en ON en.id = ei.encargo_id"
                    + " JOIN mod04_cuadrilla cu ON cu.id = en.cuadrilla_id JOIN mod04_personal pc"
                    + " ON pc.id = cu.capataz_id WHERE ei.incidencia_id = i.id AND pc.usuario_id ="
                    + " :usuario))";

    private static final String FILTRO_INCIDENCIAS =
            "i.area_verde_id = :area AND :ver_incidencias AND (:ve_todas OR "
                    + ALCANCE_PROPIO
                    + ")";

    /** Como MOD-04: el capataz solo ve las actividades de sus partes (personal activo propio). */
    private static final String FILTRO_ACTIVIDADES =
            "ac.area_verde_id IN ("
                    + ESPACIOS
                    + ") AND :ver_actividades AND (NOT :actividades_propias OR EXISTS (SELECT 1"
                    + " FROM mod04_parte_diario pd WHERE pd.id = ac.parte_id AND pd.capataz_id IN"
                    + " (SELECT pe.id FROM mod04_personal pe WHERE pe.usuario_id = :usuario AND"
                    + " pe.activo AND pe.tipo_personal = 'Capataz')))";

    private static final String FILTRO_ATENCIONES =
            "EXISTS (SELECT 1 FROM mod05_alcanceatencion al WHERE al.atencion_id = at.id AND"
                    + " al.area_verde_id IN ("
                    + ESPACIOS
                    + ")) AND :ver_atenciones";

    private static final String HISTORIAL =
            "SELECT 'actividad' AS tipo, ac.id, ac.fecha_hora_actividad AS fecha, ta.nombre AS"
                + " titulo, ac.observaciones AS detalle, ac.estado_actividad AS estado FROM"
                + " mod04_actividad ac JOIN mod04_tipo_actividad ta ON ta.id = ac.tipo_actividad_id"
                + " WHERE "
                    + FILTRO_ACTIVIDADES
                    + " UNION ALL SELECT 'incidencia', i.id, i.fecha_registro, ti.nombre,"
                    + " i.descripcion, i.estado FROM mod03_incidencia i JOIN mod03_tipo_incidencia"
                    + " ti ON ti.id = i.tipo_incidencia_id WHERE "
                    + FILTRO_INCIDENCIAS
                    + " UNION ALL SELECT 'atencion', at.id, coalesce(at.fecha_hora_inicio,"
                    + " at.fecha_creacion), at.codigo, at.descripcion, at.estado FROM"
                    + " mod05_atenciontercerizada at WHERE "
                    + FILTRO_ATENCIONES
                    + " UNION ALL SELECT 'cambio_sector', h.id, h.fecha_inicio, 'Asignada al sector"
                    + " ' || s.nombre, NULL, CASE WHEN h.fecha_fin IS NULL THEN 'vigente' ELSE"
                    + " 'finalizada' END FROM mod02_historial_area_sector h JOIN mod02_sector s ON"
                    + " s.id = h.sector_id WHERE h.area_verde_id = :area";

    private static final String ULTIMA_ATENCION =
            "SELECT tipo, id, fecha, titulo, estado FROM (SELECT 'actividad' AS tipo, ac.id,"
                + " ac.fecha_hora_actividad AS fecha, ta.nombre AS titulo, ac.estado_actividad AS"
                + " estado FROM mod04_actividad ac JOIN mod04_tipo_actividad ta ON ta.id ="
                + " ac.tipo_actividad_id WHERE ac.estado_actividad <> 'Anulada' AND "
                    + FILTRO_ACTIVIDADES
                    + " UNION ALL SELECT 'atencion', at.id, at.fecha_hora_inicio, at.codigo,"
                    + " at.estado FROM mod05_atenciontercerizada at WHERE at.fecha_hora_inicio IS"
                    + " NOT NULL AND "
                    + FILTRO_ATENCIONES
                    + ") u ORDER BY fecha DESC, tipo, id DESC LIMIT 1";

    private final NamedParameterJdbcTemplate jdbc;
    private final AreasService areas;
    private final Clock reloj;

    public ResumenAreaService(NamedParameterJdbcTemplate jdbc, AreasService areas, Clock reloj) {
        this.jdbc = jdbc;
        this.areas = areas;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public ResumenAreaVerde resumen(
            long id, int pagina, int tamano, Usuario usuario, IntFunction<String> enlace) {
        AccesoCoberturas.consultar(usuario);
        if (pagina < 1) {
            throw new ErrorCoberturas(400, "pagina-invalida", "La página debe ser mayor que cero");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new ErrorCoberturas(
                    400, "tamano-invalido", "El tamaño debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        AreaVerde area = areas.obtenerPorId(id);
        Fuentes fuentes = Fuentes.de(usuario);
        MapSqlParameterSource p = fuentes.parametros(id, usuario);

        return new ResumenAreaVerde(
                area,
                ejemplaresPorSituacion(id),
                fuentes.incidencias() ? incidenciasAbiertas(p) : null,
                ultimaAtencion(p),
                fuentes.programacion() ? proximaActividad(id) : null,
                fuentes.prioridad() ? prioridadValidada(id) : null,
                fuentes.visibles(),
                historial(p, pagina, tamano, enlace));
    }

    private Map<String, Integer> ejemplaresPorSituacion(long id) {
        Map<String, Integer> resultado = new LinkedHashMap<>();
        SITUACIONES.forEach(s -> resultado.put(s, 0));
        jdbc.query(
                "SELECT situacion, count(*) AS total FROM mod02_ejemplar WHERE"
                        + " area_verde_inventario_id = :id GROUP BY situacion",
                Map.of("id", id),
                rs -> {
                    resultado.put(rs.getString("situacion"), rs.getInt("total"));
                });
        return resultado;
    }

    private int incidenciasAbiertas(MapSqlParameterSource p) {
        Integer total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod03_incidencia i WHERE i.estado NOT IN ('Atendida',"
                                + " 'Cerrada', 'Descartada') AND "
                                + FILTRO_INCIDENCIAS,
                        p,
                        Integer.class);
        return total == null ? 0 : total;
    }

    private UltimaAtencion ultimaAtencion(MapSqlParameterSource p) {
        return jdbc
                .query(
                        ULTIMA_ATENCION,
                        p,
                        (rs, n) ->
                                new UltimaAtencion(
                                        rs.getString("tipo"),
                                        rs.getLong("id"),
                                        lima(rs, "fecha"),
                                        rs.getString("titulo"),
                                        rs.getString("estado")))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ProximaActividad proximaActividad(long id) {
        return jdbc
                .query(
                        "SELECT p.id, ts.nombre, p.fecha_inicio_prevista, p.fecha_fin_prevista,"
                                + " p.estado FROM mod03_programacion_servicio p JOIN"
                                + " mod03_programacion_area pa ON pa.programacion_id = p.id JOIN"
                                + " mod05_tiposervicio ts ON ts.id = p.tipo_servicio_id WHERE"
                                + " pa.area_verde_id = :id AND p.estado IN ('Programada',"
                                + " 'EnCurso') AND p.fecha_fin_prevista >= :hoy ORDER BY"
                                + " p.fecha_inicio_prevista, p.id LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("id", id)
                                .addValue("hoy", LocalDate.now(reloj)),
                        (rs, n) ->
                                new ProximaActividad(
                                        rs.getLong("id"),
                                        rs.getString("nombre"),
                                        rs.getObject("fecha_inicio_prevista", LocalDate.class),
                                        rs.getObject("fecha_fin_prevista", LocalDate.class),
                                        rs.getString("estado")))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /** La decisión de la última priorización validada que incluyó el área (clave «area-id»). */
    private PrioridadValidada prioridadValidada(long id) {
        String prioridad =
                "s.contenido -> 'decision' -> 'por_elemento' -> :clave ->> 'prioridad_final'";
        return jdbc
                .query(
                        "SELECT s.id, s.fecha_validacion, "
                                + prioridad
                                + " AS prioridad FROM mod06_sugerencia_ia s WHERE s.tipo ="
                                + " 'Priorizacion' AND s.estado = 'Validada' AND "
                                + prioridad
                                + " IS NOT NULL ORDER BY s.fecha_validacion DESC, s.id DESC LIMIT"
                                + " 1",
                        Map.of("clave", "area-" + id),
                        (rs, n) ->
                                new PrioridadValidada(
                                        rs.getString("prioridad"),
                                        lima(rs, "fecha_validacion"),
                                        rs.getLong("id")))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private PaginaHistorial historial(
            MapSqlParameterSource p, int pagina, int tamano, IntFunction<String> enlace) {
        Integer total =
                jdbc.queryForObject("SELECT count(*) FROM (" + HISTORIAL + ") h", p, Integer.class);
        int count = total == null ? 0 : total;
        List<EntradaHistorial> entradas =
                count == 0
                        ? new ArrayList<>()
                        : jdbc.query(
                                "SELECT * FROM ("
                                        + HISTORIAL
                                        + ") h ORDER BY fecha DESC NULLS LAST, tipo, id DESC LIMIT"
                                        + " :limite OFFSET :desplazamiento",
                                new MapSqlParameterSource(p.getValues())
                                        .addValue("limite", tamano)
                                        .addValue("desplazamiento", (pagina - 1) * tamano),
                                entrada());
        String anterior = pagina > 1 ? enlace.apply(pagina - 1) : null;
        String siguiente = (long) pagina * tamano < count ? enlace.apply(pagina + 1) : null;
        return new PaginaHistorial(count, siguiente, anterior, entradas);
    }

    private static RowMapper<EntradaHistorial> entrada() {
        return (rs, n) ->
                new EntradaHistorial(
                        rs.getString("tipo"),
                        rs.getLong("id"),
                        lima(rs, "fecha"),
                        rs.getString("titulo"),
                        rs.getString("detalle"),
                        rs.getString("estado"));
    }

    private static OffsetDateTime lima(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime fecha = rs.getObject(columna, OffsetDateTime.class);
        return fecha == null
                ? null
                : fecha.atZoneSameInstant(AreasVerdesApplication.LIMA).toOffsetDateTime();
    }

    /**
     * Qué fuentes puede ver el usuario; la de cambios de sector es la propia del inventario. Como
     * en MOD-04, quien registra partes sin gestionar cuadrillas solo ve las actividades suyas.
     */
    record Fuentes(
            boolean actividades,
            boolean actividadesPropias,
            boolean incidencias,
            boolean veTodasIncidencias,
            boolean atenciones,
            boolean programacion,
            boolean prioridad) {

        static Fuentes de(Usuario u) {
            return new Fuentes(
                    u.puede(VER_ACTIVIDADES),
                    u.puede(REGISTRAR_PARTE) && !u.puede(GESTIONAR_CUADRILLAS),
                    u.puede(VER_INCIDENCIAS),
                    u.puede(VER_TODAS_INCIDENCIAS),
                    u.puede(VER_ATENCIONES),
                    u.puede(VER_PROGRAMACION),
                    u.puede(VER_PRIORIDAD));
        }

        MapSqlParameterSource parametros(long area, Usuario usuario) {
            return new MapSqlParameterSource()
                    .addValue("area", area)
                    .addValue("usuario", usuario.id())
                    .addValue("ver_actividades", actividades)
                    .addValue("actividades_propias", actividadesPropias)
                    .addValue("ver_incidencias", incidencias)
                    .addValue("ve_todas", veTodasIncidencias)
                    .addValue("ver_atenciones", atenciones);
        }

        List<String> visibles() {
            List<String> lista = new ArrayList<>();
            if (actividades) lista.add("actividades");
            if (incidencias) lista.add("incidencias");
            if (atenciones) lista.add("atenciones");
            if (programacion) lista.add("programacion");
            if (prioridad) lista.add("prioridad");
            lista.add("cambios_sector");
            return lista;
        }
    }
}
