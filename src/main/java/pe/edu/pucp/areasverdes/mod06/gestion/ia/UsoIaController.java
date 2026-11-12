package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.ia.ConfiguracionIaRepository;
import pe.edu.pucp.areasverdes.mod06.ia.ResumenUsoIaRepository;

/**
 * Uso y coste de la IA (AV-396): resumen de un periodo y precios por modelo. Solo el jefe de
 * sección (IA.PROPUESTA.VALIDAR). Por defecto, el mes en curso en Lima.
 */
@RestController
@RequestMapping("/api/v1/ia")
@SecurityRequirement(name = "sesionCore")
@Tag(name = "AV-396 Uso y costes de la IA")
public class UsoIaController {

    static final int LIMITE_HISTORIAL = 100;

    private final ResumenUsoIaRepository resumen;
    private final Clock reloj;

    public UsoIaController(ResumenUsoIaRepository resumen, Clock reloj) {
        this.resumen = resumen;
        this.reloj = reloj;
    }

    public record Precio(
            String modelo,
            String vigenteDesde,
            BigDecimal usdMillonEntrada,
            BigDecimal usdMillonSalida,
            BigDecimal usdMillonCache) {}

    @Operation(
            summary = "Llamadas, tokens, coste y errores de la IA en un periodo, con su desglose")
    @GetMapping({"/uso", "/uso/"})
    public Map<String, Object> uso(
            @AuthenticationPrincipal UsuarioSesion usuario,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        SugerenciasIa.puedeValidar(usuario);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate inicio = fecha("desde", desde, hoy.withDayOfMonth(1));
        LocalDate fin = fecha("hasta", hasta, hoy);
        if (fin.isBefore(inicio))
            throw ErrorGestion.validacion(
                    "hasta", "La fecha final no puede ser anterior a la inicial");
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("desde", inicio.toString());
        salida.put("hasta", fin.toString());
        salida.put("totales", resumen.totales(inicio, fin));
        salida.put("por_asistente", resumen.porAsistente(inicio, fin));
        salida.put("por_modelo", resumen.porModelo(inicio, fin));
        salida.put("por_dia", resumen.porDia(inicio, fin));
        salida.put("historial", resumen.historial(inicio, fin, LIMITE_HISTORIAL));
        return salida;
    }

    @Operation(
            summary = "Precios por modelo, en USD por millón de tokens, con su fecha de vigencia")
    @GetMapping({"/precios", "/precios/"})
    public List<Map<String, Object>> precios(@AuthenticationPrincipal UsuarioSesion usuario) {
        SugerenciasIa.puedeValidar(usuario);
        return resumen.precios();
    }

    @Operation(
            summary =
                    "Fija el precio de un modelo desde una fecha (las llamadas anteriores no"
                            + " cambian)")
    @PutMapping({"/precios", "/precios/"})
    public List<Map<String, Object>> guardarPrecio(
            @AuthenticationPrincipal UsuarioSesion usuario, @RequestBody Precio precio) {
        SugerenciasIa.puedeValidar(usuario);
        boolean conocido =
                precio != null
                        && ConfiguracionIaRepository.MODELOS.values().stream()
                                .anyMatch(l -> l.contains(precio.modelo()));
        if (!conocido) throw ErrorGestion.validacion("modelo", "Elige un modelo de la lista");
        LocalDate vigente = fecha("vigente_desde", precio.vigenteDesde(), LocalDate.now(reloj));
        positivo("usd_millon_entrada", precio.usdMillonEntrada(), true);
        positivo("usd_millon_salida", precio.usdMillonSalida(), true);
        positivo("usd_millon_cache", precio.usdMillonCache(), false);
        resumen.guardarPrecio(
                precio.modelo(),
                vigente,
                precio.usdMillonEntrada(),
                precio.usdMillonSalida(),
                precio.usdMillonCache());
        return resumen.precios();
    }

    private static LocalDate fecha(String campo, String valor, LocalDate porDefecto) {
        if (valor == null || valor.isBlank()) return porDefecto;
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw ErrorGestion.validacion(campo, "Usa el formato AAAA-MM-DD");
        }
    }

    private static void positivo(String campo, BigDecimal valor, boolean obligatorio) {
        if (valor == null) {
            if (obligatorio) throw ErrorGestion.validacion(campo, "Indica el precio");
            return;
        }
        if (valor.signum() < 0)
            throw ErrorGestion.validacion(campo, "El precio no puede ser negativo");
    }
}
