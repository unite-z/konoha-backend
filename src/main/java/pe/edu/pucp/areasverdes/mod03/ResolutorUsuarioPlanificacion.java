package pe.edu.pucp.areasverdes.mod03;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.edu.pucp.areasverdes.mod01.config.SesionCompartida;
import pe.edu.pucp.areasverdes.mod01.datos.RolesRepository;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Da a los controladores de MOD-03 el {@link UsuarioPlanificacion} de la petición. Con la sesión de
 * MOD-01 es el principal; con HTTP Basic se traduce el {@link Usuario} de MOD-05 a los permisos del
 * rol de MOD-01 equivalente. Sin usuario o inactivo, {@code null}: {@link Acceso} responde 403.
 */
@Configuration
class ResolutorUsuarioPlanificacion implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    /**
     * Código del rol de MOD-01 para cada rol de MOD-05 (el inverso de {@link SesionCompartida}).
     */
    private static final Map<String, String> ROL_MOD01 =
            SesionCompartida.ROL_MOD05.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private final RolesRepository roles;

    ResolutorUsuarioPlanificacion(RolesRepository roles) {
        this.roles = roles;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolutores) {
        resolutores.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return parametro.getParameterType() == UsuarioPlanificacion.class;
    }

    @Override
    public UsuarioPlanificacion resolveArgument(
            MethodParameter parametro,
            ModelAndViewContainer modelo,
            NativeWebRequest peticion,
            WebDataBinderFactory enlaces) {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        Object principal = autenticacion == null ? null : autenticacion.getPrincipal();
        if (principal instanceof UsuarioPlanificacion u) {
            return u;
        }
        // El principal de MOD-05 ya trae los permisos de MOD-01 (AV-382).
        if (principal instanceof Usuario m && m.activo()) {
            return new UsuarioPlanificacion(m.id(), m.nombre(), m.permisos());
        }
        return null;
    }
}
