package pe.edu.pucp.areasverdes.mod06.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import pe.edu.pucp.areasverdes.mod06.apoyo.FuenteDatosMemoria;
import pe.edu.pucp.areasverdes.mod06.config.ConfiguracionInvalida;
import pe.edu.pucp.areasverdes.mod06.config.Mod06Propiedades;

/** Propiedad {@code IA_FUENTE_REPORTES} ({@code mod06.ia.fuente-reportes}) de RF-IA-001. */
class SelectorFuenteReportesProveedorTest {

    private static final FuenteReportesProveedorMod05 MOD05 = new FuenteReportesProveedorMod05(null, null);

    private static SelectorFuenteReportesProveedor selector(String valor) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("fuenteMod05", MOD05));
        return new SelectorFuenteReportesProveedor(new Mod06Propiedades("mock", null, null, null, new Mod06Propiedades.Ia(valor)),
            beans.getBeanProvider(FuenteReportesProveedorMod05.class));
    }

    @Test
    void conMockSeUsaLaFuenteDatosDelMod06() {
        assertInstanceOf(FuenteReportesProveedorMock.class, selector("mock").obtener(new FuenteDatosMemoria()));
        assertInstanceOf(FuenteReportesProveedorMock.class, selector(" MOCK ").obtener(new FuenteDatosMemoria()));
    }

    @Test
    void conMod05SeUsaElMod05() {
        assertSame(MOD05, selector("mod05").obtener(new FuenteDatosMemoria()));
    }

    @Test
    void porDefectoEsMod05() {
        assertEquals("mod05", new Mod06Propiedades("mock", null, null, null).iaFuenteReportes());
        assertSame(MOD05, selector(null).obtener(new FuenteDatosMemoria()));
    }

    @Test
    void valorInvalidoNoArranca() {
        ConfiguracionInvalida e = assertThrows(ConfiguracionInvalida.class, () -> selector("x"));
        assertEquals("IA_FUENTE_REPORTES='x' no es válido. Usa 'mod05' o 'mock' en backend/.env.", e.getMessage());
    }

    @Test
    void seEnlazaDesdeMod06IaFuenteReportes() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("mod06.ia.fuente-reportes", "mock")));
        assertEquals("mock", binder.bindOrCreate("mod06", Mod06Propiedades.class).iaFuenteReportes());
    }
}
