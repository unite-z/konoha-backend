package pe.edu.pucp.areasverdes.mod01;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Reglas que garantiza la base: RI-35 (bitácora inmutable), un rol y la matriz cargada. */
class CoreMigracionIntegrationTest extends ApiCore {

    @Test
    void laAuditoriaYElRegistroDeAccesosNoSeModificanNiSeBorran() {
        jdbc.update(
                "INSERT INTO core_registro_auditoria (entidad, registro_id, accion, usuario_id)"
                        + " VALUES ('ejemplar', 1, 'Alta', :u)",
                Map.of("u", ids.get("jefe")));
        jdbc.update(
                "INSERT INTO core_registro_acceso (usuario_id, canal, resultado)"
                        + " VALUES (:u, 'oficina', 'Exitoso')",
                Map.of("u", ids.get("jefe")));
        for (String tabla : new String[] {"core_registro_auditoria", "core_registro_acceso"}) {
            assertThrows(
                    DataAccessException.class,
                    () -> jdbc.update("UPDATE " + tabla + " SET fecha_hora = now()", Map.of()));
            assertThrows(
                    DataAccessException.class, () -> jdbc.update("DELETE FROM " + tabla, Map.of()));
        }
    }

    @Test
    void cadaUsuarioTieneExactamenteUnRol() {
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO core_usuario (nombre_usuario, nombre, credencial_hash)"
                                        + " VALUES ('sin-rol', 'Sin rol', 'x')",
                                Map.of()));
    }

    @Test
    void laMatrizDeMinimoPrivilegioEstaCargada() {
        assertEquals(29, permisos("jefe-seccion"));
        assertEquals(0, permisos("operario"));
        assertEquals(
                0,
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM core_rol_permiso rp
                        JOIN core_rol r ON r.id = rp.rol_id JOIN core_permiso p ON p.id = rp.permiso_id
                        WHERE r.codigo <> 'jefe-seccion' AND p.es_sensible
                        """,
                        Map.of(),
                        Integer.class),
                "Ningún permiso sensible fuera del jefe de sección (RNF-008)");
    }

    private int permisos(String rol) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM core_rol_permiso rp JOIN core_rol r ON r.id = rp.rol_id
                WHERE r.codigo = :rol
                """,
                Map.of("rol", rol),
                Integer.class);
    }
}
