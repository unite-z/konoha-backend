import base64
import copy
import json
from datetime import timedelta
from unittest.mock import patch
import pytest
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.core.files.uploadedfile import SimpleUploadedFile
from django.db import transaction, IntegrityError, DatabaseError
from django.utils import timezone
from rest_framework.test import APIClient
from tercerizados import models as m, servicios
from tercerizados.permisos import ROLES

pytestmark = pytest.mark.django_db
BASE = '/api/tercerizados/atenciones/'


def crear(cliente, entrada):
    response = cliente.post(BASE, entrada, format='json')
    assert response.status_code == 201, response.data
    return response.data


def ejecutar(cliente, identificador):
    url = f'{BASE}{identificador}/'
    assert cliente.patch(url+'orden-compra/', {'numero_orden_compra': 'OC-001'}, format='json').status_code == 200
    inicio = timezone.now()-timedelta(hours=2)
    assert cliente.post(url+'inicio/', {'fecha_hora_inicio': inicio.isoformat()}, format='json').status_code == 200
    assert cliente.post(url+'fin/', {'fecha_hora_fin': (inicio+timedelta(hours=1)).isoformat()}, format='json').status_code == 200


def reporte(cliente, identificador, **cambios):
    data = {'fecha_recepcion': timezone.localdate().isoformat(), 'documentos': json.dumps([{'categoria': 'InformeFinal'}]),
            'archivos': [SimpleUploadedFile('informe.pdf', b'%PDF-1.7\ncontenido de prueba', content_type='application/pdf')]}
    data.update(cambios)
    return cliente.post(f'{BASE}{identificador}/reportes/', data, format='multipart')


def test_flujo_documentos_revision_descarga(cliente, entrada, almacenamiento_privado):
    atencion = crear(cliente, entrada)
    assert len(atencion['alcances']) == 2
    ejecutar(cliente, atencion['id'])
    primero = reporte(cliente, atencion['id'])
    assert primero.status_code == 201, primero.data
    segundo = reporte(cliente, atencion['id'])
    assert segundo.status_code == 201, segundo.data
    assert segundo.data['revision'] == 2
    assert primero.data['revision'] == 1
    assert m.Archivo.objects.count() == 2
    documento = primero.data['documentos'][0]
    assert 'ruta_almacenamiento' not in documento['archivo']
    url = f'{BASE}{atencion["id"]}/reportes/{primero.data["id"]}/documentos/{documento["id"]}/descarga/'
    response = cliente.get(url)
    assert response.status_code == 200
    assert b''.join(response.streaming_content).startswith(b'%PDF-')
    assert APIClient().get(url).status_code == 403
    assert cliente.get(f'{BASE}{atencion["id"]}/').data['estado'] == 'Reporte adjunto'
    assert m.HistorialEstado.objects.count() == 5
    assert cliente.get(f'{BASE}{atencion["id"]}/reportes/').data['count'] == 2


@pytest.mark.parametrize('cambio', [
    {'alcances': []}, {'alcances': [{}]}, {'alcances': [{'area_verde_id': 999999}]},
    {'origen': ''}, {'descripcion': ''}, {'origen': 'SolicitudUnidad'},
    {'origen': 'NoValido'}, {'tipo_servicio_id': 1}, {'estado': 'Reporte adjunto'},
])
def test_registro_invalido_no_deja_datos(cliente, entrada, cambio):
    entrada.update(cambio)
    response = cliente.post(BASE, entrada, format='json')
    assert response.status_code in (400, 404)
    assert m.AtencionTercerizada.objects.count() == 0
    assert m.AlcanceAtencion.objects.count() == 0
    assert m.RegistroAuditoria.objects.count() == 0


@pytest.mark.parametrize('caso', ['duplicado', 'ambos', 'metrado_sin_unidad', 'unidad_sin_metrado', 'negativo'])
def test_alcances_invalidos(cliente, entrada, referencias, caso):
    _, area, ejemplar, unidad = referencias
    cambios = {
        'duplicado': [{'area_verde_id': area.pk}]*2,
        'ambos': [{'area_verde_id': area.pk, 'ejemplar_id': ejemplar.pk}],
        'metrado_sin_unidad': [{'area_verde_id': area.pk, 'metrado': '10'}],
        'unidad_sin_metrado': [{'area_verde_id': area.pk, 'unidad_medida_id': unidad.pk}],
        'negativo': [{'area_verde_id': area.pk, 'metrado': '-1', 'unidad_medida_id': unidad.pk}],
    }
    entrada['alcances'] = cambios[caso]
    assert cliente.post(BASE, entrada, format='json').status_code == 400
    assert not m.AtencionTercerizada.objects.exists()


