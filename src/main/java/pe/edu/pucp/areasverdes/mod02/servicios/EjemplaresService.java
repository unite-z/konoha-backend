package pe.edu.pucp.areasverdes.mod02.servicios;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class EjemplaresService {

    private static final String CONSULTA =
            "SELECT e.id, e.codigo, e.referencia_catastro, e.codigo_catastro,"
                    + " e.codigo_placa_antigua, e.especie_id, s.nombre_comun especie_nombre_comun,"
                    + " s.nombre_cientifico especie_nombre_cientifico, s.forma_biologica"
                    + " especie_forma_biologica, e.tipo_vegetacion, e.ubicacion,"
                    + " ST_Y(e.ubicacion_geografica)::numeric(10, 7) latitud,"
                    + " ST_X(e.ubicacion_geografica)::numeric(10, 7) longitud, e.cantidad,"
                    + " e.area_verde_id, e.altura_total_m, (e.codigo_placa_antigua IS NOT NULL AND"
                    + " EXISTS (SELECT 1 FROM mod02_ejemplar otro WHERE otro.id <> e.id AND"
                    + " lower(otro.codigo_placa_antigua) = lower(e.codigo_placa_antigua)))"
                    + " advertencia_placa_repetida FROM mod02_ejemplar e LEFT JOIN"
                    + " mod02_especie_vegetal s ON s.id = e.especie_id";

    private final NamedParameterJdbcTemplate jdbc;

    public EjemplaresService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Ejemplar> listar(
            String q, String referencia, Long areaVerdeId, int page, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        if (page < 1) {
            throw new ErrorCoberturas(400, "pagina-invalida", "La página debe ser mayor que cero");
        }
        String termino = q == null ? "" : q.strip();
        String ref = referencia == null ? "" : referencia.strip();
        var parametros =
                new MapSqlParameterSource()
                        .addValue("q", termino)
                        .addValue("referencia", ref)
                        .addValue("area", areaVerdeId)
                        .addValue("limite", 25)
                        .addValue("offset", (page - 1) * 25);
        return jdbc.query(
                CONSULTA
                        + " WHERE (:q = '' OR e.codigo ILIKE '%' || :q || '%' OR"
                        + " e.referencia_catastro ILIKE '%' || :q || '%' OR"
                        + " e.codigo_placa_antigua ILIKE '%' || :q || '%' OR"
                        + " s.nombre_comun ILIKE '%' || :q || '%' OR"
                        + " s.nombre_cientifico ILIKE '%' || :q || '%')"
                        + " AND (:referencia = '' OR e.referencia_catastro = :referencia)"
                        + " AND (CAST(:area AS bigint) IS NULL OR e.area_verde_id = :area)"
                        + " ORDER BY e.id LIMIT :limite OFFSET :offset",
                parametros,
                new DataClassRowMapper<>(Ejemplar.class));
    }

    @Transactional
    public Ejemplar crear(NuevoEjemplar entrada, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        validarParCoordenadas(entrada.latitud(), entrada.longitud());
        validarEspecie(entrada.especieId());
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod02_ejemplar (codigo_placa_antigua, especie_id,"
                            + " tipo_vegetacion, ubicacion, ubicacion_geografica, cantidad,"
                            + " altura_total_m) VALUES (:placa, :especie, :vegetacion, :ubicacion,"
                            + " CASE WHEN CAST(:latitud AS numeric) IS NULL THEN NULL ELSE"
                            + " ST_SetSRID (ST_MakePoint(CAST(:longitud AS double precision),"
                            + " CAST(:latitud AS double precision)), 4326)::geometry(Point, 4326)"
                            + " END, :cantidad, :altura) RETURNING id",
                        parametros(entrada),
                        Long.class);
        return buscar(id);
    }

    @Transactional
    public Ejemplar cambiar(long id, CambioEjemplar cambio, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        if (cambio.especieId() == null
                && cambio.tipoVegetacion() == null
                && cambio.ubicacion() == null
                && cambio.latitud() == null
                && cambio.longitud() == null
                && cambio.cantidad() == null
                && cambio.alturaTotalM() == null
                && cambio.codigoPlacaAntigua() == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica algún dato que quieras modificar");
        }
        boolean cambiaUbicacion = cambio.latitud() != null || cambio.longitud() != null;
        if (cambiaUbicacion
                && ((cambio.latitud() == null || cambio.longitud() == null)
                        && !tieneUbicacion(id))) {
            throw new ErrorCoberturas(
                    400,
                    "coordenadas-incompletas",
                    "Indica latitud y longitud para registrar la ubicación");
        }
        if (cambio.latitud() != null
                && cambio.longitud() != null
                && ((cambio.latitud().compareTo(BigDecimal.valueOf(-90)) < 0)
                        || (cambio.latitud().compareTo(BigDecimal.valueOf(90)) > 0)
                        || (cambio.longitud().compareTo(BigDecimal.valueOf(-180)) < 0)
                        || (cambio.longitud().compareTo(BigDecimal.valueOf(180)) > 0))) {
            throw new ErrorCoberturas(
                    400, "coordenadas-invalidas", "Las coordenadas están fuera de rango");
        }
        validarEspecie(cambio.especieId());
        int filas =
                jdbc.update(
                        "UPDATE mod02_ejemplar SET especie_id = coalesce(:especie, especie_id),"
                                + " tipo_vegetacion = coalesce(:vegetacion, tipo_vegetacion),"
                                + " ubicacion = coalesce(:ubicacion, ubicacion),"
                                + " ubicacion_geografica = CASE WHEN :latitud IS NULL AND"
                                + " :longitud IS NULL THEN ubicacion_geografica ELSE"
                                + " ST_SetSRID(ST_MakePoint(coalesce(:longitud,"
                                + " ST_X(ubicacion_geografica)), coalesce(:latitud,"
                                + " ST_Y(ubicacion_geografica))), 4326)::geometry(Point, 4326) END,"
                                + " cantidad = coalesce(:cantidad, cantidad),"
                                + " altura_total_m = coalesce(:altura, altura_total_m),"
                                + " codigo_placa_antigua = coalesce(:placa, codigo_placa_antigua)"
                                + " WHERE id = :id",
                        parametros(cambio).addValue("id", id));
        if (filas == 0) {
            throw noExiste();
        }
        return buscar(id);
    }

    @Transactional
    public Ejemplar quitarPlacaAntigua(long id, Usuario usuario) {
        AccesoCoberturas.registrar(usuario);
        int filas =
                jdbc.update(
                        "UPDATE mod02_ejemplar SET codigo_placa_antigua = NULL WHERE id = :id",
                        Map.of("id", id));
        if (filas == 0) {
            throw noExiste();
        }
        return buscar(id);
    }

    public List<Intervencion> historialActividades(long id, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        buscar(id);
        return jdbc.query(
                """
                SELECT a.id actividad_id,a.fecha_hora_actividad,coalesce(a.tipo_actividad_nombre,t.nombre) tipo_actividad,
                    a.observaciones,a.pendiente_vinculacion
                FROM mod04_actividad a
                JOIN mod04_tipo_actividad t ON t.id=a.tipo_actividad_id
                WHERE a.ejemplar_id=:id ORDER BY a.fecha_hora_actividad DESC,a.id DESC
                """,
                Map.of("id", id),
                new DataClassRowMapper<>(Intervencion.class));
    }

    private Ejemplar buscar(long id) {
        return jdbc.queryForObject(
                CONSULTA + " WHERE e.id = :id",
                Map.of("id", id),
                new DataClassRowMapper<>(Ejemplar.class));
    }

    private void validarEspecie(Long especieId) {
        if (especieId == null) return;
        Boolean activa =
                jdbc.query(
                        "SELECT activo FROM mod02_especie_vegetal WHERE id = :id",
                        Map.of("id", especieId),
                        rs -> rs.next() ? rs.getBoolean(1) : null);
        if (!Boolean.TRUE.equals(activa)) {
            throw new ErrorCoberturas(
                    404, "especie-no-encontrada", "La especie no existe o está inactiva");
        }
    }

    private boolean tieneUbicacion(long id) {
        return Boolean.TRUE.equals(
                jdbc.query(
                        "SELECT ubicacion_geografica IS NOT NULL FROM mod02_ejemplar WHERE id ="
                                + " :id",
                        Map.of("id", id),
                        rs -> rs.next() && rs.getBoolean(1)));
    }

    private static void validarParCoordenadas(BigDecimal latitud, BigDecimal longitud) {
        if ((latitud == null) != (longitud == null)) {
            throw new ErrorCoberturas(
                    400,
                    "coordenadas-incompletas",
                    "Indica latitud y longitud juntas, o deja ambas vacías");
        }
        if (latitud != null
                && (latitud.compareTo(BigDecimal.valueOf(-90)) < 0
                        || latitud.compareTo(BigDecimal.valueOf(90)) > 0
                        || longitud.compareTo(BigDecimal.valueOf(-180)) < 0
                        || longitud.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new ErrorCoberturas(
                    400, "coordenadas-invalidas", "Las coordenadas están fuera de rango");
        }
    }

    private static MapSqlParameterSource parametros(NuevoEjemplar entrada) {
        return new MapSqlParameterSource()
                .addValue("placa", entrada.codigoPlacaAntigua())
                .addValue("especie", entrada.especieId())
                .addValue("vegetacion", entrada.tipoVegetacion())
                .addValue("ubicacion", entrada.ubicacion())
                .addValue("latitud", entrada.latitud())
                .addValue("longitud", entrada.longitud())
                .addValue("cantidad", entrada.cantidad() == null ? 1 : entrada.cantidad())
                .addValue("altura", entrada.alturaTotalM());
    }

    private static MapSqlParameterSource parametros(CambioEjemplar cambio) {
        return new MapSqlParameterSource()
                .addValue("placa", cambio.codigoPlacaAntigua())
                .addValue("especie", cambio.especieId())
                .addValue("vegetacion", cambio.tipoVegetacion())
                .addValue("ubicacion", cambio.ubicacion())
                .addValue("latitud", cambio.latitud())
                .addValue("longitud", cambio.longitud())
                .addValue("cantidad", cambio.cantidad())
                .addValue("altura", cambio.alturaTotalM());
    }

    private ErrorCoberturas noExiste() {
        return new ErrorCoberturas(404, "ejemplar-no-encontrado", "No existe ese ejemplar");
    }

    public record Ejemplar(
            long id,
            String codigo,
            String referenciaCatastro,
            String codigoCatastro,
            String codigoPlacaAntigua,
            Long especieId,
            String especieNombreComun,
            String especieNombreCientifico,
            String especieFormaBiologica,
            String tipoVegetacion,
            String ubicacion,
            BigDecimal latitud,
            BigDecimal longitud,
            int cantidad,
            Long areaVerdeId,
            BigDecimal alturaTotalM,
            boolean advertenciaPlacaRepetida) {}

    public record Intervencion(
            long actividadId,
            java.time.OffsetDateTime fechaHoraActividad,
            String tipoActividad,
            String observaciones,
            boolean pendienteVinculacion) {}

    public record NuevoEjemplar(
            String codigoPlacaAntigua,
            Long especieId,
            String tipoVegetacion,
            String ubicacion,
            BigDecimal latitud,
            BigDecimal longitud,
            Integer cantidad,
            BigDecimal alturaTotalM) {}

    public record CambioEjemplar(
            String codigoPlacaAntigua,
            Long especieId,
            String tipoVegetacion,
            String ubicacion,
            BigDecimal latitud,
            BigDecimal longitud,
            Integer cantidad,
            BigDecimal alturaTotalM) {}
}
