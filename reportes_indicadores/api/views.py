"""
Vistas de la API REST del MOD-06.

Cada vista solo: (1) valida la entrada con un serializer, (2) llama al servicio y (3) serializa la salida.
No calcula ni lee datos (ADR-001, figura 1).

- RF-REP-002 · HU-MOD06-009 · CU-MOD06-009 · CP-TR-MOD06-009
- RF-REP-004 · HU-MOD06-002 · CU-MOD06-002
- RF-REP-006 · HU-MOD06-014 · CU-MOD06-014 · CP-TR-MOD06-014
- RF-IA-001 · HU-MOD06-010 · CU-MOD06-010 · CP-TR-MOD06-010 · RN-IA-001

Las vistas de reporte son de solo lectura (GET) y nunca escriben (CU-MOD06-001: «no se modifica ningún
registro operativo»).
"""

from __future__ import annotations

from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import OpenApiExample, OpenApiResponse, extend_schema
from rest_framework import status
from rest_framework.generics import GenericAPIView
from rest_framework.pagination import PageNumberPagination
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.views import APIView

from ..ia import extraccion_reportes
from ..permisos import (
    ROLES_CONSULTA_REPORTES,
    ROLES_SOLICITAR_IA,
    ROLES_VALIDAR_IA,
    PermisoPorRol,
    id_de_usuario,
)
from ..servicios import atenciones_espacio_sector, cumplimiento_corte, distribucion_personal
from . import serializers as s

TAG_REP_002 = "RF-REP-002 Cumplimiento del corte"
TAG_REP_004 = "RF-REP-004 Distribución del personal"
TAG_REP_006 = "RF-REP-006 Atenciones por espacio y sector"
TAG_IA_001 = "RF-IA-001 Extracción de informes con IA"

# --- Respuestas de error documentadas (sección 4.5) ----------------------------

R400 = OpenApiResponse(
    OpenApiTypes.OBJECT,
    description="Parámetros no válidos. Formato DRF por campo, p. ej. {\"hasta\": [\"La fecha de fin debe ser igual "
    "o posterior a la fecha de inicio\"]}, o {codigo: \"ParametroInvalido\", detalle}.",
)
R403 = OpenApiResponse(OpenApiTypes.OBJECT, description="Tu rol no tiene acceso a esta función.")
R404 = OpenApiResponse(s.ErrorSerializer, description="RecursoNoEncontrado.")
R409 = OpenApiResponse(s.ErrorSerializer, description="TransicionNoPermitida: la propuesta ya fue revisada.")
R415 = OpenApiResponse(s.ErrorSerializer, description="TipoArchivoNoAdmitido: el informe no es PDF.")
R422 = OpenApiResponse(s.ErrorSerializer, description="ConfiguracionFaltante: falta el formato estándar vigente.")
R503_DATOS = OpenApiResponse(
    s.ErrorSerializer, description="FuenteDatosNoDisponible: no se pudieron recuperar los datos; reintenta."
)
R503_IA = OpenApiResponse(
    s.ErrorSerializer,
    description="ServicioIANoDisponible o FuenteDatosNoDisponible: continúa con el registro manual o reintenta.",
)


class Paginacion25(PageNumberPagination):
    """Paginación de 25 elementos por página (Guía Web 5.5)."""

    page_size = 25


class VistaConRoles:
    """Aplica `PermisoPorRol(roles_permitidos)` a la vista."""

    roles_permitidos: frozenset[str] = frozenset()

    def get_permissions(self):
        return [PermisoPorRol(self.roles_permitidos)]


# =============================================================================
# Reportes (solo lectura)
# =============================================================================


