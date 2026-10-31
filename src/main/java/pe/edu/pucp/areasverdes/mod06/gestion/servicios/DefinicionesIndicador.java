package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.util.List;
import java.util.Optional;

/**
 * Catálogo de las definiciones de indicador de los reportes de gestión (RF-REP-001, RF-REP-005 y
 * RF-REP-007).
 *
 * <p>{@code DefinicionIndicador} es una entidad propia del MOD-06 (Modelo BD v1, tabla 2). Mientras
 * no exista su tabla (habría que crear una migración y la numeración de Flyway está ocupada por
 * ramas sin fusionar), el catálogo vive aquí. Los códigos y textos de RF-REP-006 son los de {@code
 * mock-data/mod06/DefinicionIndicador.json}. PENDIENTE: catálogo persistente.
 *
 * <p>El {@code codigo} es único en el modelo: RF-REP-005 tiene una sola definición, que comparten
 * el reporte mensual y la tarjeta del panel.
 */
public final class DefinicionesIndicador {

    private DefinicionesIndicador() {}

    public static final Indicador ACTIVIDADES_POR_ESTADO =
            new Indicador(
                    "RF-REP-001",
                    "Actividades por estado y tipo",
                    "Número de actividades de personal propio del periodo por estado y por tipo de"
                            + " actividad.",
                    null,
                    "actividades");

    public static final Indicador DISTRIBUCION_OPERARIOS =
            new Indicador(
                    "RF-REP-004",
                    "Distribución de operarios",
                    "Operarios que participan en las actividades del periodo, por cuadrilla y"
                            + " espacio.",
                    null,
                    "operarios");

    public static final Indicador ATENCIONES_POR_ESPACIO =
            new Indicador(
                    "RF-REP-006",
                    "Atenciones por espacio y sector",
                    "Número de atenciones realizadas en el periodo por sector, espacio y cuartel"
                            + " (si está registrado). Cuentan las actividades de personal propio,"
                            + " las atenciones tercerizadas ejecutadas (por cada área de su"
                            + " alcance), las aplicaciones fitosanitarias sin atención y las podas"
                            + " no registradas por otro origen. Cada evento se cuenta una sola vez"
                            + " por área.",
                    null,
                    "atenciones");

    public static final Indicador CONSUMO_MATERIALES =
            new Indicador(
                    "RF-REP-005",
                    "Consumo de materiales e insumos",
                    "Movimientos de material vegetal e insumos de las actividades del periodo,"
                            + " separados en consumido, propagado y trasladado según su tipo de"
                            + " movimiento, por tipo de material, unidad y espacio. Las cantidades"
                            + " de unidades distintas no se suman.",
                    null,
                    "movimientos");

    // SUPUESTO (pendiente de confirmar): la documentación no define fórmula ni meta (pendiente 6
    // de los mockups); revisar con la contraparte.
    public static final Indicador CUMPLIMIENTO_TERCERIZADOS =
            new Indicador(
                    "RF-REP-008",
                    "Cumplimiento de servicios tercerizados",
                    "Porcentaje de atenciones tercerizadas del periodo que están ejecutadas"
                            + " (Ejecutado pendiente de reporte, Reporte adjunto, Observado o"
                            + " Conforme / cerrado) sobre el total de atenciones tercerizadas del"
                            + " periodo, con su desglose por estado. Una atención pertenece al"
                            + " periodo por la fecha local de inicio o, si no la tiene, por la de"
                            + " creación. Fórmula pendiente de confirmar con la contraparte.",
                    null,
                    "%");

    public static final Indicador MAPA_ATENCIONES =
            new Indicador(
                    "RF-REP-007",
                    "Mapa de atenciones",
                    "Atenciones de mantenimiento, poda y control fitosanitario realizadas en el"
                            + " periodo, una por cada espacio o ejemplar de su alcance, con su"
                            + " ubicación si está registrada. El tipo de atención se deduce del"
                            + " tipo de servicio.",
                    null,
                    "atenciones");

    public static final List<Indicador> TODAS =
            List.of(
                    ACTIVIDADES_POR_ESTADO,
                    DISTRIBUCION_OPERARIOS,
                    ATENCIONES_POR_ESPACIO,
                    CONSUMO_MATERIALES,
                    CUMPLIMIENTO_TERCERIZADOS,
                    MAPA_ATENCIONES);

    /** Definición por código, o vacío si no existe. */
    public static Optional<Indicador> porCodigo(String codigo) {
        return TODAS.stream().filter(i -> i.codigo().equals(codigo)).findFirst();
    }
}
