package pe.edu.pucp.areasverdes.mod06.servicios;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;

/**
 * Capa de servicios del MOD-06: reglas de negocio de RF-REP-002, RF-REP-004 y RF-REP-006.
 *
 * <p>ADR-001, figura 1: «Servicios por módulo MOD-01 a MOD-06. Reglas de negocio».
 * Los servicios no conocen HTTP y <b>no modifican ningún registro operativo</b> (CU-MOD06-001/002).
 * Reciben la fuente de datos por parámetro; si no se indica, usan {@code SelectorFuenteDatos}.
 *
 * <p>Esta clase reúne las utilidades comunes ({@code servicios/__init__.py}).
 */
public final class Comunes {

    private Comunes() {}

    /**
     * DefinicionIndicador con ese {@code codigo} como {@code {codigo, nombre, descripcion_calculo, meta, unidad}}, o
     * {@code null}.
     *
     * <p>Diagrama de Clases v1: DefinicionIndicador.calcular(periodo). Que no exista no es un error.
     */
    public static Map<String, Object> indicadorPorCodigo(FuenteDatos fuente, String codigo) {
        List<Map<String, Object>> definiciones = fuente.listar("DefinicionIndicador", "codigo", codigo);
        if (definiciones.isEmpty()) return null;
        Map<String, Object> definicion = definiciones.get(0);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("codigo", definicion.get("codigo"));
        r.put("nombre", definicion.get("nombre"));
        r.put("descripcion_calculo", definicion.get("descripcion_calculo"));
        r.put("meta", definicion.get("meta"));
        r.put("unidad", definicion.get("unidad"));
        return r;
    }

    /** {@code nombres apellidos} de un registro de Personal (sin datos personales sensibles, RNF-008). */
    public static String nombreCompleto(Map<String, Object> persona) {
        if (!Py.truthy(persona)) return null;
        List<Object> partes = List.of(Py.or(persona.get("nombres"), ""), Py.or(persona.get("apellidos"), ""));
        List<String> presentes = new ArrayList<>();
        for (Object p : partes) {
            if (!Py.truthy(p)) continue;
            if (!(p instanceof String s)) throw new Py.TypeError("sequence item: expected str instance, " + Py.nombreTipo(p) + " found");
            presentes.add(s);
        }
        String texto = Py.strip(String.join(" ", presentes));
        return texto.isEmpty() ? null : texto;
    }

    /** {@code {k: v for k, v in filtros.items() if v is not None}}. */
    public static Map<String, Object> filtrosAplicados(Map<String, Object> filtros) {
        Map<String, Object> r = new LinkedHashMap<>();
        filtros.forEach((k, v) -> {
            if (v != null) r.put(k, v);
        });
        return r;
    }

    /**
     * Grupos de un {@code dict} de Python indexado por una clave: la clave conserva el valor original de la primera
     * aparición y los grupos se recorren en orden de inserción.
     */
    public static final class Grupos<V> {
        private final Map<Object, Object> claves = new LinkedHashMap<>();
        private final Map<Object, List<V>> valores = new LinkedHashMap<>();

        public void agregar(Object clave, V valor) {
            Object k = Py.k(clave);
            claves.putIfAbsent(k, clave);
            valores.computeIfAbsent(k, x -> new ArrayList<>()).add(valor);
        }

        public List<V> get(Object clave) {
            return valores.get(Py.k(clave));
        }

        public boolean contiene(Object clave) {
            return valores.containsKey(Py.k(clave));
        }

        /** Pares (clave original, valores) en orden de inserción. */
        public List<Map.Entry<Object, List<V>>> entradas() {
            List<Map.Entry<Object, List<V>>> r = new ArrayList<>();
            valores.forEach((k, v) -> r.add(new java.util.AbstractMap.SimpleImmutableEntry<>(claves.get(k), v)));
            return r;
        }
    }
}
