package pe.edu.pucp.areasverdes.mod03;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Configuración de los servicios periódicos por el jefe de sección, con autor y auditoría. */
@Service
public class ConfiguracionServicios {

    /** Frecuencia de una estación en días (RF-PLA-001). La práctica y la máxima son opcionales. */
    public record NuevaFrecuencia(
            @NotNull(message = "Indica la frecuencia teórica")
                    @Positive(message = "Debe ser mayor que cero")
                    Integer frecuenciaTeoricaDias,
            @Positive(message = "Debe ser mayor que cero") Integer frecuenciaPracticaDias,
            @Positive(message = "Debe ser mayor que cero") Integer frecuenciaMaximaDias) {}

    /** Duración máxima en días (RF-PLA-002), días de limpieza y horario permitido. */
    public record NuevaDuracion(
            @NotNull(message = "Indica la duración máxima")
                    @Positive(message = "Debe ser mayor que cero")
                    Integer duracionMaximaDias,
            @PositiveOrZero(message = "No puede ser negativo") Integer diasLimpieza,
            LocalTime horaInicio,
            LocalTime horaFin) {}

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

    private static final String GUARDAR_DURACION =
            """
            INSERT INTO mod03_duracion_servicio(tipo_servicio_id, duracion_maxima_dias, dias_limpieza,
                hora_inicio, hora_fin, usuario_creacion_id, usuario_modificacion_id)
            VALUES (:servicio, :maxima, :limpieza, :inicio, :fin, :usuario, :usuario)
            ON CONFLICT (tipo_servicio_id) DO UPDATE SET duracion_maxima_dias = :maxima,
                dias_limpieza = :limpieza, hora_inicio = :inicio, hora_fin = :fin,
                fecha_modificacion = now(), usuario_modificacion_id = :usuario
            RETURNING id
            """;

    /** Fila de auditoría (patrón de MOD-05) con el estado anterior y el nuevo de la tabla dada. */
    private static final String AUDITAR =
            """
            INSERT INTO mod05_registroauditoria(entidad, registro_id, accion, fecha_hora,
                valores_anteriores, valores_nuevos, motivo, usuario_id)
            SELECT '%1$s', t.id, 'configurar', now(), CAST(:antes AS jsonb), to_jsonb(t), '', :usuario
            FROM %1$s t WHERE t.id = :id
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
        guardar(
                "mod03_frecuencia_servicio",
                "tipo_servicio_id = :servicio AND estacion = :estacion",
                GUARDAR_FRECUENCIA,
                datos);
    }

    @Transactional
    public void duracion(long servicio, NuevaDuracion nueva, Usuario usuario) {
        Acceso.gestionar(usuario);
        exigirPeriodico(servicio);
        if (nueva.horaInicio() != null
                && nueva.horaFin() != null
                && !nueva.horaFin().isAfter(nueva.horaInicio())) {
            throw new ErrorPlanificacion(
                    400, "horario-invalido", "La hora de fin debe ser posterior a la de inicio");
        }
        var datos =
                new MapSqlParameterSource()
                        .addValue("servicio", servicio)
                        .addValue("maxima", nueva.duracionMaximaDias())
                        .addValue("limpieza", nueva.diasLimpieza())
                        .addValue("inicio", nueva.horaInicio())
                        .addValue("fin", nueva.horaFin())
                        .addValue("usuario", usuario.id());
        guardar("mod03_duracion_servicio", "tipo_servicio_id = :servicio", GUARDAR_DURACION, datos);
    }

    /** Guarda con la sentencia dada y deja la auditoría. Tabla y filtro vienen del código. */
    private void guardar(
            String tabla, String filtro, String sentencia, MapSqlParameterSource datos) {
        String antes =
                jdbc.query(
                        "SELECT to_jsonb(t)::text FROM " + tabla + " t WHERE " + filtro,
                        datos,
                        rs -> rs.next() ? rs.getString(1) : "{}");
        long id = jdbc.queryForObject(sentencia, datos, Long.class);
        jdbc.update(AUDITAR.formatted(tabla), datos.addValue("id", id).addValue("antes", antes));
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
                    409, "servicio-no-periodico", "Solo se configuran los servicios periódicos");
        }
    }
}
