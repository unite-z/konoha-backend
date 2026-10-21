"""
Selección de la fuente de datos del MOD-06 (patrón Estrategia).

`obtener_fuente_datos()` es el **único** lugar del código que lee `settings.DATA_SOURCE`:
- `mock` → `FuenteDatosMock` (JSON en `mock-data/`);
- `db` → `FuenteDatosBD` (aún no disponible: responde 503).

Ningún otro módulo debe leer `DATA_SOURCE` ni abrir los JSON directamente.
"""

from __future__ import annotations

from django.conf import settings

from .base import FuenteDatos
from .bd import FuenteDatosBD
from .mock import FuenteDatosMock

__all__ = ["FuenteDatos", "FuenteDatosBD", "FuenteDatosMock", "obtener_fuente_datos"]


def obtener_fuente_datos() -> FuenteDatos:
    """Devuelve la fuente de datos configurada en `settings.DATA_SOURCE` (`mock` o `db`)."""
    if settings.DATA_SOURCE == "db":
        return FuenteDatosBD()
    return FuenteDatosMock()
