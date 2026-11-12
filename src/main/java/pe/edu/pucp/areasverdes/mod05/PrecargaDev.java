package pe.edu.pucp.areasverdes.mod05;

import java.nio.file.Path;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import pe.edu.pucp.areasverdes.mod03.PrecargaPlanificacion;

@Configuration
@Profile("dev")
public class PrecargaDev {
    @Bean
    ApplicationRunner precarga(
            PrecargaService seed,
            PrecargaPlanificacion planificacion,
            FormatosReporteIniciales formatos,
            @Value("${mod05.seed:false}") boolean enabled,
            @Value("${mod05.mock-data}") String folder,
            @Value("${MOD05_DEMO_PASSWORD:}") String password) {
        return args -> {
            if (enabled) {
                seed.cargar(Path.of(folder), password);
                // MOD-03: servicios periódicos y ruidosos (AV-368) y valores iniciales de la
                // sección (AV-363) para los servicios de demostración.
                planificacion.cargar(Path.of(folder));
                // RF-SER-009: formato estándar del informe del proveedor que usa la IA (RF-IA-001).
                formatos.precargar(Path.of(folder));
                LoggerFactory.getLogger(PrecargaDev.class)
                        .info("Precarga MOD-05 completada. Usuarios existentes no modificados.");
            }
        };
    }
}
