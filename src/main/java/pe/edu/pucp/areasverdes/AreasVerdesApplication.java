package pe.edu.pucp.areasverdes;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/**
 * Back-end Áreas Verdes. Está en la raíz del paquete base para que, en la fusión, escanee tanto
 * {@code ...mod06} como {@code ...mod05}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AreasVerdesApplication {

    /** Supuesto: campus en Lima. La zona horaria está pendiente en el Modelo de BD v1 (sección 11.2). */
    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    public static void main(String[] args) {
        SpringApplication.run(AreasVerdesApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.system(LIMA);
    }
}
