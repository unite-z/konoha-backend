package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * Datos de EJEMPLO de MOD-04 (actividades, operarios y movimientos de material) para probar el
 * panel en desarrollo mientras MOD-04 no esté programado. Solo los usan {@link
 * FuenteOperacionCampoEjemplo} y {@link FuenteMovimientosMaterialEjemplo}, que se activan con
 * {@code MOD06_DATOS_EJEMPLO=true}. Nunca escriben en la base.
 *
 * <p>Son deterministas: cada día del periodo tiene 0, 1 o 2 actividades según la fecha, así el
 * mismo periodo da siempre los mismos datos. El estado es el del parte diario ({@code Abierto} o
 * {@code Cerrado}): SUPUESTO hasta que MOD-04 defina el estado de una actividad.
 *
 * <p>TODO: borrar esta clase y las dos fuentes de ejemplo cuando exista el adaptador real sobre las
 * tablas de MOD-04.
 */
final class EjemplosCampo {

    static final String ABIERTO = "Abierto";
    static final String CERRADO = "Cerrado";

    /** Como mucho un año de días: el periodo del panel nunca debería ser mayor. */
    private static final int MAX_DIAS = 366;

    private static final String MATERIAL_VEGETAL = "Material vegetal";
    private static final String INSUMO = "Insumo";

    private static final String[] TIPOS = {"Poda", "Riego", "Limpieza", "Siembra"};
    private static final long[] AREAS = {1, 2, 3};
    private static final String[] ESPACIOS = {
        "Espacio de ejemplo 1", "Espacio de ejemplo 2", "Espacio de ejemplo 3"
    };
    private static final String[] CUADRILLAS = {
        "Cuadrilla Norte (ejemplo)", "Cuadrilla Sur (ejemplo)"
    };

    private record Material(long id, String nombre, String categoria, String unidad) {}

    private static final Material[] MATERIALES = {
        new Material(1, "Fertilizante NPK", INSUMO, "kg"),
        new Material(2, "Abono orgánico", INSUMO, "kg"),
        new Material(3, "Grass americano", MATERIAL_VEGETAL, "m²"),
        new Material(4, "Plantón de geranio", MATERIAL_VEGETAL, "und")
    };

    private EjemplosCampo() {}

    static List<ActividadCampo> actividades(RangoFechas periodo, Long areaVerdeId, Clock reloj) {
        LocalDate hoy = LocalDate.now(reloj);
        List<ActividadCampo> resultado = new ArrayList<>();
        LocalDate dia = periodo.desde();
        for (int i = 0; i < MAX_DIAS && !dia.isAfter(periodo.hasta()); i++) {
            long n = Math.floorMod(dia.toEpochDay(), 3);
            for (int k = 0; k < n; k++) {
                int semilla = (int) Math.floorMod(dia.toEpochDay() + k, 12);
                int area = semilla % AREAS.length;
                if (areaVerdeId != null && areaVerdeId != AREAS[area]) {
                    continue;
                }
                // Los partes de ayer y hoy siguen abiertos; de los antiguos, uno de cada cinco.
                boolean abierto =
                        !dia.isBefore(hoy.minusDays(1)) || Math.floorMod(dia.toEpochDay(), 5) == 0;
                resultado.add(
                        new ActividadCampo(
                                dia.toEpochDay() * 10 + k,
                                dia,
                                abierto ? ABIERTO : CERRADO,
                                TIPOS[semilla % TIPOS.length],
                                AREAS[area],
                                ESPACIOS[area],
                                CUADRILLAS[semilla % CUADRILLAS.length]));
            }
            dia = dia.plusDays(1);
        }
        return resultado;
    }

    /** De 2 a 3 operarios por actividad, de los 4 de su cuadrilla. */
    static List<ParticipacionOperario> participaciones(
            RangoFechas periodo, Long areaVerdeId, Clock reloj) {
        List<ParticipacionOperario> resultado = new ArrayList<>();
        for (ActividadCampo a : actividades(periodo, areaVerdeId, reloj)) {
            int cuadrilla = a.cuadrilla().equals(CUADRILLAS[0]) ? 0 : 1;
            int cuantos = 2 + (int) Math.floorMod(a.id(), 2);
            for (int j = 0; j < cuantos; j++) {
                int operario = cuadrilla * 4 + (int) Math.floorMod(a.id() + j, 4) + 1;
                resultado.add(
                        new ParticipacionOperario(
                                a.id(),
                                a.fecha(),
                                operario,
                                "Operario de ejemplo " + operario,
                                a.cuadrilla(),
                                a.areaVerdeId(),
                                a.espacio()));
            }
        }
        return resultado;
    }

    /**
     * Un movimiento por actividad: sobre todo consumos, algunas propagaciones y, de vez en cuando,
     * un traslado de material vegetal al espacio siguiente.
     */
    static List<MovimientoMaterial> movimientos(
            RangoFechas periodo, Long areaVerdeId, Clock reloj) {
        List<MovimientoMaterial> resultado = new ArrayList<>();
        // El filtro de espacio se aplica después: un traslado cuenta en su origen y en su destino.
        for (ActividadCampo a : actividades(periodo, null, reloj)) {
            int semilla = (int) Math.floorMod(a.id(), 10);
            Material m = MATERIALES[semilla % MATERIALES.length];
            BigDecimal cantidad = BigDecimal.valueOf(5 + semilla * 2L);
            boolean vegetal = m.categoria().equals(MATERIAL_VEGETAL);
            MovimientoMaterial movimiento;
            if (vegetal && semilla % 5 == 2) {
                int destino = (int) (a.areaVerdeId() % AREAS.length);
                movimiento =
                        new MovimientoMaterial(
                                a.id(),
                                a.id(),
                                a.fecha(),
                                null,
                                null,
                                m.id(),
                                m.nombre(),
                                m.categoria(),
                                null,
                                null,
                                TipoMovimiento.TRASLADO,
                                cantidad,
                                m.unidad(),
                                a.areaVerdeId(),
                                a.espacio(),
                                AREAS[destino],
                                ESPACIOS[destino]);
            } else {
                TipoMovimiento tipo =
                        vegetal && semilla % 3 == 0
                                ? TipoMovimiento.PROPAGACION
                                : TipoMovimiento.CONSUMO;
                movimiento =
                        new MovimientoMaterial(
                                a.id(),
                                a.id(),
                                a.fecha(),
                                a.areaVerdeId(),
                                a.espacio(),
                                m.id(),
                                m.nombre(),
                                m.categoria(),
                                null,
                                null,
                                tipo,
                                cantidad,
                                m.unidad(),
                                null,
                                null,
                                null,
                                null);
            }
            if (areaVerdeId == null || tocaEspacio(movimiento, areaVerdeId)) {
                resultado.add(movimiento);
            }
        }
        return resultado;
    }

    private static boolean tocaEspacio(MovimientoMaterial m, Long areaVerdeId) {
        return areaVerdeId.equals(m.areaVerdeId())
                || areaVerdeId.equals(m.origenAreaId())
                || areaVerdeId.equals(m.destinoAreaId());
    }
}
