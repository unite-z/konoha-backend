package pe.edu.pucp.areasverdes.mod03;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Alta de programaciones de servicios periódicos (HU-MOD03-004, RN-002). */
@Service
public class ProgramacionServicios {

    public record NuevaProgramacion(
            @NotNull(message = "Indica el servicio") Long tipoServicioId,
            Long proveedorId,
            @NotNull(message = "Indica el inicio") LocalDate fechaInicioPrevista,
            @NotNull(message = "Indica el fin") LocalDate fechaFinPrevista) {}

    private record Duracion(boolean ruidoso, Integer maxima, Integer limpieza) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ProgramacionServicios(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long crear(NuevaProgramacion nueva, Usuario usuario) {
        Acceso.gestionar(usuario);
        LocalDate inicio = nueva.fechaInicioPrevista();
        LocalDate fin = nueva.fechaFinPrevista();
        if (fin.isBefore(inicio)) {
            throw new ErrorPlanificacion(
                    400, "periodo-invalido", "El fin debe ser posterior al inicio");
        }
        List<Duracion> servicios =
                jdbc.query(
                        "SELECT t.es_ruidoso ruidoso, d.duracion_maxima_dias maxima,"
                            + " coalesce(d.dias_limpieza, 0) limpieza FROM mod05_tiposervicio t"
                            + " LEFT JOIN mod03_duracion_servicio d ON d.tipo_servicio_id = t.id"
                            + " WHERE t.id = :id AND t.activo AND t.es_periodico",
                        Map.of("id", nueva.tipoServicioId()),
                        new DataClassRowMapper<>(Duracion.class));
        if (servicios.isEmpty()) {
            throw new ErrorPlanificacion(404, "servicio-no-encontrado", "No existe ese servicio");
        }
        Duracion servicio = servicios.getFirst();
        if (servicio.maxima() == null) {
            throw new ErrorPlanificacion(
                    409, "configuracion-incompleta", "Configura antes la duración del servicio");
        }
        Programador.exigirDuracion(
                Programador.dias(inicio, fin), servicio.maxima(), servicio.limpieza());
        var datos =
                new MapSqlParameterSource()
                        .addValue("servicio", nueva.tipoServicioId())
                        .addValue("proveedor", nueva.proveedorId())
                        .addValue("estacion", Programador.estacion(inicio))
                        .addValue("inicio", inicio)
                        .addValue("fin", fin)
                        .addValue("usuario", usuario.id());
        return jdbc.queryForObject(
                """
                INSERT INTO mod03_programacion_servicio(tipo_servicio_id, proveedor_id, estacion,
                    fecha_inicio_prevista, fecha_fin_prevista, creado_por_id, usuario_creacion_id,
                    usuario_modificacion_id)
                VALUES (:servicio, :proveedor, :estacion, :inicio, :fin,
                    :usuario, :usuario, :usuario) RETURNING id
                """,
                datos,
                Long.class);
    }
}
