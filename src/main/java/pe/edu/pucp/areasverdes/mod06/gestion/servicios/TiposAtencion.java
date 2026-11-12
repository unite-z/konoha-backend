package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * Tipo de atención del mapa (RF-REP-007 v2: mantenimiento, poda y control fitosanitario), deducido
 * del nombre del tipo de servicio de MOD-05 (mod05_tiposervicio.nombre). Se usa el nombre, igual
 * que la migración V2 de MOD-03 marca los servicios periódicos por nombre. Valores estables de la
 * API (api.md).
 */
public final class TiposAtencion {

    public static final String MANTENIMIENTO = "Mantenimiento";
    public static final String PODA = "Poda";
    public static final String FITOSANITARIO = "Fitosanitario";
    public static final List<String> TODOS = List.of(MANTENIMIENTO, PODA, FITOSANITARIO);

    /** Nombre del corte de césped en {@code mod05_tiposervicio}. */
    public static final String CORTE_DE_CESPED = "Corte de césped";

    private static final Map<String, String> POR_TIPO_SERVICIO =
            Map.of(
                    AtencionesEspacioSectorService.TIPO_FITOSANITARIO_POR_DEFECTO,
                    FITOSANITARIO,
                    "Poda mayor programada",
                    PODA,
                    "Poda de altura a demanda",
                    PODA,
                    "Poda menor",
                    PODA,
                    "Mantenimiento de jardines",
                    MANTENIMIENTO,
                    // CONFIRMADO (decisión del responsable, 28/10/2026): el corte de césped es
                    // mantenimiento.
                    CORTE_DE_CESPED,
                    MANTENIMIENTO);

    private TiposAtencion() {}

    /** Tipo de atención por coincidencia exacta del nombre; vacío si no se clasifica. */
    public static Optional<String> deTipoServicio(String nombreTipoServicio) {
        return nombreTipoServicio == null
                ? Optional.empty()
                : Optional.ofNullable(POR_TIPO_SERVICIO.get(nombreTipoServicio));
    }
}
