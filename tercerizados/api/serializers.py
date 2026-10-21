from drf_spectacular.utils import extend_schema_field
from rest_framework import serializers as s
from .. import models as m


def nombre_usuario(usuario):
    """Nombre completo del usuario; si no lo tiene, su nombre de usuario."""
    return (usuario.get_full_name().strip() or usuario.get_username()) if usuario else None


def texto(origen):
    return s.CharField(source=origen, read_only=True, allow_null=True, default=None)


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
    area_verde_nombre = texto('area_verde.nombre')
    ejemplar_codigo = texto('ejemplar.codigo')
    ejemplar_area_verde_nombre = texto('ejemplar.area_verde.nombre')
    unidad_medida_nombre = texto('unidad_medida.nombre')
    unidad_medida_simbolo = texto('unidad_medida.simbolo')

    class Meta:
        model = m.AlcanceAtencion
        fields = ['id', 'area_verde_id', 'area_verde_nombre', 'ejemplar_id', 'ejemplar_codigo',
                  'ejemplar_area_verde_nombre', 'metrado', 'unidad_medida_id', 'unidad_medida_nombre',
                  'unidad_medida_simbolo']


class HistorialSalida(s.ModelSerializer):
    usuario_nombre = s.SerializerMethodField()

    class Meta:
        model = m.HistorialEstado
        fields = ['estado_anterior', 'estado_nuevo', 'usuario_id', 'usuario_nombre', 'fecha_hora']

    def get_usuario_nombre(self, obj) -> str | None:
        return nombre_usuario(obj.usuario)


class AtencionSalida(s.ModelSerializer):
    contrato_numero = texto('contrato.numero')
    proveedor_id = s.IntegerField(source='contrato.proveedor_id', read_only=True)
    proveedor_nombre = texto('contrato.proveedor.razon_social')
    tipo_servicio_nombre = texto('tipo_servicio.nombre')
    unidad_solicitante_nombre = texto('unidad_solicitante.nombre')
    unidad_solicitante_provisional = s.BooleanField(source='unidad_solicitante.provisional', read_only=True, allow_null=True, default=None)
    solicitado_por_nombre = s.SerializerMethodField()
    usuario_creacion_nombre = s.SerializerMethodField()
    alcances = s.SerializerMethodField()
    historial = s.SerializerMethodField()

    class Meta:
        model = m.AtencionTercerizada
        fields = ['id', 'codigo', 'contrato_id', 'contrato_numero', 'proveedor_id', 'proveedor_nombre',
            'tipo_servicio_id', 'tipo_servicio_nombre', 'clase', 'descripcion', 'origen',
            'unidad_solicitante_id', 'unidad_solicitante_nombre', 'unidad_solicitante_provisional',
            'solicitado_por_id', 'solicitado_por_nombre', 'numero_orden_compra', 'fecha_orden_compra',
            'fecha_hora_inicio', 'fecha_hora_fin', 'estado', 'es_riesgo', 'acuerdo_unidad',
            'fecha_acuerdo_unidad', 'comentarios_acuerdo_unidad', 'fecha_creacion', 'fecha_modificacion',
            'usuario_creacion_id', 'usuario_creacion_nombre', 'usuario_modificacion_id', 'alcances', 'historial']

    def get_solicitado_por_nombre(self, obj) -> str | None:
        return nombre_usuario(obj.solicitado_por)

    def get_usuario_creacion_nombre(self, obj) -> str | None:
        return nombre_usuario(obj.usuario_creacion)

    # Se ordenan en memoria para que el orden sea estable también cuando no vienen precargados.
    @extend_schema_field(AlcanceSalida(many=True))
    def get_alcances(self, obj):
        return AlcanceSalida(sorted(obj.alcances.all(), key=lambda a: a.pk), many=True).data

    @extend_schema_field(HistorialSalida(many=True))
    def get_historial(self, obj):
        return HistorialSalida(sorted(obj.historial.all(), key=lambda h: (h.fecha_hora, h.pk)), many=True).data


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
    usuario_creacion_nombre = s.SerializerMethodField()

    class Meta:
        model = m.ReporteProveedor
        fields = ['id', 'atencion_id', 'revision', 'fecha_recepcion', 'resumen', 'documentos',
                  'usuario_creacion_id', 'usuario_creacion_nombre', 'fecha_creacion']

    def get_usuario_creacion_nombre(self, obj) -> str | None:
        return nombre_usuario(obj.usuario_creacion)


class ErrorSalida(s.Serializer):
    codigo = s.CharField()
    detalle = s.CharField()


class UsuarioActualSalida(s.Serializer):
    id = s.IntegerField()
    usuario = s.CharField()
    nombre = s.CharField()
    rol = s.CharField(allow_null=True, help_text='Rol de mayor jerarquía en MOD-05; null si el usuario no tiene acceso al módulo')
