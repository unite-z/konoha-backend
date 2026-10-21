"""Acceso ORM; los bloqueos se adquieren dentro de las transacciones de servicios."""
from django.db.models import Prefetch
from . import filtros as f, models as m
from .errores import NoEncontrado

BUSQUEDA_ATENCIONES = ('codigo', 'descripcion', 'numero_orden_compra', 'contrato__numero',
                       'contrato__proveedor__razon_social', 'tipo_servicio__nombre', 'unidad_solicitante__nombre')

FILTROS_ATENCIONES = (
    f.filtro_opcion('estado', m.AtencionTercerizada.Estado.values, 'Estado de la atención'),
    f.filtro_opcion('origen', m.AtencionTercerizada.Origen.values, 'Origen de la atención'),
    f.filtro_entero('contrato_id', 'Identificador del contrato'),
)


def obtener(modelo, identificador, *, bloquear=False):
    consulta = modelo.objects.select_for_update() if bloquear else modelo.objects
    try:
        return consulta.get(pk=identificador)
    except modelo.DoesNotExist:
        raise NoEncontrado(f'No existe {modelo.__name__} con id {identificador}')


def _atenciones():
    """Atenciones con lo necesario para serializar nombres sin consultas adicionales."""
    return m.AtencionTercerizada.objects.select_related(
        'contrato__proveedor', 'tipo_servicio', 'unidad_solicitante', 'solicitado_por', 'usuario_creacion',
    ).prefetch_related(
        Prefetch('alcances', queryset=m.AlcanceAtencion.objects.select_related(
            'area_verde', 'ejemplar__area_verde', 'unidad_medida').order_by('pk')),
        Prefetch('historial', queryset=m.HistorialEstado.objects.select_related('usuario').order_by('fecha_hora', 'pk')),
    )


def atenciones(parametros=None):
    consulta = _atenciones()
    if parametros is not None:
        consulta = f.filtrar(consulta, parametros, FILTROS_ATENCIONES, BUSQUEDA_ATENCIONES)
    return consulta


def atencion(identificador):
    try:
        return _atenciones().get(pk=identificador)
    except m.AtencionTercerizada.DoesNotExist:
        raise NoEncontrado(f'No existe AtencionTercerizada con id {identificador}')


def reportes(atencion_id):
    obtener(m.AtencionTercerizada, atencion_id)
    return m.ReporteProveedor.objects.filter(atencion_id=atencion_id).select_related('usuario_creacion').prefetch_related(
        Prefetch('documentos', queryset=m.ArchivoAsociado.objects.select_related('archivo').order_by('pk')))


def reporte(atencion_id, reporte_id):
    try:
        return reportes(atencion_id).get(pk=reporte_id)
    except m.ReporteProveedor.DoesNotExist:
        raise NoEncontrado('El reporte no pertenece a esta atención')


def documento(atencion_id, reporte_id, documento_id):
    actual = reporte(atencion_id, reporte_id)
    try:
        return actual.documentos.select_related('archivo').get(pk=documento_id)
    except m.ArchivoAsociado.DoesNotExist:
        raise NoEncontrado('El documento no pertenece a este reporte')
