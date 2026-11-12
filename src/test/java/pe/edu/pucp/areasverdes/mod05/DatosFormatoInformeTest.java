package pe.edu.pucp.areasverdes.mod05;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.pucp.areasverdes.mod05.DatosFormatoInforme.Campo;
import pe.edu.pucp.areasverdes.mod05.DatosFormatoInforme.Subcampo;

/** RF-SER-009: validación y normalización de los datos del informe final. */
class DatosFormatoInformeTest {

    static final ObjectMapper JSON = new ObjectMapper();

    /** El formato de Control fitosanitario de V73, más un campo de cada tipo restante. */
    static final List<Campo> CAMPOS =
            List.of(
                    new Campo("Metrado", "Decimal", true, 1, null),
                    new Campo(
                            "Productos aplicados",
                            "JSON",
                            true,
                            2,
                            List.of(
                                    new Subcampo("producto", "Texto", "Nombre del producto"),
                                    new Subcampo("cantidad", "Decimal", "Cantidad total"),
                                    new Subcampo("unidad", "Texto", "Unidad"))),
                    new Campo("Ruta recorrida", "Texto largo", true, 3, null),
                    new Campo("Operarios", "Entero", false, 4, null),
                    new Campo("Con charla técnica", "Booleano", false, 5, null),
                    new Campo("Fecha de cierre", "Fecha", false, 6, null),
                    new Campo("Supervisor", "Texto", false, 7, null));

    static JsonNode leer(String texto) throws Exception {
        return JSON.readTree(texto);
    }

    static String mensaje(String datos) {
        return assertThrows(
                        ApiError.class, () -> DatosFormatoInforme.normalizar(CAMPOS, leer(datos)))
                .getMessage();
    }

    @Test
    void normalizaEnElOrdenDelFormatoYDescartaLoVacio() throws Exception {
        var datos =
                DatosFormatoInforme.normalizar(
                        CAMPOS,
                        leer(
                                """
                                {"Supervisor": "  Ana  ", "Ruta recorrida": "Jardín A → B",
                                 "Metrado": "120,50", "Operarios": "4", "Con charla técnica": true,
                                 "Fecha de cierre": "2026-11-10",
                                 "Productos aplicados": [
                                   {"producto": "Abamectina", "cantidad": 2, "unidad": "L"},
                                   {"producto": "", "cantidad": null},
                                   {}
                                 ]}
                                """));
        assertEquals(
                List.of(
                        "Metrado",
                        "Productos aplicados",
                        "Ruta recorrida",
                        "Operarios",
                        "Con charla técnica",
                        "Fecha de cierre",
                        "Supervisor"),
                List.copyOf(datos.keySet()));
        assertEquals("120.50", datos.get("Metrado"));
        assertEquals(4L, datos.get("Operarios"));
        assertEquals(true, datos.get("Con charla técnica"));
        assertEquals("Ana", datos.get("Supervisor"));
        assertEquals(
                List.of(Map.of("producto", "Abamectina", "cantidad", "2", "unidad", "L")),
                datos.get("Productos aplicados"));
    }

    @Test
    void losOpcionalesVaciosNoSeGuardan() throws Exception {
        var datos =
                DatosFormatoInforme.normalizar(
                        CAMPOS,
                        leer(
                                """
                                {"Metrado": 10, "Ruta recorrida": "R",
                                 "Productos aplicados": [{"producto": "X"}],
                                 "Operarios": "", "Supervisor": null, "Fecha de cierre": "  "}
                                """));
        assertEquals(
                List.of("Metrado", "Productos aplicados", "Ruta recorrida"),
                List.copyOf(datos.keySet()));
    }

    @Test
    void nombraLosObligatoriosQueFaltan() {
        assertEquals(
                "Faltan datos obligatorios del informe final: Productos aplicados, Ruta recorrida",
                mensaje(
                        """
                        {"Metrado": "1", "Productos aplicados": [{"producto": " "}]}
                        """));
    }

    @Test
    void rechazaCamposQueElFormatoNoDefine() {
        assertTrue(mensaje("{\"Metrado\": 1, \"Plantas\": 3}").contains("«Plantas»"));
        assertTrue(
                mensaje(
                                """
                                {"Metrado": 1, "Ruta recorrida": "R",
                                 "Productos aplicados": [{"producto": "X", "lote": "7"}]}
                                """)
                        .contains("Productos aplicados, fila 1: «lote» no es un dato de la lista"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"Metrado\": \"abc\"}",
                "{\"Metrado\": -1}",
                "{\"Metrado\": \"1.12345\"}",
                "{\"Operarios\": 2.5}",
                "{\"Con charla técnica\": \"sí\"}",
                "{\"Fecha de cierre\": \"10/11/2026\"}",
                "{\"Productos aplicados\": {\"producto\": \"X\"}}",
                "{\"Productos aplicados\": [{\"cantidad\": \"mucho\"}]}",
                "{\"Supervisor\": {\"nombre\": \"Ana\"}}"
            })
    void rechazaValoresQueNoCorrespondenASuTipo(String datos) {
        assertTrue(mensaje(datos).startsWith("Revisa los datos del informe final: "));
    }

    @Test
    void exigeUnObjeto() {
        assertEquals("Los datos del informe final deben ser un objeto JSON", mensaje("[1]"));
    }

    @Test
    void respetaLosLimitesDeTamano() {
        String largo = "x".repeat(DatosFormatoInforme.LARGO_TEXTO + 1);
        assertTrue(mensaje("{\"Supervisor\": \"" + largo + "\"}").contains("hasta 500 caracteres"));
        String filas =
                "{\"Productos aplicados\": ["
                        + "{\"producto\": \"X\"},".repeat(DatosFormatoInforme.MAXIMO_FILAS)
                        + "{\"producto\": \"X\"}]}";
        assertTrue(mensaje(filas).contains("hasta 200 filas"));
    }
}
