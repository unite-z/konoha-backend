"""
Serializadores de la API REST del MOD-06.

- Entrada: validan los parámetros de consulta y los cuerpos (mensajes de la Guía de Estilo Web, sección 7).
- Salida: declaran de forma explícita todos los campos de los resultados de los servicios para que el esquema
  OpenAPI sea completo (RF-REP-002, RF-REP-004, RF-REP-006 y RF-IA-001).

Las claves son `snake_case` en español, idénticas a los atributos del Modelo de BD v1 cuando corresponden a uno.
Las cifras se devuelven como números JSON sin formato (el formato lo aplica el front).
"""

from __future__ import annotations

from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers

from ..comun.periodo import (
    MENSAJE_FALTA_DESDE,
    MENSAJE_FALTA_HASTA,
    MENSAJE_FORMATO_FECHA,
    MENSAJE_ORDEN_FECHAS,
    Periodo,
)
from ..ia.extraccion_reportes import ACCIONES, ESTADOS_SUGERENCIA, etiqueta_estado
from ..servicios.atenciones_espacio_sector import MODALIDADES, ORDEN_ASC, ORDEN_DESC, SIN_SECTOR

MENSAJE_ENTERO = "Usa un número entero positivo"


# =============================================================================
# Entrada
# =============================================================================


class FechaPeriodoField(serializers.DateField):
    """Fecha `AAAA-MM-DD` obligatoria; vacía cuenta como no indicada."""

    def __init__(self, mensaje_requerido: str, **kwargs) -> None:
        super().__init__(
            input_formats=["%Y-%m-%d"],
            error_messages={
                "required": mensaje_requerido,
                "null": mensaje_requerido,
                "invalid": MENSAJE_FORMATO_FECHA,
                "datetime": MENSAJE_FORMATO_FECHA,
            },
            **kwargs,
        )

    def to_internal_value(self, value):
        if value in ("", None):
            self.fail("required")
        return super().to_internal_value(value)


def _entero_opcional(help_text: str) -> serializers.IntegerField:
    return serializers.IntegerField(
        required=False,
        min_value=1,
        help_text=help_text,
        error_messages={"invalid": MENSAJE_ENTERO, "min_value": MENSAJE_ENTERO},
    )


class PeriodoQuerySerializer(serializers.Serializer):
    """Periodo obligatorio de todos los reportes (CU-MOD06-001: el actor selecciona un rango de fechas)."""

    desde = FechaPeriodoField(MENSAJE_FALTA_DESDE, help_text="Fecha de inicio del periodo (AAAA-MM-DD), incluida.")
    hasta = FechaPeriodoField(MENSAJE_FALTA_HASTA, help_text="Fecha de fin del periodo (AAAA-MM-DD), incluida.")

    def validate(self, attrs: dict) -> dict:
        if attrs["hasta"] < attrs["desde"]:
            raise serializers.ValidationError({"hasta": [MENSAJE_ORDEN_FECHAS]})
        return attrs

    def periodo(self) -> Periodo:
        return Periodo(desde=self.validated_data["desde"], hasta=self.validated_data["hasta"])

    def filtros(self) -> dict:
        """Filtros recibidos, sin el periodo."""
        return {k: v for k, v in self.validated_data.items() if k not in ("desde", "hasta") and v is not None}


class CumplimientoCorteQuerySerializer(PeriodoQuerySerializer):
    """RF-REP-002: solo el periodo."""


class DistribucionPersonalQuerySerializer(PeriodoQuerySerializer):
    """RF-REP-004: periodo y filtros opcionales."""

    cuadrilla_id = _entero_opcional("Cuadrilla (Cuadrilla.id).")
    clase_actividad_id = _entero_opcional("Clase de actividad (ClaseActividad.id).")
    tipo_actividad_id = _entero_opcional("Tipo de actividad (TipoActividad.id).")
    area_verde_id = _entero_opcional("Espacio (AreaVerde.id).")


class SectorFiltroField(serializers.Field):
    """Entero (SectorMantenimiento.id) o el valor especial `sin_sector`."""

    default_error_messages = {"invalid": f"Usa el id del sector (número entero) o '{SIN_SECTOR}'"}

    def to_internal_value(self, data):
        if data == SIN_SECTOR:
            return SIN_SECTOR
        try:
            valor = int(str(data))
        except (TypeError, ValueError):
            self.fail("invalid")
        if valor < 1:
            self.fail("invalid")
        return valor

    def to_representation(self, value):
        return value


