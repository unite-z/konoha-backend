package pe.edu.pucp.areasverdes.mod02.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod02.api.AreasController.AreaVerde;

/**
 * Respuesta única del panel del área (AV-431): ficha, indicadores, próxima actividad, prioridad
 * validada e historial paginado. Un dato cuya fuente el usuario no puede ver llega a {@code null}
 * (o a lista vacía) y la fuente no figura en {@code fuentes_visibles}.
 */
public record ResumenAreaVerde(
        AreaVerde area,
        Map<String, Integer> ejemplaresPorSituacion,
        Integer incidenciasAbiertas,
        UltimaAtencion ultimaAtencion,
        ProximaActividad proximaActividad,
        PrioridadValidada prioridadValidada,
        List<String> fuentesVisibles,
        PaginaHistorial historial) {

    /** Última atención hecha en el área: actividad de personal propio o atención tercerizada. */
    public record UltimaAtencion(
            String tipo, long id, OffsetDateTime fecha, String titulo, String estado) {}

    /** Próxima programación de servicio que incluye el área (Programada o EnCurso). */
    public record ProximaActividad(
            long programacionId,
            String tipoServicio,
            LocalDate fechaInicioPrevista,
            LocalDate fechaFinPrevista,
            String estado) {}

    /** Prioridad decidida por una persona al validar una priorización de la IA (RN-IA-002). */
    public record PrioridadValidada(
            String prioridad, OffsetDateTime fechaValidacion, long sugerenciaId) {}

    /** Entrada del historial: {@code tipo} es actividad, incidencia, atencion o cambio_sector. */
    public record EntradaHistorial(
            String tipo,
            long id,
            OffsetDateTime fecha,
            String titulo,
            String detalle,
            String estado) {}

    public record PaginaHistorial(
            int count, Integer next, Integer previous, List<EntradaHistorial> results) {}
}
