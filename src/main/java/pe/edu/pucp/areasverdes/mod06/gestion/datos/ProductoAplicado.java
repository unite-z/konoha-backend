package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.LocalDate;

/**
 * Un producto agronómico aplicado por un proveedor en una atención tercerizada (CU-MOD05-025,
 * MOD-05), en solo lectura para RF-REP-005 (flujo 3a).
 *
 * @param id fila de la aplicación en el módulo dueño
 * @param fecha día de inicio de la atención en America/Lima
 * @param dosis texto libre que registra MOD-05; vacío si no se indicó
 */
public record ProductoAplicado(
        long id,
        long atencionId,
        String atencionCodigo,
        LocalDate fecha,
        long productoId,
        String producto,
        String principioActivo,
        String dosis,
        long proveedorId,
        String proveedor) {}
