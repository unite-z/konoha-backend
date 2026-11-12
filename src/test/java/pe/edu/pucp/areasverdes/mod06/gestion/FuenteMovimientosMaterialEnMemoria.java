package pe.edu.pucp.areasverdes.mod06.gestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.Disponibilidad;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteMovimientosMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.MovimientoMaterial;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.TipoMovimiento;

/**
 * Doble de {@link FuenteMovimientosMaterial} en memoria, con la regla del área del puerto
 * (actividad, origen o destino). Guarda las llamadas para comprobar que el servicio pide una sola
 * vez el rango del mes y de los anteriores. Incluye constructores de datos de prueba.
 */
class FuenteMovimientosMaterialEnMemoria implements FuenteMovimientosMaterial {

    private static final AtomicLong IDS = new AtomicLong();

    private final Disponibilidad disponibilidad;
    private final List<MovimientoMaterial> datos;
    final List<RangoFechas> periodosPedidos = new ArrayList<>();
    final List<Long> areasPedidas = new ArrayList<>();

    FuenteMovimientosMaterialEnMemoria(
            Disponibilidad disponibilidad, List<MovimientoMaterial> datos) {
        this.disponibilidad = disponibilidad;
        this.datos = List.copyOf(datos);
    }

    static FuenteMovimientosMaterialEnMemoria con(MovimientoMaterial... datos) {
        return new FuenteMovimientosMaterialEnMemoria(Disponibilidad.DISPONIBLE, List.of(datos));
    }

    @Override
    public Disponibilidad disponibilidad() {
        return disponibilidad;
    }

    @Override
    public List<MovimientoMaterial> movimientos(RangoFechas periodo, Long areaVerdeId) {
        periodosPedidos.add(periodo);
        areasPedidas.add(areaVerdeId);
        return datos.stream()
                .filter(m -> periodo.contiene(m.fecha()))
                .filter(
                        m ->
                                areaVerdeId == null
                                        || Objects.equals(m.areaVerdeId(), areaVerdeId)
                                        || Objects.equals(m.origenAreaId(), areaVerdeId)
                                        || Objects.equals(m.destinoAreaId(), areaVerdeId))
                .toList();
    }

    // --- Constructores de datos ------------------------------------------------------------------

    static MovimientoMaterial consumo(
            String fecha,
            Long area,
            String espacio,
            String material,
            String unidad,
            String cantidad) {
        return movimiento(TipoMovimiento.CONSUMO, fecha, area, espacio, material, unidad, cantidad);
    }

    static MovimientoMaterial propagacion(
            String fecha,
            Long area,
            String espacio,
            String material,
            String unidad,
            String cantidad) {
        return movimiento(
                TipoMovimiento.PROPAGACION, fecha, area, espacio, material, unidad, cantidad);
    }

    static MovimientoMaterial traslado(
            String fecha,
            Long origenId,
            String origen,
            Long destinoId,
            String destino,
            String material,
            String unidad,
            String cantidad) {
        return new MovimientoMaterial(
                IDS.incrementAndGet(),
                1,
                LocalDate.parse(fecha),
                null,
                null,
                material.hashCode(),
                material,
                "Material vegetal",
                null,
                null,
                TipoMovimiento.TRASLADO,
                new BigDecimal(cantidad),
                unidad,
                origenId,
                origen,
                destinoId,
                destino);
    }

    private static MovimientoMaterial movimiento(
            TipoMovimiento tipo,
            String fecha,
            Long area,
            String espacio,
            String material,
            String unidad,
            String cantidad) {
        return new MovimientoMaterial(
                IDS.incrementAndGet(),
                1,
                LocalDate.parse(fecha),
                area,
                espacio,
                material.hashCode(),
                material,
                "Material vegetal",
                null,
                null,
                tipo,
                new BigDecimal(cantidad),
                unidad,
                null,
                null,
                null,
                null);
    }
}
