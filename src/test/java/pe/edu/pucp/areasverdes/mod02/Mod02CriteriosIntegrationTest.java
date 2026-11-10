package pe.edu.pucp.areasverdes.mod02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

@SpringBootTest
@AutoConfigureMockMvc
class Mod02CriteriosIntegrationTest {

    private static final String CLAVE = "prueba-integration-local";
    private static final String API = "/api/v1/inventario";

    @DynamicPropertySource
    static void baseDePruebas(DynamicPropertyRegistry propiedades) {
        BaseDePruebas.registrar(propiedades);
    }

    @Autowired MockMvc http;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder claves;

    @BeforeEach
    void prepararBase() {
        jdbc.getJdbcTemplate()
                .execute("TRUNCATE mod02_ejemplar, mod02_especie_vegetal RESTART IDENTITY CASCADE");
        jdbc.getJdbcTemplate()
                .execute("TRUNCATE core_usuario, mod05_usuario RESTART IDENTITY CASCADE");
        crearUsuario("jefe", "Jefe de sección");
        crearUsuario("ingeniero", "Ingeniero de campo");
        crearUsuario("capataz-a", "Capataz");
        crearUsuario("capataz-b", "Capataz");
    }

    @Test
    void historialDistingueFuentesVaciasPermisosYEjemplarInexistente() throws Exception {
        String cuerpo =
                http.perform(
                                post(API + "/ejemplares")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String sufijo = "/historial-intervenciones";
        String historial = API + "/ejemplares/" + idJson(cuerpo, "$.id") + sufijo;
        for (String fuente : List.of("podas", "fitosanitario", "incidencias")) {
            http.perform(get(historial).queryParam("fuente", fuente))
                    .andExpect(status().isUnauthorized());
            http.perform(get(historial).queryParam("fuente", fuente).with(httpBasic("jefe", CLAVE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
        http.perform(
                        get(API + "/ejemplares/0" + sufijo)
                                .queryParam("fuente", "incidencias")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void trasladaYRetiraUnEjemplarConHistorialYPermisos() throws Exception {
        String cuerpo =
                http.perform(
                                post(API + "/ejemplares")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content(
                                                """
                                                {"ubicacion":"Jardín anterior","latitud":-12.07,"longitud":-77.08}
                                                """))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.situacion").value("activo"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id = idJson(cuerpo, "$.id");
        String codigo = JsonPath.read(cuerpo, "$.codigo");
        String ficha = API + "/ejemplares/" + id;
        String traslado =
                """
                {"situacion":"trasladado","fecha":"2026-10-21","motivo":"Obras",
                 "ubicacion":"Jardín de destino","latitud":-12.071,"longitud":-77.081}
                """;
        http.perform(
                        patch(ficha + "/situacion")
                                .with(httpBasic("ingeniero", CLAVE))
                                .contentType("application/json")
                                .content(traslado))
                .andExpect(status().isForbidden());
        http.perform(
                        patch(ficha + "/situacion")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"situacion":"trasladado","fecha":"2026-10-21","motivo":"Obras"}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("destino-requerido"));
        http.perform(
                        patch(ficha + "/situacion")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(traslado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacion").value("trasladado"))
                .andExpect(jsonPath("$.ubicacion").value("Jardín de destino"))
                .andExpect(jsonPath("$.latitud").value(-12.071));
        http.perform(
                        patch(ficha + "/situacion")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(traslado))
                .andExpect(status().isConflict());
        http.perform(
                        patch(ficha + "/situacion")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"situacion":"retirado","fecha":"2026-10-22","motivo":"Fin de vida útil"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacion").value("Jardín de destino"));
        http.perform(
                        get(API + "/ejemplares")
                                .queryParam("q", codigo)
                                .with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].situacion").value("retirado"))
                .andExpect(jsonPath("$[0].fecha_situacion").value("2026-10-22"));
        http.perform(get(ficha + "/historial-situacion").with(httpBasic("ingeniero", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].situacion_nueva").value("retirado"))
                .andExpect(jsonPath("$[1].situacion_anterior").value("activo"))
                .andExpect(jsonPath("$[1].motivo").value("Obras"))
                .andExpect(jsonPath("$[1].autor").value("jefe"))
                .andExpect(jsonPath("$[1].ubicacion_anterior").value("Jardín anterior"))
                .andExpect(jsonPath("$[1].latitud_anterior").value(-12.07))
                .andExpect(jsonPath("$[1].ubicacion_nueva").value("Jardín de destino"));
    }

    @Test
    void especiesYPlacasDuplicadasSeAdviertenYLosCodigosSonUnicosBajoConcurrencia()
            throws Exception {
        String nombreCientifico = "Schinus molle MOD02 test " + UUID.randomUUID();
        String placaAntigua = "PLACA-" + UUID.randomUUID();
        String especie =
                http.perform(
                                post(API + "/especies")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content(
                                                "{\"nombre_comun\":\"Molle\",\"nombre_cientifico\":\""
                                                        + nombreCientifico
                                                        + "\",\"forma_biologica\":\"Árbol\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.nombre_cientifico").value(nombreCientifico))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long especieId = idJson(especie, "$.id");
        http.perform(
                        post(API + "/especies")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"nombre_comun\":\"Molle"
                                                + " duplicado\",\"nombre_cientifico\":\""
                                                + nombreCientifico
                                                + "\",\"forma_biologica\":\"Árbol\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("especie-duplicada"));
        http.perform(
                        post(API + "/especies")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"nombre_comun\":\"Sin científico\","
                                                + "\"forma_biologica\":\"Árbol\"}"))
                .andExpect(status().isBadRequest());

        String ejemplar =
                "{\"codigo_placa_antigua\":\""
                        + placaAntigua
                        + "\",\"especie_id\":"
                        + especieId
                        + "}";
        String codigoUno = crearEjemplar(ejemplar);
        String codigoDos = crearEjemplar(ejemplar);
        assertThat(codigoUno).isNotEqualTo(codigoDos);

        for (String codigo : List.of(codigoUno, codigoDos)) {
            http.perform(
                            get(API + "/ejemplares")
                                    .queryParam("q", codigo)
                                    .with(httpBasic("jefe", CLAVE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].codigo_placa_antigua").value(placaAntigua))
                    .andExpect(jsonPath("$[0].advertencia_placa_repetida").value(true))
                    .andExpect(jsonPath("$[0].especie_id").value(especieId));
        }
        List<String> codigosConcurrentes = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(6)) {
            var tareas = new ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 12; i++) {
                tareas.add(executor.submit(() -> crearEjemplar("{}")));
            }
            for (var tarea : tareas) codigosConcurrentes.add(tarea.get());
            assertThat(codigosConcurrentes).doesNotHaveDuplicates();
        }
        for (String codigo : codigosConcurrentes) {
            http.perform(
                            get(API + "/ejemplares")
                                    .queryParam("q", codigo)
                                    .with(httpBasic("jefe", CLAVE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].codigo").value(codigo))
                    .andExpect(
                            jsonPath("$[0].codigo_placa_antigua")
                                    .value(org.hamcrest.Matchers.nullValue()));
        }
        http.perform(
                        get(API + "/ejemplares")
                                .queryParam("q", placaAntigua)
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo_placa_antigua").isNotEmpty())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
        http.perform(
                        get(API + "/ejemplares")
                                .queryParam("q", "sin-coincidencia-" + UUID.randomUUID())
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        http.perform(get(API + "/ejemplares")).andExpect(status().isUnauthorized());
    }

    @Test
    void tipoDeUsoSeAsociaAlAreaYPermiteFiltrarlaSinExigirCuartel() throws Exception {
        String tipoUso =
                http.perform(
                                post(API + "/tipos-uso")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"Administrativo\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long tipoUsoId = idJson(tipoUso, "$.id");
        String area = crearArea("Edificio central", tipoUsoId, null);
        long areaId = idJson(area, "$.id");
        assertThat(JsonPath.<Object>read(area, "$.cuartel_id")).isNull();

        http.perform(
                        get(API + "/areas-verdes")
                                .queryParam("tipo_uso_id", String.valueOf(tipoUsoId))
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.results[0].id").value(areaId))
                .andExpect(jsonPath("$.results[0].tipo_uso_nombre").value("Administrativo"));
        assertThat(JsonPath.<Object>read(area, "$.sector_id")).isNull();
    }

    @Test
    void areaSinSectorSigueConsultableYSePuedeCrearSectorDeAtencionADemanda() throws Exception {
        String sectorDemanda =
                http.perform(
                                post(API + "/sectores")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content(
                                                "{\"nombre\":\"Atención a demanda\","
                                                        + "\"atencion_a_demanda\":true}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.atencion_demanda").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(idJson(sectorDemanda, "$.id")).isPositive();

        long areaId = idJson(crearArea("Bosque húmedo", null, null), "$.id");
        http.perform(
                        get(API + "/areas-verdes")
                                .queryParam("q", "Bosque húmedo")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value(areaId))
                .andExpect(
                        jsonPath("$.results[0].sector_id")
                                .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void coberturaAdministradaSigueAsociadaAlAreaAlDesactivarla() throws Exception {
        String cobertura =
                http.perform(
                                post(API + "/coberturas/")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"Cubresuelo MOD02 test\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long coberturaId = idJson(cobertura, "$.id");
        String cuerpo =
                "{\"nombre\":\"Área con cobertura\",\"lugar\":\"Campus\","
                        + "\"ubicacion\":\"Zona central\",\"superficie_m2\":\"85.50\","
                        + "\"tipo_cobertura_id\":"
                        + coberturaId
                        + "}";
        long areaId =
                idJson(
                        http.perform(
                                        post(API + "/areas-verdes/")
                                                .with(httpBasic("jefe", CLAVE))
                                                .contentType("application/json")
                                                .content(cuerpo))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.id");
        http.perform(
                        patch(API + "/coberturas/" + coberturaId)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        http.perform(
                        get(API + "/areas-verdes")
                                .queryParam("q", "Área con cobertura")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value(areaId))
                .andExpect(jsonPath("$.results[0].tipo_cobertura_id").value(coberturaId))
                .andExpect(
                        jsonPath("$.results[0].tipo_cobertura_nombre")
                                .value("Cubresuelo MOD02 test"));
    }

    @Test
    void incorporarYReasignarAreasConservaHistorialYSuperficie() throws Exception {
        long sectorUno = crearSector("Sector Norte");
        long sectorDos = crearSector("Sector Sur");
        long areaId = idJson(crearArea("Jardín Central", null, sectorUno), "$.id");
        crearArea("Jardín Anexo", null, sectorUno);
        String sectores =
                http.perform(
                                get(API + "/sectores")
                                        .queryParam("incluir_inactivos", "true")
                                        .with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Object> superficies =
                JsonPath.read(sectores, "$[?(@.id == " + sectorUno + ")].superficie_m2");
        assertThat(superficies)
                .hasSize(1)
                .first()
                .satisfies(
                        superficie ->
                                assertThat(new BigDecimal(superficie.toString()))
                                        .isEqualByComparingTo("171.00"));

        http.perform(
                        patch(API + "/areas-verdes/" + areaId + "/sector")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"sector_id\":" + sectorDos + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector_id").value(sectorDos));
        http.perform(
                        get(API + "/areas-verdes/" + areaId + "/historial-sector")
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sector_id").value(sectorDos))
                .andExpect(jsonPath("$[0].fecha_fin").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[1].sector_id").value(sectorUno))
                .andExpect(jsonPath("$[1].fecha_fin").isNotEmpty());
    }

    @Test
    void reasignarYQuitarCapatazConservaHistorialYRespetaPermisos() throws Exception {
        long sector = crearSector("Sector capataces");
        long capatazUno = idCore("capataz-a");
        long capatazDos = idCore("capataz-b");
        http.perform(get(API + "/capataces").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
        asignarCapataz(sector, capatazUno);
        asignarCapataz(sector, capatazDos);
        http.perform(
                        patch(API + "/sectores/" + sector + "/capataz")
                                .with(httpBasic("ingeniero", CLAVE))
                                .contentType("application/json")
                                .content("{\"capataz_id\":null}"))
                .andExpect(status().isForbidden());
        http.perform(
                        patch(API + "/sectores/" + sector + "/capataz")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"capataz_id\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capataz_nombre").value(org.hamcrest.Matchers.nullValue()));
        String historialCapataz =
                http.perform(
                                get(API + "/sectores/" + sector + "/historial-capataz")
                                        .with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Map<String, Object>> asignaciones = JsonPath.read(historialCapataz, "$[*]");
        assertThat(asignaciones).hasSize(2);
        assertThat(asignaciones)
                .extracting(asignacion -> ((Number) asignacion.get("capataz_id")).longValue())
                .containsExactlyInAnyOrder(capatazUno, capatazDos);
        assertThat(asignaciones)
                .filteredOn(
                        asignacion ->
                                ((Number) asignacion.get("capataz_id")).longValue() == capatazDos)
                .singleElement()
                .satisfies(asignacion -> assertThat(asignacion.get("fecha_fin")).isNotNull());
        assertThat(asignaciones)
                .filteredOn(
                        asignacion ->
                                ((Number) asignacion.get("capataz_id")).longValue() == capatazUno)
                .singleElement()
                .satisfies(asignacion -> assertThat(asignacion.get("fecha_fin")).isNotNull());
        Integer eventosAuditoria =
                jdbc.queryForObject(
                        "SELECT count(*) FROM core_registro_auditoria"
                                + " WHERE entidad = 'mod02_sector' AND registro_id = :id",
                        Map.of("id", sector),
                        Integer.class);
        assertThat(eventosAuditoria).isGreaterThanOrEqualTo(4);
    }

    @Test
    void clasificarUsoDeMapaYVincularPoligonoRequierePermisoYSeConserva() throws Exception {
        String tipoUso =
                http.perform(
                                post(API + "/tipos-uso")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"Recreativo MOD02 test\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long tipoUsoId = idJson(tipoUso, "$.id");
        http.perform(
                        put(API + "/mapa/usos-fuente")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"uso_fuente\":\"Categoría mapa MOD02 test\","
                                                + "\"tipo_uso_id\":"
                                                + tipoUsoId
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo_uso_id").value(tipoUsoId));
        http.perform(
                        put(API + "/mapa/usos-fuente")
                                .with(httpBasic("ingeniero", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"uso_fuente\":\"Categoría mapa MOD02 test\","
                                                + "\"tipo_uso_id\":"
                                                + tipoUsoId
                                                + "}"))
                .andExpect(status().isForbidden());

        long areaId = idJson(crearArea("Área mapa MOD02", tipoUsoId, null), "$.id");
        UUID idFuente = UUID.fromString("00000000-0000-0000-0000-000000000172");
        http.perform(
                        put(API + "/mapa/poligonos/" + idFuente)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"area_verde_id\":" + areaId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area_verde_id").value(areaId));
        String poligonos =
                http.perform(get(API + "/mapa/poligonos").with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Number> areasVinculadas =
                JsonPath.read(poligonos, "$[?(@.id_fuente == '" + idFuente + "')].area_verde_id");
        assertThat(areasVinculadas.stream().map(Number::longValue)).containsExactly(areaId);
    }

    @Test
    void mapaDevuelveLaFichaDeCadaPoligonoParaCalcularClasificacionEfectiva() throws Exception {
        long ornamental = crearTipoUso("Ornamental mapa " + UUID.randomUUID());
        long deportivo = crearTipoUso("Deportivo mapa " + UUID.randomUUID());
        long institucional = crearTipoUso("Institucional mapa " + UUID.randomUUID());
        long areaA = idJson(crearArea("Área A mapa", ornamental, null), "$.id");
        long areaB = idJson(crearArea("Área B mapa", deportivo, null), "$.id");
        UUID poligonoA = UUID.randomUUID();
        UUID poligonoB = UUID.randomUUID();
        UUID poligonoC = UUID.randomUUID();

        vincularPoligono(poligonoA, areaA);
        vincularPoligono(poligonoB, areaB);
        vincularPoligono(poligonoC, null);

        String mapaInicial =
                http.perform(get(API + "/mapa/poligonos").with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(
                        JsonPath.<List<Number>>read(
                                mapaInicial,
                                "$[?(@.id_fuente == '" + poligonoA + "')].area_verde_tipo_uso_id"))
                .extracting(Number::longValue)
                .containsExactly(ornamental);
        assertThat(
                        JsonPath.<List<Boolean>>read(
                                mapaInicial,
                                "$[?(@.id_fuente == '" + poligonoA + "')].area_verde_activa"))
                .containsExactly(true);
        assertThat(
                        JsonPath.<List<Object>>read(
                                mapaInicial,
                                "$[?(@.id_fuente == '" + poligonoC + "')].area_verde_id"))
                .containsExactly((Object) null);

        http.perform(
                        patch(API + "/areas-verdes/" + areaA)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"tipo_uso_id\":" + institucional + "}"))
                .andExpect(status().isOk());

        String mapaActualizado =
                http.perform(get(API + "/mapa/poligonos").with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(
                        JsonPath.<List<Number>>read(
                                mapaActualizado,
                                "$[?(@.id_fuente == '" + poligonoA + "')].area_verde_tipo_uso_id"))
                .extracting(Number::longValue)
                .containsExactly(institucional);
        assertThat(
                        JsonPath.<List<Number>>read(
                                mapaActualizado,
                                "$[?(@.id_fuente == '" + poligonoB + "')].area_verde_tipo_uso_id"))
                .extracting(Number::longValue)
                .containsExactly(deportivo);

        http.perform(
                        patch(API + "/areas-verdes/" + areaA)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"activo\":false}"))
                .andExpect(status().isOk());
        String mapaInactivo =
                http.perform(get(API + "/mapa/poligonos").with(httpBasic("jefe", CLAVE)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(
                        JsonPath.<List<Boolean>>read(
                                mapaInactivo,
                                "$[?(@.id_fuente == '" + poligonoA + "')].area_verde_activa"))
                .containsExactly(false);
    }

    private long crearTipoUso(String nombre) throws Exception {
        String tipo =
                http.perform(
                                post(API + "/tipos-uso")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"" + nombre + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return idJson(tipo, "$.id");
    }

    private void vincularPoligono(UUID idFuente, Long areaId) throws Exception {
        http.perform(
                        put(API + "/mapa/poligonos/" + idFuente)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"area_verde_id\":"
                                                + (areaId == null ? "null" : areaId)
                                                + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void validaElPuntoYConfirmaLaReasignacionCartografica() throws Exception {
        UUID poligono = UUID.fromString("d52663f2-83ee-5d20-84f7-65e644611c2a");
        long anterior = idJson(crearArea("Área anterior geográfica", null, null), "$.id");
        long destino = idJson(crearArea("Área nueva geográfica", null, null), "$.id");
        vincularPoligono(poligono, anterior);
        Map<String, Object> borde =
                jdbc.queryForMap(
                        """
                        SELECT ST_X(punto) longitud, ST_Y(punto) latitud
                        FROM (SELECT ST_PointN(ST_ExteriorRing(ST_GeometryN(geometria, 1)), 1) punto
                              FROM mod02_mapa_geometria WHERE id_fuente = :id) coordenadas
                        """,
                        Map.of("id", poligono));
        String punto =
                "\"latitud\":" + borde.get("latitud") + ",\"longitud\":" + borde.get("longitud");
        String vinculo = "\"area_verde_id\":" + anterior;
        String codigo = crearEjemplar("{" + punto + "," + vinculo + "}");
        String sinVinculo = crearEjemplar("{" + punto + "}");
        long ejemplar =
                jdbc.queryForObject(
                        "SELECT id FROM mod02_ejemplar WHERE codigo = :codigo",
                        Map.of("codigo", codigo),
                        Long.class);
        String ficha = API + "/ejemplares/" + ejemplar;
        String fuera = "{\"latitud\":0,\"longitud\":0}";
        http.perform(
                        post(API + "/ejemplares")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"latitud\":0,\"longitud\":0," + vinculo + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("punto-fuera-del-area"));
        http.perform(
                        patch(ficha)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(fuera))
                .andExpect(status().isUnprocessableEntity());
        http.perform(
                        patch(ficha + "/situacion")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(
                                        "{\"situacion\":\"trasladado\",\"fecha\":\"2026-10-21\",\"motivo\":\"Obras\",\"latitud\":0,\"longitud\":0,"
                                                + vinculo
                                                + "}"))
                .andExpect(status().isUnprocessableEntity());

        String mapa = API + "/mapa";
        String ruta = mapa + "/poligonos/" + poligono;
        http.perform(get(mapa + "/geometria").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(521));
        http.perform(
                        get(mapa + "/ubicacion")
                                .with(httpBasic("ingeniero", CLAVE))
                                .param("latitud", borde.get("latitud").toString())
                                .param("longitud", borde.get("longitud").toString())
                                .param("area_verde_id", String.valueOf(anterior)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("dentro"))
                .andExpect(jsonPath("$.areas[0].id").value((int) anterior));
        http.perform(get(ruta + "/ejemplares").with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value(codigo))
                .andExpect(jsonPath("$[1].codigo").value(sinVinculo));
        http.perform(
                        get(ruta + "/impacto")
                                .param("area_verde_id", String.valueOf(destino))
                                .with(httpBasic("capataz-a", CLAVE)))
                .andExpect(status().isForbidden());
        http.perform(
                        get(ruta + "/impacto")
                                .param("area_verde_id", String.valueOf(destino))
                                .with(httpBasic("jefe", CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasignables[0].codigo").value(codigo))
                .andExpect(jsonPath("$.conflictos").isEmpty());
        String cambio = "{\"area_verde_id\":" + destino;
        http.perform(
                        put(ruta)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(cambio + "}"))
                .andExpect(status().isConflict());
        String confirmacion =
                ",\"confirmacion\":{\"area_anterior_id\":" + anterior + ",\"ejemplares_ids\":";
        http.perform(
                        put(ruta)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(cambio + confirmacion + "[]}}"))
                .andExpect(status().isConflict());
        http.perform(
                        put(ruta)
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(cambio + confirmacion + "[" + ejemplar + "]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area_verde_id").value((int) destino));
        assertThat(
                        jdbc.queryForMap(
                                """
                                SELECT area_verde_inventario_id area_verde_id, ST_X(ubicacion_geografica) longitud, ST_Y(ubicacion_geografica) latitud
                                FROM mod02_ejemplar WHERE id = :id
                                """,
                                Map.of("id", ejemplar)))
                .containsEntry("area_verde_id", destino)
                .containsEntry("longitud", borde.get("longitud"))
                .containsEntry("latitud", borde.get("latitud"));
    }

    private String crearEjemplar(String cuerpo) throws Exception {
        MvcResult resultado =
                http.perform(
                                post(API + "/ejemplares")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content(cuerpo))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.codigo").isNotEmpty())
                        .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.codigo");
    }

    private String crearArea(String nombre, Long tipoUsoId, Long sectorId) throws Exception {
        StringBuilder cuerpo =
                new StringBuilder(
                        "{\"nombre\":\""
                                + nombre
                                + "\",\"lugar\":\"Campus\",\"ubicacion\":\"Zona central\","
                                + "\"superficie_m2\":\"85.50\"");
        if (tipoUsoId != null) cuerpo.append(",\"tipo_uso_id\":").append(tipoUsoId);
        if (sectorId != null) cuerpo.append(",\"sector_id\":").append(sectorId);
        cuerpo.append('}');
        return http.perform(
                        post(API + "/areas-verdes/")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content(cuerpo.toString()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long crearSector(String nombre) throws Exception {
        String cuerpo =
                http.perform(
                                post(API + "/sectores")
                                        .with(httpBasic("jefe", CLAVE))
                                        .contentType("application/json")
                                        .content("{\"nombre\":\"" + nombre + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return idJson(cuerpo, "$.id");
    }

    private long idJson(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private void asignarCapataz(long sectorId, long capatazId) throws Exception {
        http.perform(
                        patch(API + "/sectores/" + sectorId + "/capataz")
                                .with(httpBasic("jefe", CLAVE))
                                .contentType("application/json")
                                .content("{\"capataz_id\":" + capatazId + "}"))
                .andExpect(status().isOk());
    }

    private long idCore(String nombreUsuario) {
        return jdbc.queryForObject(
                "SELECT id FROM core_usuario WHERE nombre_usuario = :nombre",
                Map.of("nombre", nombreUsuario),
                Long.class);
    }

    private void crearUsuario(String nombre, String rol) {
        long id =
                jdbc.queryForObject(
                        "INSERT INTO mod05_usuario(usuario, nombre, password_hash, activo) "
                                + "VALUES (:nombre, :nombre, :clave, true) RETURNING id",
                        Map.of("nombre", nombre, "clave", claves.encode(CLAVE)),
                        Long.class);
        jdbc.update(
                "INSERT INTO mod05_usuario_rol (usuario_id, rol) VALUES (:id, :rol)",
                Map.of("id", id, "rol", rol));
        String codigoRol =
                switch (rol) {
                    case "Jefe de sección" -> "jefe-seccion";
                    case "Ingeniero de campo" -> "ingeniero-campo";
                    case "Capataz" -> "capataz";
                    default -> throw new IllegalArgumentException("Rol de prueba desconocido");
                };
        Long rolId =
                jdbc.queryForObject(
                        "SELECT id FROM core_rol WHERE codigo = :codigo",
                        Map.of("codigo", codigoRol),
                        Long.class);
        jdbc.update(
                "INSERT INTO core_usuario (id, nombre_usuario, nombre, credencial_hash,"
                        + " rol_id, activo) VALUES (:id, :usuario, :nombre, :hash, :rol, true)",
                Map.of(
                        "id", id,
                        "usuario", nombre,
                        "nombre", nombre,
                        "hash", claves.encode(CLAVE),
                        "rol", rolId));
    }
}