@pytest.mark.parametrize('dias', [-20, 20])
def test_contrato_no_vigente(cliente, entrada, referencias, dias):
    contrato = referencias[0]
    contrato.vigencia_inicio = timezone.localdate()+timedelta(days=dias)
    contrato.vigencia_fin = contrato.vigencia_inicio+timedelta(days=1)
    contrato.save()
    assert cliente.post(BASE, entrada, format='json').status_code == 400


def test_transiciones_y_correccion_oc(cliente, entrada):
    a = crear(cliente, entrada)
    url = f'{BASE}{a["id"]}/'
    ahora = timezone.now().isoformat()
    assert cliente.post(url+'inicio/', {'fecha_hora_inicio': ahora}, format='json').status_code == 409
    assert cliente.patch(url+'orden-compra/', {'numero_orden_compra': '  '}, format='json').status_code == 400
    assert cliente.patch(url+'orden-compra/', {'numero_orden_compra': 'OC-1'}, format='json').status_code == 200
    assert cliente.patch(url+'orden-compra/', {'numero_orden_compra': 'OC-2'}, format='json').status_code == 400
    assert cliente.patch(url+'orden-compra/', {'numero_orden_compra': 'OC-2', 'motivo': 'Error de digitación'}, format='json').status_code == 200
    assert cliente.post(url+'inicio/', {'fecha_hora_inicio': ahora}, format='json').status_code == 200
    assert cliente.post(url+'inicio/', {'fecha_hora_inicio': ahora}, format='json').status_code == 409
    assert cliente.post(url+'fin/', {'fecha_hora_fin': '2000-01-01T12:00:00Z'}, format='json').status_code == 400
    assert cliente.patch(url+'origen/', {'origen': 'ProgramacionEstacional', 'motivo': 'Cambio'}, format='json').status_code == 409
    assert cliente.patch(url+'orden-compra/', {'numero_orden_compra': 'OC-3', 'motivo': 'Cambio'}, format='json').status_code == 409


def test_riesgo_no_se_omite(cliente, entrada, referencias):
    servicio = referencias[0].tipo_servicio
    servicio.es_riesgo = True
    servicio.save()
    entrada['es_riesgo'] = False
    a = crear(cliente, entrada)
    assert a['es_riesgo'] is True
    url = f'{BASE}{a["id"]}/'
    cliente.patch(url+'orden-compra/', {'numero_orden_compra': 'OC-1'}, format='json')
    assert cliente.post(url+'inicio/', {'fecha_hora_inicio': timezone.now().isoformat()}, format='json').status_code == 409


def test_origen_acuerdo_y_auditoria(cliente, entrada):
    a = crear(cliente, entrada)
    url = f'{BASE}{a["id"]}/'
    assert cliente.patch(url+'origen/', {'origen': 'SolicitudUnidad', 'unidad_provisional': 'Facultad X', 'motivo': 'Solicitud recibida'}, format='json').status_code == 200
    assert m.UnidadInstitucional.objects.get(nombre='Facultad X').provisional
    assert cliente.patch(url+'acuerdo-unidad/', {'acuerdo_unidad': True, 'fecha_acuerdo_unidad': timezone.localdate()}, format='json').status_code == 409
    ejecutar(cliente, a['id'])
    for valor in (True, False, None):
        respuesta = cliente.patch(url+'acuerdo-unidad/', {'acuerdo_unidad': valor, 'fecha_acuerdo_unidad': timezone.localdate() if valor is not None else None, 'comentarios_acuerdo_unidad': 'Respuesta'}, format='json')
        assert respuesta.status_code == 200, respuesta.data
        assert respuesta.data['acuerdo_unidad'] is valor
        assert respuesta.data['estado'] == 'Ejecutado pendiente de reporte'
    auditoria = m.RegistroAuditoria.objects.filter(accion='Modificacion', motivo='Solicitud recibida').get()
    assert auditoria.valores_anteriores['origen'] == 'Demanda'
    assert auditoria.valores_nuevos['origen'] == 'SolicitudUnidad'


