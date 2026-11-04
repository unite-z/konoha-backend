package pe.edu.pucp.areasverdes.mod04.servicios;

import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.values;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod04.api.Entradas;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod05.ApiError;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class CatalogoActividadesService {
    private final Mod04Repository repo;

    public CatalogoActividadesService(Mod04Repository repo) {
        this.repo = repo;
    }

    @Transactional
    public Map<String, Object> guardar(Long id, Entradas.TipoActividad entrada, Usuario usuario) {
        OperacionCampoService.exigirJefe(usuario);
        String nombre = entrada.nombre().strip();
        if (nombre.isEmpty()) throw ApiError.invalid("El nombre del tipo es obligatorio");
        var clase = repo.get("clase_actividad", entrada.claseId(), true);
        if (!Boolean.TRUE.equals(clase.get("activo")))
            throw ApiError.invalid("La clase de actividad debe estar activa");
        Map<String, Object> anterior = id == null ? Map.of() : repo.get("tipo_actividad", id, true);
        var duplicados =
                repo.rows(
                        "SELECT id FROM mod04_tipo_actividad WHERE clase_id=:clase AND"
                                + " lower(trim(nombre))=lower(:nombre) AND id<>:id",
                        values(
                                "clase",
                                entrada.claseId(),
                                "nombre",
                                nombre,
                                "id",
                                id == null ? -1 : id));
        if (!duplicados.isEmpty())
            throw ApiError.conflict("Ya existe un tipo con ese nombre en la clase");
        var datos =
                values("nombre", nombre, "clase_id", entrada.claseId(), "activo", entrada.activo());
        long guardado;
        if (id == null) guardado = repo.insert("tipo_actividad", datos);
        else {
            repo.update("tipo_actividad", id, datos);
            guardado = id;
        }
        var resultado = repo.get("tipo_actividad", guardado, false);
        repo.audit(
                "TipoActividad",
                guardado,
                id == null ? "Crear" : "Actualizar",
                usuario.id(),
                anterior,
                resultado);
        return resultado;
    }
}
