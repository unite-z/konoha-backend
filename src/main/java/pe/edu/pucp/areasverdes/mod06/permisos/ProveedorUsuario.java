package pe.edu.pucp.areasverdes.mod06.permisos;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Obtiene el usuario de la petición.
 *
 * <p>TEMPORAL (igual que {@code DEFAULT_AUTHENTICATION_CLASSES = []} del Django): sin autenticación, todas las
 * peticiones son anónimas. TODO: sustituir por la autenticación por token y los permisos por rol del MOD-01
 * (RF-T-001, RF-T-004, RNF-007). Las pruebas sustituyen este bean para simular un usuario con rol.
 */
@Component
public class ProveedorUsuario {

    public Usuario usuario(HttpServletRequest peticion) {
        return Usuario.ANONIMO;
    }
}
