package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * RF-REP-010: programado frente a ejecutado, con la forma de {@code ResultadoProgramadoEjecutado}
 * de {@code konoha-frontend/src/types/reportesGestion.ts}.
 */
public record ProgramadoEjecutado(
        RangoFechas periodo,
        Filtros filtrosAplicados,
        OffsetDateTime generadoEn,
        boolean sinDatos,
        String mensajeSinDatos,
        Resumen resumen,
        List<PorServicio> porServicio,
        List<Fila> filas,
        List<Ejecucion> sinProgramacion,
        SeccionNoDisponible personalPropio,
        List<String> notas) {

    public record Filtros(
            Long tipoServicioId, Long areaVerdeId, String modalidad, String situacion) {}

    /** Atención tercerizada ({@code Atencion}) o ejecución propia ({@code Actividad}). */
    public record Ejecucion(
            String origen,
            long id,
            String codigo,
            String estado,
            String etiquetaEstado,
            OffsetDateTime inicioReal,
            OffsetDateTime finReal) {}

    public record Fila(
            long programacionId,
            String tipoServicio,
            String modalidad,
            String responsable,
            String espacio,
            String ventanaMes,
            LocalDate inicioPrevisto,
            LocalDate finPrevisto,
            String estadoProgramacion,
            List<Ejecucion> ejecuciones,
            String situacion,
            String etiquetaSituacion,
            Integer desviacionDias) {}

    public record Resumen(
            int programados,
            int ejecutados,
            int ejecutadosConDesviacion,
            int enCurso,
            int pendientes,
            int cancelados,
            String porcentajeCumplimiento,
            int ejecucionesSinProgramacion) {}

    public record PorServicio(
            long tipoServicioId,
            String tipoServicio,
            String modalidad,
            int programados,
            int ejecutados,
            int pendientes) {}
}
