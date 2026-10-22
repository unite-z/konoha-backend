package pe.edu.pucp.areasverdes.mod06.api;

import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.anidado;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.booleano;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.decimal;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.dicc;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.entero;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.fecha;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.fechaHora;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.json;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.lista;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.metodo;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.muchos;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.opcion;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.serializador;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.talCual;
import static pe.edu.pucp.areasverdes.mod06.api.drf.Salida.texto;

import pe.edu.pucp.areasverdes.mod06.api.drf.Salida.Serializador;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.ia.ExtraccionReportesService;

/**
 * Serializadores de salida de la API REST del MOD-06 (parte de salida de {@code api/serializers.py}).
 *
 * <p>Declaran de forma explícita todos los campos de los resultados de los servicios (RF-REP-002, RF-REP-004,
 * RF-REP-006 y RF-IA-001), en el mismo orden y con el mismo tipo que el Django. Las claves son {@code snake_case} en
 * español, idénticas a los atributos del Modelo de BD v1 cuando corresponden a uno. Las cifras se devuelven como
 * números JSON sin formato (el formato lo aplica el front).
 */
public final class Serializadores {

    private Serializadores() {}

    // =========================================================================
    // Comunes
    // =========================================================================

    public static final Serializador PERIODO_SALIDA = serializador()
        .campo("desde", fecha())
        .campo("hasta", fecha());

    /** DefinicionIndicador asociada al reporte. */
    public static final Serializador INDICADOR = serializador()
        .campo("codigo", texto())
        .campo("nombre", texto().nulo())
        .campo("descripcion_calculo", texto().nulo())
        .campo("meta", decimal().nulo())
        .campo("unidad", texto().nulo());

    /** Campos comunes de toda respuesta de reporte. */
    private static Serializador reporteBase() {
        return serializador()
            .campo("periodo", anidado(PERIODO_SALIDA))
            .campo("generado_en", fechaHora().ayuda("Instante de generación (ISO 8601, America/Lima)."))
            .campo("sin_datos", booleano())
            .campo("mensaje_sin_datos", texto().nulo());
    }

    // =========================================================================
    // RF-REP-002
    // =========================================================================

    /** RF-REP-002 no tiene filtros adicionales. */
    public static final Serializador FILTROS_CUMPLIMIENTO = serializador();

    public static final Serializador FILA_CUMPLIMIENTO = serializador()
        .campo("ciclo_id", entero())
        .campo("estado_ciclo", texto().nulo())
        .campo("proveedor_id", entero().nulo())
        .campo("proveedor_razon_social", texto().nulo())
        .campo("ruta_id", entero().nulo())
        .campo("ruta_nombre", texto().nulo())
        .campo("estacion", texto().nulo())
        .campo("fecha_inicio_real", fecha())
        .campo("ciclo_anterior_id", entero().nulo())
        .campo("fecha_inicio_anterior", fecha().nulo())
        .campo("dias_entre_ciclos", entero().nulo())
        .campo("frecuencia_teorica_dias", entero().nulo())
        .campo("frecuencia_practica_dias", entero().nulo())
        .campo("frecuencia_maxima_dias", entero().nulo())
        .campo("estado_frecuencia", texto())
        .campo("fecha_fin_corte", fecha().nulo())
        .campo("dias_corte", entero().nulo())
        .campo("fecha_inicio_limpieza", fecha().nulo())
        .campo("fecha_fin_limpieza", fecha().nulo())
        .campo("dias_limpieza", entero().nulo())
        .campo("dias_total", entero().nulo())
        .campo("duracion_maxima_dias", entero().nulo())
        .campo("dias_limpieza_maximo", entero().nulo())
        .campo("maximo_total", entero().nulo())
        .campo("estado_duracion", texto())
        .campo("faltantes", lista(texto()));

    public static final Serializador RESUMEN_CUMPLIMIENTO = serializador()
        .campo("total_ciclos", entero())
        .campo("por_estado_frecuencia", dicc(entero()))
        .campo("por_estado_duracion", dicc(entero()));

    /** Respuesta de RF-REP-002. */
    public static final Serializador RESULTADO_CUMPLIMIENTO_CORTE = reporteBase()
        .campo("filtros_aplicados", anidado(FILTROS_CUMPLIMIENTO))
        .campo("indicador", anidado(INDICADOR).nulo())
        .campo("ciclos", muchos(FILA_CUMPLIMIENTO))
        .campo("resumen", anidado(RESUMEN_CUMPLIMIENTO).nulo())
        .campo("ultimo_ciclo", anidado(FILA_CUMPLIMIENTO).nulo());

    // =========================================================================
    // RF-REP-004
    // =========================================================================