class CumplimientoCorteView(VistaConRoles, APIView):
    roles_permitidos = ROLES_CONSULTA_REPORTES

    @extend_schema(
        tags=[TAG_REP_002],
        summary="Cumplimiento del corte de césped",
        description="Días entre ciclos frente a la frecuencia y días de ejecución frente a la duración máxima "
        "(HU-MOD06-009). Si faltan fechas o límites, se informa en `faltantes` y no se calcula el cumplimiento. "
        "Solo lectura.",
        parameters=[s.CumplimientoCorteQuerySerializer],
        responses={200: s.ResultadoCumplimientoCorteSerializer, 400: R400, 403: R403, 503: R503_DATOS},
        examples=[
            OpenApiExample(
                "Ciclo tolerado",
                response_only=True,
                value={
                    "periodo": {"desde": "2026-01-01", "hasta": "2026-10-20"},
                    "filtros_aplicados": {},
                    "generado_en": "2026-10-20T09:00:00-05:00",
                    "sin_datos": False,
                    "mensaje_sin_datos": None,
                    "indicador": {
                        "codigo": "RF-REP-002",
                        "nombre": "Cumplimiento del corte de césped",
                        "descripcion_calculo": "…",
                        "meta": None,
                        "unidad": "días",
                    },
                    "ciclos": [
                        {
                            "ciclo_id": 3,
                            "estado_ciclo": "Cerrado",
                            "proveedor_id": 1,
                            "proveedor_razon_social": "Terraverde Ingenieros SAC",
                            "ruta_id": 1,
                            "ruta_nombre": "Ruta de jardines 2026",
                            "estacion": "Verano",
                            "fecha_inicio_real": "2026-03-19",
                            "ciclo_anterior_id": 2,
                            "fecha_inicio_anterior": "2026-02-09",
                            "dias_entre_ciclos": 38,
                            "frecuencia_teorica_dias": 21,
                            "frecuencia_practica_dias": 30,
                            "frecuencia_maxima_dias": 45,
                            "estado_frecuencia": "Tolerado",
                            "fecha_fin_corte": "2026-03-21",
                            "dias_corte": 3,
                            "fecha_inicio_limpieza": "2026-03-22",
                            "fecha_fin_limpieza": "2026-03-22",
                            "dias_limpieza": 1,
                            "dias_total": 4,
                            "duracion_maxima_dias": 3,
                            "dias_limpieza_maximo": 1,
                            "maximo_total": 4,
                            "estado_duracion": "Dentro del máximo",
                            "faltantes": [],
                        }
                    ],
                    "resumen": {"total_ciclos": 1, "por_estado_frecuencia": {"Tolerado": 1}, "por_estado_duracion": {"Dentro del máximo": 1}},
                    "ultimo_ciclo": "(misma estructura que un elemento de ciclos)",
                },
            ),
            OpenApiExample(
                "Sin datos",
                response_only=True,
                value={
                    "periodo": {"desde": "2026-12-01", "hasta": "2026-12-31"},
                    "filtros_aplicados": {},
                    "generado_en": "2026-10-20T09:00:00-05:00",
                    "sin_datos": True,
                    "mensaje_sin_datos": "No hay ciclos de corte con fecha de inicio en el periodo elegido",
                    "indicador": None,
                    "ciclos": [],
                    "resumen": None,
                    "ultimo_ciclo": None,
                },
            ),
        ],
    )
    def get(self, request: Request) -> Response:
        entrada = s.CumplimientoCorteQuerySerializer(data=request.query_params)
        entrada.is_valid(raise_exception=True)
        resultado = cumplimiento_corte.calcular(entrada.periodo(), **entrada.filtros())
        return Response(s.ResultadoCumplimientoCorteSerializer(resultado).data)


