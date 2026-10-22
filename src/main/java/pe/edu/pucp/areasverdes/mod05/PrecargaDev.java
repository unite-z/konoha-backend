package pe.edu.pucp.areasverdes.mod05;

import java.nio.file.Path;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.LoggerFactory;

@Configuration
@Profile("dev")
public class PrecargaDev {
    @Bean ApplicationRunner precarga(PrecargaService seed,@Value("${mod05.seed:false}") boolean enabled,
        @Value("${mod05.mock-data}") String folder,@Value("${MOD05_DEMO_PASSWORD:}") String password) {
        return args -> {
            if(enabled) {
                seed.cargar(Path.of(folder),password);
                LoggerFactory.getLogger(PrecargaDev.class).info("Precarga MOD-05 completada. Usuarios existentes no modificados.");
            }
        };
    }
}
