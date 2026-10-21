from io import StringIO
import pytest
from django.contrib.auth import get_user_model
from django.core.management import call_command, CommandError
from tercerizados import models as m

pytestmark = pytest.mark.django_db


def test_precarga_idempotente(settings):
    settings.DEBUG = True
    call_command('precargar_tercerizados', usuarios=True, stdout=StringIO())
    conteos = (m.Proveedor.objects.count(), m.Contrato.objects.count(), m.Ejemplar.objects.count())
    user = get_user_model().objects.get(username='mod05_jefe')
    assert not user.has_usable_password()
    user.set_password('cambiada-localmente')
    user.save()
    call_command('precargar_tercerizados', usuarios=True, stdout=StringIO())
    assert conteos == (m.Proveedor.objects.count(), m.Contrato.objects.count(), m.Ejemplar.objects.count())
    assert all(conteos)
    user.refresh_from_db()
    assert user.check_password('cambiada-localmente')
    assert not m.AtencionTercerizada.objects.exists()
    nuevo = m.Proveedor.objects.create(razon_social='Nuevo')
    assert nuevo.pk > 2


def test_precarga_falla_sin_sobrescribir(settings):
    settings.DEBUG = True
    m.Proveedor.objects.create(pk=1, razon_social='Proveedor operativo')
    with pytest.raises(CommandError):
        call_command('precargar_tercerizados', stdout=StringIO())
    assert m.Proveedor.objects.get(pk=1).razon_social == 'Proveedor operativo'
    assert not m.Contrato.objects.exists()


def test_precarga_no_produccion(settings):
    settings.DEBUG = False
    with pytest.raises(CommandError):
        call_command('precargar_tercerizados', stdout=StringIO())
