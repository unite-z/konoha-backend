package pe.edu.pucp.areasverdes.mod06.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ConfiguracionFaltante;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatosMock;

/** Subcampos de los campos JSON del formato estándar en el esquema que se pide a Gemini. */
class EsquemaSubcamposTest {

    private static Map<String, Object> campo(Object subcampos) {
        Map<String, Object> c = new LinkedHashMap<>(Map.of("id", 2L, "nombre_campo", "Productos aplicados", "tipo_dato", "JSON"));
        c.put("subcampos", subcampos);
        return c;
    }

    private static Map<String, Object> sub(String nombre, String tipo, String descripcion) {
        Map<String, Object> s = new LinkedHashMap<>(Map.of("nombre", nombre, "tipo_dato", tipo));
        if (descripcion != null) s.put("descripcion", descripcion);
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> items(Map<String, Object> campo) {
        Map<String, Object> props = (Map<String, Object>) ExtraccionReportesService.construirEsquema(List.of(campo)).get("properties");
        return (Map<String, Object>) ((Map<String, Object>) props.get("campo_2")).get("items");
    }

    @Test
    @SuppressWarnings("unchecked")
    void conSubcamposCadaElementoTienePropiedades() {
        Map<String, Object> items = items(campo(List.of(sub("producto", "Texto", "Nombre del producto"),
            sub("cantidad", "Decimal", null), sub("fecha", "Fecha", " "))));
        assertEquals("object", items.get("type"));
        assertEquals(List.of("producto", "cantidad", "fecha"), items.get("required"));
        Map<String, Object> props = (Map<String, Object>) items.get("properties");
        assertEquals(Map.of("type", List.of("string", "null"), "description", "Nombre del producto"), props.get("producto"));
        assertEquals(Map.of("type", List.of("number", "null"), "description", "cantidad"), props.get("cantidad"));
        assertEquals(Map.of("type", List.of("string", "null"), "format", "date", "description", "fecha"), props.get("fecha"));
    }

    @Test
    void sinSubcamposElElementoSigueSiendoUnObjetoLibre() {
        Map<String, Object> c = campo(null);
        c.remove("subcampos");
        assertEquals(Map.of("type", "object"), items(c));
    }

    @Test
    void subcamposMalDefinidosDanConfiguracionFaltante() {
        for (Object malo : List.of(List.of(), "texto", List.of("x"), List.of(sub("", "Texto", null)),
            List.of(sub("a", "JSON", null)), List.of(sub("a", "Imagen", null)),
            List.of(sub("a", "Texto", null), sub("a", "Entero", null)))) {
            ConfiguracionFaltante e = assertThrows(ConfiguracionFaltante.class, () -> items(campo(malo)), String.valueOf(malo));
            assertTrue(e.mensaje().contains("subcampos del campo 'Productos aplicados'"));
        }
    }

    /** El formato estándar de mock-data define subcampos válidos para sus tres campos JSON. */
    @Test
    @SuppressWarnings("unchecked")
    void formatoDeMockDataGeneraEsquemaConSubcampos() {
        FuenteDatosMock fuente = new FuenteDatosMock(new Mod06Propiedades("mock", null, null, null).mockDataDir());
        List<Map<String, Object>> campos = fuente.listar("CampoFormatoReporte", "formato_id", 1);
        Map<String, Object> props = (Map<String, Object>) ExtraccionReportesService.construirEsquema(campos).get("properties");
        long conSubcampos = props.values().stream()
            .map(p -> ((Map<String, Object>) p).get("items"))
            .filter(i -> i instanceof Map<?, ?> m && m.containsKey("properties"))
            .count();
        assertEquals(3, conSubcampos);
    }
}
