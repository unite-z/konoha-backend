"""Rutas raíz del back-end del MOD-06."""

from django.urls import include, path
from django.conf import settings
from drf_spectacular.views import SpectacularAPIView, SpectacularSwaggerView

urlpatterns = [
    path("api/v1/", include("reportes_indicadores.api.urls")),
    path("api/schema/", SpectacularAPIView.as_view(), name="schema"),
    path("api/docs/", SpectacularSwaggerView.as_view(url_name="schema"), name="docs"),
]

if settings.TERCERIZADOS_ENABLED:
    urlpatterns.append(path("api/tercerizados/", include("tercerizados.api.urls")))
