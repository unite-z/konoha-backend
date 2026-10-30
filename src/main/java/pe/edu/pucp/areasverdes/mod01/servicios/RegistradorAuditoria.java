package pe.edu.pucp.areasverdes.mod01.servicios;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository;

/**
 * Único punto de escritura de la auditoría común (RF-T-008, DiagramaClases v2). Lo usan MOD-01 y
 * los módulos que registran altas y cambios de actividades, atenciones, incidencias y ejemplares.
 * Se llama dentro de la transacción del cambio, así que si el cambio falla no queda rastro falso.
 */
@Service
public class RegistradorAuditoria {

    private final BitacoraRepository bitacora;
    private final Clock reloj;

    public RegistradorAuditoria(BitacoraRepository bitacora, Clock reloj) {
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    public void registrarAlta(String entidad, long id, Map<String, ?> valores, long autorId) {
        bitacora.insertarCambio(
                entidad, id, "Alta", autorId, OffsetDateTime.now(reloj), Map.of(), valores, null);
    }

    public void registrarCambio(
            String entidad,
            long id,
            String accion,
            Map<String, ?> antes,
            Map<String, ?> despues,
            String motivo,
            long autorId) {
        bitacora.insertarCambio(
                entidad, id, accion, autorId, OffsetDateTime.now(reloj), antes, despues, motivo);
    }
}
