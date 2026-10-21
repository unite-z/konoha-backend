from django.urls import path
from .. import models as m
from . import views as v

urlpatterns = [
    path('atenciones/', v.Atenciones.as_view()),
    path('atenciones/<int:atencion_id>/', v.AtencionDetalle.as_view()),
    path('atenciones/<int:atencion_id>/orden-compra/', v.OrdenCompra.as_view()),
    path('atenciones/<int:atencion_id>/inicio/', v.Inicio.as_view()),
    path('atenciones/<int:atencion_id>/fin/', v.Fin.as_view()),
    path('atenciones/<int:atencion_id>/origen/', v.Origen.as_view()),
    path('atenciones/<int:atencion_id>/acuerdo-unidad/', v.AcuerdoUnidad.as_view()),
    path('atenciones/<int:atencion_id>/reportes/', v.Reportes.as_view()),
    path('atenciones/<int:atencion_id>/reportes/<int:reporte_id>/', v.ReporteDetalle.as_view()),
    path('atenciones/<int:atencion_id>/reportes/<int:reporte_id>/documentos/<int:documento_id>/descarga/', v.Descargar.as_view()),
    path('catalogos/proveedores/', v.catalogo(m.Proveedor, ['id', 'razon_social', 'activo'])),
    path('catalogos/contratos/', v.catalogo(m.Contrato, ['id', 'numero', 'proveedor_id', 'tipo_servicio_id', 'vigencia_inicio', 'vigencia_fin'])),
    path('catalogos/tipos-servicio/', v.catalogo(m.TipoServicio, ['id', 'nombre', 'ejecutor', 'es_riesgo', 'activo'])),
    path('catalogos/areas-verdes/', v.catalogo(m.AreaVerde, ['id', 'nombre', 'activo'])),
    path('catalogos/ejemplares/', v.catalogo(m.Ejemplar, ['id', 'codigo', 'area_verde_id'])),
    path('catalogos/unidades-medida/', v.catalogo(m.UnidadMedida, ['id', 'nombre', 'simbolo'])),
    path('catalogos/unidades-institucionales/', v.catalogo(m.UnidadInstitucional, ['id', 'nombre', 'provisional', 'activo'])),
]
