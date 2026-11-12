package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;

/**
 * {@link CatalogoFormatos} sobre una {@link FuenteDatos} ({@code FormatoReporteServicio} y {@code
 * CampoFormatoReporte}). Solo lo usan las pruebas que construyen sus datos en memoria.
 *
 * <p>Transitorio: desaparece con el sistema {@code FuenteDatos} (Fase 2 del plan de MOD-06).
 */
public final class CatalogoFormatosFuenteDatos implements CatalogoFormatos {

    private final FuenteDatos fuente;

    public CatalogoFormatosFuenteDatos(FuenteDatos fuente) {
        this.fuente = fuente;
    }

    @Override
    public Map<String, Object> formatoVigente(Object tipoServicioId) {
        if (tipoServicioId == null) return null;
        Map<String, Object> mejor = null;
        List<Object> claveMejor = null;
        for (Map<String, Object> f :
                fuente.listar(
                        "FormatoReporteServicio",
                        "tipo_servicio_id",
                        tipoServicioId,
                        "vigente",
                        true)) {
            List<Object> clave = Arrays.asList(Py.or(f.get("version"), 0L), Py.item(f, "id"));
            if (mejor == null || Py.compare(clave, claveMejor) > 0) {
                mejor = f;
                claveMejor = clave;
            }
        }
        return mejor;
    }

    @Override
    public List<Map<String, Object>> campos(Object formatoId) {
        List<Map<String, Object>> campos =
                new ArrayList<>(fuente.listar("CampoFormatoReporte", "formato_id", formatoId));
        campos.sort((a, b) -> Py.compare(Py.item(a, "id"), Py.item(b, "id")));
        return campos;
    }
}
