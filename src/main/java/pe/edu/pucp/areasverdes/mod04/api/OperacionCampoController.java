package pe.edu.pucp.areasverdes.mod04.api;

import static pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository.values;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod04.servicios.ConsultasCampoService;
import pe.edu.pucp.areasverdes.mod04.servicios.IdempotenciaCampo;
import pe.edu.pucp.areasverdes.mod04.servicios.OperacionCampoService;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@RestController
@RequestMapping("/api/v1/operacion-campo")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Datos inválidos"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Sin autenticar"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Sin permiso"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Registro no encontrado"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Conflicto con el estado actual")
})
@Tag(name = "MOD-04", description = "RF-T-005, RF-PLA-005 y RF-ACT-003")
@SecurityRequirement(name = "basicAuth")
public class OperacionCampoController {
    final OperacionCampoService service;
    final ConsultasCampoService consultas;
    final IdempotenciaCampo idempotencia;

    public OperacionCampoController(
            OperacionCampoService service,
            ConsultasCampoService consultas,
            IdempotenciaCampo idempotencia) {
        this.service = service;
        this.consultas = consultas;
        this.idempotencia = idempotencia;
    }

    @GetMapping({"/me", "/me/"})
    @Operation(summary = "Consultar sesión y permisos de campo")
    public Map<String, Object> me(@AuthenticationPrincipal Usuario user) {
        service.exigirLectura(user);
        return values(
                "id",
                user.id(),
                "usuario",
                user.usuario(),
                "nombre",
                user.nombre(),
                "rol",
                user.rol() == null ? "Capataz" : user.rol(),
                "personal_id",
                service.personalUsuario(user));
    }

    @GetMapping({
        "/{recurso:personal|cuadrillas|encargos|partes|actividades}",
        "/{recurso:personal|cuadrillas|encargos|partes|actividades}/"
    })
    @Operation(summary = "Listar personal, cuadrillas, encargos, partes o actividades")
    public ConsultasCampoService.Pagina listar(
            @PathVariable String recurso,
            @RequestParam Map<String, String> params,
            HttpServletRequest request,
            @AuthenticationPrincipal Usuario user) {
        return consultas.listar(recurso, params, request.getRequestURL().toString(), user);
    }

    @GetMapping({"/catalogos/{catalogo}", "/catalogos/{catalogo}/"})
    @Operation(summary = "Consultar catálogos compartidos y tipos de actividad")
    public ConsultasCampoService.Pagina catalogo(
            @PathVariable String catalogo,
            @RequestParam Map<String, String> params,
            HttpServletRequest request,
            @AuthenticationPrincipal Usuario user) {
        return consultas.listar(catalogo, params, request.getRequestURL().toString(), user);
    }

    @PostMapping({"/personal", "/personal/"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar personal de campo")
    public Map<String, Object> personal(
            @Valid @RequestBody Entradas.Personal body, @AuthenticationPrincipal Usuario user) {
        return service.crearPersonal(body, user);
    }

    @PostMapping({"/cuadrillas", "/cuadrillas/"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "RF-T-005: registrar cuadrilla, capataz y operarios")
    public Map<String, Object> crearCuadrilla(
            @Valid @RequestBody Entradas.Cuadrilla body, @AuthenticationPrincipal Usuario user) {
        return service.guardarCuadrilla(null, body, user);
    }

    @PutMapping({"/cuadrillas/{id}", "/cuadrillas/{id}/"})
    @Operation(summary = "Actualizar composición de cuadrilla")
    public Map<String, Object> actualizarCuadrilla(
            @PathVariable long id,
            @Valid @RequestBody Entradas.Cuadrilla body,
            @AuthenticationPrincipal Usuario user) {
        return service.guardarCuadrilla(id, body, user);
    }

    @GetMapping({"/cuadrillas/{id}", "/cuadrillas/{id}/"})
    @Operation(summary = "Consultar cuadrilla e integrantes")
    public Map<String, Object> cuadrilla(
            @PathVariable long id, @AuthenticationPrincipal Usuario user) {
        return service.cuadrilla(id, user);
    }

    @PostMapping({"/encargos", "/encargos/"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "RF-PLA-005: registrar encargo semanal o quincenal")
    public Map<String, Object> crearEncargo(
            @Valid @RequestBody Entradas.Encargo body, @AuthenticationPrincipal Usuario user) {
        return service.guardarEncargo(null, body, user);
    }

    @PutMapping({"/encargos/{id}", "/encargos/{id}/"})
    @Operation(summary = "Actualizar planificación de encargo")
    public Map<String, Object> actualizarEncargo(
            @PathVariable long id,
            @Valid @RequestBody Entradas.Encargo body,
            @AuthenticationPrincipal Usuario user) {
        return service.guardarEncargo(id, body, user);
    }

    @GetMapping({"/encargos/{id}", "/encargos/{id}/"})
    @Operation(summary = "Consultar encargo")
    public Map<String, Object> encargo(
            @PathVariable long id, @AuthenticationPrincipal Usuario user) {
        return service.encargo(id, user);
    }

    @PostMapping({"/partes", "/partes/"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Soporte mínimo: abrir parte para registrar participantes")
    public Map<String, Object> crearParte(
            @Valid @RequestBody Entradas.Parte body,
            @AuthenticationPrincipal Usuario user,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID clave) {
        return idempotencia.ejecutar(
                clave, user, "crear-parte", body, () -> service.crearParte(body, user));
    }

    @PostMapping({"/actividades", "/actividades/"})
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Soporte mínimo: crear actividad asociada a un parte")
    public Map<String, Object> crearActividad(
            @Valid @RequestBody Entradas.Actividad body,
            @AuthenticationPrincipal Usuario user,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID clave) {
        return idempotencia.ejecutar(
                clave, user, "crear-actividad", body, () -> service.crearActividad(body, user));
    }

    @GetMapping({"/actividades/{id}/participantes", "/actividades/{id}/participantes/"})
    @Operation(summary = "Consultar participantes de actividad")
    public Map<String, Object> participantes(
            @PathVariable long id, @AuthenticationPrincipal Usuario user) {
        return service.participantes(id, user);
    }

    @GetMapping({"/actividades/{id}", "/actividades/{id}/"})
    @Operation(summary = "Consultar actividad")
    public Map<String, Object> actividad(
            @PathVariable long id, @AuthenticationPrincipal Usuario user) {
        return service.actividad(id, user);
    }

    @GetMapping({
        "/actividades/{id}/operarios-disponibles",
        "/actividades/{id}/operarios-disponibles/"
    })
    @Operation(summary = "Consultar operarios elegibles en la fecha de actividad")
    public List<Map<String, Object>> disponibles(
            @PathVariable long id, @AuthenticationPrincipal Usuario user) {
        return service.operariosDisponibles(id, user);
    }

    @PutMapping({"/actividades/{id}/participantes", "/actividades/{id}/participantes/"})
    @Operation(summary = "RF-ACT-003: reemplazar participantes; el servidor calcula la cantidad")
    public Map<String, Object> guardarParticipantes(
            @PathVariable long id,
            @Valid @RequestBody Entradas.Participantes body,
            @AuthenticationPrincipal Usuario user) {
        return service.guardarParticipantes(id, body, user);
    }
}
