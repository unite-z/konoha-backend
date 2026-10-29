package pe.edu.pucp.areasverdes.mod01.servicios;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Lo que MOD-01 necesita de MOD-04 (cuadrillas y encargos) para el alcance del capataz
 * (HU-MOD01-007) y la desactivación con encargos abiertos (HU-MOD01-005). MOD-04 aporta su
 * implementación con {@code @Primary} cuando fusione; hasta entonces responde {@link SinOperacion}.
 */
public interface ConsultaCampo {

    record EncargoAbierto(long id, String descripcion) {}

    /** Cuadrillas activas de las que el usuario es capataz. */
    Set<Long> cuadrillasDelCapataz(long usuarioId);

    /** Encargos abiertos asignados al usuario como capataz. */
    List<EncargoAbierto> encargosAbiertos(long usuarioId);

    /** Implementación mientras MOD-04 no esté en {@code main}: ningún capataz tiene cuadrilla. */
    @Component
    class SinOperacion implements ConsultaCampo {
        @Override
        public Set<Long> cuadrillasDelCapataz(long usuarioId) {
            return Set.of();
        }

        @Override
        public List<EncargoAbierto> encargosAbiertos(long usuarioId) {
            return List.of();
        }
    }
}
