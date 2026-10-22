package pe.edu.pucp.areasverdes.mod06.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Configuración no válida que impide arrancar ({@code ImproperlyConfigured} de Django), por ejemplo un
 * {@code DATA_SOURCE} distinto de {@code mock} o {@code db}.
 */
public class ConfiguracionInvalida extends IllegalStateException {

    public ConfiguracionInvalida(String mensaje) {
        super(mensaje);
    }

    /** Muestra el mensaje sin la traza de Spring al fallar el arranque. */
    public static class Analizador extends AbstractFailureAnalyzer<ConfiguracionInvalida> {
        @Override
        protected FailureAnalysis analyze(Throwable rootFailure, ConfiguracionInvalida causa) {
            return new FailureAnalysis(causa.getMessage(), "Corrige el valor en backend/.env o en la variable de entorno y vuelve a arrancar.", causa);
        }
    }
}
