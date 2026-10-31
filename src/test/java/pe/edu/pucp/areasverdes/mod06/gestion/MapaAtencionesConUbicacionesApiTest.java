package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteTiposUso;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteUbicaciones;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteUbicacionesNoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.MapaAtencionesService;

/**
 * RF-REP-007 con ubicaciones y tipos de uso disponibles (dobles {@code @Primary}): el día que
 * MOD-02 esté en {@code main}. Las coordenadas son datos de prueba inventados.
 *
 * <p>Espacio 1 → polígono; ejemplar 1 → punto. Tipos de uso: espacio 1 → 1, 2 → 2, 3 → 1. Así, van
 * al mapa las filas a (polígono del espacio 1), b y d (punto del ejemplar 1); a la lista, las filas
 * c (espacio 2), e (espacio 3) y f (sin alcance).
 */
@Import(MapaAtencionesConUbicacionesApiTest.ConUbicaciones.class)
class MapaAtencionesConUbicacionesApiTest extends ApiGestion {

    @TestConfiguration
    static class ConUbicaciones {

        /** Datos de prueba inventados, en [longitud, latitud]. */
        @Bean
        @Primary
        FuenteUbicaciones ubicacionesDePrueba(ObjectMapper json) throws Exception {
            JsonNode poligono =
                    json.readTree(
                            """
                            {"type": "Polygon", "coordinates": [[[-77.0800, -12.0690],
                              [-77.0790, -12.0690], [-77.0790, -12.0680], [-77.0800, -12.0690]]]}
                            """);
            JsonNode punto =
                    json.readTree("{\"type\": \"Point\", \"coordinates\": [-77.0794, -12.0689]}");
            return new FuenteUbicaciones() {
                @Override
                public Disponibilidad disponibilidad() {
                    return Disponibilidad.DISPONIBLE;
                }

                @Override
                public Map<Long, JsonNode> geometriasDeEspacios(Collection<Long> ids) {
                    return filtrar(Map.of(1L, poligono), ids);
                }

                @Override
                public Map<Long, JsonNode> geometriasDeEjemplares(Collection<Long> ids) {
                    return filtrar(Map.of(1L, punto), ids);
                }
            };
        }

        @Bean
        @Primary
        FuenteTiposUso tiposUsoDePrueba() {
            return new FuenteTiposUso() {
                @Override
                public Disponibilidad disponibilidad() {
                    return Disponibilidad.DISPONIBLE;
                }

                @Override
                public Map<Long, Long> tipoUsoDeEspacios(Collection<Long> ids) {
                    return filtrar(Map.of(1L, 1L, 2L, 2L, 3L, 1L), ids);
                }
            };
        }

        static <V> Map<Long, V> filtrar(Map<Long, V> datos, Collection<Long> ids) {
            Map<Long, V> r = new LinkedHashMap<>();
            for (Long id : ids) {
                if (datos.containsKey(id)) {
                    r.put(id, datos.get(id));
                }
            }
            return r;
        }
    }

    @BeforeEach
    void escenario() throws Exception {
        cargarEscenario();
    }

    ResultActions mapa(String filtros) throws Exception {
        return http.perform(
                get("/api/v1/reportes/mapa-atenciones/?desde=2026-10-01&hasta=2026-10-31" + filtros)
                        .cookie(sesion("jefe")));
    }

    @Test
    void lasAtencionesConUbicacionVanAlMapa() throws Exception {
        mapa("").andExpect(status().isOk())
                .andExpect(jsonPath("$.mapa.features.length()").value(3))
                .andExpect(jsonPath("$.sin_coordenadas.length()").value(3))
                .andExpect(jsonPath("$.mapa.features[0].type").value("Feature"))
                .andExpect(
                        jsonPath(
                                "$.mapa.features[*].geometry.type",
                                containsInAnyOrder("Polygon", "Point", "Point")))
                .andExpect(jsonPath("$.mapa.features[0].properties.codigo", notNullValue()))
                .andExpect(jsonPath("$.mapa.features[0].properties.tipo_atencion", notNullValue()))
                .andExpect(
                        jsonPath(
                                "$.mapa.features[?(@.geometry.type =="
                                        + " 'Point')].geometry.coordinates[0]",
                                contains(-77.0794, -77.0794)))
                .andExpect(jsonPath("$.notas", not(hasItem(FuenteUbicacionesNoDisponible.MOTIVO))));
    }

    @Test
    void filtroPorTipoDeUso() throws Exception {
        mapa("&tipo_uso_id=1")
                .andExpect(jsonPath("$.sin_datos").value(false))
                .andExpect(jsonPath("$.mapa.features[*].properties.codigo", contains("AT-001")))
                .andExpect(jsonPath("$.mapa.features[0].geometry.type").value("Polygon"))
                .andExpect(jsonPath("$.sin_coordenadas[*].codigo", contains("AT-006")));
    }

    @Test
    void unTipoDeUsoSinEspaciosNoTieneDatos() throws Exception {
        mapa("&tipo_uso_id=99")
                .andExpect(jsonPath("$.sin_datos").value(true))
                .andExpect(
                        jsonPath("$.mensaje_sin_datos")
                                .value(MapaAtencionesService.MENSAJE_SIN_DATOS));
    }
}
