package pe.edu.pucp.areasverdes.mod06.config;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proveedor de IA activo (AV-395): {@code IA_PROVEEDOR} = {@code gemini} (por defecto) o {@code
 * deepseek}, con {@code DEEPSEEK_API_KEY} y {@code DEEPSEEK_MODEL}. La clave nunca se registra:
 * {@link #toString()} no la incluye.
 */
@ConfigurationProperties(prefix = "mod06.proveedor-ia")
public record ProveedorIaPropiedades(String nombre, String deepseekApiKey, String deepseekModel) {

    public static final ProveedorIaPropiedades GEMINI =
            new ProveedorIaPropiedades(null, null, null);

    public ProveedorIaPropiedades {
        nombre =
                nombre == null || nombre.isBlank()
                        ? "gemini"
                        : nombre.strip().toLowerCase(Locale.ROOT);
        deepseekApiKey = deepseekApiKey == null ? "" : deepseekApiKey;
        deepseekModel =
                deepseekModel == null || deepseekModel.isBlank() ? "deepseek-flash" : deepseekModel;
    }

    @Override
    public String toString() {
        return "ProveedorIaPropiedades[nombre=" + nombre + ", deepseekModel=" + deepseekModel + "]";
    }
}