@extend_schema_field({"oneOf": [{"type": "integer", "minimum": 1}, {"type": "string", "enum": [SIN_SECTOR]}]})
class SectorFiltroDocumentadoField(SectorFiltroField):
    """Igual que `SectorFiltroField`, con su tipo documentado para OpenAPI."""


class AtencionesEspacioSectorQuerySerializer(PeriodoQuerySerializer):
    """RF-REP-006: periodo, filtros opcionales y orden."""

    sector_id = SectorFiltroDocumentadoField(
        required=False, help_text=f"Sector (SectorMantenimiento.id) o '{SIN_SECTOR}' para el grupo sin sector."
    )
    modalidad = serializers.ChoiceField(
        choices=MODALIDADES,
        required=False,
        help_text="Modalidad de ejecución (enumerado Ejecutor).",
        error_messages={"invalid_choice": "Usa PersonalPropio o Tercerizado"},
    )
    area_verde_id = _entero_opcional("Espacio (AreaVerde.id).")
    orden = serializers.ChoiceField(
        choices=(ORDEN_ASC, ORDEN_DESC),
        required=False,
        help_text="asc: menos atendidos primero (para priorizar); desc (por defecto): más atendidos primero.",
        error_messages={"invalid_choice": "Usa asc o desc"},
    )


class ExtraccionesQuerySerializer(serializers.Serializer):
    """Filtros de la lista de propuestas de IA."""

    estado = serializers.ChoiceField(
        choices=ESTADOS_SUGERENCIA,
        required=False,
        help_text="Estado de la sugerencia (EstadoSugerencia).",
        error_messages={"invalid_choice": "Usa Propuesta, Validada o Rechazada"},
    )
    reporte_proveedor_id = _entero_opcional("Reporte del proveedor (ReporteProveedor.id).")


class SolicitudExtraccionSerializer(serializers.Serializer):
    """Cuerpo de `POST /ia/extracciones/`."""

    reporte_proveedor_id = serializers.IntegerField(
        min_value=1,
        help_text="ReporteProveedor cuyo informe adjunto se analizará.",
        error_messages={
            "required": "Indica el reporte del proveedor que quieres analizar (reporte_proveedor_id)",
            "invalid": MENSAJE_ENTERO,
            "min_value": MENSAJE_ENTERO,
        },
    )


class DecisionCampoSerializer(serializers.Serializer):
    """Decisión sobre un campo propuesto (CU-MOD05-025 1a)."""

    accion = serializers.ChoiceField(
        choices=ACCIONES,
        help_text="aceptar: se usa el valor propuesto; corregir: se usa valor_final; descartar: no se incorpora.",
        error_messages={"invalid_choice": "Usa aceptar, corregir o descartar", "required": "Indica la acción"},
    )
    valor_final = serializers.JSONField(
        required=False,
        allow_null=True,
        help_text="Obligatorio si la acción es corregir; debe ser del tipo del campo.",
    )


class ValidacionSerializer(serializers.Serializer):
    """Cuerpo de `POST /ia/extracciones/{id}/validar/`."""

    decisiones = serializers.DictField(
        child=DecisionCampoSerializer(),
        allow_empty=False,
        help_text="Una entrada por cada campo de la propuesta: {\"campo_<id>\": {accion, valor_final}}.",
        error_messages={"required": "Indica las decisiones de cada campo", "empty": "Indica las decisiones de cada campo"},
    )


class RechazoSerializer(serializers.Serializer):
    """Cuerpo de `POST /ia/extracciones/{id}/rechazar/`."""

    motivo = serializers.CharField(
        required=False, allow_blank=True, allow_null=True, max_length=1000, help_text="Motivo del rechazo (opcional)."
    )


# =============================================================================
# Salida: comunes
# =============================================================================


class ErrorSerializer(serializers.Serializer):
    """Cuerpo de error de dominio (sección 4.5)."""

    codigo = serializers.CharField(help_text="Nombre de la excepción de dominio.")
    detalle = serializers.CharField(help_text="Mensaje que dice qué hacer.")


class PeriodoSalidaSerializer(serializers.Serializer):
    desde = serializers.DateField()
    hasta = serializers.DateField()


class IndicadorSerializer(serializers.Serializer):
    """DefinicionIndicador asociada al reporte."""

    codigo = serializers.CharField()
    nombre = serializers.CharField(allow_null=True)
    descripcion_calculo = serializers.CharField(allow_null=True)
    meta = serializers.FloatField(allow_null=True)
    unidad = serializers.CharField(allow_null=True)


