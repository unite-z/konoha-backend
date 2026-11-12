package pe.edu.pucp.areasverdes.mod06.gestion.exportaciones;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo de RF-REP-009: los {@code TipoReporte} del Modelo BD (4.6.2), con sus formatos, los
 * filtros que el frontend pinta y el límite de filas por formato (A1). Mismos textos que {@code
 * konoha-frontend/src/services/simulados/exportaciones.ts}.
 */
public final class TiposExportables {

    public static final String PDF = "PDF";
    public static final String EXCEL = "Excel";

    /** Filas que admite cada formato. PDF: legible e imprimible; Excel: el tope de MOD-01. */
    public static final Map<String, Integer> LIMITES = Map.of(PDF, 5_000, EXCEL, 50_000);

    /** GET exportaciones/tipos/: un tipo exportable. */
    public record TipoExportable(
            String tipo,
            String nombre,
            String descripcion,
            List<String> formatos,
            List<String> filtros,
            boolean disponible,
            String motivoNoDisponible,
            Map<String, Integer> limiteFilas) {}

    public static final List<TipoExportable> TODOS =
            List.of(
                    new TipoExportable(
                            "GestionOSG",
                            "Reporte general de gestión para la OSG",
                            "Indicadores del panel, cumplimiento de tercerizados, material vegetal"
                                    + " y atenciones por espacio del mes",
                            List.of(PDF, EXCEL),
                            List.of("mes"),
                            true,
                            null,
                            LIMITES),
                    new TipoExportable(
                            "ServicioTercerizado",
                            "Reporte por servicio tercerizado",
                            "Avance, rendimiento, especies o plagas y conformidad de un servicio en"
                                    + " un periodo",
                            List.of(PDF, EXCEL),
                            List.of("periodo", "tipo_servicio_id", "proveedor_id"),
                            true,
                            null,
                            LIMITES),
                    new TipoExportable(
                            "MaterialVegetal",
                            "Consumo y propagación de material vegetal",
                            "Material vegetal e insumos consumidos, propagados y trasladados en un"
                                    + " mes",
                            List.of(PDF, EXCEL),
                            List.of("mes", "area_verde_id"),
                            true,
                            null,
                            LIMITES),
                    new TipoExportable(
                            "DistribucionPersonal",
                            "Distribución del personal",
                            "Personal por cuadrilla, actividad, espacio y jornada en un periodo",
                            List.of(EXCEL),
                            List.of("periodo"),
                            true,
                            null,
                            Map.of(EXCEL, LIMITES.get(EXCEL))),
                    new TipoExportable(
                            "MatrizIncidencias",
                            "Matriz de incidencias",
                            "Incidencias del periodo por espacio, tipo y estado",
                            List.of(PDF, EXCEL),
                            List.of("periodo"),
                            true,
                            null,
                            LIMITES));

    private TiposExportables() {}

    public static Optional<TipoExportable> porTipo(String tipo) {
        return TODOS.stream().filter(t -> t.tipo().equals(tipo)).findFirst();
    }
}
