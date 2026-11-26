package pe.edu.pucp.areasverdes.mod02.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pe.edu.pucp.areasverdes.mod02.servicios.AccesoCoberturas;
import pe.edu.pucp.areasverdes.mod02.servicios.AreasService;
import pe.edu.pucp.areasverdes.mod02.servicios.ResumenAreaService;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/inventario/areas-verdes")
@Tag(name = "MOD-02 Inventario")
@SecurityRequirement(name = "basicAuth")
public class AreasController {

    private final AreasService areas;
    private final ResumenAreaService resumenes;

    public AreasController(AreasService areas, ResumenAreaService resumenes) {
        this.areas = areas;
        this.resumenes = resumenes;
    }

    public record AreaVerde(
            long id,
            String codigo,
            String nombre,
            String lugar,
            String ubicacion,
            String superficieM2,
            Long tipoCoberturaId,
            String tipoCoberturaNombre,
            Long tipoUsoId,
            String tipoUsoNombre,
            Long cuartelId,
            String cuartelNumero,
            Long sectorId,
            String sectorNombre,
            boolean activo,
            UUID poligonoMapaId,
            JsonNode contorno) {}

    public record PaginaAreas(int count, Integer next, Integer previous, List<AreaVerde> results) {}

    public record NuevaAreaVerde(
            @NotBlank(message = "Indica el nombre del área") @Size(max = 150) String nombre,
            @NotBlank(message = "Indica el lugar o referente") @Size(max = 150) String lugar,
            @NotBlank(message = "Indica la ubicación") @Size(max = 250) String ubicacion,
            @DecimalMin(value = "0.01", message = "La superficie debe ser mayor que cero")
                    BigDecimal superficieM2,
            Long tipoCoberturaId,
            Long tipoUsoId,
            Long cuartelId,
            Long sectorId,
            UUID poligonoMapaId,
            JsonNode contorno) {}

    public record CambioAreaVerde(
            @Size(max = 150) String nombre,
            @Size(max = 150) String lugar,
            @Size(max = 250) String ubicacion,
            @DecimalMin(value = "0.01", message = "La superficie debe ser mayor que cero")
                    BigDecimal superficieM2,
            Boolean activo,
            Long tipoCoberturaId,
            Long tipoUsoId,
            Long cuartelId) {}

    public record CambioSectorArea(@JsonAlias("sector_id") Long sectorId) {}

    /** Datos calculados por el servidor para el formulario de alta; no reserva el código. */
    public record SugerenciaArea(
            String codigo,
            String superficieM2,
            Long sectorId,
            String sectorNombre,
            BigDecimal distanciaSectorM) {}

    public record AsignacionSector(
            long id, long sectorId, String sectorNombre, String fechaInicio, String fechaFin) {}