class DistribucionPersonalView(VistaConRoles, APIView):
    roles_permitidos = ROLES_CONSULTA_REPORTES

    @extend_schema(
        tags=[TAG_REP_004],
        summary="Distribución del personal por cuadrilla, actividad, espacio y jornada",
        description="Filas jornada × cuadrilla × tipo de actividad × espacio, totales por cuadrilla y jornada, y "
        "cuadrillas «Sin parte aún». Marca el apoyo eventual (CU-MOD04-011 1a). No devuelve datos personales "
        "(RNF-008). Solo lectura; la exportación (RF-REP-009) queda fuera de este corte.",
        parameters=[s.DistribucionPersonalQuerySerializer],
        responses={200: s.ResultadoDistribucionPersonalSerializer, 400: R400, 403: R403, 503: R503_DATOS},
        examples=[
            OpenApiExample(
                "Fila con apoyo eventual",
                response_only=True,
                value={
                    "periodo": {"desde": "2026-10-13", "hasta": "2026-10-17"},
                    "filtros_aplicados": {},
                    "generado_en": "2026-10-20T09:00:00-05:00",
                    "sin_datos": False,
                    "mensaje_sin_datos": None,
                    "filas": [
                        {
                            "jornada": "2026-10-14",
                            "cuadrilla_id": 1,
                            "cuadrilla_nombre": "Cuadrilla 1",
                            "capataz_id": 2,
                            "capataz_nombre": "Andrés (ejemplo)",
                            "clase_actividad_id": 3,
                            "clase_actividad_nombre": "Mantenimiento de jardines",
                            "tipo_actividad_id": 17,
                            "tipo_actividad_nombre": "Limpieza de hojarasca",
                            "area_verde_id": 3,
                            "area_verde_nombre": "Arte Antiguo",
                            "numero_actividades": 1,
                            "operarios": [
                                {"operario_id": 10, "nombre_completo": "Operario 06 (ejemplo)", "es_apoyo": False},
                                {"operario_id": 14, "nombre_completo": "Operario 10 (ejemplo)", "es_apoyo": True},
                            ],
                            "numero_operarios": 2,
                            "tiempo_minutos": 120,
                            "horas_persona": 4.0,
                            "superficie_m2": 180.0,
                        }
                    ],
                    "totales_por_cuadrilla_jornada": [],
                    "cuadrillas_sin_parte": [
                        {"jornada": "2026-10-15", "cuadrilla_id": 3, "cuadrilla_nombre": "Cuadrilla 3", "estado": "Sin parte aún"}
                    ],
                    "resumen": {
                        "jornadas_con_parte": 5,
                        "partes": 14,
                        "actividades": 20,
                        "operarios_distintos": 25,
                        "horas_persona": 120.5,
                        "actividades_sin_tiempo": 1,
                    },
                },
            )
        ],
    )
    def get(self, request: Request) -> Response:
        entrada = s.DistribucionPersonalQuerySerializer(data=request.query_params)
        entrada.is_valid(raise_exception=True)
        resultado = distribucion_personal.calcular(entrada.periodo(), **entrada.filtros())
        return Response(s.ResultadoDistribucionPersonalSerializer(resultado).data)


class AtencionesEspacioSectorView(VistaConRoles, APIView):
    roles_permitidos = ROLES_CONSULTA_REPORTES

    @extend_schema(
        tags=[TAG_REP_006],
        summary="Atenciones por espacio y sector",
        description="Número de atenciones realizadas por sector (agrupador principal), espacio y cuartel (solo si "
        "está registrado). Cada evento cuenta una sola vez. «Sin sector» va siempre al final y los eventos sin "
        "espacio se informan aparte. Solo lectura.",
        parameters=[s.AtencionesEspacioSectorQuerySerializer],
        responses={200: s.ResultadoAtencionesEspacioSectorSerializer, 400: R400, 403: R403, 503: R503_DATOS},
        examples=[
            OpenApiExample(
                "Grupo sin sector",
                response_only=True,
                value={
                    "periodo": {"desde": "2026-09-01", "hasta": "2026-10-20"},
                    "filtros_aplicados": {},
                    "generado_en": "2026-10-20T09:00:00-05:00",
                    "sin_datos": False,
                    "mensaje_sin_datos": None,
                    "indicador": None,
                    "por_sector": [
                        {
                            "sector_id": None,
                            "sector_nombre": "Sin sector",
                            "atencion_a_demanda": None,
                            "capataz_vigente": None,
                            "total": 1,
                            "personal_propio": 0,
                            "tercerizado": 1,
                            "por_tipo": {"Poda de altura a demanda": 1},
                            "espacios": [
                                {
                                    "area_verde_id": 10,
                                    "area_verde_nombre": "Bosque Húmedo",
                                    "total": 1,
                                    "personal_propio": 0,
                                    "tercerizado": 1,
                                    "por_tipo": {"Poda de altura a demanda": 1},
                                }
                            ],
                        }
                    ],
                    "sin_espacio": {"total": 1, "personal_propio": 1, "tercerizado": 0, "por_tipo": {"Poda (Menor)": 1}},
                    "por_cuartel": [
                        {"cuartel_id": 1, "numero": 1, "nombre": "Cuartel 1", "total": 4, "personal_propio": 3, "tercerizado": 1}
                    ],
                    "resumen": {
                        "total_atenciones": 2,
                        "personal_propio": 1,
                        "tercerizado": 1,
                        "espacios_con_atenciones": 1,
                        "sectores_con_atenciones": 0,
                    },
                    "notas": [
                        "La agrupación usa el sector vigente del área; el modelo de datos no guarda el historial de "
                        "área a sector (Modelo de BD v1, decisión 8)"
                    ],
                },
            )
        ],
    )
    def get(self, request: Request) -> Response:
        entrada = s.AtencionesEspacioSectorQuerySerializer(data=request.query_params)
        entrada.is_valid(raise_exception=True)
        resultado = atenciones_espacio_sector.calcular(entrada.periodo(), **entrada.filtros())
        return Response(s.ResultadoAtencionesEspacioSectorSerializer(resultado).data)