@pytest.mark.parametrize('rol,permitido', [(ROLES[0], True), (ROLES[1], False), (ROLES[2], False), ('Capataz', False), ('Practicante', False)])
def test_roles(cliente, entrada, usuario, rol, permitido):
    a = crear(cliente, entrada)
    usuario.groups.clear()
    usuario.groups.add(Group.objects.get_or_create(name=rol)[0])
    response = cliente.post(BASE, entrada, format='json')
    assert response.status_code == (201 if permitido else 403)
    consulta = cliente.get(BASE)
    assert consulta.status_code == (200 if rol in ROLES else 403)
    orden = cliente.patch(f'{BASE}{a["id"]}/orden-compra/', {'numero_orden_compra': 'OC-1'}, format='json')
    assert orden.status_code == (200 if rol in ROLES else 403)
    assert cliente.patch(f'{BASE}{a["id"]}/origen/', {'origen': 'Demanda', 'motivo': 'Corrección'}, format='json').status_code == (200 if permitido else 403)


def test_auth_real_y_anonimo(usuario):
    c = APIClient()
    assert c.get(BASE).status_code == 403
    token = base64.b64encode(b'jefe:prueba-local').decode()
    c.credentials(HTTP_AUTHORIZATION=f'Basic {token}')
    assert c.get(BASE).status_code == 200
    usuario.is_active = False
    usuario.save()
    assert c.get(BASE).status_code == 403


def test_carga_invalida_y_limpieza(cliente, entrada, almacenamiento_privado, settings):
    a = crear(cliente, entrada)
    assert reporte(cliente, a['id']).status_code == 409
    ejecutar(cliente, a['id'])
    assert reporte(cliente, a['id'], documentos='[]').status_code == 400
    assert reporte(cliente, a['id'], documentos=json.dumps([{'categoria': 'RutaRecorrida'}])).status_code == 400
    assert reporte(cliente, a['id'], archivos=[SimpleUploadedFile('falso.pdf', b'No es PDF')]).status_code == 400
    settings.TERCERIZADOS_MAX_ARCHIVO_BYTES = 2
    assert reporte(cliente, a['id']).status_code == 400
    settings.TERCERIZADOS_MAX_ARCHIVO_BYTES = 10000
    real_save = almacenamiento_privado.save
    llamadas = []
    def falla(nombre, contenido, **kwargs):
        if llamadas:
            raise OSError('Disco lleno')
        resultado = real_save(nombre, contenido, **kwargs)
        llamadas.append(resultado)
        return resultado
    with patch.object(almacenamiento_privado, 'save', side_effect=falla):
        response = reporte(cliente, a['id'], documentos=json.dumps([{'categoria': 'InformeFinal'}, {'categoria': 'Metrado'}]),
            archivos=[SimpleUploadedFile('a.pdf', b'%PDF-1.7 uno'), SimpleUploadedFile('b.pdf', b'%PDF-1.7 dos')])
    assert response.status_code == 503, response.data
    assert llamadas and not almacenamiento_privado.exists(llamadas[0])
    assert not m.ReporteProveedor.objects.exists()
    assert not m.Archivo.objects.exists()
    assert m.AtencionTercerizada.objects.get(pk=a['id']).estado == 'Ejecutado pendiente de reporte'


def test_evidencia_de_otra_atencion(cliente, entrada, almacenamiento_privado):
    a, b = crear(cliente, entrada), crear(cliente, entrada)
    ejecutar(cliente, a['id'])
    docs = [{'categoria': 'InformeFinal', 'alcance_id': b['alcances'][0]['id']}]
    assert reporte(cliente, a['id'], documentos=json.dumps(docs)).status_code == 400
    r = reporte(cliente, a['id'])
    assert r.status_code == 201
    assert cliente.get(f'{BASE}{b["id"]}/reportes/{r.data["id"]}/').status_code == 404


def test_restricciones_bd_y_historial_inmutable(cliente, entrada, referencias):
    a = crear(cliente, entrada)
    with pytest.raises(IntegrityError), transaction.atomic():
        m.AlcanceAtencion.objects.create(atencion_id=a['id'])
    with pytest.raises(IntegrityError), transaction.atomic():
        m.AlcanceAtencion.objects.create(atencion_id=a['id'], area_verde=referencias[1])
    with pytest.raises(DatabaseError), transaction.atomic():
        m.RegistroAuditoria.objects.update(motivo='Sobrescribir')
    with pytest.raises(DatabaseError), transaction.atomic():
        m.HistorialEstado.objects.all().delete()


def test_reversion_completa(cliente, entrada):
    with patch('tercerizados.servicios.auditar', side_effect=RuntimeError('fallo simulado')):
        with pytest.raises(RuntimeError):
            crear(cliente, entrada)
    assert not m.AtencionTercerizada.objects.exists()
    assert not m.AlcanceAtencion.objects.exists()
