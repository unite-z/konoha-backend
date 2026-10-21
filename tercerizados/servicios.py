"""Casos de uso MOD-05. Las mutaciones son atómicas y preservan historial."""
import logging
import uuid
from pathlib import Path
from django.db import transaction
from django.db.models import Max
from django.forms.models import model_to_dict
from django.utils import timezone
from . import models as m, repositorios as repo, archivos
from .errores import ErrorDominio, Conflicto, AlmacenamientoNoDisponible

logger = logging.getLogger(__name__)
E = m.AtencionTercerizada.Estado


def snapshot(objeto):
    # JSON serializable sin perder IDs ni valores nulos.
    import json
    from django.core.serializers.json import DjangoJSONEncoder
    return json.loads(json.dumps(model_to_dict(objeto), cls=DjangoJSONEncoder))


def auditar(objeto, usuario, accion, anteriores=None, motivo=''):
    m.RegistroAuditoria.objects.create(entidad=type(objeto).__name__, registro_id=objeto.pk, usuario=usuario,
        accion=accion, valores_anteriores=anteriores or {}, valores_nuevos=snapshot(objeto), motivo=motivo)


def guardar(atencion, usuario, anteriores, motivo=''):
    atencion.usuario_modificacion = usuario
    atencion.save()
    auditar(atencion, usuario, 'Modificacion', anteriores, motivo)
    if anteriores.get('estado', '') != atencion.estado:
        m.HistorialEstado.objects.create(atencion=atencion, estado_anterior=anteriores.get('estado', ''),
            estado_nuevo=atencion.estado, usuario=usuario)
    return atencion


def exigir_estado(atencion, *estados):
    if atencion.estado not in estados:
        raise Conflicto(f'Operación no permitida desde {atencion.estado}')


def resolver_unidad(origen, unidad_id=None, nombre=None, usuario=None):
    if origen != m.AtencionTercerizada.Origen.UNIDAD:
        if unidad_id or nombre:
            raise ErrorDominio('Solo SolicitudUnidad admite una unidad solicitante')
        return None
    if bool(unidad_id) == bool(nombre):
        raise ErrorDominio('Indica una unidad existente o un nombre de unidad provisional')
    if unidad_id:
        unidad = repo.obtener(m.UnidadInstitucional, unidad_id)
        if not unidad.activo:
            raise ErrorDominio('La unidad solicitante está inactiva')
        return unidad
    unidad, creada = m.UnidadInstitucional.objects.get_or_create(nombre=nombre.strip(), defaults={'provisional': True})
    if not unidad.activo:
        raise ErrorDominio('La unidad solicitante está inactiva')
    if creada:
        auditar(unidad, usuario, 'Alta')
    return unidad


@transaction.atomic
def crear(datos, usuario):
    datos = dict(datos)
    contrato = repo.obtener(m.Contrato, datos.pop('contrato_id'), bloquear=True)
    hoy = timezone.localdate()
    if not contrato.vigencia_inicio <= hoy <= contrato.vigencia_fin:
        raise ErrorDominio('El contrato no está vigente en la fecha de registro')
    if not contrato.proveedor.activo:
        raise ErrorDominio('El proveedor está inactivo')
    if not contrato.tipo_servicio.activo:
        raise ErrorDominio('El tipo de servicio está inactivo')
    datos['es_riesgo'] = datos.get('es_riesgo', False) or contrato.tipo_servicio.es_riesgo
    if contrato.tipo_servicio.ejecutor != 'Tercerizado':
        raise ErrorDominio('El contrato debe corresponder a un servicio tercerizado')
    alcances = datos.pop('alcances')
    if not alcances:
        raise ErrorDominio('Indica al menos un espacio o ejemplar')
    unidad = resolver_unidad(datos['origen'], datos.pop('unidad_solicitante_id', None), datos.pop('unidad_provisional', None), usuario)
    destinos = set()
    for alcance in alcances:
        area, ejemplar = alcance.get('area_verde_id'), alcance.get('ejemplar_id')
        if bool(area) == bool(ejemplar):
            raise ErrorDominio('Cada alcance debe indicar solo un área verde o un ejemplar')
        destino = ('area', area) if area else ('ejemplar', ejemplar)
        if destino in destinos:
            raise ErrorDominio('No repitas un espacio o ejemplar en la misma atención')
        destinos.add(destino)
        objetivo = repo.obtener(m.AreaVerde if area else m.Ejemplar, area or ejemplar)
        if area and not objetivo.activo:
            raise ErrorDominio('El área verde está inactiva')
        if (alcance.get('metrado') is None) != (alcance.get('unidad_medida_id') is None):
            raise ErrorDominio('Metrado y unidad de medida deben registrarse juntos')
        if alcance.get('unidad_medida_id'):
            repo.obtener(m.UnidadMedida, alcance['unidad_medida_id'])
    atencion = m.AtencionTercerizada.objects.create(**datos, contrato=contrato, tipo_servicio=contrato.tipo_servicio,
        unidad_solicitante=unidad, solicitado_por=usuario, usuario_creacion=usuario, usuario_modificacion=usuario)
    for alcance in alcances:
        registro = m.AlcanceAtencion.objects.create(atencion=atencion, **alcance)
        auditar(registro, usuario, 'Alta')
    auditar(atencion, usuario, 'Alta')
    m.HistorialEstado.objects.create(atencion=atencion, estado_nuevo=atencion.estado, usuario=usuario)
    return atencion


