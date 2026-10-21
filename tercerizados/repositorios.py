"""Acceso ORM; los bloqueos se adquieren dentro de las transacciones de servicios."""
from . import models as m
from .errores import NoEncontrado


def obtener(modelo, identificador, *, bloquear=False):
    consulta = modelo.objects.select_for_update() if bloquear else modelo.objects
    try:
        return consulta.get(pk=identificador)
    except modelo.DoesNotExist:
        raise NoEncontrado(f'No existe {modelo.__name__} con id {identificador}')


def atenciones():
    return m.AtencionTercerizada.objects.select_related('contrato', 'tipo_servicio', 'unidad_solicitante').prefetch_related('alcances', 'historial')


def reportes(atencion_id):
    obtener(m.AtencionTercerizada, atencion_id)
    return m.ReporteProveedor.objects.filter(atencion_id=atencion_id).prefetch_related('documentos__archivo')


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
