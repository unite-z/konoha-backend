from datetime import timedelta
import pytest
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.utils import timezone
from rest_framework.test import APIClient
from tercerizados import models as m
from tercerizados.permisos import ROLES


@pytest.fixture
def usuario(db):
    user = get_user_model().objects.create_user(username='jefe', password='prueba-local')
    user.groups.add(Group.objects.create(name=ROLES[0]))
    return user


@pytest.fixture
def cliente(usuario):
    client = APIClient()
    client.force_authenticate(usuario)
    return client


@pytest.fixture
def referencias(db):
    proveedor = m.Proveedor.objects.create(razon_social='Proveedor de prueba')
    servicio = m.TipoServicio.objects.create(nombre='Corte', ejecutor='Tercerizado')
    contrato = m.Contrato.objects.create(numero='CT-TEST', proveedor=proveedor, tipo_servicio=servicio,
        vigencia_inicio=timezone.localdate()-timedelta(days=10), vigencia_fin=timezone.localdate()+timedelta(days=10))
    area = m.AreaVerde.objects.create(nombre='Jardín A')
    ejemplar = m.Ejemplar.objects.create(codigo='E-01', area_verde=area)
    unidad = m.UnidadMedida.objects.create(nombre='Metros cuadrados', simbolo='m2')
    return contrato, area, ejemplar, unidad


@pytest.fixture
def entrada(referencias):
    contrato, area, ejemplar, unidad = referencias
    return {'contrato_id': contrato.pk, 'descripcion': 'Mantenimiento', 'origen': 'Demanda',
            'alcances': [{'area_verde_id': area.pk}, {'ejemplar_id': ejemplar.pk}]}


@pytest.fixture
def almacenamiento_privado(settings, tmp_path):
    from django.core.files.storage import storages
    settings.STORAGES = {**settings.STORAGES, 'tercerizados': {
        'BACKEND': 'django.core.files.storage.FileSystemStorage', 'OPTIONS': {'location': str(tmp_path), 'base_url': None}}}
    return storages['tercerizados']
