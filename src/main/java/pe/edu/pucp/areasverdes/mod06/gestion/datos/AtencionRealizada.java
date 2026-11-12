package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.LocalDate;

/**
 * Una atención tercerizada realizada por cada destino de su alcance (espacio o ejemplar); sin
 * alcance, una sola fila con ambos nulos. {@code areaVerdeId}: el área del alcance o la del
 * ejemplar. {@code archivosAsociados}: archivos de los reportes del proveedor de la atención.
 */
public record AtencionRealizada(
        long atencionId,
        String codigo,
        long tipoServicioId,
        String tipoServicio,
        String estado,
        boolean esRiesgo,
        LocalDate fecha,
        String proveedor,
        Long areaVerdeId,
        String espacio,
        Long ejemplarId,
        String ejemplarCodigo,
        long archivosAsociados) {}
