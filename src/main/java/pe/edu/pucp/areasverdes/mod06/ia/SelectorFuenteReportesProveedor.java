package pe.edu.pucp.areasverdes.mod06.ia;

import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.config.ConfiguracionInvalida;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;

/**
 * Selección de la fuente de reportes del proveedor de RF-IA-001. Es el <b>único</b> lugar que lee
 * {@code IA_FUENTE_REPORTES}:
 * <ul>
 *   <li>{@code mod05} (por defecto) → {@link FuenteReportesProveedorMod05};</li>
 *   <li>{@code mock} → {@link FuenteReportesProveedorMock} sobre la {@code FuenteDatos} de la solicitud.</li>
 * </ul>
 * Un valor distinto impide arrancar, como {@code DATA_SOURCE}.
 */
@Component
public class SelectorFuenteReportesProveedor {

    public static final String MOD05 = "mod05";
    public static final String MOCK = "mock";

    private final String valor;
    private final ObjectProvider<FuenteReportesProveedorMod05> mod05;

    public SelectorFuenteReportesProveedor(Mod06Propiedades propiedades, ObjectProvider<FuenteReportesProveedorMod05> mod05) {
        this.valor = Py.strip(propiedades.iaFuenteReportes()).toLowerCase(Locale.ROOT);
        this.mod05 = mod05;
        if (!valor.equals(MOD05) && !valor.equals(MOCK)) {
            throw new ConfiguracionInvalida(
                "IA_FUENTE_REPORTES='" + valor + "' no es válido. Usa 'mod05' o 'mock' en backend/.env.");
        }
    }

    /** Fuente de reportes para una solicitud; {@code fuenteMod06} es la {@code FuenteDatos} que usa esa solicitud. */
    public FuenteReportesProveedor obtener(FuenteDatos fuenteMod06) {
        if (valor.equals(MOCK)) return new FuenteReportesProveedorMock(fuenteMod06);
        return mod05.getObject();
    }
}
