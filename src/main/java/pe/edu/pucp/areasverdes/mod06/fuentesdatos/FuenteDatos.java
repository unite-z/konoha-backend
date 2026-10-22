package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.comun.Py;

/**
 * Interfaz común de las fuentes de datos del MOD-06 (patrón Estrategia).
 *
 * <p>Hay dos estrategias concretas, {@link FuenteDatosMock} (JSON en {@code mock-data/}) y {@link FuenteDatosBD}
 * (aún no disponible). {@link SelectorFuenteDatos} elige una según {@code DATA_SOURCE}.
 *
 * <p>Los registros circulan como {@code Map} con claves {@code snake_case} idénticas a los atributos del Modelo de
 * BD v1, de modo que los servicios funcionan igual con las dos fuentes.
 *
 * <p>Reglas que hace cumplir la interfaz:
 * <ul>
 *   <li>Entidad desconocida → {@link EntidadDesconocida}.</li>
 *   <li>Escritura en una entidad que no es del MOD-06 → {@link EscrituraNoPermitida} (Modelo de BD v1, sección 2.1).</li>
 * </ul>
 *
 * <p>Filtrado en memoria: RNF-013 declara volúmenes pequeños (unas 35 personas, más de 100 jardines, 1 084
 * ejemplares) y ADR-001, criterio C9, dice que «ninguna opción tiene problemas de capacidad previsibles».
 */
public interface FuenteDatos {

    String MENSAJE_ESCRITURA_NO_PERMITIDA =
        "El MOD-06 solo escribe en SugerenciaIA y ReporteGenerado (Modelo de BD v1, sección 2.1)";

    /**
     * Devuelve copias de los registros cuya clave == valor para cada par de {@code igualA}.
     * La comparación es la de Python ({@code 1 == 1.0 == True}).
     */
    List<Map<String, Object>> listar(String entidad, Map<String, Object> igualA);

    /** Devuelve una copia del registro con ese {@code id}, o {@code null} si no existe. */
    Map<String, Object> obtener(String entidad, Object id);

    /** Solo {@code ENTIDADES_ESCRIBIBLES}. Asigna {@code id = max(id) + 1}. Devuelve el registro creado. */
    Map<String, Object> crear(String entidad, Map<String, Object> datos);

    /** Solo {@code ENTIDADES_ESCRIBIBLES}. Error ({@code RecursoNoEncontrado}) si no existe. Devuelve el registro actualizado. */
    Map<String, Object> actualizar(String entidad, Object id, Map<String, Object> cambios);

    /**
     * Devuelve el contenido binario de un registro de la entidad Archivo.
     *
     * <p>RT-003 y RI-33: el contenido vive fuera de la base de datos; {@code archivo} solo tiene el metadato y la ruta.
     */
    byte[] leerContenidoArchivo(Map<String, Object> archivo);

    /** {@code listar(entidad, clave1=valor1, ...)}. */
    default List<Map<String, Object>> listar(String entidad, Object... pares) {
        return listar(entidad, Py.dict(pares));
    }

    // --- utilidades comunes (base.py) --------------------------------------

    /** Lanza {@link EntidadDesconocida} si {@code entidad} no está en el registro de entidades. */
    static void validarEntidad(String entidad) {
        if (!Entidades.CARPETA_POR_ENTIDAD.containsKey(entidad)) throw new EntidadDesconocida(entidad);
    }

    /** Lanza {@link EntidadDesconocida} si la entidad no existe y {@link EscrituraNoPermitida} si no es propia del MOD-06. */
    static void validarEscritura(String entidad) {
        validarEntidad(entidad);
        if (!Entidades.ENTIDADES_ESCRIBIBLES.contains(entidad)) {
            throw new EscrituraNoPermitida(MENSAJE_ESCRITURA_NO_PERMITIDA);
        }
    }

    /** Copias profundas de los registros cuya clave coincide con el valor para cada par de {@code igualA}. */
    static List<Map<String, Object>> filtrar(List<?> registros, Map<String, Object> igualA) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Object registro : registros) {
            boolean coincide = true;
            for (Map.Entry<String, Object> par : igualA.entrySet()) {
                if (!Py.eq(Py.get(registro, par.getKey()), par.getValue())) {
                    coincide = false;
                    break;
                }
            }
            if (coincide) r.add(Py.copia(comoRegistro(registro)));
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> comoRegistro(Object registro) {
        if (registro instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new Py.TypeError("'" + Py.nombreTipo(registro) + "' object has no attribute 'get'");
    }

    /** {@code max(id) + 1}, o 1 si no hay registros. */
    static Object siguienteId(List<?> registros) {
        Object maximo = 0L;
        for (Object r : registros) {
            Object id = Py.get(r, "id");
            if (id == null) continue;
            Object entero = Py.toInt(id);
            if (Py.compare(entero, maximo) > 0) maximo = entero;
        }
        return Py.add(maximo, 1L);
    }
}
