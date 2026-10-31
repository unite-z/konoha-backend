package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.LocalDate;

/**
 * Resumen de la atención (CU-MOD06-003, paso 6): tipo, fecha, estado, responsable (proveedor) y
 * evidencias. {@code espacio} es «Sin espacio» si {@code areaVerdeId} es nulo.
 *
 * @param proveedor el responsable de una atención tercerizada (el frontend lo rotula «Responsable»)
 * @param archivosAsociados evidencias disponibles: archivos de los reportes del proveedor
 */
public record AtencionMapa(
        long atencionId,
        String codigo,
        String tipoAtencion,
        String tipoServicio,
        LocalDate fecha,
        String estado,
        String modalidad,
        boolean esRiesgo,
        String proveedor,
        Long areaVerdeId,
        String espacio,
        Long ejemplarId,
        String ejemplarCodigo,
        long archivosAsociados) {}
