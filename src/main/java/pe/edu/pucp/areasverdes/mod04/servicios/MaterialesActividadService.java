package pe.edu.pucp.areasverdes.mod04.servicios;

import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.values;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod04.api.Entradas;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod05.ApiError;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class MaterialesActividadService {
    private final Mod04Repository repo;
    private final OperacionCampoService campo;

    public MaterialesActividadService(Mod04Repository repo, OperacionCampoService campo) {
        this.repo = repo;
        this.campo = campo;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(long actividadId, Usuario usuario) {
        campo.parteDeActividad(actividadId, usuario, false);
        return repo.rows(
                """
                SELECT m.*,COALESCE(c.nombre,m.descripcion_otro) nombre_material,u.simbolo unidad_simbolo
                FROM mod04_material_actividad m LEFT JOIN mod04_material_catalogo c ON c.id=m.material_id
                JOIN mod04_unidad_medida u ON u.id=m.unidad_medida_id
                WHERE m.actividad_id=:id ORDER BY m.id
                """,
                Map.of("id", actividadId));
    }

    @Transactional
    public Map<String, Object> crear(
            long actividadId, Entradas.MaterialActividad entrada, Usuario usuario) {
        campo.parteDeActividad(actividadId, usuario, true);
        boolean otro = entrada.materialId() == null;
        String descripcion =
                entrada.descripcionOtro() == null ? "" : entrada.descripcionOtro().strip();
        if (otro == descripcion.isEmpty())
            throw ApiError.invalid("Selecciona un material o describe Otro");
        String origen = entrada.origen() == null ? "" : entrada.origen().strip();
        String destino = entrada.destino() == null ? "" : entrada.destino().strip();
        if (origen.isEmpty() != destino.isEmpty())
            throw ApiError.invalid("El traslado requiere origen y destino");
        if (entrada.materialId() != null) {
            var material =
                    repo.rows(
                            "SELECT id FROM mod04_material_catalogo WHERE id=:id AND activo",
                            Map.of("id", entrada.materialId()));
            if (material.isEmpty()) throw ApiError.invalid("El material no está disponible");
        }
        if (repo.rows(
                        "SELECT id FROM mod04_unidad_medida WHERE id=:id",
                        Map.of("id", entrada.unidadMedidaId()))
                .isEmpty()) throw ApiError.invalid("La unidad de medida no existe");
        long id =
                repo.insert(
                        "material_actividad",
                        values(
                                "actividad_id",
                                actividadId,
                                "material_id",
                                entrada.materialId(),
                                "descripcion_otro",
                                otro ? descripcion : null,
                                "cantidad",
                                entrada.cantidad(),
                                "unidad_medida_id",
                                entrada.unidadMedidaId(),
                                "origen",
                                origen.isEmpty() ? null : origen,
                                "destino",
                                destino.isEmpty() ? null : destino,
                                "usuario_creacion_id",
                                usuario.id()));
        var resultado = repo.get("material_actividad", id, false);
        repo.audit("MaterialActividad", id, "Crear", usuario.id(), Map.of(), resultado);
        return resultado;
    }
}
