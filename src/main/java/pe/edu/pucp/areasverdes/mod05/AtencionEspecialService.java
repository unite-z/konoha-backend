package pe.edu.pucp.areasverdes.mod05;

import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;

import java.time.*;
import java.util.*;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-INV-011: marcar ejemplares que requieren atención fitosanitaria especial e indicar la plaga
 * que los afecta. Lo pueden hacer los tres roles del módulo. Las aplicaciones adicionales se
 * registran en un requisito posterior: aquí solo se fija cuántas corresponden (una o dos al año) y
 * se parte de cero realizadas.
 */
@Service
public class AtencionEspecialService {
    final Mod05Repository repo;
    final ConsultasService queries;
    final Clock clock;

    public AtencionEspecialService(Mod05Repository repo, ConsultasService queries, Clock clock) {
        this.repo = repo;
        this.queries = queries;
        this.clock = clock;
    }

    public static final String EN_SEGUIMIENTO = "En seguimiento",
            COMPLETAS = "Aplicaciones completas";
    static final List<String> ESTADOS = List.of(EN_SEGUIMIENTO, COMPLETAS);
    static final String ESPECIALES =
            """
            SELECT ae.id,ae.ejemplar_id,e.codigo ejemplar_codigo,e.area_verde_id,av.nombre area_verde_nombre,ae.plaga_id,pl.nombre plaga_nombre,
                ae.aplicaciones_adicionales,ae.aplicaciones_realizadas,
                CASE WHEN ae.aplicaciones_realizadas>=ae.aplicaciones_adicionales THEN 'Aplicaciones completas' ELSE 'En seguimiento' END estado,
                ae.fecha_creacion,ae.usuario_creacion_id
            FROM mod05_atencion_especial ae JOIN mod05_ejemplar e ON e.id=ae.ejemplar_id JOIN mod05_plaga pl ON pl.id=ae.plaga_id
            LEFT JOIN mod05_areaverde av ON av.id=e.area_verde_id
            """;

    public ConsultasService.Pagina listar(Usuario user, Map<String, String> params, String url) {
        user.exigir(false);
        String estado = params.getOrDefault("estado", "").strip();
        // Valor de una lista cerrada: nunca se concatena texto de la petición.
        String where =
                estado.isEmpty()
                        ? ""
                        : " WHERE (CASE WHEN"
                                + " ae.aplicaciones_realizadas>=ae.aplicaciones_adicionales THEN"
                                + " 'Aplicaciones completas' ELSE 'En seguimiento' END)='"
                                + ConsultasService.option(estado, ESTADOS)
                                + "'";
        return queries.list(
                new ConsultasService.Catalogo(
                        ESPECIALES + where,
                        "fecha_creacion DESC,id DESC",
                        List.of("ejemplar_codigo", "plaga_nombre", "area_verde_nombre"),
                        Map.of("plaga_id", "id")),
                params,
                url,
                UnaryOperator.identity());
    }

    public Map<String, Object> detalle(Usuario user, long id) {
        user.exigir(false);
        return repo.one(ESPECIALES + " WHERE ae.id=:id", Map.of("id", id));
    }

    public ConsultasService.Pagina plagas(Usuario user, Map<String, String> params, String url) {
        user.exigir(false);
        return queries.list(
                new ConsultasService.Catalogo(
                        "SELECT id,nombre,activo FROM mod05_plaga",
                        "nombre,id",
                        List.of("nombre"),
                        Map.of("activo", "bool")),
                params,
                url,
                UnaryOperator.identity());
    }

    @Transactional
    public Map<String, Object> marcar(EntradasMaestros.AtencionEspecial in, Usuario user) {
        user.exigir(false);
        if (repo.rows("SELECT id FROM mod05_ejemplar WHERE id=:id", Map.of("id", in.ejemplarId()))
                .isEmpty()) throw ApiError.invalid("El ejemplar no existe");
        var plaga = repo.rows("SELECT * FROM mod05_plaga WHERE id=:id", Map.of("id", in.plagaId()));
        if (plaga.isEmpty()) throw ApiError.invalid("La plaga no existe en el catálogo");
        if (!(boolean) plaga.getFirst().get("activo"))
            throw ApiError.invalid("La plaga está inactiva en el catálogo");
        // Serializa el marcado de un mismo ejemplar; la restricción UNIQUE de la base es la última
        // defensa.
        repo.jdbc().getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(505102)");
        if (!repo.rows(
                        "SELECT id FROM mod05_atencion_especial WHERE ejemplar_id=:id",
                        Map.of("id", in.ejemplarId()))
                .isEmpty())
            throw ApiError.conflict("Este ejemplar ya está marcado con atención especial");
        long id =
                repo.insert(
                        "atencion_especial",
                        values(
                                "ejemplar_id",
                                in.ejemplarId(),
                                "plaga_id",
                                in.plagaId(),
                                "aplicaciones_adicionales",
                                in.aplicacionesAdicionales().shortValue(),
                                "aplicaciones_realizadas",
                                (short) 0,
                                "fecha_creacion",
                                OffsetDateTime.now(clock),
                                "usuario_creacion_id",
                                user.id()));
        var creado = repo.one(ESPECIALES + " WHERE ae.id=:id", Map.of("id", id));
        repo.audit("AtencionEspecial", id, "Alta", user, Map.of(), creado, "");
        return creado;
    }
}
