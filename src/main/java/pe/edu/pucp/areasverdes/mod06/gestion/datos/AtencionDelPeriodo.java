package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.LocalDate;

/**
 * Una atención tercerizada del periodo, en cualquier estado. {@code fecha}: fecha local de inicio
 * o, si no ha empezado, de creación.
 */
public record AtencionDelPeriodo(
        long atencionId,
        String codigo,
        String tipoServicio,
        String estado,
        LocalDate fecha,
        String proveedor) {}
