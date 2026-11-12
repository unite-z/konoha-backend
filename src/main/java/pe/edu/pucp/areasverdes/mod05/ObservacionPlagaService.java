package pe.edu.pucp.areasverdes.mod05;

import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;

import java.time.*;
import java.util.*;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-FIT-005: observaciones de presencia de plagas en un espacio o ejemplar que justifican una
 * aplicación o su intensidad. Lo pueden registrar y consultar los tres roles del módulo. Es un
 * registro de campo, no editable: no sustituye la evaluación técnica con una sugerencia de IA.
 */
@Service
public class ObservacionPlagaService {
    final Mod05Repository repo;
    final ConsultasService queries;
    final Clock clock;

    public ObservacionPlagaService(Mod05Repository repo, ConsultasService queries, Clock clock) {
        this.repo = repo;
        this.queries = queries;
        this.clock = clock;
    }

    static final String OBSERVACIONES =
            """
            SELECT o.id,o.area_verde_id,av.nombre area_verde_nombre,o.ejemplar_id,e.codigo ejemplar_codigo,
                o.plaga_id,pl.nombre plaga_nombre,o.descripcion,o.fecha_observacion,
                o.usuario_creacion_id,u.nombre usuario_creacion_nombre,o.fecha_creacion
            FROM mod05_observacion_plaga o
            LEFT JOIN mod05_areaverde av ON av.id=o.area_verde_id
            LEFT JOIN mod05_ejemplar e ON e.id=o.ejemplar_id
            JOIN mod05_plaga pl ON pl.id=o.plaga_id
            JOIN mod05_usuario u ON u.id=o.usuario_creacion_id
            """;

    public ConsultasService.Pagina listar(Usuario user, Map<String, String> params, String url) {
        user.exigir(Usuario.REGISTRAR_ATENCION);
        return queries.list(
                new ConsultasService.Catalogo(
                        OBSERVACIONES,
                        "fecha_observacion DESC,id DESC",
                        List.of(
                                "plaga_nombre",
                                "area_verde_nombre",
                                "ejemplar_codigo",
                                "descripcion"),
                        Map.of("area_verde_id", "id", "ejemplar_id", "id", "plaga_id", "id")),
                params,
                url,
                UnaryOperator.identity());
    }

    public Map<String, Object> detalle(Usuario user, long id) {
        user.exigir(Usuario.REGISTRAR_ATENCION);
        return repo.one(OBSERVACIONES + " WHERE o.id=:id", Map.of("id", id));
    }

    /**
     * Para el aviso del asistente de «Nueva atención»: observaciones de los espacios/ejemplares que
     * ya forman parte de los alcances elegidos, sin paginar. Vacío si no se indica ningún destino.
     */
    public List<Map<String, Object>> porAlcance(
            Usuario user, List<Long> areaVerdeIds, List<Long> ejemplarIds) {
        user.exigir(Usuario.REGISTRAR_ATENCION);
        if (areaVerdeIds.isEmpty() && ejemplarIds.isEmpty()) return List.of();
        return repo.rows(
                OBSERVACIONES
                        + " WHERE o.area_verde_id IN (:areas) OR o.ejemplar_id IN (:ejemplares)"
                        + " ORDER BY o.fecha_observacion DESC,o.id DESC LIMIT 50",
                Map.of(
                        "areas", areaVerdeIds.isEmpty() ? List.of(-1L) : areaVerdeIds,
                        "ejemplares", ejemplarIds.isEmpty() ? List.of(-1L) : ejemplarIds));
    }

    @Transactional
    public Map<String, Object> registrar(EntradasFitosanitario.ObservacionPlaga in, Usuario user) {
        user.exigir(Usuario.REGISTRAR_ATENCION);
        if ((in.area_verde_id() == null) == (in.ejemplar_id() == null))
            throw ApiError.invalid("Indica solo un área verde o un ejemplar");
        if (in.area_verde_id() != null) {
            if (!(boolean) repo.get("areaverde", in.area_verde_id()).get("activo"))
                throw ApiError.invalid("El área verde está inactiva");
        } else repo.get("ejemplar", in.ejemplar_id());
        var plaga = repo.get("plaga", in.plaga_id());
        if (!(boolean) plaga.get("activo"))
            throw ApiError.invalid("La plaga está inactiva en el catálogo");
        if (in.fecha_observacion().isAfter(LocalDate.now(clock)))
            throw ApiError.invalid("La fecha de la observación no puede ser futura");
        long id =
                repo.insert(
                        "observacion_plaga",
                        values(
                                "area_verde_id",
                                in.area_verde_id(),
                                "ejemplar_id",
                                in.ejemplar_id(),
                                "plaga_id",
                                in.plaga_id(),
                                "descripcion",
                                in.descripcion().strip(),
                                "fecha_observacion",
                                in.fecha_observacion(),
                                "fecha_creacion",
                                OffsetDateTime.now(clock),
                                "usuario_creacion_id",
                                user.id()));
        var creado = repo.one(OBSERVACIONES + " WHERE o.id=:id", Map.of("id", id));
        repo.audit("ObservacionPlaga", id, "Alta", user, Map.of(), creado, "");
        return creado;
    }
}
