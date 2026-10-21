from rest_framework import serializers as s
from .. import models as m


class Estricto(s.Serializer):
    def to_internal_value(self, data):
        desconocidos = set(data) - set(self.fields)
        if desconocidos:
            raise s.ValidationError({campo: 'Campo no admitido' for campo in desconocidos})
        return super().to_internal_value(data)


class AlcanceEntrada(Estricto):
    area_verde_id = s.IntegerField(min_value=1, required=False, allow_null=True)
    ejemplar_id = s.IntegerField(min_value=1, required=False, allow_null=True)
    metrado = s.DecimalField(max_digits=12, decimal_places=2, min_value=0, required=False, allow_null=True)
    unidad_medida_id = s.IntegerField(min_value=1, required=False, allow_null=True)


class OrigenEntrada(Estricto):
    origen = s.ChoiceField(choices=m.AtencionTercerizada.Origen.choices)
    unidad_solicitante_id = s.IntegerField(min_value=1, required=False, allow_null=True)
    unidad_provisional = s.CharField(max_length=150, required=False)


class CrearEntrada(OrigenEntrada):
    contrato_id = s.IntegerField(min_value=1)
    descripcion = s.CharField()
    es_riesgo = s.BooleanField(default=False)
    alcances = AlcanceEntrada(many=True, allow_empty=False)


class OrigenCambio(OrigenEntrada):
    motivo = s.CharField()


class OrdenEntrada(Estricto):
    numero_orden_compra = s.CharField(max_length=30)
    motivo = s.CharField(required=False)


class InicioEntrada(Estricto):
    fecha_hora_inicio = s.DateTimeField()


class FinEntrada(Estricto):
    fecha_hora_fin = s.DateTimeField()


class AcuerdoEntrada(Estricto):
    acuerdo_unidad = s.BooleanField(allow_null=True)
    fecha_acuerdo_unidad = s.DateField(allow_null=True, default=None)
    comentarios_acuerdo_unidad = s.CharField(allow_blank=True, default='')

    def validate(self, datos):
        if (datos['acuerdo_unidad'] is None) != (datos['fecha_acuerdo_unidad'] is None):
            raise s.ValidationError('El acuerdo comunicado requiere fecha; el pendiente no lleva fecha')
        return datos


class DocumentoEntrada(Estricto):
    categoria = s.ChoiceField(choices=m.ArchivoAsociado.Categoria.choices)
    alcance_id = s.IntegerField(min_value=1, required=False, allow_null=True)
    descripcion = s.CharField(max_length=200, required=False, allow_blank=True)


class ReporteEntrada(Estricto):
    fecha_recepcion = s.DateField()
    resumen = s.CharField(required=False, allow_blank=True)
    documentos = s.JSONField(help_text='Lista JSON de categoría, alcance_id opcional y descripción; mismo orden que archivos.')
    archivos = s.ListField(child=s.FileField(), allow_empty=False, write_only=True)

    def validate_documentos(self, valor):
        entrada = DocumentoEntrada(data=valor, many=True, allow_empty=False)
        entrada.is_valid(raise_exception=True)
        return entrada.validated_data


class AlcanceSalida(s.ModelSerializer):
    class Meta:
        model = m.AlcanceAtencion
        fields = ['id', 'area_verde_id', 'ejemplar_id', 'metrado', 'unidad_medida_id']


class HistorialSalida(s.ModelSerializer):
    class Meta:
        model = m.HistorialEstado
        fields = ['estado_anterior', 'estado_nuevo', 'usuario_id', 'fecha_hora']


class AtencionSalida(s.ModelSerializer):
    alcances = AlcanceSalida(many=True, read_only=True)
    historial = HistorialSalida(many=True, read_only=True)

    class Meta:
        model = m.AtencionTercerizada
        fields = ['id', 'codigo', 'contrato_id', 'tipo_servicio_id', 'clase', 'descripcion', 'origen',
            'unidad_solicitante_id', 'solicitado_por_id', 'numero_orden_compra', 'fecha_orden_compra',
            'fecha_hora_inicio', 'fecha_hora_fin', 'estado', 'es_riesgo', 'acuerdo_unidad',
            'fecha_acuerdo_unidad', 'comentarios_acuerdo_unidad', 'fecha_creacion', 'fecha_modificacion',
            'usuario_creacion_id', 'usuario_modificacion_id', 'alcances', 'historial']


class ArchivoSalida(s.ModelSerializer):
    class Meta:
        model = m.Archivo
        fields = ['id', 'nombre_original', 'tipo_mime', 'tamano_bytes', 'hash_contenido']


class DocumentoSalida(s.ModelSerializer):
    archivo = ArchivoSalida(read_only=True)

    class Meta:
        model = m.ArchivoAsociado
        fields = ['id', 'categoria', 'alcance_id', 'descripcion', 'archivo']


class ReporteSalida(s.ModelSerializer):
    documentos = DocumentoSalida(many=True, read_only=True)

    class Meta:
        model = m.ReporteProveedor
        fields = ['id', 'atencion_id', 'revision', 'fecha_recepcion', 'resumen', 'documentos',
                  'usuario_creacion_id', 'fecha_creacion']


class ErrorSalida(s.Serializer):
    codigo = s.CharField()
    detalle = s.CharField()
