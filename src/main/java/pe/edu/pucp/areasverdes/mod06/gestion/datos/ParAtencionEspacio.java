package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.time.LocalDate;

/**
 * Un par (atención realizada, espacio), la unidad de conteo de RF-REP-006. {@code fecha}: fecha
 * local (America/Lima) de inicio o, si no la tiene, de fin.
 */
public record ParAtencionEspacio(
        long atencionId,
        String codigo,
        String tipoServicio,
        String estado,
        LocalDate fecha,
        Long areaVerdeId,
        String espacio) {}
