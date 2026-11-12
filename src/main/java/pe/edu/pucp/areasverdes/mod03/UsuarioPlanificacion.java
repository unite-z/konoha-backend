package pe.edu.pucp.areasverdes.mod03;

import java.util.Set;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;

/**
 * Quien hace la petición en MOD-03, con los permisos de su rol de MOD-01 (AV-252). Con la sesión de
 * MOD-01 llegan tal cual; con HTTP Basic (pruebas y smoke test), los del rol de MOD-01 que
 * corresponde al rol de MOD-05 ({@link ResolutorUsuarioPlanificacion}).
 *
 * @param id el de {@code core_usuario}, igual al de {@code mod05_usuario} (copia de {@code V6})
 */
public record UsuarioPlanificacion(long id, String nombre, Set<String> permisos) {

    public UsuarioPlanificacion {
        permisos = Set.copyOf(permisos);
    }

    public boolean tiene(String permiso) {
        return permisos.contains(permiso);
    }

    static UsuarioPlanificacion deSesion(UsuarioSesion u) {
        return new UsuarioPlanificacion(u.id(), u.nombre(), u.permisos());
    }
}
