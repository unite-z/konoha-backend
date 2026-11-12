package pe.edu.pucp.areasverdes.mod03;

import static pe.edu.pucp.areasverdes.AreasVerdesApplication.LIMA;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository.Cambio;

/**
 * Historial de la configuración de un servicio (AV-256, postcondiciones de CU-001 y CU-002): cada
 * campo que cambió, con su valor anterior y el nuevo, el autor y la hora, del más reciente al más
 * antiguo. Sale de la auditoría común de MOD-01 ({@link ConfiguracionServicios#ENTIDAD}).
 */
@Service
public class HistorialServicio {

    /**
     * Un cambio. {@code campo} es la columna que cambió o {@code inicial} para la primera
     * configuración del servicio; {@code estacion}, la de la frecuencia.
     */
    public record CambioServicio(
            String campo,
            String estacion,
            String anterior,
            String nuevo,
            OffsetDateTime fechaHora,
            String autor) {}

    private static final List<String> CAMPOS = new ArrayList<>();

    static {
        CAMPOS.addAll(ConfiguracionServicios.CAMPOS_FRECUENCIA);
        CAMPOS.addAll(ConfiguracionServicios.CAMPOS_DURACION);
    }

    private final BitacoraRepository bitacora;

    public HistorialServicio(BitacoraRepository bitacora) {
        this.bitacora = bitacora;
    }

    public List<CambioServicio> de(long servicio) {
        List<Cambio> cambios = bitacora.cambios(ConfiguracionServicios.ENTIDAD, servicio);
        List<CambioServicio> historial = new ArrayList<>();
        for (int i = 0; i < cambios.size(); i++) {
            Cambio c = cambios.get(i);
            OffsetDateTime hora = c.fechaHora().atZoneSameInstant(LIMA).toOffsetDateTime();
            String estacion = texto(c.valoresNuevos().get("estacion"));
            // La primera configuración del servicio, la más antigua, es una sola fila «inicial».
            if (i == cambios.size() - 1 && "Alta".equals(c.accion())) {
                historial.add(
                        new CambioServicio("inicial", null, null, null, hora, c.usuarioNombre()));
                continue;
            }
            for (String campo : CAMPOS) {
                if (!c.valoresNuevos().containsKey(campo)) {
                    continue;
                }
                String anterior = texto(c.valoresAnteriores().get(campo));
                String nuevo = texto(c.valoresNuevos().get(campo));
                if (!Objects.equals(anterior, nuevo)) {
                    historial.add(
                            new CambioServicio(
                                    campo, estacion, anterior, nuevo, hora, c.usuarioNombre()));
                }
            }
        }
        return historial;
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
