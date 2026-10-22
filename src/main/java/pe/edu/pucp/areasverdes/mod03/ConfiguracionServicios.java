package pe.edu.pucp.areasverdes.mod03;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Configuración de los servicios periódicos por el jefe de sección, con fecha y autor. */
@Service
public class ConfiguracionServicios {

    /** Frecuencia de una estación en días (RF-PLA-001). La práctica y la máxima son opcionales. */
    public record NuevaFrecuencia(
            @NotNull(message = "Indica la frecuencia teórica")
                    @Positive(message = "Debe ser mayor que cero")
                    Integer frecuenciaTeoricaDias,
            @Positive(message = "Debe ser mayor que cero") Integer frecuenciaPracticaDias,
            @Positive(message = "Debe ser mayor que cero") Integer frecuenciaMaximaDias) {}

    static final List<String> ESTACIONES = List.of("Verano", "Otoño", "Invierno", "Primavera");

    private static final String GUARDAR_FRECUENCIA =
            """
            INSERT INTO mod03_frecuencia_servicio(tipo_servicio_id, estacion, frecuencia_teorica_dias,
                frecuencia_practica_dias, frecuencia_maxima_dias, usuario_creacion_id, usuario_modificacion_id)
            VALUES (:servicio, :estacion, :teorica, :practica, :maxima, :usuario, :usuario)
            ON CONFLICT (tipo_servicio_id, estacion) DO UPDATE SET
                frecuencia_teorica_dias = :teorica, frecuencia_practica_dias = :practica,
                frecuencia_maxima_dias = :maxima, fecha_modificacion = now(), usuario_modificacion_id = :usuario
            RETURNING id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ConfiguracionServicios(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void frecuencia(long servicio, String estacion, NuevaFrecuencia nueva, Usuario usuario) {
        Acceso.gestionar(usuario);
        if (!ESTACIONES.contains(estacion)) {
            throw new ErrorPlanificacion(
                    400,
                    "estacion-invalida",
                    "La estación debe ser Verano, Otoño, Invierno o Primavera");
        }
        exigirPeriodico(servicio);
        int habitual =
                nueva.frecuenciaPracticaDias() != null
                        ? nueva.frecuenciaPracticaDias()
                        : nueva.frecuenciaTeoricaDias();
        if (nueva.frecuenciaMaximaDias() != null && nueva.frecuenciaMaximaDias() < habitual) {
            throw new ErrorPlanificacion(
                    400,
                    "frecuencia-maxima-menor",
                    "La frecuencia máxima no puede ser menor que la habitual (%d días)"
                            .formatted(habitual));
        }
        var datos =
                new MapSqlParameterSource()
                        .addValue("servicio", servicio)
                        .addValue("estacion", estacion)
                        .addValue("teorica", nueva.frecuenciaTeoricaDias())
                        .addValue("practica", nueva.frecuenciaPracticaDias())
                        .addValue("maxima", nueva.frecuenciaMaximaDias())
                        .addValue("usuario", usuario.id());
        jdbc.queryForObject(GUARDAR_FRECUENCIA, datos, Long.class);
    }

    private void exigirPeriodico(long servicio) {
        List<Boolean> periodico =
                jdbc.queryForList(
                        "SELECT es_periodico FROM mod05_tiposervicio WHERE id = :id AND activo",
                        Map.of("id", servicio),
                        Boolean.class);
        if (periodico.isEmpty()) {
            throw new ErrorPlanificacion(404, "servicio-no-encontrado", "No existe ese servicio");
        }
        if (!periodico.getFirst()) {
            throw new ErrorPlanificacion(
                    409,
                    "servicio-no-periodico",
                    "Solo los servicios periódicos tienen frecuencia");
        }
    }
}
