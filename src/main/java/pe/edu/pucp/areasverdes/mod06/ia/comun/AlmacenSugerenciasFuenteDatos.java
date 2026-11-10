package pe.edu.pucp.areasverdes.mod06.ia.comun;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;

/**
 * {@link AlmacenSugerencias} sobre una {@link FuenteDatos} (entidad {@code SugerenciaIA}). Solo lo
 * usan las pruebas que aún construyen sus datos en memoria; no audita ni bloquea filas.
 *
 * <p>Transitorio: desaparece con el sistema {@code FuenteDatos} (Fase 2 del plan de MOD-06).
 */
public final class AlmacenSugerenciasFuenteDatos implements AlmacenSugerencias {

    private static final String ENTIDAD = "SugerenciaIA";

    private final FuenteDatos fuente;

    public AlmacenSugerenciasFuenteDatos(FuenteDatos fuente) {
        this.fuente = fuente;
    }

    @Override
    public Map<String, Object> crear(Map<String, Object> datos) {
        Map<String, Object> registro = new LinkedHashMap<>(datos);
        registro.putIfAbsent("estado", "Propuesta");
        registro.putIfAbsent("validador_id", null);
        registro.putIfAbsent("fecha_validacion", null);
        registro.putIfAbsent("fecha_modificacion", datos.get("fecha_creacion"));
        registro.putIfAbsent("usuario_modificacion_id", datos.get("usuario_creacion_id"));
        return fuente.crear(ENTIDAD, registro);
    }

    @Override
    public Map<String, Object> obtener(long id) {
        return fuente.obtener(ENTIDAD, id);
    }

    @Override
    public Map<String, Object> obtenerParaRevisar(long id) {
        return obtener(id);
    }

    @Override
    public List<Map<String, Object>> listar(
            String tipo, String estado, String entidad, Long registroId) {
        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("tipo", tipo);
        if (estado != null) filtros.put("estado", estado);
        if (entidad != null) {
            filtros.put("entidad", entidad);
            filtros.put("registro_id", registroId);
        }
        List<Map<String, Object>> r = new ArrayList<>(fuente.listar(ENTIDAD, filtros));
        r.sort(
                (a, b) -> {
                    int porFecha = orden(b).compareTo(orden(a));
                    return porFecha != 0
                            ? porFecha
                            : Py.compare(Py.or(b.get("id"), 0L), Py.or(a.get("id"), 0L));
                });
        return r;
    }

    private static Instant orden(Map<String, Object> s) {
        ZonedDateTime fecha = Fechas.aFechaHora(s.get("fecha_creacion"));
        return fecha != null ? fecha.toInstant() : Instant.MIN;
    }

    @Override
    public Map<String, Object> guardarRevision(
            long id, Map<String, Object> cambios, String motivo) {
        return fuente.actualizar(ENTIDAD, id, cambios);
    }
}
