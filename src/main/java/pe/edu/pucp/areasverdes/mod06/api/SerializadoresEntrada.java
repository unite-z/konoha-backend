package pe.edu.pucp.areasverdes.mod06.api;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.areasverdes.mod06.api.drf.Entrada;
import pe.edu.pucp.areasverdes.mod06.api.drf.Entrada.Serializador;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.ia.ExtraccionReportesService;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * Serializadores de entrada de la API REST del MOD-06 (parte de entrada de {@code api/serializers.py}): validan los
 * parámetros de consulta y los cuerpos con los mensajes de la Guía de Estilo Web (sección 7).
 */
public final class SerializadoresEntrada {

    private SerializadoresEntrada() {}

    public static final String MENSAJE_ENTERO = "Usa un número entero positivo";

    private static Entrada.Campo enteroOpcional() {
        return new Entrada.Entero().minimo(1).opcional()
            .mensaje("invalid", MENSAJE_ENTERO)
            .mensaje("min_value", MENSAJE_ENTERO);
    }

    /** Periodo obligatorio de todos los reportes (CU-MOD06-001: el actor selecciona un rango de fechas). */
    private static Serializador periodoQuery() {
        return Entrada.serializador()
            .campo("desde", new Entrada.FechaPeriodo(Periodo.MENSAJE_FALTA_DESDE, Periodo.MENSAJE_FORMATO_FECHA))
            .campo("hasta", new Entrada.FechaPeriodo(Periodo.MENSAJE_FALTA_HASTA, Periodo.MENSAJE_FORMATO_FECHA))
            .validacion(attrs -> {
                if (((LocalDate) attrs.get("hasta")).isBefore((LocalDate) attrs.get("desde"))) {
                    throw new Entrada.ErrorValidacion(Py.dict("hasta", new ArrayList<>(List.of(Periodo.MENSAJE_ORDEN_FECHAS))));
                }
                return attrs;
            });
    }

    /** RF-REP-002: solo el periodo. */
    public static final Serializador CUMPLIMIENTO_CORTE_QUERY = periodoQuery();

    /** RF-REP-004: periodo y filtros opcionales. */
    public static final Serializador DISTRIBUCION_PERSONAL_QUERY = periodoQuery()
        .campo("cuadrilla_id", enteroOpcional())
        .campo("clase_actividad_id", enteroOpcional())
        .campo("tipo_actividad_id", enteroOpcional())
        .campo("area_verde_id", enteroOpcional());

    /** RF-REP-006: periodo, filtros opcionales y orden. */
    public static final Serializador ATENCIONES_ESPACIO_SECTOR_QUERY = periodoQuery()
        .campo("sector_id", new Entrada.SectorFiltro(AtencionesEspacioSectorService.SIN_SECTOR).opcional())
        .campo("modalidad", new Entrada.Opcion(AtencionesEspacioSectorService.MODALIDADES).opcional()
            .mensaje("invalid_choice", "Usa PersonalPropio o Tercerizado"))
        .campo("area_verde_id", enteroOpcional())
        .campo("orden", new Entrada.Opcion(List.of(AtencionesEspacioSectorService.ORDEN_ASC, AtencionesEspacioSectorService.ORDEN_DESC))
            .opcional().mensaje("invalid_choice", "Usa asc o desc"));

    /** Filtros de la lista de propuestas de IA. */
    public static final Serializador EXTRACCIONES_QUERY = Entrada.serializador()
        .campo("estado", new Entrada.Opcion(ExtraccionReportesService.ESTADOS_SUGERENCIA).opcional()
            .mensaje("invalid_choice", "Usa Propuesta, Validada o Rechazada"))
        .campo("reporte_proveedor_id", enteroOpcional());

    /** Cuerpo de {@code POST /ia/extracciones/}. */
    public static final Serializador SOLICITUD_EXTRACCION = Entrada.serializador()
        .campo("reporte_proveedor_id", new Entrada.Entero().minimo(1)
            .mensaje("required", "Indica el reporte del proveedor que quieres analizar (reporte_proveedor_id)")
            .mensaje("invalid", MENSAJE_ENTERO)
            .mensaje("min_value", MENSAJE_ENTERO));

    /** Decisión sobre un campo propuesto (CU-MOD05-025 1a). */
    public static final Serializador DECISION_CAMPO = Entrada.serializador()
        .campo("accion", new Entrada.Opcion(ExtraccionReportesService.ACCIONES)
            .mensaje("invalid_choice", "Usa aceptar, corregir o descartar")
            .mensaje("required", "Indica la acción"))
        .campo("valor_final", new Entrada.Json().opcional().nulo());

    /** Cuerpo de {@code POST /ia/extracciones/{id}/validar/}. */
    public static final Serializador VALIDACION = Entrada.serializador()
        .campo("decisiones", new Entrada.Dicc(DECISION_CAMPO).noVacio()
            .mensaje("required", "Indica las decisiones de cada campo")
            .mensaje("empty", "Indica las decisiones de cada campo"));

    /** Cuerpo de {@code POST /ia/extracciones/{id}/rechazar/}. */
    public static final Serializador RECHAZO = Entrada.serializador()
        .campo("motivo", new Entrada.Texto().vacioPermitido().largoMaximo(1000).opcional().nulo());

    // ------------------------------------------------------------------ PeriodoQuerySerializer

    public static Periodo periodo(Map<String, Object> validado) {
        return new Periodo((LocalDate) validado.get("desde"), (LocalDate) validado.get("hasta"));
    }

    /** Filtros recibidos, sin el periodo. */
    public static Map<String, Object> filtros(Map<String, Object> validado) {
        Map<String, Object> r = new LinkedHashMap<>();
        validado.forEach((k, v) -> {
            if (!k.equals("desde") && !k.equals("hasta") && v != null) r.put(k, v);
        });
        return r;
    }
}
