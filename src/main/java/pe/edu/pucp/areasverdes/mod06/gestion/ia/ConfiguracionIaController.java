package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ServicioIANoDisponible;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteDeepSeek;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteGemini;
import pe.edu.pucp.areasverdes.mod06.ia.ConfiguracionIaRepository;
import pe.edu.pucp.areasverdes.mod06.ia.FabricaClienteIA;

/**
 * Proveedor y modelo de IA desde Configuración (AV-395): consultar, cambiar y probar la conexión.
 * Afecta a todos los asistentes, así que exige IA.PROPUESTA.VALIDAR (jefe de sección). Las claves
 * siguen en el {@code api.env}: solo se indica si están.
 */
@RestController
@RequestMapping("/api/v1/ia")
@SecurityRequirement(name = "sesionCore")
@Tag(name = "AV-395 Configuración de la IA")
public class ConfiguracionIaController {

    private static final Map<String, Object> ESQUEMA_PRUEBA =
            Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of("ok", Map.of("type", "boolean")),
                    "required",
                    List.of("ok"));

    private final FabricaClienteIA fabrica;
    private final ConfiguracionIaRepository configuracion;

    public ConfiguracionIaController(
            FabricaClienteIA fabrica, ConfiguracionIaRepository configuracion) {
        this.fabrica = fabrica;
        this.configuracion = configuracion;
    }

    public record Cambio(String proveedor, String modelo) {}

    @Operation(summary = "Proveedor y modelo activos, y los que se pueden elegir")
    @GetMapping({"/configuracion", "/configuracion/"})
    public Map<String, Object> ver(@AuthenticationPrincipal UsuarioSesion usuario) {
        SugerenciasIa.puedeValidar(usuario);
        FabricaClienteIA.Activo activo = fabrica.activo();
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("proveedor", activo.proveedor());
        salida.put("modelo", activo.modelo());
        salida.put("origen", activo.origen());
        configuracion
                .leer()
                .ifPresent(e -> salida.put("actualizado_en", e.actualizadoEn().toString()));
        List<Map<String, Object>> proveedores = new ArrayList<>();
        for (String id : List.of("gemini", "deepseek")) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", id);
            p.put(
                    "nombre",
                    id.equals("gemini")
                            ? ClienteGemini.PROVEEDOR_IA
                            : ClienteDeepSeek.PROVEEDOR_IA);
            p.put("modelos", ConfiguracionIaRepository.MODELOS.get(id));
            p.put("clave_configurada", fabrica.tieneClave(id));
            proveedores.add(p);
        }
        salida.put("proveedores", proveedores);
        return salida;
    }

    @Operation(summary = "Cambia el proveedor y el modelo de todos los asistentes de IA")
    @PutMapping({"/configuracion", "/configuracion/"})
    public Map<String, Object> cambiar(
            @AuthenticationPrincipal UsuarioSesion usuario, @RequestBody Cambio cambio) {
        SugerenciasIa.puedeValidar(usuario);
        List<String> modelos =
                cambio == null || cambio.proveedor() == null
                        ? null
                        : ConfiguracionIaRepository.MODELOS.get(cambio.proveedor());
        if (modelos == null) throw ErrorGestion.validacion("proveedor", "Elige Gemini o DeepSeek");
        if (!modelos.contains(cambio.modelo()))
            throw ErrorGestion.validacion("modelo", "Elige un modelo de la lista");
        configuracion.guardar(cambio.proveedor(), cambio.modelo(), usuario.id());
        return ver(usuario);
    }

    @Operation(
            summary =
                    "Prueba un proveedor y un modelo con una petición mínima (por defecto, los"
                            + " activos)")
    @PostMapping({"/configuracion/probar", "/configuracion/probar/"})
    public Map<String, Object> probar(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestBody(required = false) Cambio cambio) {
        SugerenciasIa.puedeValidar(usuario);
        FabricaClienteIA.Activo activo = fabrica.activo();
        String proveedor =
                cambio != null && cambio.proveedor() != null
                        ? cambio.proveedor()
                        : activo.proveedor();
        String modelo =
                cambio != null && cambio.modelo() != null ? cambio.modelo() : activo.modelo();
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("proveedor", proveedor);
        salida.put("modelo", modelo);
        long inicio = System.nanoTime();
        try {
            fabrica.crear(proveedor, modelo)
                    .generarJson(
                            "Comprobación de conexión.",
                            List.of(),
                            ESQUEMA_PRUEBA,
                            "Responde {\"ok\": true}.");
            salida.put("ok", true);
        } catch (ServicioIANoDisponible e) {
            salida.put("ok", false);
            salida.put("detalle", e.getMessage());
        }
        salida.put("duracion_ms", (System.nanoTime() - inicio) / 1_000_000);
        return salida;
    }
}
