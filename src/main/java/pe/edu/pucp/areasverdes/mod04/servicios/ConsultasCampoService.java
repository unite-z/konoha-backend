package pe.edu.pucp.areasverdes.mod04.servicios;

import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.number;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod05.ApiError;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
@Transactional(readOnly = true)
public class ConsultasCampoService {
    final Mod04Repository repo;
    final OperacionCampoService service;

    public ConsultasCampoService(Mod04Repository repo, OperacionCampoService service) {
        this.repo = repo;
        this.service = service;
    }

    public record Pagina(
            long count, String next, String previous, List<Map<String, Object>> results) {}

    public Pagina listar(String recurso, Map<String, String> params, String url, Usuario user) {
        service.exigirLectura(user);
        String sql =
                switch (recurso) {
                    case "personal" ->
                            """
                            SELECT p.id,p.nombres,p.apellidos,p.tipo_personal,p.activo,p.usuario_id,
                                a.cuadrilla_id,c.nombre cuadrilla_nombre,p.nombres||' '||p.apellidos texto
                            FROM mod04_personal p LEFT JOIN mod04_asignacion_cuadrilla a ON a.operario_id=p.id AND a.fecha_fin IS NULL
                            LEFT JOIN mod04_cuadrilla c ON c.id=a.cuadrilla_id
                            """;
                    case "cuadrillas" ->
                            "SELECT id,nombre,capataz_id,activa,nombre texto FROM mod04_cuadrilla";
                    case "encargos" ->
                            "SELECT"
                                + " id,cuadrilla_id,capataz_id,periodicidad,fecha_inicio,fecha_fin,descripcion"
                                + " texto FROM mod04_encargo";
                    case "partes" ->
                            "SELECT id,cuadrilla_id,capataz_id,fecha,estado,estado texto FROM"
                                    + " mod04_parte_diario";
                    case "actividades" ->
                            """
                            SELECT a.*,p.cuadrilla_id,p.capataz_id,p.fecha,p.estado,t.nombre texto,
                                t.nombre tipo_actividad_nombre,av.nombre area_verde_nombre,c.nombre cuadrilla_nombre FROM mod04_actividad a
                            JOIN mod04_parte_diario p ON p.id=a.parte_id JOIN mod04_tipo_actividad t ON t.id=a.tipo_actividad_id
                            JOIN mod05_areaverde av ON av.id=a.area_verde_id JOIN mod04_cuadrilla c ON c.id=p.cuadrilla_id
                            """;
                    case "tipos-actividad" ->
                            "SELECT id,nombre,activo,nombre texto FROM mod04_tipo_actividad";
                    case "areas-verdes" ->
                            "SELECT id,nombre,activo,nombre texto FROM mod05_areaverde";
                    default -> throw ApiError.missing("Catálogo inexistente");
                };
        Map<String, String> filters =
                switch (recurso) {
                    case "personal" -> Map.of("tipo_personal", "tipo", "activo", "bool");
                    case "cuadrillas" -> Map.of("capataz_id", "id", "activa", "bool");
                    case "encargos" ->
                            Map.of(
                                    "capataz_id",
                                    "id",
                                    "cuadrilla_id",
                                    "id",
                                    "periodicidad",
                                    "periodo");
                    case "partes" -> Map.of("cuadrilla_id", "id", "fecha", "date");
                    case "actividades" ->
                            Map.of("cuadrilla_id", "id", "parte_id", "id", "fecha", "date");
                    default -> Map.of("activo", "bool");
                };
        Set<String> allowed = new HashSet<>(filters.keySet());
        allowed.addAll(List.of("q", "page"));
        if (recurso.equals("encargos")) allowed.addAll(List.of("desde", "hasta"));
        if (!allowed.containsAll(params.keySet()))
            throw ApiError.invalid("Filtros no admitidos para " + recurso);
        StringBuilder where = new StringBuilder(" WHERE true");
        Map<String, Object> args = new HashMap<>();
        String q = params.getOrDefault("q", "").strip();
        if (q.length() > 100) throw ApiError.invalid("La búsqueda admite hasta 100 caracteres");
        if (!q.isEmpty()) {
            where.append(" AND position(lower(:q) in lower(texto))>0");
            args.put("q", q);
        }
        filters.forEach(
                (field, type) -> {
                    String raw = params.getOrDefault(field, "").strip();
                    if (raw.isEmpty()) return;
                    Object value =
                            switch (type) {
                                case "id" -> positive(raw);
                                case "bool" ->
                                        switch (raw.toLowerCase(Locale.ROOT)) {
                                            case "true", "1" -> true;
                                            case "false", "0" -> false;
                                            default -> throw ApiError.invalid("Booleano inválido");
                                        };
                                case "date" -> date(raw);
                                case "tipo" -> option(raw, List.of("Capataz", "Operario"));
                                case "periodo" -> option(raw, List.of("Semanal", "Quincenal"));
                                default -> throw new IllegalStateException();
                            };
                    where.append(" AND ").append(field).append("=:").append(field);
                    args.put(field, value);
                });
        boolean soloCapataz =
                !OperacionCampoService.jefe(user)
                        && user.roles().stream().noneMatch(Usuario.ROLES::contains);
        if (soloCapataz
                && Set.of("cuadrillas", "encargos", "partes", "actividades").contains(recurso)) {
            Long personal = service.personalUsuario(user);
            if (personal == null) throw OperacionCampoService.forbidden();
            where.append(" AND capataz_id=:propio");
            args.put("propio", personal);
        }
        if (recurso.equals("encargos")) {
            LocalDate desde = null, hasta = null;
            if (!params.getOrDefault("desde", "").isBlank()) {
                desde = date(params.get("desde"));
                where.append(" AND fecha_fin>=:desde");
                args.put("desde", desde);
            }
            if (!params.getOrDefault("hasta", "").isBlank()) {
                hasta = date(params.get("hasta"));
                where.append(" AND fecha_inicio<=:hasta");
                args.put("hasta", hasta);
            }
            if (desde != null && hasta != null && desde.isAfter(hasta))
                throw ApiError.invalid("desde no puede ser posterior a hasta");
        }
        String base = " FROM (" + sql + ") datos" + where;
        long count = repo.jdbc().queryForObject("SELECT count(*)" + base, args, Long.class);
        long pages = Math.max(1, (count + 24) / 25), page;
        try {
            String raw = params.getOrDefault("page", "1");
            page = raw.equals("last") ? pages : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw ApiError.missing("Página inválida");
        }
        if (page < 1 || page > pages) throw ApiError.missing("Página inexistente");
        args.put("offset", (page - 1) * 25);
        var results =
                repo.rows("SELECT *" + base + " ORDER BY id LIMIT 25 OFFSET :offset", args).stream()
                        .map(
                                row -> {
                                    row.remove("texto");
                                    long id = number(row, "id");
                                    if (recurso.equals("cuadrillas")) return repo.cuadrilla(id);
                                    if (recurso.equals("encargos")) return repo.encargo(id);
                                    if (recurso.equals("actividades"))
                                        row.put("participantes", repo.participantes(id));
                                    return row;
                                })
                        .toList();
        return new Pagina(
                count,
                page < pages ? link(url, params, page + 1) : null,
                page > 1 ? link(url, params, page - 1) : null,
                results);
    }

    static String option(String value, List<String> options) {
        if (!options.contains(value)) throw ApiError.invalid("Valor de filtro inválido");
        return value;
    }

    static long positive(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) return value;
        } catch (NumberFormatException ignored) {
        }
        throw ApiError.invalid("El identificador debe ser un entero positivo");
    }

    static LocalDate date(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw ApiError.invalid("La fecha debe tener formato YYYY-MM-DD");
        }
    }

    static String link(String url, Map<String, String> params, long page) {
        var builder = UriComponentsBuilder.fromUriString(url);
        params.forEach(
                (k, v) -> {
                    if (!k.equals("page")) builder.queryParam(k, v);
                });
        return builder.queryParam("page", page).build().encode().toUriString();
    }
}
