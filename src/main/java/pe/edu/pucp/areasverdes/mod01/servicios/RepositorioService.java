package pe.edu.pucp.areasverdes.mod01.servicios;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Repositorio lógico común de jardines, especies, sectores y servicios (RF-T-010, HU-MOD01-012).
 * Lee las tablas de cada módulo dueño sin copiarlas (base-datos.md, «Propiedad de las entidades»).
 * Si la tabla del dueño aún no está en la base, el tipo responde vacío y {@code disponible =
 * false}: no se inventan datos.
 */
@Service
public class RepositorioService {

    public static final int TAMANO_PAGINA = 25;

    public record Elemento(long id, String tipo, String nombre, String detalle, String modulo) {}

    public record Resultado(boolean disponible, long total, List<Elemento> elementos) {}

    /**
     * Fuente de un tipo. El SQL es fijo (no se arma con entradas del usuario); {@code :q} filtra
     * por nombre. Se combinan todas las fuentes disponibles durante la transición entre módulos.
     */
    record Fuente(String tabla, String seleccion) {}

    static final Map<String, List<Fuente>> FUENTES =
            Map.of(
                    "jardin",
                    List.of(
                            new Fuente(
                                    "mod02_area_verde",
                                    "SELECT id, nombre, lugar detalle, 'MOD-02' modulo"
                                            + " FROM mod02_area_verde"
                                            + " WHERE activo"),
                            new Fuente(
                                    "mod05_areaverde",
                                    "SELECT id, nombre, NULL detalle, 'MOD-05' modulo"
                                            + " FROM mod05_areaverde"
                                            + " WHERE activo")),
                    "especie",
                    List.of(
                            new Fuente(
                                    "mod02_especie_vegetal",
                                    "SELECT id, coalesce(nombre_comun, nombre_cientifico) nombre,"
                                            + " nombre_cientifico detalle, 'MOD-02' modulo"
                                            + " FROM mod02_especie_vegetal"
                                            + " WHERE activo")),
                    "sector",
                    List.of(
                            new Fuente(
                                    "mod02_sector",
                                    "SELECT id, nombre, NULL detalle, 'MOD-02' modulo"
                                            + " FROM mod02_sector WHERE activo")),
                    "servicio",
                    List.of(
                            new Fuente(
                                    "mod05_tiposervicio",
                                    "SELECT id, nombre, ejecutor detalle, 'MOD-05' modulo"
                                            + " FROM mod05_tiposervicio"
                                            + " WHERE activo")));

    private final NamedParameterJdbcTemplate jdbc;
    private final Autorizacion autorizacion;

    public RepositorioService(NamedParameterJdbcTemplate jdbc, Autorizacion autorizacion) {
        this.jdbc = jdbc;
        this.autorizacion = autorizacion;
    }

    public List<String> tipos() {
        return List.of("jardin", "especie", "sector", "servicio");
    }

    public Resultado buscar(String tipo, String texto, int pagina, UsuarioSesion usuario) {
        autorizacion.exigir(usuario, "CORE.REPOSITORIO.CONSULTAR");
        List<Fuente> fuentes = FUENTES.get(tipo);
        if (fuentes == null) {
            throw ErrorCore.invalido(
                    "tipo-desconocido", "El tipo debe ser jardin, especie, sector o servicio");
        }
        if (pagina < 1) {
            throw ErrorCore.invalido("pagina-invalida", "La página empieza en 1");
        }
        List<Fuente> disponibles = fuentes.stream().filter(this::existe).toList();
        if (disponibles.isEmpty()) {
            return new Resultado(false, 0, List.of());
        }
        // Lo concatenado sale solo de las constantes de FUENTES (ConsultasFijasTest); la petición
        // elige la clave y su texto va siempre en los parámetros :q y :patron.
        String seleccion =
                String.join(" UNION ALL ", disponibles.stream().map(Fuente::seleccion).toList());
        String consulta =
                "SELECT * FROM ("
                        + seleccion
                        + ") f WHERE (CAST(:q AS text) IS NULL OR f.nombre ILIKE :patron"
                        + " OR f.detalle ILIKE :patron)";
        var p = new HashMap<String, Object>();
        String q = texto == null || texto.isBlank() ? null : texto.trim();
        p.put("q", q);
        p.put("patron", q == null ? null : "%" + escaparLike(q) + "%");
        p.put("limite", TAMANO_PAGINA);
        p.put("desde", (pagina - 1) * TAMANO_PAGINA);
        Long total =
                jdbc.queryForObject("SELECT count(*) FROM (" + consulta + ") c", p, Long.class);
        List<Elemento> elementos =
                jdbc.query(
                        consulta + " ORDER BY f.nombre, f.id LIMIT :limite OFFSET :desde",
                        p,
                        (fila, n) ->
                                new Elemento(
                                        fila.getLong("id"),
                                        tipo,
                                        fila.getString("nombre"),
                                        fila.getString("detalle"),
                                        fila.getString("modulo")));
        return new Resultado(true, total == null ? 0 : total, elementos);
    }

    private boolean existe(Fuente fuente) {
        return jdbc.queryForObject(
                        "SELECT to_regclass(:t) IS NOT NULL",
                        Map.of("t", fuente.tabla()),
                        Boolean.class)
                .booleanValue();
    }

    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