    @Operation(summary = "Consulta áreas verdes por nombre")
    @ApiResponse(responseCode = "200", description = "Página del inventario")
    @GetMapping({"", "/"})
    public PaginaAreas listar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) String q,
            @RequestParam(name = "tipo_uso_id", required = false) Long tipoUsoId,
            @RequestParam(name = "sector_id", required = false) Long sectorId,
            @RequestParam(name = "cuartel_id", required = false) Long cuartelId,
            @RequestParam(defaultValue = "1") int page) {
        AccesoCoberturas.consultar(usuario);
        return areas.listar(q, tipoUsoId, sectorId, cuartelId, page);
    }

    @Operation(summary = "Sugiere código, superficie y sector para un contorno antes de registrar")
    @ApiResponse(responseCode = "200", description = "Sugerencia; sin sector si no hay vecinos")
    @ApiResponse(responseCode = "400", description = "El contorno no es un GeoJSON de polígono")
    @ApiResponse(responseCode = "422", description = "El polígono no es simple y cerrado")
    @GetMapping({"/sugerencia", "/sugerencia/"})
    public SugerenciaArea sugerir(
            @AuthenticationPrincipal Usuario usuario,
            @Parameter(
                            description =
                                    "Contorno dibujado: GeoJSON Polygon (o Feature con ese"
                                            + " Polygon) en EPSG:4326, codificado en la URL")
                    @RequestParam
                    String contorno) {
        return areas.sugerir(contorno, usuario);
    }

    @GetMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Consulta la ficha de un área verde")
    public AreaVerde obtener(@AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        AccesoCoberturas.consultar(usuario);
        return areas.obtenerPorId(id);
    }

    @Operation(
            summary = "Registra un área verde",
            description =
                    "Con `contorno` (GeoJSON Polygon, EPSG:4326) el servidor guarda la geometría,"
                            + " la vincula al área y calcula `superficie_m2` en UTM 18S (ignora la"
                            + " enviada); sin él, `superficie_m2` es obligatoria. Dibujar el"
                            + " contorno exige además CORE.CATALOGO.GESTIONAR, como vincular un"
                            + " polígono existente. Con `contorno` o `poligono_mapa_id`, la"
                            + " respuesta incluye `poligono_mapa_id` y `contorno` (GeoJSON).")
    @ApiResponse(responseCode = "201", description = "Área verde registrada")
    @ApiResponse(responseCode = "400", description = "Datos no válidos")
    @ApiResponse(
            responseCode = "404",
            description = "La cobertura indicada no existe o está inactiva")
    @ApiResponse(
            responseCode = "422",
            description =
                    "Contorno no válido: contorno-invalido, contorno-no-cerrado,"
                            + " contorno-fuera-del-campus, contorno-solapado o contorno-y-poligono")
    @PostMapping(
            value = {"", "/"},
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AreaVerde> crear(
            @AuthenticationPrincipal Usuario usuario, @Valid @RequestBody NuevaAreaVerde nueva) {
        AreaVerde area = areas.crear(nueva, usuario);
        return ResponseEntity.created(URI.create("/api/v1/inventario/areas-verdes/" + area.id()))
                .body(area);
    }

    @PatchMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Actualiza o da de baja un área verde conservando sus referencias")
    public AreaVerde modificar(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @Valid @RequestBody CambioAreaVerde cambio) {
        return areas.modificar(id, cambio, usuario);
    }

    @PatchMapping({"/{id}/sector", "/{id}/sector/"})
    @Operation(summary = "Asigna o reasigna un área a un sector manteniendo su historial")
    public AreaVerde asignarSector(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @RequestBody CambioSectorArea cambio) {
        return areas.asignarSector(id, cambio.sectorId(), usuario);
    }

    @GetMapping({"/{id}/historial-sector", "/{id}/historial-sector/"})
    @Operation(summary = "Consulta las asignaciones históricas de sector de un área verde")
    public List<AsignacionSector> historialSector(
            @AuthenticationPrincipal Usuario usuario, @PathVariable long id) {
        AccesoCoberturas.consultar(usuario);
        return areas.historialSector(id);
    }

    @GetMapping({"/{id}/resumen", "/{id}/resumen/"})
    @Operation(
            summary = "Resumen e historial agregados de un área verde",
            description =
                    "Ficha, ejemplares por situación, incidencias abiertas, última atención,"
                        + " próxima actividad programada, prioridad validada e historial paginado"
                        + " (actividades, incidencias, atenciones y cambios de sector, de más"
                        + " reciente a más antiguo; ?page=N y ?page_size=1..100, 25 por defecto)."
                        + " Exige INV.INVENTARIO.CONSULTAR y cada fuente su permiso: actividades"
                        + " CAM.PARTE.CONSULTAR (el capataz, solo las suyas), incidencias"
                        + " PLA.INCIDENCIA.REGISTRAR (sin CONSULTAR_TODAS, solo las propias),"
                        + " atenciones SER.ATENCION.REGISTRAR, programación"
                        + " PLA.PLANIFICACION.CONSULTAR y prioridad REP.INDICADOR.CONSULTAR. Sin"
                        + " él, su dato llega nulo y no entra en el historial; fuentes_visibles"
                        + " dice cuáles se consultaron.")
    @ApiResponse(responseCode = "404", description = "No existe esa área verde")
    @ApiResponse(responseCode = "400", description = "page o page_size fuera de rango")
    public ResumenAreaVerde resumen(
            @AuthenticationPrincipal Usuario usuario,
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "25") int pageSize,
            HttpServletRequest peticion) {
        return resumenes.resumen(
                id,
                page,
                pageSize,
                usuario,
                pagina ->
                        ServletUriComponentsBuilder.fromRequest(peticion)
                                .replaceQueryParam("page", pagina)
                                .build()
                                .toUriString());
    }
}
