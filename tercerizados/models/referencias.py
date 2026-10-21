"""Proyecciones locales de catálogos. TODO: integrar módulos propietarios sin duplicar IDs."""
from django.db import models


class Proveedor(models.Model):
    razon_social = models.CharField(max_length=200)
    ruc = models.CharField(max_length=11, null=True, blank=True, unique=True)
    activo = models.BooleanField(default=True)


class TipoServicio(models.Model):
    nombre = models.CharField(max_length=120)
    ejecutor = models.CharField(max_length=40)
    es_riesgo = models.BooleanField(default=False)
    activo = models.BooleanField(default=True)


class UnidadMedida(models.Model):
    nombre = models.CharField(max_length=80)
    simbolo = models.CharField(max_length=20, blank=True)


class Contrato(models.Model):
    numero = models.CharField(max_length=40, unique=True)
    proveedor = models.ForeignKey(Proveedor, on_delete=models.PROTECT)
    tipo_servicio = models.ForeignKey(TipoServicio, on_delete=models.PROTECT)
    vigencia_inicio = models.DateField()
    vigencia_fin = models.DateField()

    class Meta:
        constraints = [models.CheckConstraint(condition=models.Q(vigencia_fin__gte=models.F('vigencia_inicio')), name='mod05_contrato_fechas')]


class AreaVerde(models.Model):
    nombre = models.CharField(max_length=150)
    activo = models.BooleanField(default=True)


class Ejemplar(models.Model):
    codigo = models.CharField(max_length=40, unique=True)
    area_verde = models.ForeignKey(AreaVerde, null=True, blank=True, on_delete=models.PROTECT)


class UnidadInstitucional(models.Model):
    nombre = models.CharField(max_length=150, unique=True)
    provisional = models.BooleanField(default=False)
    activo = models.BooleanField(default=True)
