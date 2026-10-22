package pe.edu.pucp.areasverdes.mod03;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.areasverdes.mod03.ServicioPeriodico.Duracion;
import pe.edu.pucp.areasverdes.mod03.ServicioPeriodico.Frecuencia;

@Repository
public class ServiciosRepository {

    private static final String FRECUENCIAS =
            "SELECT f.*, u.nombre modificado_por FROM mod03_frecuencia_servicio f JOIN"
                    + " mod05_usuario u ON u.id = f.usuario_modificacion_id ORDER BY"
                    + " array_position(ARRAY['Verano','Otoño','Invierno','Primavera']::varchar[],"
                    + " f.estacion)";
    private static final String DURACIONES =
            "SELECT d.*, u.nombre modificado_por FROM mod03_duracion_servicio d"
                    + " JOIN mod05_usuario u ON u.id = d.usuario_modificacion_id";
    private static final String SERVICIOS =
            "SELECT id, nombre, es_ruidoso FROM mod05_tiposervicio"
                    + " WHERE activo AND es_periodico ORDER BY id";

    private final NamedParameterJdbcTemplate jdbc;

    public ServiciosRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ServicioPeriodico> periodicos() {
        Map<Long, List<Frecuencia>> frecuencias =
                jdbc.query(FRECUENCIAS, ServiciosRepository::frecuencia).stream()
                        .collect(groupingBy(Entry::getKey, mapping(Entry::getValue, toList())));
        Map<Long, Duracion> duraciones =
                jdbc.query(DURACIONES, ServiciosRepository::duracion).stream()
                        .collect(toMap(Entry::getKey, Entry::getValue));
        return jdbc.query(
                SERVICIOS,
                (rs, i) ->
                        new ServicioPeriodico(
                                rs.getLong("id"),
                                rs.getString("nombre"),
                                rs.getBoolean("es_ruidoso"),
                                frecuencias.getOrDefault(rs.getLong("id"), List.of()),
                                duraciones.get(rs.getLong("id"))));
    }

    private static Entry<Long, Frecuencia> frecuencia(ResultSet rs, int fila) throws SQLException {
        return Map.entry(
                rs.getLong("tipo_servicio_id"),
                new Frecuencia(
                        rs.getString("estacion"),
                        rs.getInt("frecuencia_teorica_dias"),
                        rs.getObject("frecuencia_practica_dias", Integer.class),
                        rs.getObject("frecuencia_maxima_dias", Integer.class),
                        modificacion(rs),
                        rs.getString("modificado_por")));
    }

    private static Entry<Long, Duracion> duracion(ResultSet rs, int fila) throws SQLException {
        return Map.entry(
                rs.getLong("tipo_servicio_id"),
                new Duracion(
                        rs.getInt("duracion_maxima_dias"),
                        rs.getObject("dias_limpieza", Integer.class),
                        rs.getObject("hora_inicio", LocalTime.class),
                        rs.getObject("hora_fin", LocalTime.class),
                        modificacion(rs),
                        rs.getString("modificado_por")));
    }

    private static OffsetDateTime modificacion(ResultSet rs) throws SQLException {
        return rs.getObject("fecha_modificacion", OffsetDateTime.class)
                .atZoneSameInstant(LIMA)
                .toOffsetDateTime();
    }
}
