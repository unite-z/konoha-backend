package pe.edu.pucp.areasverdes.mod04.config;

import java.nio.file.Path;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod04.servicios.PrecargaCampoService;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "mod04.seed", havingValue = "true")
public class PrecargaCampoDev implements CommandLineRunner {
    final PrecargaCampoService seed;
    final Environment env;

    public PrecargaCampoDev(PrecargaCampoService seed, Environment env) {
        this.seed = seed;
        this.env = env;
    }

    @Override
    public void run(String... args) {
        seed.cargar(
                Path.of(env.getProperty("mod04.mock-data", "mock-data")),
                env.getProperty("MOD04_DEMO_PASSWORD"));
    }
}
