package pe.edu.pucp.areasverdes.mod06.ia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Resumen del uso de la IA y precios por modelo (AV-396). El coste de cada llamada sale de sus
 * tokens y del precio vigente en su fecha (Lima); sin precio o sin tokens, el coste es nulo.
 */
@Repository
public class ResumenUsoIaRepository {

    /** Llamadas del periodo con su fecha en Lima y su coste en USD. */
    private static final String BASE =
            "WITH base AS (SELECT u.*, (u.solicitado_en AT TIME ZONE 'America/Lima')::date AS"
                + " fecha, CASE WHEN p.modelo IS NULL OR u.tokens_entrada IS NULL THEN NULL ELSE"
                + " ((u.tokens_entrada - COALESCE(u.tokens_cache, 0)) * p.usd_millon_entrada +"
                + " COALESCE(u.tokens_cache, 0) * COALESCE(p.usd_millon_cache,"
                + " p.usd_millon_entrada) + COALESCE(u.tokens_salida, 0) * p.usd_millon_salida) /"
                + " 1000000.0 END AS coste FROM mod06_uso_ia u LEFT JOIN LATERAL (SELECT * FROM"
                + " mod06_precio_ia p WHERE p.modelo = u.modelo AND p.vigente_desde <="
                + " (u.solicitado_en AT TIME ZONE 'America/Lima')::date ORDER BY p.vigente_desde"
                + " DESC LIMIT 1) p ON true WHERE (u.solicitado_en AT TIME ZONE"
                + " 'America/Lima')::date BETWEEN :desde AND :hasta) ";

    private static final String METRICAS =
            "count(*) AS llamadas, count(*) FILTER (WHERE NOT correcto) AS fallidas,"
                    + " round(avg(duracion_ms)) AS duracion_media_ms, COALESCE(sum(tokens_entrada),"
                    + " 0) AS tokens_entrada, COALESCE(sum(tokens_salida), 0) AS tokens_salida,"
                    + " COALESCE(sum(tokens_cache), 0) AS tokens_cache, round(sum(coste)::numeric,"
                    + " 6) AS coste_usd, count(*) FILTER (WHERE correcto AND coste IS NULL) AS"
                    + " sin_coste";

    private final NamedParameterJdbcTemplate jdbc;

    public ResumenUsoIaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource periodo(LocalDate desde, LocalDate hasta) {
        return new MapSqlParameterSource().addValue("desde", desde).addValue("hasta", hasta);
    }

    public Map<String, Object> totales(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForMap(
                BASE
                        + "SELECT "
                        + METRICAS
                        + ", percentile_disc(0.95) WITHIN GROUP (ORDER BY duracion_ms) AS"
                        + " duracion_p95_ms FROM base",
                periodo(desde, hasta));
    }

    public List<Map<String, Object>> porAsistente(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForList(
                BASE
                        + "SELECT asistente, "
                        + METRICAS
                        + " FROM base GROUP BY asistente ORDER BY"
                        + " llamadas DESC, asistente",
                periodo(desde, hasta));
    }

    public List<Map<String, Object>> porModelo(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForList(
                BASE
                        + "SELECT proveedor, modelo, "
                        + METRICAS
                        + " FROM base GROUP BY proveedor,"
                        + " modelo ORDER BY llamadas DESC, modelo",
                periodo(desde, hasta));
    }

    public List<Map<String, Object>> porDia(LocalDate desde, LocalDate hasta) {
        return jdbc.queryForList(
                BASE + "SELECT fecha, " + METRICAS + " FROM base GROUP BY fecha ORDER BY fecha",
                periodo(desde, hasta));
    }

    public List<Map<String, Object>> historial(LocalDate desde, LocalDate hasta, int limite) {
        return jdbc.queryForList(
                BASE
                        + "SELECT id, solicitado_en, asistente, proveedor, modelo, duracion_ms,"
                        + " correcto, error, tokens_entrada, tokens_salida, tokens_cache,"
                        + " round(coste::numeric, 6) AS coste_usd FROM base ORDER BY solicitado_en"
                        + " DESC, id DESC LIMIT :limite",
                periodo(desde, hasta).addValue("limite", limite));
    }

    public List<Map<String, Object>> precios() {
        return jdbc.queryForList(
                "SELECT modelo, vigente_desde, usd_millon_entrada, usd_millon_salida,"
                        + " usd_millon_cache FROM mod06_precio_ia ORDER BY modelo, vigente_desde"
                        + " DESC",
                Map.of());
    }

    public void guardarPrecio(
            String modelo,
            LocalDate vigenteDesde,
            BigDecimal entrada,
            BigDecimal salida,
            BigDecimal cache) {
        jdbc.update(
                "INSERT INTO mod06_precio_ia (modelo, vigente_desde, usd_millon_entrada,"
                    + " usd_millon_salida, usd_millon_cache) VALUES (:modelo, :desde, :entrada,"
                    + " :salida, :cache) ON CONFLICT (modelo, vigente_desde) DO UPDATE SET"
                    + " usd_millon_entrada = EXCLUDED.usd_millon_entrada, usd_millon_salida ="
                    + " EXCLUDED.usd_millon_salida, usd_millon_cache = EXCLUDED.usd_millon_cache",
                new MapSqlParameterSource()
                        .addValue("modelo", modelo)
                        .addValue("desde", vigenteDesde)
                        .addValue("entrada", entrada)
                        .addValue("salida", salida)
                        .addValue("cache", cache));
    }
}
