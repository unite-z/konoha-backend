"""Rutas de la API REST del MOD-06 (montadas bajo `api/v1/` en `config/urls.py`)."""

from django.urls import path

from . import views

app_name = "reportes_indicadores"

urlpatterns = [
    # RF-REP-002
    path("reportes/cumplimiento-corte/", views.CumplimientoCorteView.as_view(), name="cumplimiento_corte"),
    # RF-REP-004
    path("reportes/distribucion-personal/", views.DistribucionPersonalView.as_view(), name="distribucion_personal"),
    # RF-REP-006
    path(
        "reportes/atenciones-espacio-sector/",
        views.AtencionesEspacioSectorView.as_view(),
        name="atenciones_espacio_sector",
    ),
    # RF-IA-001
    path("ia/extracciones/", views.ExtraccionesView.as_view(), name="extracciones"),
    path("ia/extracciones/metricas/", views.ExtraccionMetricasView.as_view(), name="extracciones_metricas"),
    path("ia/extracciones/<int:sugerencia_id>/", views.ExtraccionDetalleView.as_view(), name="extraccion_detalle"),
    path(
        "ia/extracciones/<int:sugerencia_id>/validar/",
        views.ExtraccionValidarView.as_view(),
        name="extraccion_validar",
    ),
    path(
        "ia/extracciones/<int:sugerencia_id>/rechazar/",
        views.ExtraccionRechazarView.as_view(),
        name="extraccion_rechazar",
    ),
]
