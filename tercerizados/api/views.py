from django.http import FileResponse
from rest_framework.authentication import SessionAuthentication, BasicAuthentication
from rest_framework.generics import GenericAPIView
from rest_framework.pagination import PageNumberPagination
from rest_framework.parsers import MultiPartParser
from rest_framework.response import Response
from rest_framework.serializers import CharField, ModelSerializer
from drf_spectacular.utils import extend_schema, OpenApiTypes
from django.utils import timezone
from django.db.models import Q
from .. import filtros as f, servicios, repositorios as repo, models as m, archivos
from ..errores import NoEncontrado, AlmacenamientoNoDisponible
from ..permisos import EstaAutenticado, PermisoModulo, rol_de
from . import serializers as s


class Paginacion(PageNumberPagination):
    page_size = 25


class Base(GenericAPIView):
    authentication_classes = [SessionAuthentication, BasicAuthentication]
    permission_classes = [PermisoModulo]
    pagination_class = Paginacion

    def entrada(self, clase, data=None):
        serializer = clase(data=self.request.data if data is None else data)
        serializer.is_valid(raise_exception=True)
        return serializer.validated_data

    def listado(self, consulta, serializer):
        pagina = self.paginate_queryset(consulta)
        return self.get_paginated_response(serializer(pagina, many=True).data)


class UsuarioActual(Base):
    """Quién es el usuario autenticado. Sirve también para comprobar credenciales al ingresar."""
    permission_classes = [EstaAutenticado]
    serializer_class = s.UsuarioActualSalida
    pagination_class = None

    @extend_schema(tags=['MOD-05'], responses={200: s.UsuarioActualSalida, 403: s.ErrorSalida},
                   description='Credenciales inválidas o ausentes responden 403. Un usuario válido sin rol del módulo recibe `rol: null`.')
    def get(self, request):
        usuario = request.user
        return Response(s.UsuarioActualSalida({'id': usuario.pk, 'usuario': usuario.get_username(),
            'nombre': s.nombre_usuario(usuario), 'rol': rol_de(usuario)}).data)


class Atenciones(Base):
    serializer_class = s.AtencionSalida
    solo_jefe = True

    @extend_schema(tags=['MOD-05'], responses=s.AtencionSalida(many=True),
                   parameters=f.parametros_openapi(repo.FILTROS_ATENCIONES, repo.BUSQUEDA_ATENCIONES))
    def get(self, request):
        return self.listado(repo.atenciones(request.query_params), s.AtencionSalida)

    @extend_schema(tags=['MOD-05'], request=s.CrearEntrada, responses={201: s.AtencionSalida, 400: s.ErrorSalida, 404: s.ErrorSalida})
    def post(self, request):
        resultado = servicios.crear(self.entrada(s.CrearEntrada), request.user)
        return Response(s.AtencionSalida(resultado).data, status=201)


class AtencionDetalle(Base):
    serializer_class = s.AtencionSalida

    @extend_schema(tags=['MOD-05'])
    def get(self, request, atencion_id):
        return Response(s.AtencionSalida(repo.atencion(atencion_id)).data)


class Accion(Base):
    serializer_class = s.AtencionSalida

    def ejecutar(self, request, atencion_id):
        resultado = servicios.modificar(atencion_id, self.operacion, self.entrada(self.entrada_class), request.user)
        return Response(s.AtencionSalida(resultado).data)


class OrdenCompra(Accion):
    operacion, entrada_class = 'orden-compra', s.OrdenEntrada

    @extend_schema(tags=['MOD-05'], request=s.OrdenEntrada, responses={200: s.AtencionSalida, 400: s.ErrorSalida, 409: s.ErrorSalida})
    def patch(self, request, atencion_id):
        return self.ejecutar(request, atencion_id)


class Inicio(Accion):
    operacion, entrada_class = 'inicio', s.InicioEntrada

    @extend_schema(tags=['MOD-05'], request=s.InicioEntrada, responses={200: s.AtencionSalida, 409: s.ErrorSalida})
    def post(self, request, atencion_id):
        return self.ejecutar(request, atencion_id)


class Fin(Accion):
    operacion, entrada_class = 'fin', s.FinEntrada

    @extend_schema(tags=['MOD-05'], request=s.FinEntrada, responses={200: s.AtencionSalida, 400: s.ErrorSalida, 409: s.ErrorSalida})
    def post(self, request, atencion_id):
        return self.ejecutar(request, atencion_id)


