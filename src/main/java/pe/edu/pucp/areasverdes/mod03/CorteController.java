package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.pucp.areasverdes.mod03.CorteCesped.CicloCorte;
import pe.edu.pucp.areasverdes.mod03.CorteCesped.RutaCorte;

/** Ciclo de corte de césped (AV-259 a AV-262). Ver {@link CorteCesped}. */
@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Corte de césped")
@SecurityRequirement(name = "basicAuth")
public class CorteController {

    private final CorteCesped corte;

    public CorteController(CorteCesped corte) {
        this.corte = corte;
    }

    @Operation(
            summary =
                    "Ruta propuesta por zonas para programar el servicio, y si va fuera de"
                            + " frecuencia")
    @GetMapping({"/servicios/{id}/ruta", "/servicios/{id}/ruta/"})
    public RutaCorte ruta(UsuarioPlanificacion usuario, @PathVariable long id) {
        return corte.ruta(id, usuario);
    }

    @Operation(summary = "Ciclo de corte con su ruta de jardines y sus marcas")
    @GetMapping({"/ciclos/{id}", "/ciclos/{id}/"})
    public CicloCorte ciclo(UsuarioPlanificacion usuario, @PathVariable long id) {
        return corte.ciclo(id, usuario);
    }
}
