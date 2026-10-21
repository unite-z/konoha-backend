"""
Traducción de las excepciones de dominio del MOD-06 a respuestas HTTP (superprompt, sección 4.5).

Cuerpo de error: `{"codigo": "<NombreExcepcion>", "detalle": "<mensaje que dice qué hacer>"}`.
Los errores propios de DRF (validación de serializers, 403, 404 de ruta, etc.) conservan el formato por
defecto de DRF.

Registrado en `REST_FRAMEWORK["EXCEPTION_HANDLER"]`.
"""

from __future__ import annotations

import logging
from typing import Any

from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import exception_handler

from ..comun.errores import (
    ConfiguracionFaltante,
    ErrorDominio,
    FuenteDatosNoDisponible,
    ParametroInvalido,
    RecursoNoEncontrado,
    ServicioIANoDisponible,
    TipoArchivoNoAdmitido,
    TransicionNoPermitida,
)

logger = logging.getLogger(__name__)

ESTADO_HTTP_POR_EXCEPCION: dict[type[ErrorDominio], int] = {
    ParametroInvalido: status.HTTP_400_BAD_REQUEST,
    RecursoNoEncontrado: status.HTTP_404_NOT_FOUND,
    TransicionNoPermitida: status.HTTP_409_CONFLICT,
    ConfiguracionFaltante: status.HTTP_422_UNPROCESSABLE_ENTITY,
    TipoArchivoNoAdmitido: status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
    FuenteDatosNoDisponible: status.HTTP_503_SERVICE_UNAVAILABLE,
    ServicioIANoDisponible: status.HTTP_503_SERVICE_UNAVAILABLE,
}


def manejar_excepcion(exc: Exception, context: dict[str, Any]) -> Response | None:
    """Manejador de excepciones de DRF para el MOD-06."""
    if isinstance(exc, ErrorDominio):
        codigo_http = next(
            (estado for clase, estado in ESTADO_HTTP_POR_EXCEPCION.items() if isinstance(exc, clase)),
            status.HTTP_500_INTERNAL_SERVER_ERROR,
        )
        if codigo_http >= 500:
            logger.warning("%s: %s", type(exc).__name__, exc.mensaje)
        return Response({"codigo": type(exc).__name__, "detalle": exc.mensaje}, status=codigo_http)
    return exception_handler(exc, context)
