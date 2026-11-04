package pe.edu.pucp.areasverdes.mod03;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valores iniciales de la sección por servicio periódico (AV-363): las frecuencias de las cuatro
 * estaciones y la duración de las que parte el jefe con «Usar valores iniciales».
 */
@Service
public class ValoresIniciales {

    public record FrecuenciaInicial(
            String estacion,
            int frecuenciaTeoricaDias,
            Integer frecuenciaPracticaDias,
            Integer frecuenciaMaximaDias) {}

    public record DuracionInicial(
            int duracionMaximaDias,
            Integer diasLimpieza,
            LocalTime horaInicio,
            LocalTime horaFin) {}

    public record Valores(List<FrecuenciaInicial> frecuencias, DuracionInicial duracion) {}

    private final NamedParameterJdbcTemplate jdbc;

    public ValoresIniciales(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Los valores del servicio; 404 si el servicio no existe o la sección no tiene valores. */
    public Valores de(long servicio) {
        List<DuracionInicial> duracion =
                jdbc.query(
                        "SELECT d.* FROM mod03_valor_inicial_duracion d JOIN mod05_tiposervicio t"
                                + " ON t.id = d.tipo_servicio_id WHERE t.id = :id AND t.activo"
                                + " AND t.es_periodico",
                        Map.of("id", servicio),
                        new DataClassRowMapper<>(DuracionInicial.class));
        List<FrecuenciaInicial> frecuencias =
                jdbc.query(
                        "SELECT * FROM mod03_valor_inicial_frecuencia WHERE tipo_servicio_id = :id"
                                + " ORDER BY array_position(ARRAY['Verano','Otoño','Invierno',"
                                + "'Primavera']::varchar[], estacion)",
                        Map.of("id", servicio),
                        new DataClassRowMapper<>(FrecuenciaInicial.class));
        if (duracion.isEmpty() || frecuencias.isEmpty()) {
            throw new ErrorPlanificacion(
                    404,
                    "sin-valores-iniciales",
                    "La sección no tiene valores iniciales para ese servicio");
        }
        return new Valores(frecuencias, duracion.getFirst());
    }

    /**
     * Precarga del perfil dev: los valores de la sección para los servicios de demostración, por
     * nombre. No toca los que ya existen.
     */
    @Transactional
    public void precargar() {
        precargar("Corte de césped", 21, 35, 45, 3, 1, null, null);
        precargar(
                "Control fitosanitario",
                90,
                90,
                100,
                1,
                0,
                LocalTime.of(6, 0),
                LocalTime.of(18, 0));
        precargar("Poda mayor programada", 365, 365, 365, 10, 0, null, null);
    }

    private void precargar(
            String servicio,
            int teorica,
            int practica,
            int maxima,
            int duracion,
            int limpieza,
            LocalTime horaInicio,
            LocalTime horaFin) {
        var datos =
                new MapSqlParameterSource()
                        .addValue("servicio", servicio)
                        .addValue("teorica", teorica)
                        .addValue("practica", practica)
                        .addValue("maxima", maxima)
                        .addValue("duracion", duracion)
                        .addValue("limpieza", limpieza)
                        .addValue("horaInicio", horaInicio)
                        .addValue("horaFin", horaFin);
        jdbc.update(
                """
                INSERT INTO mod03_valor_inicial_frecuencia (tipo_servicio_id, estacion,
                    frecuencia_teorica_dias, frecuencia_practica_dias, frecuencia_maxima_dias)
                SELECT t.id, e, :teorica, :practica, :maxima FROM mod05_tiposervicio t,
                    unnest(ARRAY['Verano', 'Otoño', 'Invierno', 'Primavera']) e
                WHERE t.nombre = :servicio
                ON CONFLICT DO NOTHING
                """,
                datos);
        jdbc.update(
                """
                INSERT INTO mod03_valor_inicial_duracion (tipo_servicio_id, duracion_maxima_dias,
                    dias_limpieza, hora_inicio, hora_fin)
                SELECT t.id, :duracion, :limpieza, CAST(:horaInicio AS time), CAST(:horaFin AS time)
                FROM mod05_tiposervicio t WHERE t.nombre = :servicio
                ON CONFLICT DO NOTHING
                """,
                datos);
    }
}
