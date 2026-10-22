package pe.edu.pucp.areasverdes.mod06.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Salidas de la API del MOD-06 ({@code serializers.py}, parte de salida) para la documentación OpenAPI, con
 * componentes {@code snake_case}. El JSON real lo producen {@code Serializadores} con el mismo orden y tipo que el
 * Django; las claves nulas se devuelven.
 */
public final class Salidas {

    private Salidas() {}

    /** Cuerpo de error de dominio. */
    public record Error(@Schema(description = "Nombre de la excepción de dominio.") String codigo,
                        @Schema(description = "Mensaje que dice qué hacer.") String detalle) {}

    /** Errores de DRF con {@code detail} (403, 404, 405, 406, 415). */
    public record Detalle(String detail) {}

    public record PeriodoSalida(LocalDate desde, LocalDate hasta) {}

    /** DefinicionIndicador asociada al reporte. */
    public record Indicador(String codigo, String nombre, String descripcion_calculo, Double meta, String unidad) {}

    // ------------------------------------------------------------------ RF-REP-002

    public record FilaCumplimiento(Long ciclo_id, String estado_ciclo, Long proveedor_id, String proveedor_razon_social,
                                   Long ruta_id, String ruta_nombre, String estacion, LocalDate fecha_inicio_real,
                                   Long ciclo_anterior_id, LocalDate fecha_inicio_anterior, Long dias_entre_ciclos,
                                   Long frecuencia_teorica_dias, Long frecuencia_practica_dias, Long frecuencia_maxima_dias,
                                   String estado_frecuencia, LocalDate fecha_fin_corte, Long dias_corte,
                                   LocalDate fecha_inicio_limpieza, LocalDate fecha_fin_limpieza, Long dias_limpieza,
                                   Long dias_total, Long duracion_maxima_dias, Long dias_limpieza_maximo, Long maximo_total,
                                   String estado_duracion, List<String> faltantes) {}

    public record ResumenCumplimiento(Long total_ciclos, Map<String, Long> por_estado_frecuencia,
                                      Map<String, Long> por_estado_duracion) {}

    /** Respuesta de RF-REP-002. */
    public record ResultadoCumplimientoCorte(
        PeriodoSalida periodo,
        @Schema(description = "Instante de generación (ISO 8601, America/Lima).") String generado_en,
        Boolean sin_datos, String mensaje_sin_datos,
        @Schema(description = "RF-REP-002 no tiene filtros adicionales.") Map<String, Object> filtros_aplicados,
        Indicador indicador, List<FilaCumplimiento> ciclos, ResumenCumplimiento resumen, FilaCumplimiento ultimo_ciclo) {}

    // ------------------------------------------------------------------ RF-REP-004

    public record FiltrosDistribucion(Long cuadrilla_id, Long clase_actividad_id, Long tipo_actividad_id, Long area_verde_id) {}

    public record OperarioFila(Long operario_id, String nombre_completo,
                               @Schema(description = "Sin asignación vigente en la cuadrilla del parte (CU-MOD04-011 1a).") Boolean es_apoyo) {}

    public record FilaDistribucion(LocalDate jornada, Long cuadrilla_id, String cuadrilla_nombre, Long capataz_id,
                                   String capataz_nombre, Long clase_actividad_id, String clase_actividad_nombre,
                                   Long tipo_actividad_id, String tipo_actividad_nombre, Long area_verde_id,
                                   String area_verde_nombre, Long numero_actividades, List<OperarioFila> operarios,
                                   Long numero_operarios, Long tiempo_minutos, Double horas_persona, Double superficie_m2) {}

    public record TotalCuadrillaJornada(LocalDate jornada, Long cuadrilla_id, String cuadrilla_nombre, Long numero_actividades,
                                        Long numero_operarios, Long tiempo_minutos, Double horas_persona, Double superficie_m2) {}

    public record CuadrillaSinParte(LocalDate jornada, Long cuadrilla_id, String cuadrilla_nombre, String estado) {}

    public record ResumenDistribucion(Long jornadas_con_parte, Long partes, Long actividades, Long operarios_distintos,
                                      Double horas_persona, Long actividades_sin_tiempo) {}

    /** Respuesta de RF-REP-004. */
    public record ResultadoDistribucionPersonal(PeriodoSalida periodo, String generado_en, Boolean sin_datos,
                                                String mensaje_sin_datos, FiltrosDistribucion filtros_aplicados,
                                                List<FilaDistribucion> filas,
                                                List<TotalCuadrillaJornada> totales_por_cuadrilla_jornada,
                                                List<CuadrillaSinParte> cuadrillas_sin_parte, ResumenDistribucion resumen) {}