class ReporteBaseSerializer(serializers.Serializer):
    """Campos comunes de toda respuesta de reporte (sección 4.4)."""

    periodo = PeriodoSalidaSerializer()
    generado_en = serializers.DateTimeField(help_text="Instante de generación (ISO 8601, America/Lima).")
    sin_datos = serializers.BooleanField()
    mensaje_sin_datos = serializers.CharField(allow_null=True)


# =============================================================================
# Salida: RF-REP-002
# =============================================================================


class FiltrosCumplimientoSerializer(serializers.Serializer):
    """RF-REP-002 no tiene filtros adicionales."""


class FilaCumplimientoSerializer(serializers.Serializer):
    ciclo_id = serializers.IntegerField()
    estado_ciclo = serializers.CharField(allow_null=True)
    proveedor_id = serializers.IntegerField(allow_null=True)
    proveedor_razon_social = serializers.CharField(allow_null=True)
    ruta_id = serializers.IntegerField(allow_null=True)
    ruta_nombre = serializers.CharField(allow_null=True)
    estacion = serializers.CharField(allow_null=True)
    fecha_inicio_real = serializers.DateField()
    ciclo_anterior_id = serializers.IntegerField(allow_null=True)
    fecha_inicio_anterior = serializers.DateField(allow_null=True)
    dias_entre_ciclos = serializers.IntegerField(allow_null=True)
    frecuencia_teorica_dias = serializers.IntegerField(allow_null=True)
    frecuencia_practica_dias = serializers.IntegerField(allow_null=True)
    frecuencia_maxima_dias = serializers.IntegerField(allow_null=True)
    estado_frecuencia = serializers.CharField()
    fecha_fin_corte = serializers.DateField(allow_null=True)
    dias_corte = serializers.IntegerField(allow_null=True)
    fecha_inicio_limpieza = serializers.DateField(allow_null=True)
    fecha_fin_limpieza = serializers.DateField(allow_null=True)
    dias_limpieza = serializers.IntegerField(allow_null=True)
    dias_total = serializers.IntegerField(allow_null=True)
    duracion_maxima_dias = serializers.IntegerField(allow_null=True)
    dias_limpieza_maximo = serializers.IntegerField(allow_null=True)
    maximo_total = serializers.IntegerField(allow_null=True)
    estado_duracion = serializers.CharField()
    faltantes = serializers.ListField(child=serializers.CharField())


class ResumenCumplimientoSerializer(serializers.Serializer):
    total_ciclos = serializers.IntegerField()
    por_estado_frecuencia = serializers.DictField(child=serializers.IntegerField())
    por_estado_duracion = serializers.DictField(child=serializers.IntegerField())


class ResultadoCumplimientoCorteSerializer(ReporteBaseSerializer):
    """Respuesta de RF-REP-002."""

    filtros_aplicados = FiltrosCumplimientoSerializer()
    indicador = IndicadorSerializer(allow_null=True)
    ciclos = FilaCumplimientoSerializer(many=True)
    resumen = ResumenCumplimientoSerializer(allow_null=True)
    ultimo_ciclo = FilaCumplimientoSerializer(allow_null=True)


# =============================================================================
# Salida: RF-REP-004
# =============================================================================


class FiltrosDistribucionSerializer(serializers.Serializer):
    cuadrilla_id = serializers.IntegerField(required=False)
    clase_actividad_id = serializers.IntegerField(required=False)
    tipo_actividad_id = serializers.IntegerField(required=False)
    area_verde_id = serializers.IntegerField(required=False)


class OperarioFilaSerializer(serializers.Serializer):
    operario_id = serializers.IntegerField()
    nombre_completo = serializers.CharField(allow_null=True)
    es_apoyo = serializers.BooleanField(help_text="Sin asignación vigente en la cuadrilla del parte (CU-MOD04-011 1a).")


class FilaDistribucionSerializer(serializers.Serializer):
    jornada = serializers.DateField()
    cuadrilla_id = serializers.IntegerField(allow_null=True)
    cuadrilla_nombre = serializers.CharField(allow_null=True)
    capataz_id = serializers.IntegerField(allow_null=True)
    capataz_nombre = serializers.CharField(allow_null=True)
    clase_actividad_id = serializers.IntegerField(allow_null=True)
    clase_actividad_nombre = serializers.CharField(allow_null=True)
    tipo_actividad_id = serializers.IntegerField(allow_null=True)
    tipo_actividad_nombre = serializers.CharField(allow_null=True)
    area_verde_id = serializers.IntegerField(allow_null=True)
    area_verde_nombre = serializers.CharField(allow_null=True)
    numero_actividades = serializers.IntegerField()
    operarios = OperarioFilaSerializer(many=True)
    numero_operarios = serializers.IntegerField()
    tiempo_minutos = serializers.IntegerField(allow_null=True)
    horas_persona = serializers.FloatField(allow_null=True)
    superficie_m2 = serializers.FloatField(allow_null=True)


