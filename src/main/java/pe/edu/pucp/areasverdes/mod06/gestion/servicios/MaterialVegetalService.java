package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteMovimientosMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.MovimientoMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.TipoMovimiento;

/**
 * RF-REP-005 · HU-MOD05-029 · CU-MOD05-029: reporte mensual de consumo y propagación de material
 * vegetal por tipo y espacio. Solo lectura (postcondición del CU: ninguna).
 *
 * <ol>
 *   <li>El actor elige el mes.
 *   <li>Consumo por tipo de material o insumo y por espacio; sin espacio, «Sin espacio» (2a).
 *   <li>Lo propagado, separado de lo consumido; y lo trasladado, con su origen y destino (HU v2).
 *   <li>Comparación con los meses anteriores.
 * </ol>
 *
 * <p>La exportación a Excel o PDF es RF-REP-009 (fuera de este reporte). Los productos aplicados
 * por el proveedor (3a) van en una vista aparte, hoy sin datos.
 */
@Service
@Transactional(readOnly = true)
public class MaterialVegetalService {

    public static final String MENSAJE_SIN_DATOS_MES =
            "No hay movimientos de material en el mes elegido";
    public static final String MOTIVO_PRODUCTOS_PROVEEDOR =
            "Los productos agronómicos aplicados por el proveedor todavía no se registran en el"
                    + " sistema";
    // CONFIRMADO: Modelo BD v1, enumerado TipoMovimiento = Consumo; Propagacion; Traslado, y
    // CU-MOD05-029 paso 3.
    public static final String NOTA_SEPARACION =
            "Lo consumido, lo propagado y lo trasladado se separan por el tipo de movimiento"
                    + " registrado en cada actividad";
    public static final String NOTA_UNIDADES =
            "Las cantidades con unidades distintas se muestran por separado y no se suman";

    private static final List<String> NOTAS = List.of(NOTA_SEPARACION, NOTA_UNIDADES);

    private final FuenteMovimientosMaterial fuente;
    private final Clock reloj;
    private final int mesesComparacion;

    public MaterialVegetalService(
            FuenteMovimientosMaterial fuente,
            Clock reloj,
            // CONFIRMADO: 3 meses (decisión del responsable, 28/10/2026).
            @Value("${mod06.gestion.meses-comparacion:3}") int mesesComparacion) {
        if (mesesComparacion < 1) {
            throw new IllegalStateException(
                    "mod06.gestion.meses-comparacion tiene que ser 1 o más: " + mesesComparacion);
        }
        this.fuente = fuente;
        this.reloj = reloj;
        this.mesesComparacion = mesesComparacion;
    }

    /**
     * Reporte del mes, opcionalmente de un espacio (el de la actividad, el origen o el destino).
     */
    public ReporteMaterialVegetal generar(YearMonth mes, Long areaVerdeId) {
        RangoFechas periodo = RangoFechas.delMes(mes);
        var filtros = new FiltrosMaterialVegetal(mes.toString(), areaVerdeId);
        OffsetDateTime generadoEn = OffsetDateTime.now(reloj);
        // TODO: cuando exista AplicacionProducto (CU-MOD05-025), leerla en solo lectura de la
        // tabla de su módulo dueño, en una vista aparte, sin mezclarla con el material de las
        // cuadrillas.
        var productosProveedor = new SeccionNoDisponible(true, MOTIVO_PRODUCTOS_PROVEEDOR);

        Disponibilidad disponibilidad = fuente.disponibilidad();
        if (!disponibilidad.disponible()) {
            return new ReporteMaterialVegetal(
                    periodo,
                    filtros,
                    generadoEn,
                    true,
                    disponibilidad.motivo(),
                    DefinicionesIndicador.CONSUMO_MATERIALES,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    productosProveedor,
                    NOTAS);
        }

        YearMonth primero = mes.minusMonths(mesesComparacion);
        var rango = new RangoFechas(primero.atDay(1), mes.atEndOfMonth());
        Map<YearMonth, List<MovimientoMaterial>> porMes = new LinkedHashMap<>();
        for (MovimientoMaterial m : fuente.movimientos(rango, areaVerdeId)) {
            porMes.computeIfAbsent(YearMonth.from(m.fecha()), k -> new ArrayList<>()).add(m);
        }

        List<MovimientoMaterial> delMes = porMes.getOrDefault(mes, List.of());
        List<ComparacionMes> comparacion = new ArrayList<>();
        for (YearMonth anterior = primero;
                anterior.isBefore(mes);
                anterior = anterior.plusMonths(1)) {
            comparacion.add(comparar(anterior, porMes.getOrDefault(anterior, List.of())));
        }

        return new ReporteMaterialVegetal(
                periodo,
                filtros,
                generadoEn,
                delMes.isEmpty(),
                delMes.isEmpty() ? MENSAJE_SIN_DATOS_MES : null,
                DefinicionesIndicador.CONSUMO_MATERIALES,
                AgrupadorMaterial.porEspacio(delMes, TipoMovimiento.CONSUMO),
                AgrupadorMaterial.porEspacio(delMes, TipoMovimiento.PROPAGACION),
                AgrupadorMaterial.traslados(delMes),
                List.copyOf(comparacion),
                productosProveedor,
                NOTAS);
    }

    /** Un mes sin movimientos va sin datos y con listas vacías, nunca con totales en 0. */
    private static ComparacionMes comparar(YearMonth mes, List<MovimientoMaterial> movimientos) {
        return new ComparacionMes(
                mes.toString(),
                movimientos.isEmpty(),
                AgrupadorMaterial.porMaterial(movimientos, TipoMovimiento.CONSUMO),
                AgrupadorMaterial.porMaterial(movimientos, TipoMovimiento.PROPAGACION),
                AgrupadorMaterial.porMaterial(movimientos, TipoMovimiento.TRASLADO));
    }
}