# =============================================================================
# RF-IA-001
# =============================================================================

EJEMPLO_SUGERENCIA = {
    "id": 1,
    "tipo": "ExtraccionReporte",
    "entidad": "ReporteProveedor",
    "registro_id": 1,
    "archivo_id": 1,
    "contenido": {
        "version_contenido": 1,
        "entrada": {
            "reporte_proveedor_id": 1,
            "atencion_id": 4,
            "tipo_servicio_id": 2,
            "formato_id": 1,
            "formato_version": 1,
            "archivo_id": 1,
            "campos_solicitados": [{"campo_id": 1, "nombre_campo": "Metrado", "tipo_dato": "Decimal", "obligatorio": True}],
        },
        "ia": {
            "proveedor": "Gemini (Google AI Studio)",
            "modelo": "<GEMINI_MODEL>",
            "solicitado_en": "2026-10-20T09:00:00-05:00",
            "duracion_ms": 4200,
        },
        "propuesta": {"campo_1": {"nombre_campo": "Metrado", "valor": 1250.5}},
        "campos_obligatorios_faltantes": [],
        "decision": None,
    },
    "estado": "Propuesta",
    "etiqueta_estado": "Pendiente de revisión",
    "validador_id": None,
    "fecha_validacion": None,
    "fecha_creacion": "2026-10-20T09:00:04-05:00",
    "usuario_creacion_id": 1,
    "fecha_modificacion": "2026-10-20T09:00:04-05:00",
    "usuario_modificacion_id": 1,
}


class ExtraccionesView(VistaConRoles, GenericAPIView):
    roles_permitidos = ROLES_SOLICITAR_IA
    pagination_class = Paginacion25
    serializer_class = s.SugerenciaIASerializer

    @extend_schema(
        tags=[TAG_IA_001],
        summary="Listar propuestas de extracción con IA",
        description="Sugerencias de tipo ExtraccionReporte, de la más reciente a la más antigua, 25 por página.",
        parameters=[s.ExtraccionesQuerySerializer],
        responses={200: s.SugerenciaIASerializer(many=True), 400: R400, 403: R403, 503: R503_DATOS},
    )
    def get(self, request: Request) -> Response:
        entrada = s.ExtraccionesQuerySerializer(data=request.query_params)
        entrada.is_valid(raise_exception=True)
        sugerencias = extraccion_reportes.listar(**entrada.validated_data)
        pagina = self.paginate_queryset(sugerencias)
        return self.get_paginated_response(s.SugerenciaIASerializer(pagina, many=True).data)

    @extend_schema(
        tags=[TAG_IA_001],
        summary="Solicitar el análisis con IA del informe de un proveedor",
        description="Analiza el informe PDF adjunto al ReporteProveedor y crea una propuesta en estado Propuesta "
        "(«Pendiente de revisión»). Nada se incorpora al registro oficial sin validación humana (RN-IA-001). Si la "
        "IA no está disponible, responde 503 y no crea ninguna propuesta: continúa con el registro manual.",
        request=s.SolicitudExtraccionSerializer,
        responses={
            201: s.SugerenciaIASerializer,
            400: R400,
            403: R403,
            404: R404,
            415: R415,
            422: R422,
            503: R503_IA,
        },
        examples=[
            OpenApiExample("Solicitud", request_only=True, value={"reporte_proveedor_id": 1}),
            OpenApiExample("Propuesta creada", response_only=True, status_codes=["201"], value=EJEMPLO_SUGERENCIA),
        ],
    )
    def post(self, request: Request) -> Response:
        entrada = s.SolicitudExtraccionSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        sugerencia = extraccion_reportes.solicitar_extraccion(
            entrada.validated_data["reporte_proveedor_id"], id_de_usuario(request.user)
        )
        return Response(s.SugerenciaIASerializer(sugerencia).data, status=status.HTTP_201_CREATED)