    public static final Serializador FILTROS_DISTRIBUCION = serializador()
        .campo("cuadrilla_id", entero().opcional())
        .campo("clase_actividad_id", entero().opcional())
        .campo("tipo_actividad_id", entero().opcional())
        .campo("area_verde_id", entero().opcional());

    public static final Serializador OPERARIO_FILA = serializador()
        .campo("operario_id", entero())
        .campo("nombre_completo", texto().nulo())
        .campo("es_apoyo", booleano().ayuda("Sin asignación vigente en la cuadrilla del parte (CU-MOD04-011 1a)."));

    public static final Serializador FILA_DISTRIBUCION = serializador()
        .campo("jornada", fecha())
        .campo("cuadrilla_id", entero().nulo())
        .campo("cuadrilla_nombre", texto().nulo())
        .campo("capataz_id", entero().nulo())
        .campo("capataz_nombre", texto().nulo())
        .campo("clase_actividad_id", entero().nulo())
        .campo("clase_actividad_nombre", texto().nulo())
        .campo("tipo_actividad_id", entero().nulo())
        .campo("tipo_actividad_nombre", texto().nulo())
        .campo("area_verde_id", entero().nulo())
        .campo("area_verde_nombre", texto().nulo())
        .campo("numero_actividades", entero())
        .campo("operarios", muchos(OPERARIO_FILA))
        .campo("numero_operarios", entero())
        .campo("tiempo_minutos", entero().nulo())
        .campo("horas_persona", decimal().nulo())
        .campo("superficie_m2", decimal().nulo());

    public static final Serializador TOTAL_CUADRILLA_JORNADA = serializador()
        .campo("jornada", fecha())
        .campo("cuadrilla_id", entero().nulo())
        .campo("cuadrilla_nombre", texto().nulo())
        .campo("numero_actividades", entero())
        .campo("numero_operarios", entero())
        .campo("tiempo_minutos", entero().nulo())
        .campo("horas_persona", decimal().nulo())
        .campo("superficie_m2", decimal().nulo());

    public static final Serializador CUADRILLA_SIN_PARTE = serializador()
        .campo("jornada", fecha())
        .campo("cuadrilla_id", entero())
        .campo("cuadrilla_nombre", texto().nulo())
        .campo("estado", texto());

    public static final Serializador RESUMEN_DISTRIBUCION = serializador()
        .campo("jornadas_con_parte", entero())
        .campo("partes", entero())
        .campo("actividades", entero())
        .campo("operarios_distintos", entero())
        .campo("horas_persona", decimal().nulo())
        .campo("actividades_sin_tiempo", entero());

    /** Respuesta de RF-REP-004. */
    public static final Serializador RESULTADO_DISTRIBUCION_PERSONAL = reporteBase()
        .campo("filtros_aplicados", anidado(FILTROS_DISTRIBUCION))
        .campo("filas", muchos(FILA_DISTRIBUCION))
        .campo("totales_por_cuadrilla_jornada", muchos(TOTAL_CUADRILLA_JORNADA))
        .campo("cuadrillas_sin_parte", muchos(CUADRILLA_SIN_PARTE))
        .campo("resumen", anidado(RESUMEN_DISTRIBUCION).nulo());

    // =========================================================================
    // RF-REP-006
    // =========================================================================

    public static final Serializador FILTROS_ATENCIONES = serializador()
        .campo("sector_id", talCual().opcional())
        .campo("modalidad", texto().opcional())
        .campo("area_verde_id", entero().opcional())
        .campo("orden", texto().opcional());

    public static final Serializador CAPATAZ_VIGENTE = serializador()
        .campo("id", entero())
        .campo("nombre", texto().nulo());

    private static Serializador medidas() {
        return serializador()
            .campo("total", entero())
            .campo("personal_propio", entero())
            .campo("tercerizado", entero())
            .campo("por_tipo", dicc(entero()).ayuda("Tipo de atención → número."));
    }

    public static final Serializador MEDIDAS = medidas();

    public static final Serializador ESPACIO_ATENCIONES = medidas()
        .campo("area_verde_id", entero())
        .campo("area_verde_nombre", texto().nulo());

    public static final Serializador SECTOR_ATENCIONES = medidas()
        .campo("sector_id", entero().nulo().ayuda("null para el grupo «Sin sector»."))
        .campo("sector_nombre", texto().nulo())
        .campo("atencion_a_demanda", booleano().nulo())
        .campo("capataz_vigente", anidado(CAPATAZ_VIGENTE).nulo())
        .campo("espacios", muchos(ESPACIO_ATENCIONES));

    public static final Serializador CUARTEL_ATENCIONES = serializador()
        .campo("cuartel_id", entero())
        .campo("numero", entero().nulo())
        .campo("nombre", texto().nulo())
        .campo("total", entero())
        .campo("personal_propio", entero())
        .campo("tercerizado", entero());

