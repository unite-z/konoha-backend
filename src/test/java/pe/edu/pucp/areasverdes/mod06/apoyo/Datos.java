package pe.edu.pucp.areasverdes.mod06.apoyo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.comun.Py;

/** Utilidades para escribir los datos de prueba como en Python (diccionarios y listas con nulos). */
public final class Datos {

    private Datos() {}

    /** Diccionario ordenado: {@code d("id", 1, "nombre", "x")}. Admite valores nulos. */
    public static Map<String, Object> d(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) m.put((String) pares[i], normalizar(pares[i + 1]));
        return m;
    }

    /** Lista mutable que admite nulos. */
    @SafeVarargs
    public static <T> List<T> l(T... elementos) {
        return new ArrayList<>(Arrays.asList(elementos));
    }

    /** Números como los de un JSON leído (int → Long), en profundidad. */
    public static Object normalizar(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            m.forEach((k, x) -> r.put((String) k, normalizar(x)));
            return r;
        }
        if (v instanceof List<?> lista) {
            List<Object> r = new ArrayList<>();
            for (Object x : lista) r.add(normalizar(x));
            return r;
        }
        return Py.normalizar(v);
    }

    /** Datos de auditoría de una entidad de negocio ({@code auditoria()} de {@code conftest.py}). */
    public static Map<String, Object> auditoria(String fecha, Object usuario) {
        return d("fecha_creacion", fecha, "usuario_creacion_id", usuario, "fecha_modificacion", fecha,
            "usuario_modificacion_id", usuario);
    }

    /** {@code assert esperado == obtenido} con la igualdad de Python ({@code 1 == 1.0}). */
    public static void igual(Object esperado, Object obtenido) {
        assertTrue(Py.eq(normalizar(esperado), obtenido), () -> "esperado " + Py.repr(normalizar(esperado)) + " pero fue " + Py.repr(obtenido));
    }
}