class ExtraccionDetalleView(VistaConRoles, APIView):
    roles_permitidos = ROLES_SOLICITAR_IA

    @extend_schema(
        tags=[TAG_IA_001],
        summary="Detalle de una propuesta de extracción con IA",
        responses={200: s.SugerenciaIASerializer, 403: R403, 404: R404, 503: R503_DATOS},
        examples=[OpenApiExample("Propuesta", response_only=True, value=EJEMPLO_SUGERENCIA)],
    )
    def get(self, request: Request, sugerencia_id: int) -> Response:
        return Response(s.SugerenciaIASerializer(extraccion_reportes.obtener(sugerencia_id)).data)


class ExtraccionValidarView(VistaConRoles, APIView):
    roles_permitidos = ROLES_VALIDAR_IA

    @extend_schema(
        tags=[TAG_IA_001],
        summary="Validar campo por campo una propuesta de IA",
        description="Una decisión por campo: aceptar, corregir (con valor_final) o descartar. Si todo se descarta, "
        "la propuesta queda Rechazada; si no, Validada. Solo desde Propuesta (RI-32).",
        request=s.ValidacionSerializer,
        responses={200: s.SugerenciaIASerializer, 400: R400, 403: R403, 404: R404, 409: R409, 503: R503_DATOS},
        examples=[
            OpenApiExample(
                "Decisiones",
                request_only=True,
                value={
                    "decisiones": {
                        "campo_1": {"accion": "aceptar"},
                        "campo_2": {"accion": "corregir", "valor_final": [{"producto": "…", "dosis": "…"}]},
                        "campo_3": {"accion": "descartar"},
                    }
                },
            )
        ],
    )
    def post(self, request: Request, sugerencia_id: int) -> Response:
        entrada = s.ValidacionSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        sugerencia = extraccion_reportes.validar(
            sugerencia_id, id_de_usuario(request.user), entrada.validated_data["decisiones"]
        )
        return Response(s.SugerenciaIASerializer(sugerencia).data)


class ExtraccionRechazarView(VistaConRoles, APIView):
    roles_permitidos = ROLES_VALIDAR_IA

    @extend_schema(
        tags=[TAG_IA_001],
        summary="Rechazar una propuesta de IA completa",
        description="Solo desde Propuesta. Todos los campos quedan descartados y se guarda el motivo.",
        request=s.RechazoSerializer,
        responses={200: s.SugerenciaIASerializer, 400: R400, 403: R403, 404: R404, 409: R409, 503: R503_DATOS},
        examples=[OpenApiExample("Motivo", request_only=True, value={"motivo": "El informe no corresponde a la atención"})],
    )
    def post(self, request: Request, sugerencia_id: int) -> Response:
        entrada = s.RechazoSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        sugerencia = extraccion_reportes.rechazar(
            sugerencia_id, id_de_usuario(request.user), entrada.validated_data.get("motivo")
        )
        return Response(s.SugerenciaIASerializer(sugerencia).data)


class ExtraccionMetricasView(VistaConRoles, APIView):
    roles_permitidos = ROLES_VALIDAR_IA

    @extend_schema(
        tags=[TAG_IA_001],
        summary="Métricas de la función de extracción con IA",
        responses={200: s.MetricasIASerializer, 403: R403, 503: R503_DATOS},
        examples=[
            OpenApiExample(
                "Métricas",
                response_only=True,
                value={
                    "total_sugerencias": 4,
                    "por_estado": {"Propuesta": 1, "Validada": 2, "Rechazada": 1},
                    "campos_aceptados": 6,
                    "campos_corregidos": 2,
                    "campos_descartados": 7,
                    "tasa_aceptacion_sin_cambios": 0.4,
                    "duracion_media_ms": 3850.5,
                },
            )
        ],
    )
    def get(self, request: Request) -> Response:
        return Response(s.MetricasIASerializer(extraccion_reportes.metricas()).data)
