package pe.edu.pucp.areasverdes.mod03;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Avisos dentro de la aplicación del usuario de la sesión (AV-264). */
@RestController
@RequestMapping("/api/v1/planificacion")
@Tag(name = "MOD-03 Avisos")
@SecurityRequirement(name = "basicAuth")
public class AvisosController {

    private final Avisos avisos;

    public AvisosController(Avisos avisos) {
        this.avisos = avisos;
    }

    @Operation(summary = "Mis avisos, del más reciente al más antiguo")
    @GetMapping({"/avisos", "/avisos/"})
    public List<Avisos.Aviso> mios(UsuarioPlanificacion usuario) {
        return avisos.mios(usuario);
    }

    @Operation(summary = "Marca como leído un aviso propio")
    @PostMapping({"/avisos/{id}/leido", "/avisos/{id}/leido/"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leido(UsuarioPlanificacion usuario, @PathVariable long id) {
        avisos.marcarLeido(id, usuario);
    }
}