    public static final Serializador RESUMEN_ATENCIONES = serializador()
        .campo("total_atenciones", entero())
        .campo("personal_propio", entero())
        .campo("tercerizado", entero())
        .campo("espacios_con_atenciones", entero())
        .campo("sectores_con_atenciones", entero());

    /** Respuesta de RF-REP-006. */
    public static final Serializador RESULTADO_ATENCIONES_ESPACIO_SECTOR = reporteBase()
        .campo("filtros_aplicados", anidado(FILTROS_ATENCIONES))
        .campo("indicador", anidado(INDICADOR).nulo())
        .campo("por_sector", muchos(SECTOR_ATENCIONES))
        .campo("sin_espacio", anidado(MEDIDAS).nulo())
        .campo("por_cuartel", muchos(CUARTEL_ATENCIONES))
        .campo("resumen", anidado(RESUMEN_ATENCIONES).nulo())
        .campo("notas", lista(texto()));

    // =========================================================================
    // RF-IA-001
    // =========================================================================

    public static final Serializador CAMPO_SOLICITADO = serializador()
        .campo("campo_id", entero())
        .campo("nombre_campo", texto().nulo())
        .campo("tipo_dato", texto().nulo())
        .campo("obligatorio", booleano());

    public static final Serializador ENTRADA_IA = serializador()
        .campo("reporte_proveedor_id", entero())
        .campo("atencion_id", entero())
        .campo("tipo_servicio_id", entero().nulo())
        .campo("formato_id", entero())
        .campo("formato_version", entero().nulo())
        .campo("archivo_id", entero())
        .campo("campos_solicitados", muchos(CAMPO_SOLICITADO));

    public static final Serializador LLAMADA_IA = serializador()
        .campo("proveedor", texto())
        .campo("modelo", texto())
        .campo("solicitado_en", fechaHora())
        .campo("duracion_ms", entero());

    public static final Serializador VALOR_PROPUESTO = serializador()
        .campo("nombre_campo", texto().nulo())
        .campo("valor", json().nulo());

    public static final Serializador DECISION_FINAL_CAMPO = serializador()
        .campo("accion", opcion(ExtraccionReportesService.ACCIONES))
        .campo("valor_final", json().nulo());

    public static final Serializador RESUMEN_DECISION = serializador()
        .campo("aceptados", entero())
        .campo("corregidos", entero())
        .campo("descartados", entero());

    public static final Serializador DECISION = serializador()
        .campo("por_campo", dicc(anidado(DECISION_FINAL_CAMPO)))
        .campo("resumen", anidado(RESUMEN_DECISION))
        .campo("motivo_rechazo", texto().nulo());

    /** Estructura interna de {@code SugerenciaIA.contenido} (versión 1). */
    public static final Serializador CONTENIDO_SUGERENCIA = serializador()
        .campo("version_contenido", entero())
        .campo("entrada", anidado(ENTRADA_IA))
        .campo("ia", anidado(LLAMADA_IA))
        .campo("propuesta", dicc(anidado(VALOR_PROPUESTO)).ayuda("Claves campo_<id>."))
        .campo("campos_obligatorios_faltantes", lista(texto()))
        .campo("decision", anidado(DECISION).nulo());

    /** SugerenciaIA (atributos exactos del Modelo de BD v1) más {@code etiqueta_estado}. */
    public static final Serializador SUGERENCIA_IA = serializador()
        .campo("id", entero())
        .campo("tipo", texto())
        .campo("entidad", texto())
        .campo("registro_id", entero())
        .campo("archivo_id", entero().nulo())
        .campo("contenido", anidado(CONTENIDO_SUGERENCIA))
        .campo("estado", opcion(ExtraccionReportesService.ESTADOS_SUGERENCIA))
        .campo("etiqueta_estado", metodo(obj -> ExtraccionReportesService.etiquetaEstado(Py.get(obj, "estado"))))
        .campo("validador_id", entero().nulo())
        .campo("fecha_validacion", fechaHora().nulo())
        .campo("fecha_creacion", fechaHora().nulo())
        .campo("usuario_creacion_id", entero().nulo())
        .campo("fecha_modificacion", fechaHora().nulo())
        .campo("usuario_modificacion_id", entero().nulo());

    /** Métricas de la función de IA. */
    public static final Serializador METRICAS_IA = serializador()
        .campo("total_sugerencias", entero())
        .campo("por_estado", dicc(entero()))
        .campo("campos_aceptados", entero())
        .campo("campos_corregidos", entero())
        .campo("campos_descartados", entero())
        .campo("tasa_aceptacion_sin_cambios", decimal().nulo())
        .campo("duracion_media_ms", decimal().nulo());
}
