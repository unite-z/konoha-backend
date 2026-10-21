import uuid
from django.conf import settings
from django.db import models
from .referencias import Contrato, TipoServicio, AreaVerde, Ejemplar, UnidadMedida, UnidadInstitucional


def codigo_atencion():
    return f'AT-{uuid.uuid4().hex}'


class Auditable(models.Model):
    fecha_creacion = models.DateTimeField(auto_now_add=True)
    fecha_modificacion = models.DateTimeField(auto_now=True)
    usuario_creacion = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name='+')
    usuario_modificacion = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name='+')

    class Meta:
        abstract = True


class AtencionTercerizada(Auditable):
    class Estado(models.TextChoices):
        REQUERIMIENTO = 'Requerimiento registrado'
        OC = 'OC referenciada'
        EJECUCION = 'En ejecución'
        EJECUTADO = 'Ejecutado pendiente de reporte'
        REPORTE = 'Reporte adjunto'

    class Origen(models.TextChoices):
        DEMANDA = 'Demanda'
        ESTACIONAL = 'ProgramacionEstacional'
        UNIDAD = 'SolicitudUnidad'

    codigo = models.CharField(max_length=35, unique=True, default=codigo_atencion, editable=False)
    contrato = models.ForeignKey(Contrato, on_delete=models.PROTECT)
    tipo_servicio = models.ForeignKey(TipoServicio, on_delete=models.PROTECT)
    clase = models.CharField(max_length=30, default='ProgramadaContrato', editable=False)
    descripcion = models.TextField()
    origen = models.CharField(max_length=30, choices=Origen.choices)
    unidad_solicitante = models.ForeignKey(UnidadInstitucional, null=True, blank=True, on_delete=models.PROTECT)
    solicitado_por = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT, related_name='+')
    numero_orden_compra = models.CharField(max_length=30, blank=True)
    fecha_orden_compra = models.DateTimeField(null=True, blank=True)
    fecha_hora_inicio = models.DateTimeField(null=True, blank=True)
    fecha_hora_fin = models.DateTimeField(null=True, blank=True)
    estado = models.CharField(max_length=40, choices=Estado.choices, default=Estado.REQUERIMIENTO)
    es_riesgo = models.BooleanField(default=False)
    acuerdo_unidad = models.BooleanField(null=True, blank=True)
    fecha_acuerdo_unidad = models.DateField(null=True, blank=True)
    comentarios_acuerdo_unidad = models.TextField(blank=True)

    class Meta:
        ordering = ['-fecha_creacion', '-pk']
        constraints = [
            models.CheckConstraint(condition=models.Q(fecha_hora_fin__isnull=True) | (models.Q(fecha_hora_inicio__isnull=False) & models.Q(fecha_hora_fin__gte=models.F('fecha_hora_inicio'))), name='mod05_atencion_fechas'),
            models.CheckConstraint(condition=(models.Q(origen='SolicitudUnidad', unidad_solicitante__isnull=False) | (models.Q(origen__in=['Demanda', 'ProgramacionEstacional'], unidad_solicitante__isnull=True))), name='mod05_origen_unidad'),
        ]


class AlcanceAtencion(models.Model):
    atencion = models.ForeignKey(AtencionTercerizada, on_delete=models.PROTECT, related_name='alcances')
    area_verde = models.ForeignKey(AreaVerde, null=True, blank=True, on_delete=models.PROTECT)
    ejemplar = models.ForeignKey(Ejemplar, null=True, blank=True, on_delete=models.PROTECT)
    metrado = models.DecimalField(max_digits=12, decimal_places=2, null=True, blank=True)
    unidad_medida = models.ForeignKey(UnidadMedida, null=True, blank=True, on_delete=models.PROTECT)

    class Meta:
        constraints = [
            models.CheckConstraint(condition=(models.Q(area_verde__isnull=False, ejemplar__isnull=True) | models.Q(area_verde__isnull=True, ejemplar__isnull=False)), name='mod05_alcance_destino'),
            models.UniqueConstraint(fields=['atencion', 'area_verde'], name='mod05_alcance_area_unica'),
            models.UniqueConstraint(fields=['atencion', 'ejemplar'], name='mod05_alcance_ejemplar_unico'),
            models.CheckConstraint(condition=(models.Q(metrado__isnull=True, unidad_medida__isnull=True) | models.Q(metrado__isnull=False, metrado__gte=0, unidad_medida__isnull=False)), name='mod05_metrado_unidad'),
        ]


class ReporteProveedor(Auditable):
    atencion = models.ForeignKey(AtencionTercerizada, on_delete=models.PROTECT, related_name='reportes')
    revision = models.PositiveIntegerField()
    fecha_recepcion = models.DateField()
    resumen = models.TextField(blank=True)

    class Meta:
        ordering = ['-revision']
        constraints = [models.UniqueConstraint(fields=['atencion', 'revision'], name='mod05_revision_unica')]


class Archivo(models.Model):
    nombre_original = models.CharField(max_length=200)
    tipo_mime = models.CharField(max_length=100)
    tamano_bytes = models.PositiveBigIntegerField()
    ruta_almacenamiento = models.CharField(max_length=400, unique=True)
    hash_contenido = models.CharField(max_length=64)


class ArchivoAsociado(models.Model):
    class Categoria(models.TextChoices):
        INFORME = 'InformeFinal'
        METRADO = 'Metrado'
        PLANTAS = 'PlantasAtendidas'
        FICHAS = 'FichasDiarias'
        CHARLAS = 'CharlasTecnicas'
        PLAGAS = 'PlagasAtendidas'
        PRODUCTOS = 'ProductosAplicados'
        EVIDENCIA = 'EvidenciaEspacio'
        RUTA = 'RutaRecorrida'

    reporte = models.ForeignKey(ReporteProveedor, on_delete=models.PROTECT, related_name='documentos')
    archivo = models.OneToOneField(Archivo, on_delete=models.PROTECT)
    alcance = models.ForeignKey(AlcanceAtencion, null=True, blank=True, on_delete=models.PROTECT)
    categoria = models.CharField(max_length=30, choices=Categoria.choices)
    descripcion = models.CharField(max_length=200, blank=True)


class RegistroAuditoria(models.Model):
    entidad = models.CharField(max_length=60)
    registro_id = models.BigIntegerField()
    accion = models.CharField(max_length=40)
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    fecha_hora = models.DateTimeField(auto_now_add=True)
    valores_anteriores = models.JSONField(default=dict)
    valores_nuevos = models.JSONField(default=dict)
    motivo = models.TextField(blank=True)


class HistorialEstado(models.Model):
    atencion = models.ForeignKey(AtencionTercerizada, on_delete=models.PROTECT, related_name='historial')
    estado_anterior = models.CharField(max_length=40, blank=True)
    estado_nuevo = models.CharField(max_length=40)
    usuario = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    fecha_hora = models.DateTimeField(auto_now_add=True)