@transaction.atomic
def modificar(atencion_id, operacion, datos, usuario):
    atencion = repo.obtener(m.AtencionTercerizada, atencion_id, bloquear=True)
    antes = snapshot(atencion)
    motivo = datos.get('motivo', '')
    if operacion == 'orden-compra':
        exigir_estado(atencion, E.REQUERIMIENTO, E.OC)
        if atencion.numero_orden_compra and not motivo:
            raise ErrorDominio('Indica el motivo de la corrección de la OC')
        atencion.numero_orden_compra = datos['numero_orden_compra']
        atencion.fecha_orden_compra = timezone.now()
        atencion.estado = E.OC
    elif operacion == 'inicio':
        exigir_estado(atencion, E.OC)
        if atencion.es_riesgo:
            raise Conflicto('Atención de riesgo: falta integrar la validación de seguridad; no se permite iniciar')
        atencion.fecha_hora_inicio = datos['fecha_hora_inicio']
        atencion.estado = E.EJECUCION
    elif operacion == 'fin':
        exigir_estado(atencion, E.EJECUCION)
        if datos['fecha_hora_fin'] < atencion.fecha_hora_inicio:
            raise ErrorDominio('La fecha de fin no puede ser anterior al inicio')
        atencion.fecha_hora_fin = datos['fecha_hora_fin']
        atencion.estado = E.EJECUTADO
    elif operacion == 'origen':
        exigir_estado(atencion, E.REQUERIMIENTO, E.OC)
        atencion.unidad_solicitante = resolver_unidad(datos['origen'], datos.get('unidad_solicitante_id'), datos.get('unidad_provisional'), usuario)
        atencion.origen = datos['origen']
    elif operacion == 'acuerdo-unidad':
        exigir_estado(atencion, E.EJECUTADO, E.REPORTE)
        if atencion.origen != m.AtencionTercerizada.Origen.UNIDAD:
            raise ErrorDominio('El acuerdo corresponde únicamente a solicitudes de unidad')
        if datos['acuerdo_unidad'] is not None and datos['fecha_acuerdo_unidad'] < timezone.localdate(atencion.fecha_hora_fin):
            raise ErrorDominio('El acuerdo no puede ser anterior a la finalización')
        for campo in ('acuerdo_unidad', 'fecha_acuerdo_unidad', 'comentarios_acuerdo_unidad'):
            setattr(atencion, campo, datos[campo])
    else:
        raise ErrorDominio('Operación desconocida')
    return guardar(atencion, usuario, antes, motivo)


def adjuntar(atencion_id, datos, usuario):
    guardados = []
    storage = archivos.almacenamiento()
    try:
        with transaction.atomic():
            atencion = repo.obtener(m.AtencionTercerizada, atencion_id, bloquear=True)
            exigir_estado(atencion, E.EJECUTADO, E.REPORTE)
            if datos['fecha_recepcion'] < timezone.localdate(atencion.fecha_hora_fin):
                raise ErrorDominio('La recepción del informe final no puede preceder al fin del servicio')
            documentos = datos['documentos']
            ficheros = datos['archivos']
            if len(documentos) != len(ficheros):
                raise ErrorDominio('Debe existir un archivo por cada entrada de documentos, en el mismo orden')
            if not any(d['categoria'] == m.ArchivoAsociado.Categoria.INFORME for d in documentos):
                raise ErrorDominio('Cada revisión debe incluir un informe final')
            metadatos = [archivos.validar(fichero) for fichero in ficheros]
            for doc in documentos:
                if doc.get('alcance_id'):
                    alcance = repo.obtener(m.AlcanceAtencion, doc['alcance_id'])
                    if alcance.atencion_id != atencion.pk:
                        raise ErrorDominio('La evidencia debe corresponder a un alcance de esta atención')
                if doc['categoria'] == m.ArchivoAsociado.Categoria.EVIDENCIA and not doc.get('alcance_id'):
                    raise ErrorDominio('La evidencia por espacio requiere indicar su alcance')
            revision = (atencion.reportes.aggregate(valor=Max('revision'))['valor'] or 0) + 1
            reporte = m.ReporteProveedor.objects.create(atencion=atencion, revision=revision,
                fecha_recepcion=datos['fecha_recepcion'], resumen=datos.get('resumen', ''),
                usuario_creacion=usuario, usuario_modificacion=usuario)
            for doc, fichero, meta in zip(documentos, ficheros, metadatos):
                ruta = f'reportes/{reporte.pk}/{uuid.uuid4().hex}{Path(meta["nombre_original"]).suffix.lower()}'
                try:
                    nombre = storage.save(ruta, fichero)
                except Exception:
                    # El backend puede haber escrito antes de fallar.
                    guardados.append(ruta)
                    raise AlmacenamientoNoDisponible('No se pudo almacenar el reporte. Reintenta la carga')
                guardados.append(nombre)
                archivo = m.Archivo.objects.create(**meta, ruta_almacenamiento=nombre)
                asociado = m.ArchivoAsociado.objects.create(reporte=reporte, archivo=archivo, **doc)
                auditar(asociado, usuario, 'Alta')
            auditar(reporte, usuario, 'Alta')
            antes = snapshot(atencion)
            atencion.estado = E.REPORTE
            guardar(atencion, usuario, antes)
        return reporte
    except Exception as exc:
        for ruta in guardados:
            try:
                storage.delete(ruta)
            except Exception:
                logger.exception('No se pudo limpiar archivo huérfano: %s', ruta)
        if isinstance(exc, OSError):
            raise AlmacenamientoNoDisponible('No se pudo almacenar el reporte. Reintenta la carga') from exc
        raise