class Origen(Accion):
    operacion, entrada_class = 'origen', s.OrigenCambio
    solo_jefe = True

    @extend_schema(tags=['MOD-05'], request=s.OrigenCambio, responses={200: s.AtencionSalida, 400: s.ErrorSalida, 409: s.ErrorSalida})
    def patch(self, request, atencion_id):
        return self.ejecutar(request, atencion_id)


class AcuerdoUnidad(Accion):
    operacion, entrada_class = 'acuerdo-unidad', s.AcuerdoEntrada
    solo_jefe = True

    @extend_schema(tags=['MOD-05'], request=s.AcuerdoEntrada, responses={200: s.AtencionSalida, 400: s.ErrorSalida, 409: s.ErrorSalida})
    def patch(self, request, atencion_id):
        return self.ejecutar(request, atencion_id)


class Reportes(Base):
    serializer_class = s.ReporteSalida
    parser_classes = [MultiPartParser]

    @extend_schema(tags=['MOD-05'], responses=s.ReporteSalida(many=True))
    def get(self, request, atencion_id):
        return self.listado(repo.reportes(atencion_id), s.ReporteSalida)

    @extend_schema(tags=['MOD-05'], request=s.ReporteEntrada, responses={201: s.ReporteSalida, 400: s.ErrorSalida, 409: s.ErrorSalida, 503: s.ErrorSalida})
    def post(self, request, atencion_id):
        resultado = servicios.adjuntar(atencion_id, self.entrada(s.ReporteEntrada), request.user)
        return Response(s.ReporteSalida(resultado).data, status=201)


class ReporteDetalle(Base):
    serializer_class = s.ReporteSalida

    @extend_schema(tags=['MOD-05'])
    def get(self, request, atencion_id, reporte_id):
        return Response(s.ReporteSalida(repo.reporte(atencion_id, reporte_id)).data)


class Descargar(Base):
    @extend_schema(tags=['MOD-05'], responses={(200, 'application/octet-stream'): OpenApiTypes.BINARY})
    def get(self, request, atencion_id, reporte_id, documento_id):
        archivo = repo.documento(atencion_id, reporte_id, documento_id).archivo
        try:
            contenido = archivos.almacenamiento().open(archivo.ruta_almacenamiento, 'rb')
        except FileNotFoundError:
            raise NoEncontrado('El archivo no está disponible en el almacenamiento')
        except OSError:
            raise AlmacenamientoNoDisponible('No se pudo leer el archivo; reintenta la descarga')
        response = FileResponse(contenido, as_attachment=True, filename=archivo.nombre_original, content_type=archivo.tipo_mime)
        response['X-Content-Type-Options'] = 'nosniff'
        response['Cache-Control'] = 'private, no-store'
        return response


def _vigente(consulta, valor):
    hoy = timezone.localdate()  # America/Lima
    vigente = Q(vigencia_inicio__lte=hoy, vigencia_fin__gte=hoy)
    return consulta.filter(vigente) if valor else consulta.exclude(vigente)


# Consultas de selección; TODO: mantenimiento de proveedores/contratos en su módulo propietario.
def catalogo(modelo, campos, *, orden=('pk',), buscar=(), filtros=(), relaciones=(), extras=None):
    """Catálogo de solo lectura paginado, con búsqueda `q` y filtros declarados.

    `extras` agrega campos de texto de solo lectura `{nombre: 'ruta.del.atributo'}` (nombres de relaciones).
    """
    declarados = {nombre: CharField(source=ruta, read_only=True, allow_null=True, default=None)
                  for nombre, ruta in (extras or {}).items()}
    meta = type('Meta', (), {'model': modelo, 'fields': [*campos, *declarados]})
    serializer = type(f'{modelo.__name__}Catalogo', (ModelSerializer,), {'Meta': meta, **declarados})

    class Catalogo(Base):
        serializer_class = serializer

        @extend_schema(tags=['MOD-05 Catálogos'], responses=serializer(many=True),
                       parameters=f.parametros_openapi(filtros, buscar))
        def get(self, request):
            consulta = modelo.objects.select_related(*relaciones).order_by(*orden)
            consulta = f.filtrar(consulta, request.query_params, filtros, buscar)
            return self.listado(consulta, serializer)
    return Catalogo.as_view()
