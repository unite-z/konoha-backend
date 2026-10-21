from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from datetime import timedelta
import pytest
from django.contrib.auth import get_user_model
from django.db import connections, close_old_connections
from django.core.files.uploadedfile import SimpleUploadedFile
from django.utils import timezone
from tercerizados import servicios, models as m
from tercerizados.errores import Conflicto

pytestmark = pytest.mark.django_db(transaction=True)


def simultaneos(usuario_id, operacion):
    barrera = Barrier(2)
    def ejecutar(_):
        close_old_connections()
        try:
            usuario = get_user_model().objects.get(pk=usuario_id)
            barrera.wait(timeout=10)
            return operacion(usuario)
        finally:
            connections.close_all()
    with ThreadPoolExecutor(max_workers=2) as pool:
        return list(pool.map(ejecutar, range(2)))


def test_inicio_concurrente_unica_transicion(usuario, entrada):
    a = servicios.crear(entrada, usuario)
    servicios.modificar(a.pk, 'orden-compra', {'numero_orden_compra': 'OC-X'}, usuario)
    def iniciar(user):
        try:
            servicios.modificar(a.pk, 'inicio', {'fecha_hora_inicio': timezone.now()}, user)
            return 'ok'
        except Conflicto:
            return 'conflicto'
    assert sorted(simultaneos(usuario.pk, iniciar)) == ['conflicto', 'ok']
    assert a.historial.filter(estado_nuevo='En ejecución').count() == 1


def test_reportes_concurrentes_revision_unica(usuario, entrada, almacenamiento_privado):
    a = servicios.crear(entrada, usuario)
    servicios.modificar(a.pk, 'orden-compra', {'numero_orden_compra': 'OC-X'}, usuario)
    servicios.modificar(a.pk, 'inicio', {'fecha_hora_inicio': timezone.now()-timedelta(hours=2)}, usuario)
    servicios.modificar(a.pk, 'fin', {'fecha_hora_fin': timezone.now()-timedelta(hours=1)}, usuario)
    def adjuntar(user):
        return servicios.adjuntar(a.pk, {
            'fecha_recepcion': timezone.localdate(), 'documentos': [{'categoria': 'InformeFinal'}],
            'archivos': [SimpleUploadedFile('informe.pdf', b'%PDF-1.7\nreporte')],
        }, user).revision
    assert sorted(simultaneos(usuario.pk, adjuntar)) == [1, 2]
    assert m.ReporteProveedor.objects.count() == 2
    assert a.historial.filter(estado_nuevo='Reporte adjunto').count() == 1
