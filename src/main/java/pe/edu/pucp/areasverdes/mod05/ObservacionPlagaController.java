package pe.edu.pucp.areasverdes.mod05;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** RF-FIT-005: observaciones de presencia de plagas en espacios o ejemplares. */
@RestController
@RequestMapping("/api/tercerizados")
@Tag(name = "MOD-05")
@SecurityRequirement(name = "basicAuth")
public class ObservacionPlagaController {
    final ObservacionPlagaService service;

    public ObservacionPlagaController(ObservacionPlagaService service) {
        this.service = service;
    }

    @GetMapping({"/observaciones-plagas/", "/observaciones-plagas"})
    public ConsultasService.Pagina listar(
            @AuthenticationPrincipal Usuario user,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return service.listar(user, params, request.getRequestURL().toString());
    }

    @PostMapping({"/observaciones-plagas/", "/observaciones-plagas"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registrar(
            @AuthenticationPrincipal Usuario user,
            @Valid @RequestBody EntradasFitosanitario.ObservacionPlaga body) {
        return service.registrar(body, user);
    }

    @GetMapping({"/observaciones-plagas/{id}/", "/observaciones-plagas/{id}"})
    public Map<String, Object> detalle(
            @AuthenticationPrincipal Usuario user, @PathVariable long id) {
        return service.detalle(user, id);
    }

    @GetMapping({"/observaciones-plagas/por-alcance/", "/observaciones-plagas/por-alcance"})
    public List<Map<String, Object>> porAlcance(
            @AuthenticationPrincipal Usuario user,
            @RequestParam(name = "area_verde_id", required = false) String areaVerdeIds,
            @RequestParam(name = "ejemplar_id", required = false) String ejemplarIds) {
        return service.porAlcance(user, idsDe(areaVerdeIds), idsDe(ejemplarIds));
    }

    /** `1,2,3` desde la consulta: la misma coma separa varios destinos del aviso del asistente. */
    static List<Long> idsDe(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        try {
            return java.util.Arrays.stream(csv.split(","))
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException e) {
            throw ApiError.invalid("Indica identificadores numéricos separados por comas");
        }
    }
}