class TotalCuadrillaJornadaSerializer(serializers.Serializer):
    jornada = serializers.DateField()
    cuadrilla_id = serializers.IntegerField(allow_null=True)
    cuadrilla_nombre = serializers.CharField(allow_null=True)
    numero_actividades = serializers.IntegerField()
    numero_operarios = serializers.IntegerField()
    tiempo_minutos = serializers.IntegerField(allow_null=True)
    horas_persona = serializers.FloatField(allow_null=True)
    superficie_m2 = serializers.FloatField(allow_null=True)


class CuadrillaSinParteSerializer(serializers.Serializer):
    jornada = serializers.DateField()
    cuadrilla_id = serializers.IntegerField()
    cuadrilla_nombre = serializers.CharField(allow_null=True)
    estado = serializers.CharField()


class ResumenDistribucionSerializer(serializers.Serializer):
    jornadas_con_parte = serializers.IntegerField()
    partes = serializers.IntegerField()
    actividades = serializers.IntegerField()
    operarios_distintos = serializers.IntegerField()
    horas_persona = serializers.FloatField(allow_null=True)
    actividades_sin_tiempo = serializers.IntegerField()


class ResultadoDistribucionPersonalSerializer(ReporteBaseSerializer):
    """Respuesta de RF-REP-004."""

    filtros_aplicados = FiltrosDistribucionSerializer()
    filas = FilaDistribucionSerializer(many=True)
    totales_por_cuadrilla_jornada = TotalCuadrillaJornadaSerializer(many=True)
    cuadrillas_sin_parte = CuadrillaSinParteSerializer(many=True)
    resumen = ResumenDistribucionSerializer(allow_null=True)


# =============================================================================
# Salida: RF-REP-006
# =============================================================================


class FiltrosAtencionesSerializer(serializers.Serializer):
    sector_id = SectorFiltroDocumentadoField(required=False)
    modalidad = serializers.CharField(required=False)
    area_verde_id = serializers.IntegerField(required=False)
    orden = serializers.CharField(required=False)


class CapatazVigenteSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    nombre = serializers.CharField(allow_null=True)


class MedidasSerializer(serializers.Serializer):
    total = serializers.IntegerField()
    personal_propio = serializers.IntegerField()
    tercerizado = serializers.IntegerField()
    por_tipo = serializers.DictField(child=serializers.IntegerField(), help_text="Tipo de atención → número.")


class EspacioAtencionesSerializer(MedidasSerializer):
    area_verde_id = serializers.IntegerField()
    area_verde_nombre = serializers.CharField(allow_null=True)


class SectorAtencionesSerializer(MedidasSerializer):
    sector_id = serializers.IntegerField(allow_null=True, help_text="null para el grupo «Sin sector».")
    sector_nombre = serializers.CharField(allow_null=True)
    atencion_a_demanda = serializers.BooleanField(allow_null=True)
    capataz_vigente = CapatazVigenteSerializer(allow_null=True)
    espacios = EspacioAtencionesSerializer(many=True)


class CuartelAtencionesSerializer(serializers.Serializer):
    cuartel_id = serializers.IntegerField()
    numero = serializers.IntegerField(allow_null=True)
    nombre = serializers.CharField(allow_null=True)
    total = serializers.IntegerField()
    personal_propio = serializers.IntegerField()
    tercerizado = serializers.IntegerField()


class ResumenAtencionesSerializer(serializers.Serializer):
    total_atenciones = serializers.IntegerField()
    personal_propio = serializers.IntegerField()
    tercerizado = serializers.IntegerField()
    espacios_con_atenciones = serializers.IntegerField()
    sectores_con_atenciones = serializers.IntegerField()


