package pe.edu.pucp.areasverdes.mod06.gestion.exportaciones;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.IncidenciasRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.IncidenciasRepository.Conteo;

/**
 * Matriz de incidencias de RF-REP-009, en funciones puras: una fila por espacio y tipo de
 * incidencia, una columna por estado de MOD-03 (en el orden del flujo) y el total; al final, la
 * fila de totales. Los espacios y tipos se ordenan con las reglas del español y «Sin espacio» va al
 * final.
 */
public final class MatrizIncidencias {

    /** Estados de {@code mod03_incidencia} en el orden del flujo, con su texto de interfaz. */
    public static final Map<String, String> ESTADOS = new LinkedHashMap<>();

    static {
        ESTADOS.put("Registrada", "Registrada");
        ESTADOS.put("Clasificada", "Clasificada");
        ESTADOS.put("DerivadaInterna", "Derivada a personal propio");
        ESTADOS.put("DerivadaTercerizada", "Derivada a tercerizado");
        ESTADOS.put("EnAtencion", "En atención");
        ESTADOS.put("Atendida", "Atendida");
        ESTADOS.put("Cerrada", "Cerrada");
        ESTADOS.put("Descartada", "Descartada");
    }

    public static final String TOTAL = "Total";

    private MatrizIncidencias() {}

    public static List<String> columnas() {
        List<String> columnas = new ArrayList<>(List.of("Espacio", "Tipo de incidencia"));
        columnas.addAll(ESTADOS.values());
        columnas.add(TOTAL);
        return columnas;
    }

    /** Filas de la matriz; sin incidencias, ninguna (ni siquiera la de totales). */
    public static List<List<String>> filas(List<Conteo> conteos) {
        if (conteos.isEmpty()) return List.of();
        Collator textos = Collator.getInstance(Locale.forLanguageTag("es"));
        Comparator<List<String>> orden =
                Comparator.comparing(
                                (List<String> c) ->
                                        c.get(0).equals(IncidenciasRepository.SIN_ESPACIO))
                        .thenComparing(c -> c.get(0), textos)
                        .thenComparing(c -> c.get(1), textos);
        Map<List<String>, int[]> celdas = new TreeMap<>(orden);
        int[] totales = new int[ESTADOS.size()];
        List<String> estados = List.copyOf(ESTADOS.keySet());
        for (Conteo c : conteos) {
            int i = estados.indexOf(c.estado());
            if (i < 0) continue; // estado que MOD-03 aún no tiene en su catálogo
            celdas.computeIfAbsent(List.of(c.espacio(), c.tipo()), k -> new int[estados.size()])[
                            i] +=
                    c.incidencias();
            totales[i] += c.incidencias();
        }
        List<List<String>> filas = new ArrayList<>();
        celdas.forEach((clave, valores) -> filas.add(fila(clave.get(0), clave.get(1), valores)));
        if (!filas.isEmpty()) filas.add(fila(TOTAL, "", totales));
        return filas;
    }

    private static List<String> fila(String espacio, String tipo, int[] valores) {
        List<String> fila = new ArrayList<>(List.of(espacio, tipo));
        int total = 0;
        for (int v : valores) {
            fila.add(String.valueOf(v));
            total += v;
        }
        fila.add(String.valueOf(total));
        return fila;
    }
}