    // ------------------------------------------------------------------ RF-REP-006

    public record FiltrosAtenciones(Object sector_id, String modalidad, Long area_verde_id, String orden) {}

    public record CapatazVigente(Long id, String nombre) {}

    public record Medidas(Long total, Long personal_propio, Long tercerizado,
                          @Schema(description = "Tipo de atención → número.") Map<String, Long> por_tipo) {}

    public record EspacioAtenciones(Long total, Long personal_propio, Long tercerizado, Map<String, Long> por_tipo,
                                    Long area_verde_id, String area_verde_nombre) {}

    public record SectorAtenciones(Long total, Long personal_propio, Long tercerizado, Map<String, Long> por_tipo,
                                   @Schema(description = "null para el grupo «Sin sector».") Long sector_id,
                                   String sector_nombre, Boolean atencion_a_demanda, CapatazVigente capataz_vigente,
                                   List<EspacioAtenciones> espacios) {}

    public record CuartelAtenciones(Long cuartel_id, Long numero, String nombre, Long total, Long personal_propio, Long tercerizado) {}

    public record ResumenAtenciones(Long total_atenciones, Long personal_propio, Long tercerizado, Long espacios_con_atenciones,
                                    Long sectores_con_atenciones) {}

    /** Respuesta de RF-REP-006. */
    public record ResultadoAtencionesEspacioSector(PeriodoSalida periodo, String generado_en, Boolean sin_datos,
                                                   String mensaje_sin_datos, FiltrosAtenciones filtros_aplicados,
                                                   Indicador indicador, List<SectorAtenciones> por_sector, Medidas sin_espacio,
                                                   List<CuartelAtenciones> por_cuartel, ResumenAtenciones resumen,
                                                   List<String> notas) {}

    // ------------------------------------------------------------------ RF-IA-001

    public record CampoSolicitado(Long campo_id, String nombre_campo, String tipo_dato, Boolean obligatorio) {}

    public record EntradaIA(Long reporte_proveedor_id, Long atencion_id, Long tipo_servicio_id, Long formato_id,
                            Long formato_version, Long archivo_id, List<CampoSolicitado> campos_solicitados) {}

    public record LlamadaIA(String proveedor, String modelo, String solicitado_en, Long duracion_ms) {}

    public record ValorPropuesto(String nombre_campo, Object valor) {}

    public record DecisionFinalCampo(@Schema(allowableValues = {"aceptar", "corregir", "descartar"}) String accion, Object valor_final) {}

    public record ResumenDecision(Long aceptados, Long corregidos, Long descartados) {}

    public record Decision(Map<String, DecisionFinalCampo> por_campo, ResumenDecision resumen, String motivo_rechazo) {}

    /** Estructura interna de {@code SugerenciaIA.contenido} (versión 1). */
    public record ContenidoSugerencia(Long version_contenido, EntradaIA entrada, LlamadaIA ia,
                                      @Schema(description = "Claves campo_<id>.") Map<String, ValorPropuesto> propuesta,
                                      List<String> campos_obligatorios_faltantes, Decision decision) {}

    /** SugerenciaIA (atributos exactos del Modelo de BD v1) más {@code etiqueta_estado}. */
    public record SugerenciaIA(Long id, String tipo, String entidad, Long registro_id, Long archivo_id,
                               ContenidoSugerencia contenido,
                               @Schema(allowableValues = {"Propuesta", "Validada", "Rechazada"}) String estado,
                               @Schema(description = "«Pendiente de revisión» si está en Propuesta (HU-MOD06-010; Guía Web 7).") String etiqueta_estado,
                               Long validador_id, String fecha_validacion, String fecha_creacion, Long usuario_creacion_id,
                               String fecha_modificacion, Long usuario_modificacion_id) {}

    /** Lista paginada de 25 elementos (Guía Web 5.5). */
    public record PaginaSugerencias(Long count, String next, String previous, List<SugerenciaIA> results) {}

    /** Métricas de la función de IA. */
    public record MetricasIA(Long total_sugerencias, Map<String, Long> por_estado, Long campos_aceptados,
                             Long campos_corregidos, Long campos_descartados, Double tasa_aceptacion_sin_cambios,
                             Double duracion_media_ms) {}
}
