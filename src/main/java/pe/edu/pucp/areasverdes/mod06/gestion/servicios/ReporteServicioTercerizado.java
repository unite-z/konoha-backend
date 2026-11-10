package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;

/**
 * RF-REP-008: reporte por servicio tercerizado, con la forma de {@code ResultadoReporteServicio} de
 * {@code konoha-frontend/src/types/reportesGestion.ts}. Los decimales van como texto con punto; una
 * sección sin datos lleva {@code sin_datos} y su motivo, nunca ceros.
 */
public record ReporteServicioTercerizado(
        RangoFechas periodo,
        Filtros filtrosAplicados,
        OffsetDateTime generadoEn,
        boolean sinDatos,
        String mensajeSinDatos,
        Servicio servicio,
        Avance avance,
        Rendimiento rendimiento,
        EspeciesPlagas especiesPlagas,
        Conformidad conformidad,
        List<FilaAtencion> atenciones,
        int atencionesSinEvidencia,
        List<String> notas) {

    public record Filtros(long tipoServicioId, Long proveedorId, Long contratoId) {}

    public record Referencia(long id, String nombre) {}

    public record ContratoReportado(
            long id, String codigo, LocalDate vigenciaDesde, LocalDate vigenciaHasta) {}

    public record Servicio(
            long tipoServicioId, String nombre, Referencia proveedor, ContratoReportado contrato) {}

    public record Avance(
            boolean sinDatos,
            String mensajeSinDatos,
            int programadas,
            int extraordinarias,
            int ejecutadas,
            int enEjecucion,
            int pendientes,
            String porcentajeAvance,
            String metradoPrevisto,
            String metradoEjecutado,
            String unidadMetrado) {}

    public record Rendimiento(
            boolean sinDatos,
            String mensajeSinDatos,
            String horasEjecucion,
            String rendimientoPorHora,
            String unidadRendimiento,
            String duracionMediaHoras,
            Integer atencionesFueraDePlazo) {}

    public record ElementoAtendido(
            String tipo, String nombre, int atenciones, Integer ejemplares) {}

    public record EspeciesPlagas(
            boolean sinDatos, String mensajeSinDatos, List<ElementoAtendido> elementos) {}

    public record Conformidad(
            boolean sinDatos,
            String mensajeSinDatos,
            int conformes,
            int observadas,
            int pendientesDeRevision,
            int sinReporteProveedor,
            int subsanacionesAbiertas) {}

    public record FilaAtencion(
            long id,
            String codigo,
            String clase,
            String estado,
            String etiquetaEstado,
            String espacio,
            String ejemplarCodigo,
            OffsetDateTime fechaHoraInicio,
            OffsetDateTime fechaHoraFin,
            String metrado,
            String unidad,
            String conformidad,
            int documentos,
            List<String> especiesPlagas,
            boolean conEvidencia,
            boolean esRiesgo) {}
}
