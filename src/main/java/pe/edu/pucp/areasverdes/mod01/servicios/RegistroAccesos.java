package pe.edu.pucp.areasverdes.mod01.servicios;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository;

/**
 * Registro de accesos de CU-MOD01-001 paso 6 y CU-MOD01-007: inicios correctos y fallidos,
 * bloqueos, accesos denegados y rechazos por falta de permiso. Usa una transacción propia para que
 * el rastro quede aunque la operación que lo provoca se deshaga.
 */
@Service
public class RegistroAccesos {

    private final BitacoraRepository bitacora;
    private final Clock reloj;

    public RegistroAccesos(BitacoraRepository bitacora, Clock reloj) {
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(
            Long usuarioId,
            String identificador,
            String canal,
            String resultado,
            String motivo,
            String ruta) {
        bitacora.insertarAcceso(
                usuarioId,
                usuarioId == null ? identificador : null,
                OffsetDateTime.now(reloj),
                canal,
                resultado,
                motivo,
                ruta);
    }
}
