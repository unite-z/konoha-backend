package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod03.ResumenSectores.Resumen;

/** Avance del ciclo por sector y servicio (AV-433). Ver {@link ResumenSectores}. */
@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Planificación")
@SecurityRequirement(name = "basicAuth")
public class ResumenSectoresController {

    private final ResumenSectores resumen;

    public ResumenSectoresController(ResumenSectores resumen) {
        this.resumen = resumen;
    }

    @Operation(summary = "Avance por sector y servicio (corte, riego y poda), por ciclo o fechas")
    @GetMapping({"/resumen-sectores", "/resumen-sectores/"})
    public Resumen resumenSectores(
            UsuarioPlanificacion usuario,
            @Parameter(description = "Id del ciclo (programación)")
                    @RequestParam(name = "ciclo_id", required = false)
                    Long cicloId,
            @Parameter(description = "Ciclos que acaban desde esta fecha (AAAA-MM-DD)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate desde,
            @Parameter(description = "Ciclos que empiezan hasta esta fecha (AAAA-MM-DD)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate hasta) {
        Acceso.consultar(usuario);
        return resumen.resumir(cicloId, desde, hasta);
    }
}
