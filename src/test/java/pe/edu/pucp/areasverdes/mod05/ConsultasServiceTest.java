package pe.edu.pucp.areasverdes.mod05;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AV-438: tamaño de página y validación del rango de fechas del listado de atenciones. */
class ConsultasServiceTest {

    static final ConsultasService.Catalogo ATENCIONES =
            new ConsultasService.Catalogo(
                    "SELECT 1", "id", List.of(), Map.of("desde", "fecha_desde"));
    static final ConsultasService.Catalogo SIN_RANGO =
            new ConsultasService.Catalogo("SELECT 1", "id", List.of(), Map.of());

    static final Map<String, String> SEMANA = Map.of("desde", "2026-11-23", "hasta", "2026-11-29");

    static Map<String, String> semanaCon(String pageSize) {
        return Map.of("desde", "2026-11-23", "hasta", "2026-11-29", "page_size", pageSize);
    }

    @Test
    void sinPageSizeOSinRangoCompletoSonVeinticinco() {
        assertEquals(25, ConsultasService.tamanoPagina(ATENCIONES, Map.of()));
        assertEquals(25, ConsultasService.tamanoPagina(ATENCIONES, SEMANA));
        assertEquals(25, ConsultasService.tamanoPagina(ATENCIONES, Map.of("page_size", "200")));
        assertEquals(
                25,
                ConsultasService.tamanoPagina(
                        ATENCIONES, Map.of("desde", "2026-11-23", "page_size", "200")));
        // Un catálogo sin filtro de fechas nunca amplía la página.
        assertEquals(25, ConsultasService.tamanoPagina(SIN_RANGO, semanaCon("200")));
    }

    @Test
    void conRangoCompletoManda() {
        assertEquals(200, ConsultasService.tamanoPagina(ATENCIONES, semanaCon("200")));
        assertEquals(1, ConsultasService.tamanoPagina(ATENCIONES, semanaCon("1")));
        assertEquals(60, ConsultasService.tamanoPagina(ATENCIONES, semanaCon(" 60 ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "201", "abc", "1.5"})
    void pageSizeFueraDeRangoEsUn400(String valor) {
        var error =
                assertThrows(
                        ApiError.class,
                        () -> ConsultasService.tamanoPagina(ATENCIONES, semanaCon(valor)));
        assertEquals(400, error.status);
    }

    @Test
    void fechasEnFormatoIso() {
        assertEquals(LocalDate.of(2026, 11, 23), ConsultasService.fecha("desde", "2026-11-23"));
        assertEquals(
                400,
                assertThrows(ApiError.class, () -> ConsultasService.fecha("desde", "23/11/2026"))
                        .status);
    }

    @Test
    void desdePosteriorAHastaEsUn400() {
        ConsultasService.validarRango(SEMANA);
        ConsultasService.validarRango(Map.of("desde", "2026-11-23", "hasta", "2026-11-23"));
        ConsultasService.validarRango(Map.of("desde", "2026-11-30"));
        var error =
                assertThrows(
                        ApiError.class,
                        () ->
                                ConsultasService.validarRango(
                                        Map.of("desde", "2026-11-30", "hasta", "2026-11-23")));
        assertEquals(400, error.status);
    }
}