class ResultadoAtencionesEspacioSectorSerializer(ReporteBaseSerializer):
    """Respuesta de RF-REP-006."""

    filtros_aplicados = FiltrosAtencionesSerializer()
    indicador = IndicadorSerializer(allow_null=True)
    por_sector = SectorAtencionesSerializer(many=True)
    sin_espacio = MedidasSerializer(allow_null=True)
    por_cuartel = CuartelAtencionesSerializer(many=True)
    resumen = ResumenAtencionesSerializer(allow_null=True)
    notas = serializers.ListField(child=serializers.CharField())


# =============================================================================
# Salida: RF-IA-001
# =============================================================================


class CampoSolicitadoSerializer(serializers.Serializer):
    campo_id = serializers.IntegerField()
    nombre_campo = serializers.CharField(allow_null=True)
    tipo_dato = serializers.CharField(allow_null=True)
    obligatorio = serializers.BooleanField()


class EntradaIASerializer(serializers.Serializer):
    reporte_proveedor_id = serializers.IntegerField()
    atencion_id = serializers.IntegerField()
    tipo_servicio_id = serializers.IntegerField(allow_null=True)
    formato_id = serializers.IntegerField()
    formato_version = serializers.IntegerField(allow_null=True)
    archivo_id = serializers.IntegerField()
    campos_solicitados = CampoSolicitadoSerializer(many=True)


class LlamadaIASerializer(serializers.Serializer):
    proveedor = serializers.CharField()
    modelo = serializers.CharField()
    solicitado_en = serializers.DateTimeField()
    duracion_ms = serializers.IntegerField()


class ValorPropuestoSerializer(serializers.Serializer):
    nombre_campo = serializers.CharField(allow_null=True)
    valor = serializers.JSONField(allow_null=True)


class DecisionFinalCampoSerializer(serializers.Serializer):
    accion = serializers.ChoiceField(choices=ACCIONES)
    valor_final = serializers.JSONField(allow_null=True)


class ResumenDecisionSerializer(serializers.Serializer):
    aceptados = serializers.IntegerField()
    corregidos = serializers.IntegerField()
    descartados = serializers.IntegerField()


class DecisionSerializer(serializers.Serializer):
    por_campo = serializers.DictField(child=DecisionFinalCampoSerializer())
    resumen = ResumenDecisionSerializer()
    motivo_rechazo = serializers.CharField(allow_null=True)


class ContenidoSugerenciaSerializer(serializers.Serializer):
    """Estructura interna de `SugerenciaIA.contenido` (versión 1)."""

    version_contenido = serializers.IntegerField()
    entrada = EntradaIASerializer()
    ia = LlamadaIASerializer()
    propuesta = serializers.DictField(child=ValorPropuestoSerializer(), help_text="Claves campo_<id>.")
    campos_obligatorios_faltantes = serializers.ListField(child=serializers.CharField())
    decision = DecisionSerializer(allow_null=True)


class SugerenciaIASerializer(serializers.Serializer):
    """SugerenciaIA (atributos exactos del Modelo de BD v1) más `etiqueta_estado`."""

    id = serializers.IntegerField()
    tipo = serializers.CharField()
    entidad = serializers.CharField()
    registro_id = serializers.IntegerField()
    archivo_id = serializers.IntegerField(allow_null=True)
    contenido = ContenidoSugerenciaSerializer()
    estado = serializers.ChoiceField(choices=ESTADOS_SUGERENCIA)
    etiqueta_estado = serializers.SerializerMethodField()
    validador_id = serializers.IntegerField(allow_null=True)
    fecha_validacion = serializers.DateTimeField(allow_null=True)
    fecha_creacion = serializers.DateTimeField(allow_null=True)
    usuario_creacion_id = serializers.IntegerField(allow_null=True)
    fecha_modificacion = serializers.DateTimeField(allow_null=True)
    usuario_modificacion_id = serializers.IntegerField(allow_null=True)

    @extend_schema_field(OpenApiTypes.STR)
    def get_etiqueta_estado(self, obj: dict) -> str | None:
        """«Pendiente de revisión» si está en Propuesta (HU-MOD06-010; Guía Web 7)."""
        return etiqueta_estado(obj.get("estado"))


class MetricasIASerializer(serializers.Serializer):
    """Métricas de la función de IA."""

    total_sugerencias = serializers.IntegerField()
    por_estado = serializers.DictField(child=serializers.IntegerField())
    campos_aceptados = serializers.IntegerField()
    campos_corregidos = serializers.IntegerField()
    campos_descartados = serializers.IntegerField()
    tasa_aceptacion_sin_cambios = serializers.FloatField(allow_null=True)
    duracion_media_ms = serializers.FloatField(allow_null=True)
